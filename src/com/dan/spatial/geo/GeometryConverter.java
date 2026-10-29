package com.dan.spatial.geo;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Konvertiert oracle.spatial.geometry.JGeometry → AWT Path2D[].
 *
 * Binding über Reflection → kein Compile-Zwang auf sdoapi.jar.
 * Zur Laufzeit MUSS sdoapi.jar im Classpath liegen.
 *
 * ── SDO_GTYPE-Unterstützung ──────────────────────────────────────
 *   2003  Polygon            (äußerer Ring + optionale Löcher)
 *   2007  MultiPolygon       → getElements() → JGeometry[]
 *   2006  MultiLineString    → getElements()
 *   2002  LineString
 *
 * ── Polygon mit Löchern ─────────────────────────────────────────
 *   JGeometry.getElemInfo() gibt int[] zurück:
 *     [startIndex, SDO_ETYPE, SDO_INTERPRETATION]  × N Ringe
 *   ETYPE 1003 = äußerer Ring, 2003 = innerer Ring (Loch).
 *   Löcher werden als Path2D mit WIND_EVEN_ODD behandelt.
 *
 * ── Koordinaten-Reihenfolge ─────────────────────────────────────
 *   Oracle speichert SDO_ORDINATES als flaches double[] mit
 *   (X=Lon, Y=Lat) Paaren (2D-Fall, SDO_GTYPE = 2xxx).
 *   3D (3003) wird ignoriert (Stride 2 → korrekte Paare).
 */
public class GeometryConverter {

    private GeometryConverter() {}

    // ────────────────────────────────────────────────────────────
    // Öffentliche API
    // ────────────────────────────────────────────────────────────

    /**
     * Wandelt ein JGeometry-Objekt in Path2D-Array um.
     * Ein Array-Element = ein geschlossener Polygon-Ring (Außen oder Loch)
     * bzw. eine Linie.
     *
     * @param jgeom  JGeometry-Instanz (als Object wegen Reflection)
     * @param proj   aktive Projektion WGS84→Pixel
     * @return       niemals null, kann leer sein
     */
    public static Path2D[] convert(Object jgeom, GeoProjection proj) {
        if (jgeom == null) return new Path2D[0];
        try {
            int gtype = callGetType(jgeom);
            // JGeometry.getType() liefert NUR den Typ-Teil (letzte Ziffer des SDO_GTYPE):
            //   3 = Polygon     (SDO_GTYPE 2003)
            //   7 = MultiPolygon (SDO_GTYPE 2007)
            //   2 = LineString, 6 = MultiLine
            // Zur Sicherheit werden auch die vollen SDO_GTYPE-Werte abgefangen.
            switch (gtype) {
                case 3:
                case 2003: case 3003:
                    return convertSinglePolygon(jgeom, proj);
                case 7:
                case 2007: case 3007:
                    return convertMultiPolygon(jgeom, proj);
                case 2:
                case 2002: case 3002:
                    return new Path2D[]{ buildLineFromOrds(
                        (double[]) invoke(jgeom, "getOrdinatesArray"), proj, 2) };
                case 6:
                case 2006: case 3006:
                    return convertMultiLine(jgeom, proj);
                default:
                    System.err.println("GeometryConverter: unbekannter gtype " + gtype);
                    return new Path2D[0];
            }
        } catch (Exception e) {
            System.err.println("GeometryConverter.convert(): " + e.getMessage());
            return new Path2D[0];
        }
    }

    /**
     * Extrahiert die MBR (Minimum Bounding Rectangle) als Rectangle2D.
     * Fallback: Deutschland-Gesamt-BBox.
     */
    public static Rectangle2D extractBounds(Object jgeom) {
        if (jgeom == null) return DEU_BOUNDS;
        try {
            double[] mbr = (double[]) invoke(jgeom, "getMBR");
            if (mbr != null && mbr.length >= 4
                    && mbr[2] > mbr[0] && mbr[3] > mbr[1]) {
                return new Rectangle2D.Double(
                    mbr[0], mbr[1],
                    mbr[2] - mbr[0],
                    mbr[3] - mbr[1]);
            }
        } catch (Exception e) {
            System.err.println("GeometryConverter.extractBounds(): " + e.getMessage());
        }
        return DEU_BOUNDS;
    }

    // ────────────────────────────────────────────────────────────
    // Polygon (2003)
    // ────────────────────────────────────────────────────────────

    /**
     * Konvertiert ein einfaches oder mit Löchern versehenes Polygon.
     * Verwendet ElemInfo um Außen- und Innenringe zu trennen.
     * Alle Ringe landen in einem einzigen Path2D mit WIND_EVEN_ODD,
     * sodass Löcher korrekt "ausgestanzt" werden.
     */
    private static Path2D[] convertSinglePolygon(Object jgeom, GeoProjection proj)
            throws Exception {

        double[] ords    = (double[]) invoke(jgeom, "getOrdinatesArray");
        int[]    info    = (int[])    invoke(jgeom, "getElemInfo");

        if (ords == null || ords.length < 4) return new Path2D[0];

        // Stride ermitteln: JGeometry hat getDimensions() für die Koordinatendimension.
        // Fallback: ords.length / Anzahl Punkte aus ElemInfo, sonst 2.
        int stride = getDimensions(jgeom);

        Path2D combined = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        if (info == null || info.length < 3) {
            // Kein ElemInfo → alles als äußeren Ring behandeln
            appendRing(combined, ords, 0, ords.length, stride, proj);
        } else {
            // ElemInfo: Tripel [startOrdIdx, ETYPE, INTERP]
            //   1-basiert laut Oracle-Doku → -1 für Java-Array
            for (int i = 0; i < info.length; i += 3) {
                int startIdx = info[i] - 1;  // 1-basiert → 0-basiert
                int etype    = info[i + 1];  // 1003=äußer, 2003=innen
                // Endindex: nächstes Tripel oder Arrayende
                int endIdx = (i + 3 < info.length)
                    ? info[i + 3] - 1
                    : ords.length;

                if (etype == 1003 || etype == 2003) {
                    appendRing(combined, ords, startIdx, endIdx, stride, proj);
                }
                // Andere ETYPE (z.B. 1 = Punkt) ignorieren
            }
        }
        combined.closePath();
        return new Path2D[]{ combined };
    }

    /** Hängt einen Ring (äußer oder innen) an einen bestehenden Path2D an. */
    private static void appendRing(Path2D path, double[] ords,
                                   int from, int to, int stride,
                                   GeoProjection proj) {
        boolean first = true;
        for (int i = from; i + 1 < to; i += stride) {
            double px = proj.lonToX(ords[i]);
            double py = proj.latToY(ords[i + 1]);
            if (first) { path.moveTo(px, py); first = false; }
            else        { path.lineTo(px, py); }
        }
        path.closePath();
    }

    // ────────────────────────────────────────────────────────────
    // MultiPolygon (2007)
    // ────────────────────────────────────────────────────────────

    /**
     * Oracle JGeometry.getElements() gibt JGeometry[] zurück,
     * jedes Element ist ein Polygon (2003).
     * getAllElements() ist die richtige Methode (getElements() nicht vorhanden).
     */
    private static Path2D[] convertMultiPolygon(Object jgeom, GeoProjection proj)
            throws Exception {

        // Beide Methodennamen ausprobieren – Oracle ändert API gelegentlich
        Object[] elements = getAllElements(jgeom);
        if (elements == null || elements.length == 0) {
            // Fallback: als Einzelpolygon behandeln
            return convertSinglePolygon(jgeom, proj);
        }

        List<Path2D> result = new ArrayList<>();
        for (Object elem : elements) {
            if (elem == null) continue;
            Path2D[] sub = convertSinglePolygon(elem, proj);
            for (Path2D p : sub) result.add(p);
        }
        return result.toArray(new Path2D[0]);
    }

    // ────────────────────────────────────────────────────────────
    // LineString (2002) / MultiLine (2006)
    // ────────────────────────────────────────────────────────────

    private static Path2D buildLineFromOrds(double[] ords, GeoProjection proj, int stride) {
        Path2D path = new Path2D.Double();
        if (ords == null || ords.length < 4) return path;
        boolean first = true;
        for (int i = 0; i + 1 < ords.length; i += stride) {
            double px = proj.lonToX(ords[i]);
            double py = proj.latToY(ords[i + 1]);
            if (first) { path.moveTo(px, py); first = false; }
            else        { path.lineTo(px, py); }
        }
        return path;
    }

    private static Path2D[] convertMultiLine(Object jgeom, GeoProjection proj)
            throws Exception {
        Object[] elements = getAllElements(jgeom);
        if (elements == null) return new Path2D[0];
        List<Path2D> result = new ArrayList<>();
        for (Object elem : elements) {
            if (elem == null) continue;
            double[] ords = (double[]) invoke(elem, "getOrdinatesArray");
            result.add(buildLineFromOrds(ords, proj, 2));
        }
        return result.toArray(new Path2D[0]);
    }

    // ────────────────────────────────────────────────────────────
    // Reflection-Helpers
    // ────────────────────────────────────────────────────────────

    private static int callGetType(Object jgeom) throws Exception {
        Object result = invoke(jgeom, "getType");
        return ((Number) result).intValue();
    }

    /**
     * Versucht getAllElements(), dann getElements() – beide geben JGeometry[] zurück.
     * Gibt null zurück wenn keine Methode existiert.
     */
    private static Object[] getAllElements(Object jgeom) {
        for (String name : new String[]{"getAllElements", "getElements"}) {
            try {
                Object r = invoke(jgeom, name);
                if (r instanceof Object[]) return (Object[]) r;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Object invoke(Object obj, String methodName) throws Exception {
        Method m = obj.getClass().getMethod(methodName);
        m.setAccessible(true);
        return m.invoke(obj);
    }

    /**
     * Ermittelt die Koordinatendimension (2 oder 3) via JGeometry.getDimensions().
     * Fallback: 2 (Standard für WGS84-2D-Daten).
     */
    private static int getDimensions(Object jgeom) {
        try {
            Object result = invoke(jgeom, "getDimensions");
            int dim = ((Number) result).intValue();
            return (dim >= 3) ? 3 : 2;
        } catch (Exception e) {
            return 2;  // sicherer Fallback
        }
    }

    // ────────────────────────────────────────────────────────────
    // Konstanten
    // ────────────────────────────────────────────────────────────

    private static final Rectangle2D DEU_BOUNDS =
        new Rectangle2D.Double(
            GeoProjection.DEU_LON_MIN,
            GeoProjection.DEU_LAT_MIN,
            GeoProjection.DEU_LON_MAX - GeoProjection.DEU_LON_MIN,
            GeoProjection.DEU_LAT_MAX - GeoProjection.DEU_LAT_MIN);
}
