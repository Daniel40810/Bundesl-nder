package com.dan.spatial.geo;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Web-Mercator WGS84 -> Pixel Projektion mit Zoom und Pan.
 *
 * Koordinaten-Modell:
 *   pixelX =  lon            * totalScale + transX
 *   pixelY = -mercatorY(lat) * totalScale + transY
 *
 * totalScale = baseScale (Fit-to-Canvas)
 * transX / transY beinhalten Zentrierung + Pan + Zoom-Pivot-Versatz.
 *
 * Zoom um Cursor-Punkt (cx, cy):
 *   Neues totalScale: ts' = ts * factor
 *   Pivot bleibt fest:  cx = lon_pivot * ts + transX
 *                       cx = lon_pivot * ts'+ transX'
 *   → transX' = transX + cx * (1 - factor)   (analog Y)
 *
 * Diese Vereinfachung arbeitet ausschliesslich mit transX/transY und
 * totalScale – kein getrenntes baseScale + scale mehr, da das zu
 * Pivot-Fehlern führte.
 */
public class GeoProjection {

    // Referenz-BBox Deutschland (WGS84)
    public static final double DEU_LON_MIN =  5.8;
    public static final double DEU_LON_MAX = 15.1;
    public static final double DEU_LAT_MIN = 47.2;
    public static final double DEU_LAT_MAX = 55.1;

    private double totalScale = 60.0;   // Pixel pro Grad
    private double transX     = 0;      // lon→X Translation
    private double transY     = 0;      // lat→Y Translation (mit Y-Flip)

    // Gespeicherter Fit-Zustand für resetView()
    private double fitScale = 60.0;
    private double fitTransX = 0;
    private double fitTransY = 0;

    public GeoProjection() {}

    // ----------------------------------------------------------
    // Setup: Fit-to-Canvas
    // ----------------------------------------------------------

    /**
     * Berechnet Skalierung + Translation so, dass geoBounds zentriert
     * und vollständig (mit Padding 5%) ins Canvas passt.
     *
     * @param cw         Canvas-Breite  in Pixel
     * @param ch         Canvas-Höhe   in Pixel
     * @param geoBounds  WGS84-BBox (x=lonMin, y=latMin, w=dLon, h=dLat)
     */
    public void fitToCanvas(int cw, int ch, Rectangle2D geoBounds) {
        double geoW = geoBounds.getWidth();
        double mercatorMinY = mercatorY(geoBounds.getMinY());
        double mercatorMaxY = mercatorY(geoBounds.getMaxY());
        double geoH = mercatorMaxY - mercatorMinY;
        if (geoW <= 0 || geoH <= 0) return;

        double padding = 0.92;                         // 8% Rand
        double sx = (cw * padding) / geoW;
        double sy = (ch * padding) / geoH;
        totalScale = Math.min(sx, sy);

        // Zentrierung:
        //   projW = geoW * totalScale   (Breite der projizierten BBox)
        //   transX: lon=lonMin → pixelX = (cw - projW) / 2
        //   → (cw - projW)/2 = lonMin * totalScale + transX
        //   → transX = (cw - projW)/2 - lonMin * totalScale
        double projW = geoW * totalScale;
        double projH = geoH * totalScale;
        transX =  (cw - projW) / 2.0  - geoBounds.getMinX() * totalScale;

        // Y invertiert: latMax -> mercatorMaxY -> pixelY = (ch - projH) / 2
        transY = (ch - projH) / 2.0 + mercatorMaxY * totalScale;

        // Fit-State speichern
        fitScale  = totalScale;
        fitTransX = transX;
        fitTransY = transY;
    }

    // ----------------------------------------------------------
    // Koordinaten-Transformation
    // ----------------------------------------------------------

    /** WGS84 Längengrad -> Pixel-X */
    public double lonToX(double lon) {
        return lon * totalScale + transX;
    }

    /** WGS84 Breitengrad -> Pixel-Y (Web-Mercator, Y-Achse invertiert) */
    public double latToY(double lat) {
        return -mercatorY(lat) * totalScale + transY;
    }

    public Point2D.Double project(double lon, double lat) {
        return new Point2D.Double(lonToX(lon), latToY(lat));
    }

    /** Pixel -> Längengrad (Rückprojektion) */
    public double xToLon(double px) {
        return (px - transX) / totalScale;
    }

    /** Pixel -> Breitengrad (Rückprojektion) */
    public double yToLat(double py) {
        return inverseMercatorY(-(py - transY) / totalScale);
    }

    // ----------------------------------------------------------
    // Zoom (um Cursor-Punkt)
    // ----------------------------------------------------------

    /**
     * Zoomt um den Bildschirm-Punkt (cx, cy).
     * Der geografische Punkt unter dem Cursor bleibt fest.
     *
     * @param factor  > 1 = rein,  < 1 = raus
     * @param cx      Cursor-X in Pixel
     * @param cy      Cursor-Y in Pixel
     */
    public void zoom(double factor, double cx, double cy) {
        double newScale = totalScale * factor;
        // Zoom-Grenzen: 0.5× bis 50× des Fit-Scales
        newScale = Math.max(fitScale * 0.5, Math.min(fitScale * 50.0, newScale));
        double ds = newScale / totalScale;

        // Pivot-Erhaltung: Punkt unter Cursor bleibt an cx/cy
        transX = cx - (cx - transX) * ds;
        transY = cy - (cy - transY) * ds;
        totalScale = newScale;
    }

    // ----------------------------------------------------------
    // Pan
    // ----------------------------------------------------------

    public void pan(double dx, double dy) {
        transX += dx;
        transY += dy;
    }

    // ----------------------------------------------------------
    // Reset
    // ----------------------------------------------------------

    public void resetView() {
        totalScale = fitScale;
        transX     = fitTransX;
        transY     = fitTransY;
    }

    // ----------------------------------------------------------
    // Getter (für StatusBar etc.)
    // ----------------------------------------------------------

    /** Aktueller Zoom relativ zum Fit (1.0 = Fit-to-Canvas) */
    public double getRelativeZoom() {
        return fitScale > 0 ? totalScale / fitScale : 1.0;
    }

    public double getTotalScale() { return totalScale; }
    public double getTransX()     { return transX; }
    public double getTransY()     { return transY; }

    private double mercatorY(double lat) {
        double clampedLat = Math.max(-85.05112878, Math.min(85.05112878, lat));
        double latRad = Math.toRadians(clampedLat);
        return Math.toDegrees(Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0)));
    }

    private double inverseMercatorY(double mercatorY) {
        double yRad = Math.toRadians(mercatorY);
        return Math.toDegrees(2.0 * Math.atan(Math.exp(yRad)) - Math.PI / 2.0);
    }
}
