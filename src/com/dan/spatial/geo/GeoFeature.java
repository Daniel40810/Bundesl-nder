package com.dan.spatial.geo;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Kapselt eine Zeile aus GEOBOUNDARIES_DEU_ADM0 oder ADM1.
 * JGeometry wird zur Laufzeit aus sdoapi.jar gebunden.
 */
public class GeoFeature {

    // ---------- DB-Felder ----------
    public String shapeName   = "";   // z.B. "Bayern"
    public String shapeISO    = "";   // z.B. "DE-BY"
    public String shapeID     = "";
    public String shapeGroup  = "";
    public String shapeType   = "";   // "ADM0" | "ADM1"

    // ---------- Geometrie (gecacht) ----------
    /** Rohes oracle.spatial.geometry.JGeometry-Objekt (Object, um sdoapi optional zu halten) */
    public Object rawGeom = null;

    /** Vorberechnete AWT-Pfade (eine JGeometry kann Multi-Polygon sein) */
    public Path2D[] paths = new Path2D[0];

    /** Bounding Box in WGS84-Koordinaten */
    public Rectangle2D geoBounds = new Rectangle2D.Double(5.8, 47.2, 9.5, 7.7); // Deutschland-Fallback

    // ---------- UI-Zustand ----------
    public boolean hovered  = false;
    public boolean selected = false;

    public GeoFeature() {}

    public GeoFeature(String name, String iso) {
        this.shapeName = name;
        this.shapeISO  = iso;
    }

    @Override
    public String toString() {
        return shapeName + " [" + shapeISO + "]";
    }
}
