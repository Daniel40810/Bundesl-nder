package com.dan.spatial.ui;

import com.dan.spatial.geo.GeoFeature;
import com.dan.swing.FComboBox;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * FBoundarySelector — Dropdown zur Auswahl eines Bundeslandes.
 *
 * Fixes:
 *   • Guard gegen leere ComboBox bei selectFeature()
 *   • Feedback-Loop-Schutz (programmatische Änderung feuert keinen Listener)
 */
public class FBoundarySelector extends JPanel {

    private final FComboBox comboBox;
    private List<GeoFeature> features;
    private FeatureChosenListener listener;

    /** Wenn true, kommt die Änderung von selectFeature() → Listener NICHT feuern */
    private boolean programmaticChange = false;

    public FBoundarySelector() {
        setOpaque(false);
        setLayout(new BorderLayout(6, 0));

        JLabel lbl = new JLabel("Bundesland:");
        lbl.setForeground(new Color(180, 200, 240, 200));
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        comboBox = new FComboBox();
        comboBox.setPreferredSize(new Dimension(200, 32));

        add(lbl, BorderLayout.WEST);
        add(comboBox, BorderLayout.CENTER);

        comboBox.addActionListener(e -> {
            // Programmatische Änderung → still ignorieren
            if (programmaticChange || listener == null || features == null) return;
            int idx = comboBox.getSelectedIndex();
            if (idx <= 0) {
                listener.featureChosen(null);
            } else {
                int fi = idx - 1;
                if (fi < features.size()) {
                    listener.featureChosen(features.get(fi));
                }
            }
        });
    }

    /** Füllt das Dropdown mit der ADM1-Liste. */
    public void setFeatures(List<GeoFeature> adm1Features) {
        programmaticChange = true;
        try {
            this.features = adm1Features;
            comboBox.removeAllItems();
            comboBox.addItem("— Alle Bundesländer —");
            for (GeoFeature f : adm1Features) {
                comboBox.addItem(f.shapeName);
            }
        } finally {
            programmaticChange = false;
        }
    }

    /**
     * Setzt die Auswahl programmatisch (z.B. wenn Karte geklickt wird).
     * Feuert den Listener NICHT.
     */
    public void selectFeature(GeoFeature f) {
        if (comboBox.getItemCount() == 0) return;   // noch nicht befüllt
        programmaticChange = true;
        try {
            if (f == null) {
                comboBox.setSelectedIndex(0);
                return;
            }
            if (features == null) return;
            int idx = features.indexOf(f);
            if (idx >= 0 && idx + 1 < comboBox.getItemCount()) {
                comboBox.setSelectedIndex(idx + 1);
            }
        } finally {
            programmaticChange = false;
        }
    }

    public void setFeatureChosenListener(FeatureChosenListener l) {
        this.listener = l;
    }

    public interface FeatureChosenListener {
        void featureChosen(GeoFeature feature);
    }
}
