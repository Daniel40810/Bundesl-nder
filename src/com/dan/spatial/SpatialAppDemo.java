package com.dan.spatial;

import com.dan.fframe.FFrame;
import com.dan.spatial.db.GeoDataSource;
import com.dan.spatial.geo.GeoFeature;
import com.dan.spatial.ui.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SpatialAppDemo — Einstiegspunkt.
 *
 * Layout:
 *   NORTH  → FLayerControl (Toolbar, inkl. OSM-Toggle)
 *   CENTER → FSpatialMap   (eigene Oracle-Karte + optionaler JXMapViewer-Hintergrund darunter)
 *   EAST   → FBoundarySelector + FGeoFeaturePanel
 *   SOUTH  → FStatusBar
 */
public class SpatialAppDemo {

    private FFrame            frame;
    private FSpatialMap       spatialMap;
    private FGeoFeaturePanel  featurePanel;
    private FBoundarySelector selector;
    private FLayerControl     layerControl;
    private FStatusBar        statusBar;

    private List<GeoFeature> adm0 = new ArrayList<>();
    private List<GeoFeature> adm1 = new ArrayList<>();

    private boolean selectionUpdating = false;
    private boolean jxMapsActive      = false;

    // ----------------------------------------------------------

    public void build() {
        frame = new FFrame("SpatialApp – Oracle Geodaten");
        frame.setPreferredFrameSize(new Dimension(1100, 720));
        frame.setTaskbarColors(new Color(0x00, 0x5C, 0x5A), new Color(0x6B, 0x00, 0x1C));
        frame.setComponentPaneColor(new Color(0x0E, 0x10, 0x17));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        layerControl = new FLayerControl();
        root.add(layerControl, BorderLayout.NORTH);

        spatialMap = new FSpatialMap();
        root.add(spatialMap, BorderLayout.CENTER);

        root.add(buildEastPanel(), BorderLayout.EAST);

        statusBar = new FStatusBar();
        root.add(statusBar, BorderLayout.SOUTH);

        frame.getComponentPane().add(root, BorderLayout.CENTER);
        frame.setVisible(true);

        wireEvents();
        loadData();
    }

    private JPanel buildEastPanel() {
        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.setPreferredSize(new Dimension(250, 100));
        east.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        selector = new FBoundarySelector();
        selector.setMaximumSize(new Dimension(250, 42));
        selector.setAlignmentX(Component.LEFT_ALIGNMENT);
        east.add(selector);
        east.add(Box.createVerticalStrut(10));

        featurePanel = new FGeoFeaturePanel();
        featurePanel.setMaximumSize(new Dimension(250, 280));
        featurePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        east.add(featurePanel);
        return east;
    }

    private void wireEvents() {
        layerControl.setOnZoomFit(e -> {
            spatialMap.zoomFit();
            statusBar.setStatus("Zoom zurückgesetzt");
        });

        layerControl.setOnRefresh(e -> loadData());

        // Karten-Toggle: JXMapViewer-Hintergrund unter die eigene Karte legen
        layerControl.setOnJxMapsToggle(e -> {
            jxMapsActive = !jxMapsActive;
            if (jxMapsActive) {
                statusBar.setStatus("Initialisiere JXMapViewer...");
                SwingWorker<Void,Void> w = new SwingWorker<Void,Void>() {
                    @Override protected Void doInBackground() { return null; }
                    @Override protected void done() {
                        spatialMap.enableJxMaps();
                        if (spatialMap.isJxMapsEnabled()) {
                            statusBar.setStatus("JXMapViewer-Hintergrund aktiv");
                        } else {
                            statusBar.setStatus("JXMapViewer nicht verfuegbar - jxmapviewer2-2.8.jar im lib/ pruefen");
                            jxMapsActive = false;
                        }
                    }
                };
                w.execute();
            } else {
                spatialMap.disableJxMaps();
                statusBar.setStatus("JXMapViewer-Hintergrund deaktiviert");
            }
        });

        spatialMap.addFeatureSelectionListener(f -> {
            if (selectionUpdating) return;
            selectionUpdating = true;
            try {
                featurePanel.showFeature(f);
                selector.selectFeature(f);
                statusBar.setStatus(f != null ? "Ausgewählt: " + f.shapeName : "Bereit");
            } finally {
                selectionUpdating = false;
            }
        });

        spatialMap.addMouseMoveListener((lon, lat) -> {
            statusBar.setCoordinates(lon, lat);
            statusBar.setZoom(spatialMap.getProjection().getRelativeZoom());
        });

        selector.setFeatureChosenListener(f -> {
            if (selectionUpdating) return;
            selectionUpdating = true;
            try {
                spatialMap.selectFeature(f);
                featurePanel.showFeature(f);
                statusBar.setStatus(f != null ? "Ausgewählt: " + f.shapeName : "Bereit");
            } finally {
                selectionUpdating = false;
            }
        });
    }

    private void loadData() {
        statusBar.setLoading(true);
        layerControl.setEnabled(false);

        new SwingWorker<Void, Void>() {
            List<GeoFeature> loadedADM0 = new ArrayList<>();
            List<GeoFeature> loadedADM1 = new ArrayList<>();
            String errorMsg = null;

            @Override
            protected Void doInBackground() {
                try {
                    GeoDataSource ds = new GeoDataSource();
                    loadedADM0 = ds.loadADM0();
                    loadedADM1 = ds.loadADM1();
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                adm0 = loadedADM0;
                adm1 = loadedADM1;

                selector.setFeatures(adm1);
                spatialMap.setADM0Features(adm0);
                spatialMap.setADM1Features(adm1);

                statusBar.setLoading(false);
                layerControl.setEnabled(true);

                if (errorMsg != null) {
                    statusBar.setStatus("Fehler: " + errorMsg);
                    JOptionPane.showMessageDialog(frame,
                        "Datenbankfehler:\n" + errorMsg,
                        "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                } else {
                    statusBar.setStatus("Geladen: " + adm0.size()
                        + " ADM0, " + adm1.size() + " Bundesländer");
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SpatialAppDemo().build());
    }
}
