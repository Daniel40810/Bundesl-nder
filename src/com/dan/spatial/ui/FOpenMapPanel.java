package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoFeature;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.*;
import java.util.List;

/**
 * FOpenMapPanel — optionaler OpenMap-Hintergrund-Layer.
 *
 * Kapselt com.bbn.openmap.MapBean vollständig via Reflection,
 * damit der Code auch ohne openmap.jar im Classpath kompiliert.
 * Zur Laufzeit: openmap.jar (5.x oder 6.x) im lib/-Verzeichnis.
 *
 * Was wird gerendert:
 *   Layer 1 – OpenStreetMap Tile-Hintergrund (TileLayer)
 *   Layer 2 – Bundesland-Polygone als OMPoly (Türkis-Rand, transparent)
 *
 * Einbindung in FSpatialMap:
 *   Dieser Panel wird als OVERLAY über die eigene Karte gelegt ODER
 *   als separates JPanel in einer JSplitPane / CardLayout-Tab.
 *
 * Initialisierung:
 *   FOpenMapPanel map = new FOpenMapPanel();
 *   map.init();             // MapBean + Projektion aufbauen
 *   map.setFeatures(adm1); // Bundesland-Polygone einzeichnen
 */
public class FOpenMapPanel extends JPanel {

    // ── OpenMap-Objekte (alle via Reflection als Object) ──────────
    private Object mapBean;       // com.bbn.openmap.MapBean
    private Object mapHandler;    // com.bbn.openmap.MapHandler
    private Object geomLayer;     // com.bbn.openmap.layer.OMGraphicHandlerLayer
    private Object graphicList;   // com.bbn.openmap.omGraphics.OMGraphicList

    private boolean omAvailable = false;
    private String  initError   = null;

    // ── Farben (ReagenzglasBar-Stil) ──────────────────────────────
    private final Color colorTop    = new Color(0x00, 0xB5, 0xAD);
    private final Color colorBottom = new Color(0x8B, 0x00, 0x24);
    private final Color bgError     = new Color(0x0E, 0x10, 0x17);

    // Deutschland-Mittelpunkt
    private static final float DEU_CENTER_LAT = 51.2f;
    private static final float DEU_CENTER_LON = 10.4f;
    private static final float DEU_SCALE       = 8_000_000f;  // kleinere Zahl = mehr Zoom

    public FOpenMapPanel() {
        setLayout(new BorderLayout());
        setBackground(bgError);
        setPreferredSize(new Dimension(720, 580));
    }

    // ════════════════════════════════════════════════════════════════
    // Initialisierung
    // ════════════════════════════════════════════════════════════════

    /**
     * Baut den MapBean auf und versucht einen OSM-TileLayer zu laden.
     * Schlägt der Aufbau fehl (kein openmap.jar), wird ein Fehler-Panel
     * angezeigt und alle weiteren Aufrufe sind No-Ops.
     */
    public void init() {
        try {
            buildMapBean();
            buildTileLayer();
            buildGeomLayer();
            registerLayers();
            omAvailable = true;
        } catch (Exception e) {
            initError = e.getClass().getSimpleName() + ": " + e.getMessage();
            System.err.println("FOpenMapPanel.init() fehlgeschlagen: " + initError);
            e.printStackTrace();
            showErrorPanel();
        }
    }

    /** Bundesland-Polygone als OMPoly in die Karte einzeichnen. */
    public void setFeatures(List<GeoFeature> adm1Features) {
        if (!omAvailable || graphicList == null) return;
        try {
            // OMGraphicList leeren
            invoke(graphicList, "clear");

            for (GeoFeature f : adm1Features) {
                if (f.rawGeom == null) continue;
                float[] latLon = extractLatLon(f);
                if (latLon == null || latLon.length < 4) continue;

                // OMPoly(float[] llPoints, int units, int lType)
                //   units: 0 = DECIMAL_DEGREES
                //   lType: 1 = LINETYPE_STRAIGHT
                Object poly = newInstance(
                    "com.bbn.openmap.omGraphics.OMPoly",
                    new Class[]{float[].class, int.class, int.class},
                    latLon, 0, 1);

                // Fill: leicht transparent turquoise
                invokeSet(poly, "setFillPaint",
                    new Color(colorTop.getRed(), colorTop.getGreen(),
                              colorTop.getBlue(), 30));
                // Rand: Türkis
                invokeSet(poly, "setLinePaint", colorTop);
                invokeSet(poly, "setStroke",
                    new BasicStroke(1.0f));

                invoke(graphicList, "add", poly);
            }

            // Layer neu rendern
            if (geomLayer != null) {
                invoke(geomLayer, "doPrepare");
            }
        } catch (Exception e) {
            System.err.println("FOpenMapPanel.setFeatures(): " + e.getMessage());
        }
    }

    /** Auf Deutschland zoomen. */
    public void zoomToDeutschland() {
        if (!omAvailable || mapBean == null) return;
        try {
            // setCenter via LatLonPoint (abstrakte Basisklasse → konkrete Inner-Class verwenden!)
            // openmap 5.x: LatLonPoint$Double / LatLonPoint$Float
            // Reihenfolge: $Double zuerst (höhere Genauigkeit)
            String[] llpCandidates = {
                "com.bbn.openmap.proj.coords.LatLonPoint$Double",
                "com.bbn.openmap.proj.coords.LatLonPoint$Float",
                "com.bbn.openmap.proj.coords.LatLonPoint",
                "com.bbn.openmap.LatLonPoint",
            };
            boolean centerSet = false;
            for (String llpCls : llpCandidates) {
                try {
                    Object llPoint;
                    try {
                        llPoint = Class.forName(llpCls)
                            .getConstructor(double.class, double.class)
                            .newInstance((double) DEU_CENTER_LAT, (double) DEU_CENTER_LON);
                    } catch (Exception ex) {
                        llPoint = Class.forName(llpCls)
                            .getConstructor(float.class, float.class)
                            .newInstance(DEU_CENTER_LAT, DEU_CENTER_LON);
                    }
                    // passende setCenter-Methode suchen
                    for (java.lang.reflect.Method m : mapBean.getClass().getMethods()) {
                        if (m.getName().equals("setCenter") && m.getParameterCount() == 1
                                && m.getParameterTypes()[0].isAssignableFrom(llPoint.getClass())) {
                            m.invoke(mapBean, llPoint);
                            centerSet = true;
                            break;
                        }
                    }
                    if (centerSet) break;
                } catch (Exception ignored) {}
            }
            if (!centerSet) {
                System.err.println("FOpenMapPanel.zoomToDeutschland(): setCenter nicht gefunden");
            }
            // Scale setzen
            Method setScale = mapBean.getClass().getMethod("setScale", float.class);
            setScale.invoke(mapBean, DEU_SCALE);
        } catch (Exception e) {
            System.err.println("FOpenMapPanel.zoomToDeutschland(): " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Intern: OpenMap-Aufbau
    // ════════════════════════════════════════════════════════════════

    private void buildMapBean() throws Exception {
        // MapBean erzeugen
        Class<?> mbClass = Class.forName("com.bbn.openmap.MapBean");
        mapBean = mbClass.getDeclaredConstructor().newInstance();

        // Als JComponent in dieses Panel einfügen
        add((Component) mapBean, BorderLayout.CENTER);

        // Projektion auf Deutschland setzen
        zoomToDeutschland();
    }

    private void buildTileLayer() throws Exception {
        // OSM-TileLayer (com.bbn.openmap.layer.imageTile.MapTileLayer)
        // Klasse existiert in openmap 5.x; in 6.x kann der Name abweichen.
        Object tileLayer = tryBuildTileLayer();
        if (tileLayer == null) return;

        // TileLayer braucht eine TileFactory mit OSM-URL
        try {
            Class<?> factClass = Class.forName(
                "com.bbn.openmap.layer.imageTile.StandardImageTileFactory");
            Object factory = factClass.getDeclaredConstructor().newInstance();
            // rootDir = OSM-Tile-URL-Template
            Method setRoot = factClass.getMethod("setRootDir", String.class);
            setRoot.invoke(factory,
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png");
            Method setFactory = tileLayer.getClass()
                .getMethod("setTileFactory",
                    Class.forName("com.bbn.openmap.layer.imageTile.ImageTileFactory"));
            setFactory.invoke(tileLayer, factory);
        } catch (Exception e) {
            System.err.println("TileFactory Setup fehlgeschlagen (kein OSM): " + e.getMessage());
        }

        // Layer zu MapHandler hinzufügen (via MapBean)
        addLayerToMap(tileLayer);
    }

    private Object tryBuildTileLayer() {
        String[] candidates = {
            "com.bbn.openmap.layer.imageTile.MapTileLayer",
            "com.bbn.openmap.image.MapTileLayer",
            "com.bbn.openmap.layer.image.MapTileLayer"
        };
        for (String cn : candidates) {
            try {
                Class<?> c = Class.forName(cn);
                return c.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
        }
        System.err.println("FOpenMapPanel: Kein MapTileLayer gefunden, kein OSM-Hintergrund.");
        return null;
    }

    private void buildGeomLayer() throws Exception {
        // OMGraphicHandlerLayer als Container für unsere Polygone
        String[] candidates = {
            "com.bbn.openmap.layer.OMGraphicHandlerLayer",
            "com.bbn.openmap.layer.DrawingToolLayer"
        };
        for (String cn : candidates) {
            try {
                Class<?> c = Class.forName(cn);
                geomLayer = c.getDeclaredConstructor().newInstance();
                break;
            } catch (Exception ignored) {}
        }
        if (geomLayer == null) throw new ClassNotFoundException("Kein OMGraphicHandlerLayer gefunden");

        // OMGraphicList erzeugen und in Layer setzen
        Class<?> listClass = Class.forName("com.bbn.openmap.omGraphics.OMGraphicList");
        graphicList = listClass.getDeclaredConstructor().newInstance();

        // Layer-Name
        try {
            Method setName = geomLayer.getClass().getMethod("setName", String.class);
            setName.invoke(geomLayer, "Bundesländer");
        } catch (Exception ignored) {}
    }

    private void registerLayers() throws Exception {
        if (mapBean == null) return;

        // MapHandler aufbauen
        Class<?> mhClass = Class.forName("com.bbn.openmap.MapHandler");
        mapHandler = mhClass.getDeclaredConstructor().newInstance();

        // MapBean in Handler registrieren
        Method add = mhClass.getMethod("add", Object.class);
        add.invoke(mapHandler, mapBean);

        // Geom-Layer registrieren
        if (geomLayer != null) {
            add.invoke(mapHandler, geomLayer);
        }
    }

    private void addLayerToMap(Object layer) {
        if (mapHandler == null || layer == null) return;
        try {
            Class<?> mhClass = Class.forName("com.bbn.openmap.MapHandler");
            Method add = mhClass.getMethod("add", Object.class);
            add.invoke(mapHandler, layer);
        } catch (Exception e) {
            System.err.println("addLayerToMap: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Geometrie-Extraktion (lat/lon aus GeoFeature)
    // ════════════════════════════════════════════════════════════════

    /**
     * Extrahiert Lat/Lon-Paare aus JGeometry für OMPoly.
     * OMPoly erwartet float[] {lat0, lon0, lat1, lon1, ...}
     * (ACHTUNG: Reihenfolge Lat,Lon — nicht Lon,Lat wie in SDO!)
     */
    private float[] extractLatLon(GeoFeature f) {
        if (f.rawGeom == null) return null;
        try {
            // getOrdinatesArray() → double[] {lon0,lat0, lon1,lat1,...}
            Method m = f.rawGeom.getClass().getMethod("getOrdinatesArray");
            double[] ords = (double[]) m.invoke(f.rawGeom);
            if (ords == null || ords.length < 4) return null;

            // Nur äußerer Ring: OMPoly mit einem Polygon (kein Loch-Support nötig)
            // Tausche X↔Y (lon,lat → lat,lon für OpenMap)
            float[] result = new float[ords.length];
            for (int i = 0; i + 1 < ords.length; i += 2) {
                result[i]     = (float) ords[i + 1]; // lat
                result[i + 1] = (float) ords[i];     // lon
            }
            return result;
        } catch (Exception e) {
            System.err.println("extractLatLon: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Fehler-Panel
    // ════════════════════════════════════════════════════════════════

    private void showErrorPanel() {
        removeAll();
        setBackground(bgError);

        JLabel msg = new JLabel(
            "<html><center><b style='color:#00B5AD'>OpenMap nicht verfügbar</b><br>"
            + "<span style='color:#8888AA;font-size:10px'>"
            + (initError != null ? initError : "openmap.jar fehlt im lib/-Verzeichnis")
            + "</span></center></html>",
            SwingConstants.CENTER);
        add(msg, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // ════════════════════════════════════════════════════════════════
    // Reflection-Helpers
    // ════════════════════════════════════════════════════════════════

    private static Object invoke(Object obj, String methodName, Object... args)
            throws Exception {
        if (args.length == 0) {
            return obj.getClass().getMethod(methodName).invoke(obj);
        }
        // Parametertypen aus den Argumenten ermitteln
        Class<?>[] types = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return obj.getClass().getMethod(methodName, types).invoke(obj, args);
    }

    private static void invokeSet(Object obj, String methodName, Object value)
            throws Exception {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                m.invoke(obj, value);
                return;
            }
        }
        throw new NoSuchMethodException(methodName + " nicht gefunden in "
            + obj.getClass().getSimpleName());
    }

    private static Object newInstance(String className, Class<?>[] types, Object... args)
            throws Exception {
        Class<?> c = Class.forName(className);
        Constructor<?> ctor = c.getConstructor(types);
        return ctor.newInstance(args);
    }

    // ════════════════════════════════════════════════════════════════
    // JavaBean-Accessors
    // ════════════════════════════════════════════════════════════════

    public boolean isOpenMapAvailable() { return omAvailable; }
    public String  getInitError()       { return initError; }
}
