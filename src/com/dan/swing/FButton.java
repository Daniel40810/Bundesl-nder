package com.dan.swing;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.Timer;

/**
 * FButton – ein moderner, voll selbst gezeichneter {@link JButton} im
 * ModernUI-Stil (Türkis&rarr;Weinrot {@link GradientPaint}, Glasrand,
 * Glanzstreifen, weicher Schatten, abgerundete Ecken).
 *
 * <p>Interaktion:</p>
 * <ul>
 *   <li><b>Hover</b> – die Fläche hebt sich an, wird heller und bekommt
 *       einen weichen Glow. Animiert über einen 16&nbsp;ms-{@link Timer}
 *       mit Easing.</li>
 *   <li><b>Press</b> – die Fläche sinkt minimal ein (Scale).</li>
 *   <li><b>Ripple</b> – beim Klick breitet sich ein Kreis vom Mauspunkt aus
 *       und verblasst, korrekt auf die abgerundete Form geclippt.</li>
 * </ul>
 *
 * <p>JavaBean-kompatibel: jede Eigenschaft besitzt Getter und Setter und ist
 * über einen öffentlichen, parameterlosen Konstruktor in der NetBeans-Palette
 * nutzbar.</p>
 *
 * @author dan
 */
public class FButton extends JButton {

    /* ===================== Öffentliche Style-Felder ===================== */

    /** Gradient oben (Oberfläche). Standard: Türkis {@code #00B5AD}. */
    public Color gradientTop = new Color(0x00, 0xB5, 0xAD);
    /** Gradient unten (Boden). Standard: Weinrot {@code #8B0024}. */
    public Color gradientBottom = new Color(0x8B, 0x00, 0x24);
    /** Farbe der Ripple-Welle. Standard: halbtransparentes Weiß. */
    public Color rippleColor = new Color(255, 255, 255, 110);
    /** Aufhell-Tönung beim Hover (wird additiv über die Fläche gelegt). */
    public Color hoverColor = new Color(255, 255, 255, 60);
    /** Glasrand-Farbe. Standard: helles Blau. */
    public Color glassRim = new Color(140, 200, 255, 160);
    /** Linker Glanzstreifen. Standard: helles Weiß. */
    public Color glassHighlight = new Color(255, 255, 255, 110);
    /** Schattenfarbe unter der Fläche. */
    public Color shadowColor = new Color(0, 0, 0, 90);
    /** Textfarbe. */
    public Color textColor = Color.WHITE;

    /** Eckenradius in Pixeln. */
    public int arc = 22;
    /** Strichbreite des Glasrands. */
    public float rimStrokeWidth = 1.6f;
    /** Hub in Pixeln, um den sich die Fläche beim Hover anhebt. */
    public int hoverLift = 3;
    /** Geschwindigkeit der Ripple-Ausbreitung (Anteil pro Frame, 0.02–0.20). */
    public double rippleSpeed = 0.06;
    /** Maximale Deckkraft der Ripple-Welle (0–255). */
    public int rippleAlpha = 120;
    /** Schaltet alle Animationen an/aus. */
    public boolean animated = true;
    /** Sichtbarkeit des linken Glanzstreifens. */
    public boolean highlightVisible = true;

    /* ===================== Interner Animationszustand ===================== */

    private double hoverProgress = 0.0;   // 0 = normal, 1 = voll gehovt
    private double hoverTarget = 0.0;
    private double pressProgress = 0.0;   // 0 = oben, 1 = eingedrückt
    private double pressTarget = 0.0;

    private boolean hovered = false;
    private boolean pressed = false;

    private final CopyOnWriteArrayList<Ripple> ripples = new CopyOnWriteArrayList<Ripple>();
    private Timer timer;

    /** Eine einzelne Ripple-Welle. */
    private static final class Ripple {
        final double x, y;        // Ursprung (Klickpunkt)
        final double maxRadius;   // Endradius
        double progress = 0.0;    // 0 → 1
        Ripple(double x, double y, double maxRadius) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
        }
    }

    /* ===================== Konstruktoren ===================== */

    /** Parameterloser Konstruktor für die NetBeans-Palette. */
    public FButton() {
        this("Button");
    }

    public FButton(String text) {
        super(text);
        setOpaque(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setRolloverEnabled(false);
        setForeground(textColor);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setMargin(new Insets(10, 22, 10, 22));
        installInteraction();
    }

    private void installInteraction() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!isEnabled()) return;
                hovered = true;
                hoverTarget = 1.0;
                kick();
            }
            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                hoverTarget = 0.0;
                pressed = false;
                pressTarget = 0.0;
                kick();
            }
            @Override public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                pressed = true;
                pressTarget = 1.0;
                spawnRipple(e.getX(), e.getY());
                kick();
            }
            @Override public void mouseReleased(MouseEvent e) {
                pressed = false;
                pressTarget = 0.0;
                kick();
            }
        };
        addMouseListener(ma);
    }

    private void spawnRipple(double px, double py) {
        // Größter Abstand vom Klickpunkt zu einer Ecke = nötiger Endradius.
        double w = getWidth(), h = getHeight();
        double dx = Math.max(px, w - px);
        double dy = Math.max(py, h - py);
        double max = Math.sqrt(dx * dx + dy * dy) + 4;
        ripples.add(new Ripple(px, py, max));
    }

    /* ===================== Animations-Timer ===================== */

    /** Startet den Timer, falls noch Bewegung nötig ist. */
    private void kick() {
        if (!animated) {
            // Ohne Animation: Zielzustände sofort übernehmen.
            hoverProgress = hoverTarget;
            pressProgress = pressTarget;
            for (Ripple r : ripples) r.progress = 1.0;
            ripples.clear();
            repaint();
            return;
        }
        if (timer == null) {
            timer = new Timer(16, e -> tick());
            timer.setCoalesce(true);
        }
        if (!timer.isRunning()) timer.start();
    }

    private void tick() {
        boolean busy = false;

        // Hover-Easing (logarithmisch, schnell anziehend, weich auslaufend)
        if (Math.abs(hoverProgress - hoverTarget) > 0.001) {
            hoverProgress += (hoverTarget - hoverProgress) * 0.18;
            busy = true;
        } else {
            hoverProgress = hoverTarget;
        }

        // Press-Easing (etwas steiler, damit der Druck knackig wirkt)
        if (Math.abs(pressProgress - pressTarget) > 0.001) {
            pressProgress += (pressTarget - pressProgress) * 0.30;
            busy = true;
        } else {
            pressProgress = pressTarget;
        }

        // Ripples fortschreiben
        if (!ripples.isEmpty()) {
            double step = clamp(rippleSpeed, 0.02, 0.20);
            for (Iterator<Ripple> it = ripples.iterator(); it.hasNext();) {
                Ripple r = it.next();
                r.progress += step;
                if (r.progress >= 1.0) ripples.remove(r);
            }
            busy = true;
        }

        repaint();
        if (!busy && ripples.isEmpty()) {
            timer.stop();   // CPU schonen: im Ruhezustand anhalten
        }
    }

    /* ===================== Painting ===================== */

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            boolean on = isEnabled();

            double hp = on ? hoverProgress : 0.0;
            double pp = on ? pressProgress : 0.0;

            // Geometrie: Schattenraum unten reservieren, Hover hebt an.
            int shadowGap = Math.max(2, hoverLift + 3);
            float lift = (float) (hoverLift * hp - pp * 1.5);   // hoch bei Hover, runter bei Press
            float bx = 1f;
            float by = shadowGap - lift;
            float bw = w - 2f;
            float bh = h - shadowGap - 2f;
            if (bw <= 0 || bh <= 0) return;

            RoundRectangle2D body = new RoundRectangle2D.Float(bx, by, bw, bh, arc, arc);

            // 1 · Schatten (weicher, mehrschichtig; mit Hover größer/tiefer)
            paintShadow(g2, body, w, hp, on);

            // 2 · Flüssigkeits-Gradient (Türkis oben → Weinrot unten)
            Paint grad = new GradientPaint(0, by, gradientTop, 0, by + bh, gradientBottom);
            if (!on) grad = desaturate(grad, by, by + bh);
            g2.setPaint(grad);
            g2.fill(body);

            // 3 · Hover-Aufhellung (additiver Schleier)
            if (hp > 0.001 && on) {
                Composite oc = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, (float) (alphaFrac(hoverColor) * hp)));
                g2.setColor(opaque(hoverColor));
                g2.fill(body);
                g2.setComposite(oc);
            }

            // 4 · Ripple(s) – auf die Form geclippt
            if (!ripples.isEmpty() && on) {
                Shape oldClip = g2.getClip();
                g2.clip(body);
                for (Ripple r : ripples) {
                    double rad = r.maxRadius * easeOut(r.progress);
                    int a = (int) (clamp(rippleAlpha, 0, 255) * (1.0 - r.progress));
                    if (a <= 0) continue;
                    g2.setColor(new Color(rippleColor.getRed(), rippleColor.getGreen(),
                            rippleColor.getBlue(), Math.min(255, a)));
                    g2.fill(new Ellipse2D.Double(r.x - rad, r.y - rad, rad * 2, rad * 2));
                }
                g2.setClip(oldClip);
            }

            // 5 · Linker Glanzstreifen
            if (highlightVisible && on) {
                Shape oldClip = g2.getClip();
                g2.clip(body);
                float stripeW = bw * 0.22f;
                g2.setPaint(new GradientPaint(
                        bx, 0, glassHighlight,
                        bx + stripeW, 0, new Color(
                                glassHighlight.getRed(), glassHighlight.getGreen(),
                                glassHighlight.getBlue(), 0)));
                g2.fill(new RoundRectangle2D.Float(bx, by, stripeW, bh, arc, arc));
                g2.setClip(oldClip);
            }

            // 6 · Glasrand
            g2.setColor(on ? glassRim : new Color(glassRim.getRed(), glassRim.getGreen(),
                    glassRim.getBlue(), 70));
            g2.setStroke(new BasicStroke(rimStrokeWidth));
            g2.draw(new RoundRectangle2D.Float(bx, by, bw - 1, bh - 1, arc, arc));

            // 7 · Inhalt (Icon + Text) – minimal mitskaliert beim Press
            paintContent(g2, bx, by, bw, bh, pp, on);
        } finally {
            g2.dispose();
        }
    }

    private void paintShadow(Graphics2D g2, RoundRectangle2D body, int w,
                             double hp, boolean on) {
        if (!on) return;
        Rectangle b = body.getBounds();
        int layers = 4;
        int spread = (int) (2 + hp * hoverLift);   // bei Hover größer
        int baseAlpha = shadowColor.getAlpha();
        for (int i = layers; i >= 1; i--) {
            int a = (int) (baseAlpha * (i / (double) layers) * 0.5 * (0.6 + 0.4 * hp));
            g2.setColor(new Color(shadowColor.getRed(), shadowColor.getGreen(),
                    shadowColor.getBlue(), clampInt(a)));
            int off = i + spread;
            g2.fill(new RoundRectangle2D.Float(
                    b.x - i * 0.5f, b.y + off, b.width + i, b.height,
                    arc + i, arc + i));
        }
    }

    private void paintContent(Graphics2D g2, float bx, float by, float bw, float bh,
                              double pp, boolean on) {
        String text = getText();
        Icon icon = on ? getIcon() : (getDisabledIcon() != null ? getDisabledIcon() : getIcon());
        boolean hasText = text != null && !text.isEmpty();
        boolean hasIcon = icon != null;
        if (!hasText && !hasIcon) return;

        g2.setFont(getFont());
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int gap = hasText && hasIcon ? getIconTextGap() : 0;
        int textW = hasText ? fm.stringWidth(text) : 0;
        int iconW = hasIcon ? icon.getIconWidth() : 0;
        int iconH = hasIcon ? icon.getIconHeight() : 0;

        float contentW = iconW + gap + textW;
        float cx = bx + (bw - contentW) / 2f;
        float cyMid = by + bh / 2f;
        float sink = (float) (pp * 1.0);   // sinkt beim Press minimal mit

        float drawX = cx;
        if (hasIcon) {
            int iy = Math.round(cyMid - iconH / 2f + sink);
            icon.paintIcon(this, g2, Math.round(drawX), iy);
            drawX += iconW + gap;
        }
        if (hasText) {
            float ty = cyMid + (fm.getAscent() - fm.getDescent()) / 2f + sink;
            // dezenter Textschatten für Lesbarkeit
            g2.setColor(new Color(0, 0, 0, on ? 90 : 40));
            g2.drawString(text, drawX + 1, ty + 1);
            g2.setColor(on ? textColor : new Color(textColor.getRed(), textColor.getGreen(),
                    textColor.getBlue(), 120));
            g2.drawString(text, drawX, ty);
        }
    }

    /* ===================== Hilfsmethoden ===================== */

    private static double easeOut(double t) {
        t = clamp(t, 0, 1);
        return 1 - Math.pow(1 - t, 3);   // kubisch ausklingend
    }

    private static double alphaFrac(Color c) { return c.getAlpha() / 255.0; }
    private static Color opaque(Color c) { return new Color(c.getRed(), c.getGreen(), c.getBlue()); }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
    private static int clampInt(int a) { return a < 0 ? 0 : (a > 255 ? 255 : a); }

    /** Erzeugt eine entsättigte (graue) Variante des Gradients für disabled. */
    private static Paint desaturate(Paint p, float y1, float y2) {
        return new GradientPaint(0, y1, new Color(120, 124, 132),
                0, y2, new Color(80, 84, 92));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int gap = Math.max(2, hoverLift + 3);
        return new Dimension(d.width, d.height + gap);   // Platz für Schatten/Hub
    }

    /* ===================== JavaBean-Properties ===================== */

    public Color getGradientTop() { return gradientTop; }
    public void setGradientTop(Color c) { this.gradientTop = c; repaint(); }

    public Color getGradientBottom() { return gradientBottom; }
    public void setGradientBottom(Color c) { this.gradientBottom = c; repaint(); }

    public Color getRippleColor() { return rippleColor; }
    public void setRippleColor(Color c) { this.rippleColor = c; repaint(); }

    public Color getHoverColor() { return hoverColor; }
    public void setHoverColor(Color c) { this.hoverColor = c; repaint(); }

    public Color getGlassRim() { return glassRim; }
    public void setGlassRim(Color c) { this.glassRim = c; repaint(); }

    public Color getGlassHighlight() { return glassHighlight; }
    public void setGlassHighlight(Color c) { this.glassHighlight = c; repaint(); }

    public Color getShadowColor() { return shadowColor; }
    public void setShadowColor(Color c) { this.shadowColor = c; repaint(); }

    public Color getTextColor() { return textColor; }
    public void setTextColor(Color c) { this.textColor = c; setForeground(c); repaint(); }

    public int getArc() { return arc; }
    public void setArc(int arc) { this.arc = Math.max(0, arc); repaint(); }

    public float getRimStrokeWidth() { return rimStrokeWidth; }
    public void setRimStrokeWidth(float w) { this.rimStrokeWidth = (float) clamp(w, 0.5, 6.0); repaint(); }

    public int getHoverLift() { return hoverLift; }
    public void setHoverLift(int px) { this.hoverLift = clamp(px, 0, 12); revalidate(); repaint(); }

    public double getRippleSpeed() { return rippleSpeed; }
    public void setRippleSpeed(double s) { this.rippleSpeed = clamp(s, 0.02, 0.20); }

    public int getRippleAlpha() { return rippleAlpha; }
    public void setRippleAlpha(int a) { this.rippleAlpha = clamp(a, 0, 255); }

    public boolean isAnimated() { return animated; }
    public void setAnimated(boolean b) {
        this.animated = b;
        if (!b && timer != null) timer.stop();
        repaint();
    }

    public boolean isHighlightVisible() { return highlightVisible; }
    public void setHighlightVisible(boolean b) { this.highlightVisible = b; repaint(); }
}
