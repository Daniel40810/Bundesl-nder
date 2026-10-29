package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoProjection;

import java.awt.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * FOpenMapLayer — OpenMap MapBean als Tile-Hintergrund für FSpatialMap.
 *
 * Vollständig via Reflection, kompatibel mit openmap 5.x und 6.x.
 *
 * Initialisierungsstrategie:
 *   1. MapBean instanziieren
 *   2. Projektion setzen (Mercator, Deutschland-Mitte)
 *   3. OSM-TileLayer über MapHandler anhängen
 *
 * Fehlerbehandlung: bei jedem Schritt wird die vollständige
 * Exception-Kausalkette geloggt; Fehler sind nie lautlos.
 */
public class FOpenMapLayer {

    private Object  mapBean   = null;
    private Object  mapHandler = null;
    private boolean available = false;
    private String  error     = null;

    private int lastW = 0, lastH = 0;

    // ─────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────

    public void init(int w, int h) {
        try {
            log("init START w=" + w + " h=" + h);

            // 1. MapBean
            mapBean = instantiate("com.bbn.openmap.MapBean");
            log("MapBean OK: " + mapBean.getClass().getName());

            // 2. Größe
            callMethod(mapBean, "setSize",
                new Class[]{int.class, int.class}, w, h);
            lastW = w; lastH = h;
            log("setSize OK");

            // 3. MapHandler
            mapHandler = instantiate("com.bbn.openmap.MapHandler");
            callMethod(mapHandler, "add", new Class[]{Object.class}, mapBean);
            log("MapHandler OK");

            // 4. Projektion auf Deutschland (Mercator, Scale ≈ Deutschland-Übersicht)
            setMercatorProjection(w, h, 51.2f, 10.4f, 8_000_000f);
            log("Projektion OK");

            // 5. OSM-TileLayer (best-effort, kein Fehler wenn nicht vorhanden)
            tryAttachTileLayer();

            available = true;
            log("init FERTIG");

        } catch (Exception e) {
            error = rootCause(e);
            System.err.println("FOpenMapLayer.init() FEHLER:");
            printStackTrace(e);
            System.err.println("Root cause: " + error);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────

    public void drawBackground(Graphics2D g2, int w, int h) {
        if (!available || mapBean == null) return;
        try {
            if (w != lastW || h != lastH) {
                callMethod(mapBean, "setSize",
                    new Class[]{int.class, int.class}, w, h);
                lastW = w; lastH = h;
            }
            callMethod(mapBean, "paint", new Class[]{Graphics.class}, (Graphics) g2);
        } catch (Exception e) {
            System.err.println("FOpenMapLayer.drawBackground(): " + rootCause(e));
        }
    }

    private double lastSyncLat = 0, lastSyncLon = 0;
    private float  lastSyncSc  = 0;

    public void syncProjection(GeoProjection proj, int w, int h) {
        if (!available || mapBean == null) return;
        try {
            double lon = proj.xToLon(w / 2.0);
            double lat = proj.yToLat(h / 2.0);
            // Scale-Formel für Mercator:
            //   OpenMap-Scale [m/pixel] ≈ cos(lat°) * 40_075_016 / (totalScale * 360)
            double cosLat = Math.cos(Math.toRadians(lat));
            float sc = (float)(cosLat * 40_075_016.0 / (proj.getTotalScale() * 360.0 / 256.0));
            sc = Math.max(500f, Math.min(100_000_000f, sc));

            // Nur neu setzen wenn sich etwas geändert hat (>0.001° oder >1% Scale)
            if (Math.abs(lat - lastSyncLat) < 0.001
                    && Math.abs(lon - lastSyncLon) < 0.001
                    && Math.abs(sc - lastSyncSc) / (lastSyncSc + 1) < 0.01) return;
            lastSyncLat = lat; lastSyncLon = lon; lastSyncSc = sc;

            if (w != lastW || h != lastH) {
                callMethod(mapBean, "setSize", new Class[]{int.class, int.class}, w, h);
                lastW = w; lastH = h;
            }
            setMercatorProjection(w, h, (float) lat, (float) lon, sc);
        } catch (Exception e) {
            System.err.println("FOpenMapLayer.syncProjection(): " + rootCause(e));
        }
    }

    // ─────────────────────────────────────────────────────────
    // setCenter — probiert alle bekannten OpenMap-APIs
    // ─────────────────────────────────────────────────────────

    private void setCenter(float lat, float lon) throws Exception {
        // Variante A: setCenter(float, float)  — openmap 5.x
        try {
            callMethod(mapBean, "setCenter",
                new Class[]{float.class, float.class}, lat, lon);
            return;
        } catch (NoSuchMethodException ignored) {}

        // Variante B: setCenter(double, double)
        try {
            callMethod(mapBean, "setCenter",
                new Class[]{double.class, double.class}, (double)lat, (double)lon);
            return;
        } catch (NoSuchMethodException ignored) {}

        // Variante C: openmap 5.x/6.x → LatLonPoint (konkrete Inner-Classes, verschiedene Pakete)
        // Achtung: com.bbn.openmap.proj.coords.LatLonPoint ist abstrakt → immer $Double/$Float verwenden!
        String[] llpClasses = {
            "com.bbn.openmap.proj.coords.LatLonPoint$Double",
            "com.bbn.openmap.proj.coords.LatLonPoint$Float",
            "com.bbn.openmap.proj.coords.LatLonPoint",
            "com.bbn.openmap.LatLonPoint",
            "com.bbn.openmap.proj.coords.LatLonGct",
        };
        for (String llpCls : llpClasses) {
            try {
                // Konstruktor (double,double) oder (float,float)
                Object llp = null;
                try {
                    llp = Class.forName(llpCls)
                               .getConstructor(double.class, double.class)
                               .newInstance((double)lat, (double)lon);
                } catch (Exception e1) {
                    llp = Class.forName(llpCls)
                               .getConstructor(float.class, float.class)
                               .newInstance(lat, lon);
                }
                // setCenter(LatLonPoint) — suche passende Methode
                for (Method m : mapBean.getClass().getMethods()) {
                    if (m.getName().equals("setCenter") && m.getParameterCount() == 1) {
                        if (m.getParameterTypes()[0].isAssignableFrom(llp.getClass())) {
                            m.invoke(mapBean, llp);
                            log("setCenter via " + llpCls + " OK");
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Variante D: setCenter(Point2D) — neuere API
        try {
            Object pt = Class.forName("java.awt.geom.Point2D$Double")
                             .getConstructor(double.class, double.class)
                             .newInstance((double)lon, (double)lat); // lon,lat für Point2D
            for (Method m : mapBean.getClass().getMethods()) {
                if (m.getName().equals("setCenter") && m.getParameterCount() == 1) {
                    m.invoke(mapBean, pt);
                    log("setCenter via Point2D OK");
                    return;
                }
            }
        } catch (Exception ignored) {}

        log("WARNUNG: setCenter() nicht gefunden – Karte zeigt Default-Position");
        // Kein throw – MapBean zeigt dann einfach die Default-Position
    }

    private void setScale(float scale) {
        try {
            callMethod(mapBean, "setScale", new Class[]{float.class}, scale);
        } catch (Exception e) {
            log("setScale fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Setzt eine explizite Mercator-Projektion auf MapBean.
     * Das ist der zuverlässigste Weg in OpenMap 5.x um Tiles zu triggern.
     */
    private void setMercatorProjection(int w, int h, float lat, float lon, float scale) {
        // Strategie: Nutze die **existierende** Mercator-Projektion von MapBean
        // und update nur center + scale via Setter-Methoden.
        // Das ist viel robuster als einen neuen Konstruktor aufzurufen.
        try {
            // Aktuelle Projektion holen
            Object proj = mapBean.getClass().getMethod("getProjection").invoke(mapBean);
            if (proj == null) {
                log("FEHLER: getProjection() = null");
                return;
            }

            // LatLonPoint erzeugen für center
            Object center = null;
            // LatLonPoint ist abstrakt in OpenMap 5.x → konkrete Inner-Classes verwenden!
            String[] llpClasses = {
                "com.bbn.openmap.proj.coords.LatLonPoint$Double",
                "com.bbn.openmap.proj.coords.LatLonPoint$Float",
                "com.bbn.openmap.proj.coords.LatLonPoint",
                "com.bbn.openmap.LatLonPoint",
            };
            for (String llpCls : llpClasses) {
                try {
                    center = Class.forName(llpCls)
                        .getConstructor(double.class, double.class)
                        .newInstance((double) lat, (double) lon);
                    break;
                } catch (Exception e1) {
                    try {
                        center = Class.forName(llpCls)
                            .getConstructor(float.class, float.class)
                            .newInstance(lat, lon);
                        break;
                    } catch (Exception e2) { continue; }
                }
            }
            if (center == null) {
                log("Könnte LatLonPoint nicht erzeugen");
                return;
            }

            // Projection.setCenter(LatLonPoint) aufrufen
            for (Method m : proj.getClass().getMethods()) {
                if (m.getName().equals("setCenter") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(center.getClass())) {
                    m.invoke(proj, center);
                    log("Projection.setCenter OK");
                    break;
                }
            }

            // Projection.setScale(float) aufrufen
            for (Method m : proj.getClass().getMethods()) {
                if (m.getName().equals("setScale") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == float.class) {
                    m.invoke(proj, scale);
                    log("Projection.setScale(" + (int)scale + ") OK");
                    break;
                }
            }

            // Größe updaten — via RotateInstruction oder neue Projektion
            // OpenMap 5.x braucht setSize auf der Projektion nicht, aber MapBean.setProjection
            // mit der modifizierten Projektion triggert ein repaint
            mapBean.getClass().getMethod("setProjection", 
                Class.forName("com.bbn.openmap.proj.Projection"))
                .invoke(mapBean, proj);
            log("Mercator-Projektion gesetzt: " + lat + "/" + lon + " @ " + (int)scale);

        } catch (Exception e) {
            log("setMercatorProjection fehlgeschlagen: " + rootCause(e));
            // Fallback: setCenter/setScale direkt auf MapBean
            try {
                setCenter(lat, lon);
                setScale(scale);
                log("Fallback: setCenter/setScale erfolgreich");
            } catch (Exception e2) {
                log("Fallback auch fehlgeschlagen: " + rootCause(e2));
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // TileLayer (best-effort, kein Fehler wenn nicht vorhanden)
    // ─────────────────────────────────────────────────────────

    private void tryAttachTileLayer() {
        String[] tileClasses = {
            "com.bbn.openmap.layer.imageTile.MapTileLayer",
            "com.bbn.openmap.image.MapTileLayer",
        };
        for (String cn : tileClasses) {
            try {
                Object tl = instantiate(cn);
                log("TileLayer: " + cn);
                trySetOsmUrl(tl, cn);
                callMethod(mapHandler, "add", new Class[]{Object.class}, tl);
                log("TileLayer registriert");
                // doPrepare() → startet ersten Tile-Download
                try {
                    callMethod(tl, "doPrepare", new Class[0]);
                    log("doPrepare() OK");
                } catch (Exception e) {
                    log("doPrepare() nicht verfügbar: " + e.getMessage());
                }
                return;
            } catch (Exception e) {
                log("TileLayer " + cn + " fehlgeschlagen: " + e.getMessage());
            }
        }
        log("Kein TileLayer gefunden – kein OSM-Hintergrund");
    }

    private void trySetOsmUrl(Object tl, String tlClass) {
        String OSM_ROOT  = "https://openstreetmap.org/";
        // Lokaler Tile-Cache im System-Temp-Verzeichnis
        String CACHE_DIR = System.getProperty("java.io.tmpdir")
                           + java.io.File.separator + "osm_tile_cache"
                           + java.io.File.separator;
        new java.io.File(CACHE_DIR).mkdirs();
        log("Tile-Cache: " + CACHE_DIR);

        try {
            Object factory = instantiate(
                "com.bbn.openmap.dataAccess.mapTile.ServerMapTileFactory");

            // rootDir = HTTP-Basis-URL des Tile-Servers
            callMethod(factory, "setRootDir",
                new Class[]{String.class}, OSM_ROOT);
            log("setRootDir = " + OSM_ROOT);

            // fileExt = Dateiendung
            try {
                callMethod(factory, "setFileExt",
                    new Class[]{String.class}, ".png");
                log("setFileExt = .png");
            } catch (Exception ignored) {}

            // localCacheDir = wo Tiles gespeichert werden
            // Methodenname variiert: setLocalCacheRootDir, setCacheDir, setLocalDir
            boolean cacheSet = false;
            for (String setter : new String[]{
                    "setLocalCacheRootDir", "setCacheDir",
                    "setLocalDir", "setTempDir", "setCacheRootDir"}) {
                try {
                    callMethod(factory, setter,
                        new Class[]{String.class}, CACHE_DIR);
                    log(setter + " = " + CACHE_DIR);
                    cacheSet = true;
                    break;
                } catch (Exception ignored) {}
            }
            // Alle String-Setter loggen falls Cache nicht gesetzt
            if (!cacheSet) {
                log("Cache-Setter nicht gefunden, alle String-Setter:");
                for (Method m : factory.getClass().getMethods()) {
                    if (m.getParameterCount() >= 1
                            && m.getParameterTypes()[0] == String.class) {
                        log("  " + m.getName()
                            + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
                    }
                }
            }

            // Factory in TileLayer setzen
            for (Method m : tl.getClass().getMethods()) {
                if (m.getName().equals("setTileFactory") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(factory.getClass())) {
                    m.invoke(tl, factory);
                    log("setTileFactory(ServerMapTileFactory) OK");
                    return;
                }
            }
            log("FEHLER: setTileFactory-Methode passt nicht");
        } catch (Exception e) {
            log("ServerMapTileFactory fehlgeschlagen: " + rootCause(e));
        }
        log("WARNUNG: OSM-URL konnte nicht gesetzt werden");
    }

    // ─────────────────────────────────────────────────────────
    // Reflection-Utilities
    // ─────────────────────────────────────────────────────────

    private static Object instantiate(String className) throws Exception {
        try {
            return Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException("InvocationTargetException in " + className, cause);
        }
    }

    private static Object callMethod(Object obj, String name,
                                     Class<?>[] types, Object... args) throws Exception {
        try {
            Method m = obj.getClass().getMethod(name, types);
            return m.invoke(obj, args);
        } catch (InvocationTargetException e) {
            // Echte Ursache weiterwerfen
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    /** Vollständige Exception-Kette als String. */
    private static String rootCause(Throwable e) {
        Throwable t = e;
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (t != null && seen.add(t)) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
            t = t.getCause();
        }
        return sb.toString();
    }

    private static void printStackTrace(Throwable e) {
        Throwable t = e;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (t != null && seen.add(t)) {
            System.err.println((t == e ? "" : "Caused by: ")
                + t.getClass().getName() + ": " + t.getMessage());
            for (StackTraceElement el : t.getStackTrace()) {
                System.err.println("    at " + el);
            }
            t = t.getCause();
        }
    }

    private static void log(String msg) {
        System.out.println("[FOpenMapLayer] " + msg);
    }

    // ─────────────────────────────────────────────────────────
    // Getter
    // ─────────────────────────────────────────────────────────

    public boolean isAvailable() { return available; }
    public String  getError()    { return error; }
}
