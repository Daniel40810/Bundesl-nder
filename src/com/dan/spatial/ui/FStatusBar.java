package com.dan.spatial.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * FStatusBar — Koordinatenanzeige und Status-Meldungen unten.
 *
 * Zeigt:
 *   • Aktuelle Mausposition (Lon / Lat)
 *   • Zoom-Level
 *   • Status-Text (z.B. "Lade Daten…")
 */
public class FStatusBar extends JPanel {

    private String coordText  = "Lon: —  Lat: —";
    private String zoomText   = "Zoom: 1.0x";
    private String statusText = "Bereit";

    private final Color bgColor  = new Color(0x0A, 0x0C, 0x12, 230);
    private final Color rimColor = new Color(140, 200, 255, 50);
    private final Color txtColor = new Color(160, 185, 220, 200);
    private final Color accentColor = new Color(0x00, 0xB5, 0xAD);

    public FStatusBar() {
        setOpaque(false);
        setPreferredSize(new Dimension(800, 26));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));

            g2.setColor(rimColor);
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new RoundRectangle2D.Float(0.4f, 0.4f, w - 0.8f, h - 0.8f, 8, 8));

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            // Status links (Akzentfarbe bei Lade-Text)
            boolean loading = statusText.contains("Lade");
            g2.setColor(loading ? accentColor : txtColor);
            g2.drawString(statusText, 10, h / 2 + 4);

            // Trennlinie
            g2.setColor(rimColor);
            g2.drawLine(160, 5, 160, h - 5);

            // Koordinaten Mitte
            g2.setColor(txtColor);
            g2.drawString(coordText, 170, h / 2 + 4);

            // Trennlinie
            g2.drawLine(w - 110, 5, w - 110, h - 5);

            // Zoom rechts
            g2.drawString(zoomText, w - 100, h / 2 + 4);

        } finally {
            g2.dispose();
        }
    }

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    public void setCoordinates(double lon, double lat) {
        coordText = String.format("Lon: %.4f°  Lat: %.4f°", lon, lat);
        repaint();
    }

    public void setZoom(double scale) {
        zoomText = String.format("Zoom: %.1fx", scale);
        repaint();
    }

    public void setStatus(String msg) {
        statusText = msg;
        repaint();
    }

    public void setLoading(boolean loading) {
        statusText = loading ? "Lade Daten…" : "Bereit";
        repaint();
    }
}
