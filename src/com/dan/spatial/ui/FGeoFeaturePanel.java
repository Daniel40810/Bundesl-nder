package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoFeature;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * FGeoFeaturePanel — zeigt Metadaten des selektierten/gehoverten Features.
 *
 * Zeichnet sich selbst im ReagenzglasBar-Stil:
 *   • Dunkles Glas-Panel mit Türkis-Rim
 *   • Glanz-Stripe oben
 *   • Zeilenweise Labels + Werte
 */
public class FGeoFeaturePanel extends JPanel {

    // ---------- Farben ----------
    public Color colorTop    = new Color(0x00, 0xB5, 0xAD);
    public Color colorBottom = new Color(0x8B, 0x00, 0x24);
    public Color bgColor     = new Color(0x0E, 0x10, 0x17);
    public Color rimColor    = new Color(140, 200, 255, 140);
    public Color labelColor  = new Color(180, 200, 240, 180);
    public Color valueColor  = new Color(230, 245, 255, 230);
    public Color titleColor  = Color.WHITE;

    private static final int ARC = 14;

    // ---------- Daten ----------
    private GeoFeature feature = null;

    // ---------- Konstruktor ----------
    public FGeoFeaturePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(240, 200));
    }

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    public void showFeature(GeoFeature f) {
        this.feature = f;
        repaint();
    }

    public void clear() {
        this.feature = null;
        repaint();
    }

    // ----------------------------------------------------------
    // Painting
    // ----------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            // 1. Schatten
            for (int i = 4; i >= 1; i--) {
                g2.setColor(new Color(0, 0, 0, 14 * i));
                g2.fill(new RoundRectangle2D.Float(i, i, w - i * 2, h - i * 2, ARC, ARC));
            }

            // 2. Glas-Körper
            g2.setPaint(new GradientPaint(0, 0,
                new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 240),
                0, h,
                new Color(5, 7, 14, 250)));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC));

            // 3. Glanz-Stripe oben
            g2.setPaint(new GradientPaint(0, 0,
                new Color(255, 255, 255, 30), 0, h * 0.25f,
                new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h * 0.5f, ARC, ARC));

            // 4. Titel-Bar
            int titleH = 32;
            g2.setPaint(new GradientPaint(0, 0, colorTop, w, 0, colorBottom));
            RoundRectangle2D titleBar = new RoundRectangle2D.Float(0, 0, w, titleH, ARC, ARC);
            g2.fill(titleBar);
            // Ecken unten gerade
            g2.fillRect(0, titleH / 2, w, titleH / 2);

            // 5. Titel-Text
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(titleColor);
            String title = feature != null ? feature.shapeName : "Kein Feature gewählt";
            g2.drawString(title, 10, titleH - 10);

            // 6. ISO / ShapeType Badge
            if (feature != null && !feature.shapeISO.isEmpty()) {
                String badge = feature.shapeISO;
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int bw = fm.stringWidth(badge) + 10;
                int bx = w - bw - 6, by = 6;
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRoundRect(bx, by, bw, 20, 6, 6);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.drawString(badge, bx + 5, by + 14);
            }

            // 7. Feature-Details
            int y = titleH + 18;
            int lineH = 22;
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            if (feature != null) {
                drawRow(g2, "Typ",       feature.shapeType,  10, y); y += lineH;
                drawRow(g2, "Gruppe",    feature.shapeGroup, 10, y); y += lineH;
                drawRow(g2, "ID",        feature.shapeID,    10, y); y += lineH;

                Rectangle2D gb = feature.geoBounds;
                if (gb != null) {
                    drawRow(g2, "BBox W", String.format("%.2f°", gb.getWidth()),  10, y); y += lineH;
                    drawRow(g2, "BBox H", String.format("%.2f°", gb.getHeight()), 10, y); y += lineH;
                    drawRow(g2, "Zentrum",
                        String.format("%.2f, %.2f", gb.getCenterX(), gb.getCenterY()),
                        10, y);
                    y += lineH;
                }

                // Hover/Selected Indikator
                y += 6;
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 10));
                if (feature.selected) {
                    g2.setColor(colorBottom);
                    g2.drawString("● Selektiert", 10, y);
                } else if (feature.hovered) {
                    g2.setColor(colorTop);
                    g2.drawString("● Fokus", 10, y);
                }
            } else {
                g2.setColor(labelColor);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                g2.drawString("Bundesland anklicken…", 10, y);
            }

            // 8. Rahmen (Rim)
            g2.setColor(rimColor);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, w - 1.2f, h - 1.2f, ARC, ARC));

        } finally {
            g2.dispose();
        }
    }

    private void drawRow(Graphics2D g2, String label, String value, int x, int y) {
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(labelColor);
        g2.drawString(label + ":", x, y);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(valueColor);
        FontMetrics fm = g2.getFontMetrics();
        int lw = g2.getFontMetrics(new Font("Segoe UI", Font.PLAIN, 10))
                    .stringWidth(label + ": ");
        g2.drawString(value != null ? value : "—", x + lw + 4, y);
    }

    // JavaBean-Accessors
    public Color getColorTop()    { return colorTop; }
    public void  setColorTop(Color c) { this.colorTop = c; repaint(); }
    public Color getColorBottom() { return colorBottom; }
    public void  setColorBottom(Color c) { this.colorBottom = c; repaint(); }
}
