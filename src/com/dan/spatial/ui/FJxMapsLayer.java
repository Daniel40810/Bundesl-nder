package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoProjection;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.awt.Graphics2D;

/**
 * FJxMapsLayer - JXMapViewer2/OpenStreetMap Hintergrund fuer FSpatialMap.
 *
 * Der Klassenname bleibt als interne Bruecke erhalten, die Implementierung nutzt
 * aber nicht mehr TeamDev JXMaps, sondern den reinen Swing-Renderer JXMapViewer2.
 */
public class FJxMapsLayer {

    private JXMapViewer mapViewer;
    private DefaultTileFactory tileFactory;
    private boolean available = false;
    private double renderScale = 1.0;
    private int renderWidth = 1;
    private int renderHeight = 1;

    public void init(int width, int height) {
        if (mapViewer != null) return;

        try {
            TileFactoryInfo info = new HttpsOsmTileFactoryInfo();
            tileFactory = new DefaultTileFactory(info);
            tileFactory.setThreadPoolSize(4);
            tileFactory.setUserAgent("SpatialApp/1.0 (JXMapViewer2)");

            mapViewer = new JXMapViewer();
            mapViewer.setTileFactory(tileFactory);
            mapViewer.setDrawTileBorders(false);
            mapViewer.setPanEnabled(false);
            mapViewer.setRestrictOutsidePanning(true);
            mapViewer.setHorizontalWrapped(false);
            mapViewer.setSize(Math.max(1, width), Math.max(1, height));
            mapViewer.setCenterPosition(new GeoPosition(51.1657, 10.4515));
            mapViewer.setZoom(toViewerZoom(6));
            updateRenderScale(width, height, 1.0);

            available = true;
        } catch (Throwable e) {
            available = false;
            System.err.println("FJxMapsLayer.init(): " + rootCause(e));
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void syncProjection(GeoProjection projection, int width, int height) {
        if (!available || mapViewer == null) return;

        try {
            int safeWidth = Math.max(1, width);
            int safeHeight = Math.max(1, height);
            double centerLon = projection.xToLon(safeWidth / 2.0);
            double centerLat = projection.yToLat(safeHeight / 2.0);
            int osmZoom = estimateOsmZoom(projection);
            double nativeScale = 256.0 * Math.pow(2.0, osmZoom) / 360.0;
            updateRenderScale(safeWidth, safeHeight, nativeScale == 0 ? 1.0 : projection.getTotalScale() / nativeScale);

            mapViewer.setCenterPosition(new GeoPosition(centerLat, centerLon));
            mapViewer.setZoom(toViewerZoom(osmZoom));
        } catch (Throwable e) {
            System.err.println("FJxMapsLayer.syncProjection(): " + rootCause(e));
        }
    }

    public void drawBackground(Graphics2D g2, int width, int height) {
        if (!available || mapViewer == null) return;

        try {
            Graphics2D mapGraphics = (Graphics2D) g2.create(0, 0, width, height);
            try {
                mapGraphics.translate(width / 2.0, height / 2.0);
                mapGraphics.scale(renderScale, renderScale);
                mapGraphics.translate(-renderWidth / 2.0, -renderHeight / 2.0);
                mapViewer.paint(mapGraphics);
            } finally {
                mapGraphics.dispose();
            }
        } catch (Throwable e) {
            System.err.println("FJxMapsLayer.drawBackground(): " + rootCause(e));
        }
    }

    public void dispose() {
        if (tileFactory != null) {
            tileFactory.dispose();
        }
        mapViewer = null;
        tileFactory = null;
        available = false;
    }

    private int estimateOsmZoom(GeoProjection projection) {
        double zoom = Math.log((projection.getTotalScale() * 360.0) / 256.0) / Math.log(2.0);
        return clamp((int) Math.round(zoom), 3, 17);
    }

    private void updateRenderScale(int width, int height, double scale) {
        renderScale = Math.max(0.25, Math.min(4.0, scale));
        renderWidth = Math.max(1, (int) Math.ceil(width / renderScale));
        renderHeight = Math.max(1, (int) Math.ceil(height / renderScale));
        if (mapViewer != null) {
            mapViewer.setSize(renderWidth, renderHeight);
        }
    }

    private int toViewerZoom(int osmZoom) {
        int totalMapZoom = tileFactory != null ? tileFactory.getInfo().getTotalMapZoom() : 17;
        return clamp(totalMapZoom - osmZoom, 0, totalMapZoom);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class HttpsOsmTileFactoryInfo extends OSMTileFactoryInfo {
        @Override
        public String getTileUrl(int x, int y, int zoom) {
            return super.getTileUrl(x, y, zoom).replace("http://", "https://");
        }
    }

    private String rootCause(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }
}
