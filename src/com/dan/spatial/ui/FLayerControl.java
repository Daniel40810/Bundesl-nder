package com.dan.spatial.ui;

import com.dan.swing.FButton;
import com.dan.swing.FLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

/**
 * FLayerControl — dunkle Toolbar oben.
 *
 * Enthält:
 *   • FLabel  (Titel "SpatialApp")
 *   • FButton "Zoom Fit"
 *   • FButton "ADM0 an/aus"
 *   • FButton "ADM1 an/aus"
 *   • FButton "Refresh" (Daten neu laden)
 *
 * Callbacks über ActionListener (setOnZoomFit, setOnRefresh, …)
 */
public class FLayerControl extends JPanel {

    // ---------- Farben ----------
    private final Color bgColor = new Color(0x12, 0x15, 0x1F);
    private final Color rimColor = new Color(140, 200, 255, 60);

    // ---------- Widgets ----------
    private FLabel titleLabel;
    private FButton btnJxMaps;
    private FButton btnZoomFit;
    private FButton btnRefresh;
    private JCheckBox chkADM0;
    private JCheckBox chkADM1;

    // ---------- Konstruktor ----------
    public FLayerControl() {
        setOpaque(false);
        setPreferredSize(new Dimension(800, 48));
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        initWidgets();
    }

    private void initWidgets() {
        // Titel
        titleLabel = new FLabel();
        titleLabel.setText("SpatialApp  |  Deutschland");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setPreferredSize(new Dimension(220, 32));

        // Buttons
        btnZoomFit = new FButton();
        btnZoomFit.setText("Zoom Fit");
        btnZoomFit.setPreferredSize(new Dimension(100, 32));

        btnRefresh = new FButton();
        btnRefresh.setText("Refresh");
        btnRefresh.setPreferredSize(new Dimension(100, 32));

        // Layer-Checkboxen (minimalistisch)
        chkADM0 = makeCheckBox("ADM0", true);
        chkADM1 = makeCheckBox("ADM1", true);

        // JXMapViewer-Toggle
        FButton btnJxMaps = new FButton();
        btnJxMaps.setText("Map");
        btnJxMaps.setPreferredSize(new Dimension(70, 32));
        this.btnJxMaps = btnJxMaps;

        add(titleLabel);
        add(Box.createHorizontalStrut(20));
        add(btnZoomFit);
        add(btnRefresh);
        add(Box.createHorizontalStrut(20));
        add(chkADM0);
        add(chkADM1);
        add(Box.createHorizontalStrut(20));
        add(btnJxMaps);
    }

    private JCheckBox makeCheckBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setOpaque(false);
        cb.setForeground(new Color(180, 200, 240, 220));
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return cb;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0,
                new Color(0x14, 0x18, 0x28, 240), w, 0,
                new Color(0x0A, 0x0C, 0x14, 240)));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
            g2.setColor(rimColor);
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 10, 10));
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    // ----------------------------------------------------------
    // Callback-Setter
    // ----------------------------------------------------------

    public void setOnZoomFit(ActionListener l)  { btnZoomFit.addActionListener(l); }
    public void setOnRefresh(ActionListener l)  { btnRefresh.addActionListener(l); }

    public void setOnADM0Toggle(java.awt.event.ItemListener l)  { chkADM0.addItemListener(l); }
    public void setOnADM1Toggle(java.awt.event.ItemListener l)  { chkADM1.addItemListener(l); }

    public void setOnJxMapsToggle(ActionListener l) { btnJxMaps.addActionListener(l); }

    public boolean isADM0Visible() { return chkADM0.isSelected(); }
    public boolean isADM1Visible() { return chkADM1.isSelected(); }
}
