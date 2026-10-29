package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoFeature;
import com.dan.spatial.geo.GeoProjection;
import com.dan.spatial.geo.GeometryConverter;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * FSpatialMap — interaktive Deutschlandkarte im ReagenzglasBar-Stil.
 *
 * ── Rendering-Reihenfolge ────────────────────────────────────────
 *   1  Hintergrund (dunkel, Vignette)
 *   2  Koordinatengitter (dezent)
 *   3  ADM0 Außengrenze (Basis-Fill ohne Interaktion)
 *   4  ADM1 Bundesländer Normal (alle nicht-hover, nicht-selected)
 *   5  ADM1 Hover  (Türkis-Glow, animiert)
 *   6  ADM1 Selected (Weinrot-Glow)
 *   7  ADM1 Labels (Bundesland-Namen, zoom-abhängig)
 *   8  Glas-Rim (äußerer Leuchtrand)
 *   9  Kompass-Rose (klein, rechts unten)
 *
 * ── Interaktion ──────────────────────────────────────────────────
 *   Mausrad      Zoom um Cursor-Punkt
 *   Drag         Pan
 *   Klick        Feature selektieren / deselektieren
 *   Hover        Türkis-Highlight mit Easing-Animation
 *
 * ── Animation ────────────────────────────────────────────────────
 *   hoverProgress  0→1, Easing 0.12 pro Frame, 16 ms Timer
 *   selectedGlow   pulsiert langsam (sin-basiert)
 *
 * ── Listener ─────────────────────────────────────────────────────
 *   FeatureSelectionListener   featureSelected(GeoFeature)
 *   MouseMoveListener          mouseMoved(lon, lat)
 */
public class FSpatialMap extends JPanel {

    // ── Farben ────────────────────────────────────────────────────
    public Color colorTop         = new Color(0x00, 0xB5, 0xAD);  // Türkis
    public Color colorBottom      = new Color(0x8B, 0x00, 0x24);  // Weinrot
    public Color bgDeep           = new Color(0x08, 0x0A, 0x10);  // fast schwarz
    public Color bgCanvas         = new Color(0x0E, 0x10, 0x17);  // dunkelblau
    public Color landNormal       = new Color(0x18, 0x22, 0x38);  // blaustichig dunkel
    public Color landBorder       = new Color(0x00, 0xB5, 0xAD, 55); // Türkis-Rand
    public Color landHover        = new Color(0x00, 0x66, 0x64, 190);
    public Color landSelected     = new Color(0x7A, 0x00, 0x1E, 175);
    public Color rimColor         = new Color(140, 200, 255, 90);
    public Color gridColor        = new Color(255, 255, 255, 14);
    public Color labelColor       = new Color(200, 225, 255, 190);
    public Color labelHover       = new Color(255, 255, 255, 240);

    // ── JXMapViewer-Layer (optional, unter eigenen Shapes) ────────
    private FJxMapsLayer jxMapsLayer = null;
    private boolean jxMapsEnabled = false;

    // ── Daten ─────────────────────────────────────────────────────
    private List<GeoFeature> adm0 = new ArrayList<>();
    private List<GeoFeature> adm1 = new ArrayList<>();
    private GeoFeature hovered  = null;
    private GeoFeature selected = null;

    // ── Projektion ────────────────────────────────────────────────
    private final GeoProjection proj = new GeoProjection();
    private boolean projReady = false;

    // ── Drag-State ────────────────────────────────────────────────
    private int dragLastX, dragLastY;
    private boolean dragging = false;
    private int dragStartX, dragStartY;

    // ── Animation ─────────────────────────────────────────────────
    private float  hoverProg    = 0f;       // 0→1 Hover-Glow
    private double glowPhase    = 0.0;      // für pulsierenden Selected-Glow
    private Timer  animTimer;
    private boolean animRunning = false;

    // ── Listener ──────────────────────────────────────────────────
    private final List<FeatureSelectionListener> selListeners  = new ArrayList<>();
    private final List<MouseMoveListener>        moveListeners = new ArrayList<>();

    // ── Off-Screen Buffer (Performance) ───────────────────────────
    private BufferedImage baseBuffer    = null;   // ADM0 + ADM1 Normal
    private boolean       bufferDirty  = true;

    // ═══════════════════════════════════════════════════════════════
    // Konstruktor
    // ═══════════════════════════════════════════════════════════════

    public FSpatialMap() {
        setBackground(bgCanvas);
        setOpaque(true);
        setPreferredSize(new Dimension(720, 580));
        initMouse();
        initAnimation();
    }

    // ═══════════════════════════════════════════════════════════════
    // Daten-API
    // ═══════════════════════════════════════════════════════════════

    public void setADM0Features(List<GeoFeature> features) {
        this.adm0 = features != null ? features : new ArrayList<>();
        invalidate(true);
    }

    public void setADM1Features(List<GeoFeature> features) {
        this.adm1 = features != null ? features : new ArrayList<>();
        invalidate(true);
    }

    /**
     * Feature programmatisch selektieren (z.B. aus dem Dropdown).
     * Zoomt auf das Feature und feuert FeatureSelectionListener.
     */
    public void selectFeature(GeoFeature f) {
        if (selected != null) selected.selected = false;
        selected = f;
        if (f != null) {
            f.selected = true;
            zoomToFeature(f);
        }
        invalidate(false);
        fireSelectionChanged(f);
    }

    /**
     * JXMapViewer-Hintergrund aktivieren.
     * jxmapviewer2-2.8.jar muss im Classpath sein.
     * Kann jederzeit aufgerufen werden; schlaegt lautlos fehl wenn JXMapViewer fehlt.
     */
    public void enableJxMaps() {
        if (jxMapsLayer == null) {
            jxMapsLayer = new FJxMapsLayer();
            jxMapsLayer.init(getWidth() > 0 ? getWidth() : 720,
                              getHeight() > 0 ? getHeight() : 580);
        }
        jxMapsEnabled = jxMapsLayer.isAvailable();
        bufferDirty = true;
        repaint();
    }

    public void disableJxMaps() {
        jxMapsEnabled = false;
        bufferDirty = true;
        repaint();
    }

    public boolean isJxMapsEnabled() { return jxMapsEnabled; }

    /** Zoom zurück auf Deutschland-Gesamt */
    public void zoomFit() {
        proj.resetView();
        invalidate(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Paint
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            applyHints(g2);

            // 1. Projektion initialisieren (nur beim ersten Aufruf / Resize)
            if (!projReady && w > 0 && h > 0) {
                initProjection(w, h);
            }

            // 2. Paths sicherstellen
            ensurePaths(adm0);
            ensurePaths(adm1);

            // 3. Base-Buffer (Hintergrund + ADM0 + normale ADM1)
            if (bufferDirty || baseBuffer == null
                    || baseBuffer.getWidth() != w || baseBuffer.getHeight() != h) {
                renderBaseBuffer(w, h);
            }
            g2.drawImage(baseBuffer, 0, 0, null);

            // 4. Hover
            if (hovered != null && hovered != selected) {
                drawHoverFeature(g2, hovered);
            }

            // 5. Selected
            if (selected != null) {
                drawSelectedFeature(g2, selected);
            }

            // 6. Labels (zoom-abhängig)
            if (proj.getTotalScale() > 20) {
                drawLabels(g2);
            }

            // 7. Kompass
            drawCompass(g2, w - 46, h - 46, 34);

            // 8. Glas-Rim
            drawGlassRim(g2, w, h);

        } finally {
            g2.dispose();
        }
    }

    // ── Base-Buffer rendern ────────────────────────────────────────

    private void renderBaseBuffer(int w, int h) {
        baseBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = baseBuffer.createGraphics();
        try {
            applyHints(g2);

            // Hintergrund
            g2.setColor(bgCanvas);
            g2.fillRect(0, 0, w, h);

            // JXMapViewer-Hintergrund (wenn aktiviert)
            if (jxMapsEnabled && jxMapsLayer != null) {
                jxMapsLayer.syncProjection(proj, w, h);
                jxMapsLayer.drawBackground(g2, w, h);
                // JXMapViewer laedt Tiles asynchron -> fuer 3s weiter repainten
                scheduleJxMapsRefresh();
            } else {
                drawVignette(g2, w, h);
            }

            // Koordinatengitter
            drawGrid(g2, w, h);

            // ADM0 (Außengrenze, keine Interaktion)
            g2.setColor(new Color(0x12, 0x1A, 0x2C));
            for (GeoFeature f : adm0) {
                for (Path2D path : f.paths) g2.fill(path);
            }
            // ADM0-Rand (sehr dezent)
            g2.setColor(new Color(colorTop.getRed(), colorTop.getGreen(),
                                  colorTop.getBlue(), 30));
            g2.setStroke(new BasicStroke(1.0f));
            for (GeoFeature f : adm0) {
                for (Path2D path : f.paths) g2.draw(path);
            }

            // ADM1 Normal
            for (GeoFeature f : adm1) {
                if (f == hovered || f == selected) continue;
                // Subtiler Gradient pro Feature (via Bounds)
                for (Path2D path : f.paths) {
                    g2.setColor(landNormal);
                    g2.fill(path);
                }
                // Rand
                g2.setColor(landBorder);
                g2.setStroke(new BasicStroke(0.7f));
                for (Path2D path : f.paths) g2.draw(path);
            }

        } finally {
            g2.dispose();
        }
        bufferDirty = false;
    }

    // ── Hover ─────────────────────────────────────────────────────

    private void drawHoverFeature(Graphics2D g2, GeoFeature f) {
        if (f.paths.length == 0) return;
        float p = hoverProg;

        // Äußerer Glow (mehrschichtig)
        for (int i = 5; i >= 1; i--) {
            int alpha = (int)(p * 18 * (6 - i));
            g2.setColor(new Color(colorTop.getRed(), colorTop.getGreen(),
                                  colorTop.getBlue(), Math.min(255, alpha)));
            g2.setStroke(new BasicStroke(i * 2.8f, BasicStroke.CAP_ROUND,
                                         BasicStroke.JOIN_ROUND));
            for (Path2D path : f.paths) g2.draw(path);
        }
        // Fill
        int fillAlpha = (int)(p * 190);
        g2.setColor(new Color(landHover.getRed(), landHover.getGreen(),
                              landHover.getBlue(), fillAlpha));
        for (Path2D path : f.paths) g2.fill(path);

        // Türkis-Rand
        g2.setColor(new Color(colorTop.getRed(), colorTop.getGreen(),
                              colorTop.getBlue(), (int)(p * 210)));
        g2.setStroke(new BasicStroke(1.6f));
        for (Path2D path : f.paths) g2.draw(path);

        // Shine-Stripe (diagonaler Glanzstreifen oben-links)
        drawShine(g2, f, p * 0.25f);
    }

    // ── Selected ──────────────────────────────────────────────────

    private void drawSelectedFeature(Graphics2D g2, GeoFeature f) {
        if (f.paths.length == 0) return;

        // Pulsierender Weinrot-Glow
        float pulse = (float)(0.75 + 0.25 * Math.sin(glowPhase));
        for (int i = 6; i >= 1; i--) {
            int alpha = (int)(pulse * 22 * (7 - i));
            g2.setColor(new Color(colorBottom.getRed(), colorBottom.getGreen(),
                                  colorBottom.getBlue(), Math.min(255, alpha)));
            g2.setStroke(new BasicStroke(i * 2.5f, BasicStroke.CAP_ROUND,
                                         BasicStroke.JOIN_ROUND));
            for (Path2D path : f.paths) g2.draw(path);
        }
        // Fill
        g2.setColor(new Color(landSelected.getRed(), landSelected.getGreen(),
                              landSelected.getBlue(), (int)(pulse * 185)));
        for (Path2D path : f.paths) g2.fill(path);

        // Weinrot-Rand mit innerer Türkis-Linie
        g2.setColor(colorBottom);
        g2.setStroke(new BasicStroke(2.2f));
        for (Path2D path : f.paths) g2.draw(path);

        g2.setColor(new Color(colorTop.getRed(), colorTop.getGreen(),
                              colorTop.getBlue(), 120));
        g2.setStroke(new BasicStroke(0.8f));
        for (Path2D path : f.paths) g2.draw(path);

        // Shine
        drawShine(g2, f, 0.30f);
    }

    // ── Glanz-Stripe ──────────────────────────────────────────────

    private void drawShine(Graphics2D g2, GeoFeature f, float alpha) {
        if (f.paths.length == 0 || alpha <= 0) return;
        // Clip auf erstes Polygon-Path beschränken
        Shape oldClip = g2.getClip();
        Area clipArea = new Area();
        for (Path2D p : f.paths) clipArea.add(new Area(p));
        g2.clip(clipArea);

        // Diagonaler weißer Streifen oben-links
        Rectangle2D b = clipArea.getBounds2D();
        GradientPaint shine = new GradientPaint(
            (float) b.getMinX(), (float) b.getMinY(),
            new Color(255, 255, 255, (int)(alpha * 255)),
            (float)(b.getMinX() + b.getWidth() * 0.4),
            (float)(b.getMinY() + b.getHeight() * 0.4),
            new Color(255, 255, 255, 0));
        g2.setPaint(shine);
        g2.fill(clipArea);

        g2.setClip(oldClip);
    }

    // ── Labels ────────────────────────────────────────────────────

    private void drawLabels(Graphics2D g2) {
        // Schriftgröße zoom-abhängig
        double scale = proj.getTotalScale();
        int fontSize = (int) Math.max(8, Math.min(14, scale * 0.6));
        Font font    = new Font("Segoe UI", Font.PLAIN, fontSize);
        Font bold    = new Font("Segoe UI", Font.BOLD,  fontSize);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int w = getWidth(), h = getHeight();

        for (GeoFeature f : adm1) {
            if (f.shapeName.isEmpty()) continue;

            double cx = proj.lonToX(f.geoBounds.getCenterX());
            double cy = proj.latToY(f.geoBounds.getCenterY());

            // Außerhalb Canvas → überspringen
            if (cx < -20 || cx > w + 20 || cy < -20 || cy > h + 20) continue;

            boolean active = (f == hovered || f == selected);
            g2.setFont(active ? bold : font);
            String label = f.shapeName;
            int tw = g2.getFontMetrics().stringWidth(label);

            int lx = (int)(cx - tw / 2.0);
            int ly = (int) cy;

            // Schatten
            g2.setColor(new Color(0, 0, 0, 170));
            g2.drawString(label, lx + 1, ly + 1);
            // Text
            g2.setColor(active ? labelHover : labelColor);
            g2.drawString(label, lx, ly);
        }
    }

    // ── Vignette ──────────────────────────────────────────────────

    private void drawVignette(Graphics2D g2, int w, int h) {
        RadialGradientPaint vignette = new RadialGradientPaint(
            w / 2f, h / 2f,
            Math.max(w, h) * 0.65f,
            new float[]{0.4f, 1.0f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 120)}
        );
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);
    }

    // ── Koordinatengitter ─────────────────────────────────────────

    private void drawGrid(Graphics2D g2, int w, int h) {
        g2.setColor(gridColor);
        g2.setStroke(new BasicStroke(0.5f));

        // Breitengrade alle 2°
        for (double lat = 46; lat <= 56; lat += 2) {
            int y = (int) proj.latToY(lat);
            if (y >= 0 && y <= h) g2.drawLine(0, y, w, y);
        }
        // Längengrade alle 2°
        for (double lon = 4; lon <= 16; lon += 2) {
            int x = (int) proj.lonToX(lon);
            if (x >= 0 && x <= w) g2.drawLine(x, 0, x, h);
        }
    }

    // ── Kompass ───────────────────────────────────────────────────

    private void drawCompass(Graphics2D g2, int cx, int cy, int r) {
        // Hintergrund-Kreis
        g2.setColor(new Color(0x08, 0x0C, 0x16, 180));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(rimColor);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // N-Zeiger (Türkis)
        int tipY = cy - r + 4;
        int baseW = 5;
        Path2D north = new Path2D.Double();
        north.moveTo(cx, tipY);
        north.lineTo(cx - baseW, cy + 2);
        north.lineTo(cx + baseW, cy + 2);
        north.closePath();
        g2.setPaint(new GradientPaint(cx, tipY, colorTop, cx, cy + 2,
                                      new Color(colorTop.getRed(), colorTop.getGreen(),
                                                colorTop.getBlue(), 120)));
        g2.fill(north);

        // S-Zeiger (Weinrot, gedämpft)
        Path2D south = new Path2D.Double();
        south.moveTo(cx, cy + r - 4);
        south.lineTo(cx - baseW, cy - 2);
        south.lineTo(cx + baseW, cy - 2);
        south.closePath();
        g2.setColor(new Color(colorBottom.getRed(), colorBottom.getGreen(),
                              colorBottom.getBlue(), 160));
        g2.fill(south);

        // N-Buchstabe
        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g2.setColor(new Color(220, 240, 255, 200));
        g2.drawString("N", cx - 4, tipY - 2);
    }

    // ── Glas-Rim ──────────────────────────────────────────────────

    private void drawGlassRim(Graphics2D g2, int w, int h) {
        // Äußerer Leuchtrand
        for (int i = 3; i >= 1; i--) {
            g2.setColor(new Color(rimColor.getRed(), rimColor.getGreen(),
                                  rimColor.getBlue(), 20 * i));
            g2.setStroke(new BasicStroke(i));
            g2.drawRect(i / 2, i / 2, w - i, h - i);
        }
        // Scharfe innere Linie
        g2.setColor(rimColor);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(0, 0, w - 1, h - 1);
    }

    // ═══════════════════════════════════════════════════════════════
    // Projektion & Paths
    // ═══════════════════════════════════════════════════════════════

    private void initProjection(int w, int h) {
        Rectangle2D deu = new Rectangle2D.Double(
            GeoProjection.DEU_LON_MIN, GeoProjection.DEU_LAT_MIN,
            GeoProjection.DEU_LON_MAX - GeoProjection.DEU_LON_MIN,
            GeoProjection.DEU_LAT_MAX - GeoProjection.DEU_LAT_MIN);
        proj.fitToCanvas(w, h, deu);
        projReady = true;
        invalidatePaths();
        bufferDirty = true;
    }

    private void ensurePaths(List<GeoFeature> features) {
        for (GeoFeature f : features) {
            if (f.paths.length == 0 && f.rawGeom != null) {
                f.paths = GeometryConverter.convert(f.rawGeom, proj);
            }
        }
    }

    private void invalidatePaths() {
        for (GeoFeature f : adm0) f.paths = new Path2D[0];
        for (GeoFeature f : adm1) f.paths = new Path2D[0];
    }

    private void invalidate(boolean fullRebuild) {
        if (fullRebuild) {
            projReady = false;
        }
        bufferDirty = true;
        repaint();
    }

    private void zoomToFeature(GeoFeature f) {
        if (f == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        Rectangle2D gb = f.geoBounds;
        // Erweiterte BBox (150% der Feature-Größe) einpassen
        double cx = gb.getCenterX(), cy = gb.getCenterY();
        double hw = Math.max(gb.getWidth(), 1.0) * 0.9;
        double hh = Math.max(gb.getHeight(), 0.5) * 0.9;
        Rectangle2D expanded = new Rectangle2D.Double(cx - hw, cy - hh, hw * 2, hh * 2);
        proj.fitToCanvas(w, h, expanded);
        projReady = true;
        invalidatePaths();
        bufferDirty = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Maus
    // ═══════════════════════════════════════════════════════════════

    private void initMouse() {
        MouseAdapter ma = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                // Koordinaten-Rückprojektion für Statusbar
                double lon = proj.xToLon(e.getX());
                double lat = proj.yToLat(e.getY());
                fireMoveListeners(lon, lat);

                // Hover-Feature bestimmen
                GeoFeature found = hitTest(e.getX(), e.getY());
                if (found != hovered) {
                    if (hovered != null) hovered.hovered = false;
                    hovered = found;
                    if (found != null) found.hovered = true;
                    hoverProg = 0f;
                    ensureAnimRunning();
                    // Base-Buffer muss NICHT neu gerendert werden —
                    // hover wird direkt über den Buffer gemalt
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragging    = true;
                dragLastX   = e.getX();
                dragLastY   = e.getY();
                dragStartX  = e.getX();
                dragStartY  = e.getY();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging) return;
                dragging = false;
                setCursor(Cursor.getDefaultCursor());

                // Klick (< 4px Bewegung) → Selection
                int dx = Math.abs(e.getX() - dragStartX);
                int dy = Math.abs(e.getY() - dragStartY);
                if (dx < 4 && dy < 4) {
                    GeoFeature f = hitTest(e.getX(), e.getY());
                    if (f == selected) {
                        // Nochmal klicken → deselektieren + zoomFit
                        selectFeature(null);
                        zoomFit();
                    } else {
                        selectFeature(f);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                int dx = e.getX() - dragLastX;
                int dy = e.getY() - dragLastY;
                dragLastX = e.getX();
                dragLastY = e.getY();
                proj.pan(dx, dy);
                invalidatePaths();
                bufferDirty = true;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.18 : (1.0 / 1.18);
                proj.zoom(factor, e.getX(), e.getY());
                invalidatePaths();
                bufferDirty = true;
                repaint();
                // Zoom-Level an Listener melden
                fireMoveListeners(proj.xToLon(e.getX()), proj.yToLat(e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hovered != null) {
                    hovered.hovered = false;
                    hovered = null;
                    repaint();
                }
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);

        // Resize → Projektion neu berechnen
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                projReady = false;
                bufferDirty = true;
                repaint();
            }
        });
    }

    /** Einfacher Punkt-in-Polygon Test (nutzt Path2D.contains). */
    private GeoFeature hitTest(int px, int py) {
        // Rückwärts iterieren → oben gezeichnete Features zuerst prüfen
        for (int i = adm1.size() - 1; i >= 0; i--) {
            GeoFeature f = adm1.get(i);
            for (Path2D path : f.paths) {
                if (path.contains(px, py)) return f;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Animation
    // ═══════════════════════════════════════════════════════════════

    private void initAnimation() {
        animTimer = new Timer(16, e -> {
            // Hover-Progress Easing
            if (hovered != null) {
                hoverProg += (1f - hoverProg) * 0.14f;
            } else {
                hoverProg -= hoverProg * 0.18f;
            }

            // Selected-Glow pulsieren
            glowPhase += 0.06;

            repaint();

            // Timer stoppen wenn keine Animation nötig
            boolean hoverDone = (hovered == null && hoverProg < 0.005f)
                             || (hovered != null && hoverProg > 0.995f);
            boolean selDone   = (selected == null);
            if (hoverDone && selDone) {
                animTimer.stop();
                animRunning = false;
                hoverProg = (hovered != null) ? 1f : 0f;
            }
        });
    }

    private void ensureAnimRunning() {
        if (!animRunning) {
            animTimer.start();
            animRunning = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Listener
    // ═══════════════════════════════════════════════════════════════

    public void addFeatureSelectionListener(FeatureSelectionListener l) {
        selListeners.add(l);
    }
    public void removeFeatureSelectionListener(FeatureSelectionListener l) {
        selListeners.remove(l);
    }
    private void fireSelectionChanged(GeoFeature f) {
        for (FeatureSelectionListener l : selListeners) l.featureSelected(f);
    }

    public void addMouseMoveListener(MouseMoveListener l) {
        moveListeners.add(l);
    }
    public void removeMouseMoveListener(MouseMoveListener l) {
        moveListeners.remove(l);
    }
    private void fireMoveListeners(double lon, double lat) {
        for (MouseMoveListener l : moveListeners) l.mouseMoved(lon, lat);
    }

    // ── Listener-Interfaces ────────────────────────────────────────

    public interface FeatureSelectionListener {
        void featureSelected(GeoFeature feature);
    }

    public interface MouseMoveListener {
        void mouseMoved(double lon, double lat);
    }

    // ═══════════════════════════════════════════════════════════════
    // Hilfsmethoden
    // ═══════════════════════════════════════════════════════════════

    private volatile long jxMapsRefreshUntil = 0;
    private void scheduleJxMapsRefresh() {
        jxMapsRefreshUntil = System.currentTimeMillis() + 3000;
        javax.swing.Timer t = new javax.swing.Timer(100, null);
        t.addActionListener(e -> {
            if (System.currentTimeMillis() < jxMapsRefreshUntil) {
                bufferDirty = true;
                repaint();
            } else {
                t.stop();
            }
        });
        t.setRepeats(true);
        t.start();
    }

        private void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
    }

    /** Gibt die aktuelle Projektion zurück (für Statusbar-Anbindung). */
    public GeoProjection getProjection() { return proj; }

    // ── JavaBean-Accessors ────────────────────────────────────────

    public Color getColorTop()          { return colorTop; }
    public void  setColorTop(Color c)   { colorTop = c; bufferDirty = true; repaint(); }
    public Color getColorBottom()       { return colorBottom; }
    public void  setColorBottom(Color c){ colorBottom = c; bufferDirty = true; repaint(); }
    public Color getBgCanvas()          { return bgCanvas; }
    public void  setBgCanvas(Color c)   { bgCanvas = c; setBackground(c); bufferDirty = true; repaint(); }
    public Color getLandNormal()        { return landNormal; }
    public void  setLandNormal(Color c) { landNormal = c; bufferDirty = true; repaint(); }
}
