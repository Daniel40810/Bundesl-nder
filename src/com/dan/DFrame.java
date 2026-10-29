package com.dan;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DFrame extends JFrame
  {

    private Dimension preferredFrameSize = new Dimension(400, 300);
    private Color componentPaneColor = Color.BLACK;
    private Color taskbarColor1 = new Color(0x01, 0x67, 0x6A);
    private Color taskbarColor2 = new Color(0xcc, 0x00, 0x66);
    private String frameTitle = "DFrame";
    private ImageIcon logoIcon;

    private static final int ARC = 20;
    private static final int TASKBAR_HEIGHT = 36;
    private static final int RESIZE_MARGIN = 6;

    private JPanel componentPane;
    private DTaskbar taskbar;
    private JPanel outerPanel;

    // ────────────────────────────────────────────────────────────────
    //  Konstruktor – jetzt WARNUNGSFREI
    // ────────────────────────────────────────────────────────────────
    public DFrame(String title)
      {
        this.frameTitle = title;
        initUI();   // ← gesamte UI‑Initialisierung hierhin verlagert
      }

    public DFrame()
      {
        this("DFrame");
      }

    // ────────────────────────────────────────────────────────────────
    //  UI‑Initialisierung (ersetzt alten Konstruktor)
    // ────────────────────────────────────────────────────────────────
    private void initUI()
      {

        // JFrame Setup
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(preferredFrameSize);
        setMinimumSize(new Dimension(200, 120));

        try {
            AWTUtilities_setWindowOpaque(this, false);
        } catch (Exception ignored) {
        }

        // Logo laden
        logoIcon = loadIcon("/com/dan/icon/Logo.png", 20, 20);

        // Outer Panel
        outerPanel = new JPanel(new BorderLayout(0, 0))
          {
            @Override
            protected void paintComponent(Graphics g)
              {
                // transparent
              }
          };
        outerPanel.setOpaque(false);

        // Taskbar
        taskbar = new DTaskbar();
        outerPanel.add(taskbar, BorderLayout.NORTH);

        // Component Pane
        componentPane = new JPanel()
          {
            @Override
            protected void paintComponent(Graphics g)
              {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(componentPaneColor);

                int w = getWidth(), h = getHeight();
                g2.fillRoundRect(0, -ARC, w, h + ARC, ARC * 2, ARC * 2);
                g2.dispose();
              }
          };
        componentPane.setOpaque(false);
        componentPane.setLayout(new BorderLayout());
        outerPanel.add(componentPane, BorderLayout.CENTER);

        // Resizer
        DResizer resizer = new DResizer(this);
        outerPanel.addMouseListener(resizer);
        outerPanel.addMouseMotionListener(resizer);

        setContentPane(outerPanel);

        pack();
        setLocationRelativeTo(null);
      }

    // ────────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────────
    public JPanel getComponentPane()
      {
        return componentPane;
      }

    public void setPreferredFrameSize(Dimension d)
      {
        this.preferredFrameSize = d;
        setPreferredSize(d);
        pack();
      }

    public void setComponentPaneColor(Color c)
      {
        this.componentPaneColor = c;
        componentPane.repaint();
      }

    public void setTaskbarColors(Color c1, Color c2)
      {
        this.taskbarColor1 = c1;
        this.taskbarColor2 = c2;
        taskbar.repaint();
      }

    @Override
    public void setTitle(String title)
      {
        this.frameTitle = title;
        taskbar.repaint();
      }

    @Override
    public String getTitle()
      {
        return frameTitle;
      }

    public void setLogoIcon(ImageIcon icon)
      {
        this.logoIcon = icon;
        taskbar.repaint();
      }

    // ────────────────────────────────────────────────────────────────
    //  Taskbar
    // ────────────────────────────────────────────────────────────────
    private class DTaskbar extends JPanel
      {

        private final DIconButton btnMinimize;
        private final DIconButton btnMaximize;
        private final DIconButton btnClose;

        private Point dragStart;

        DTaskbar()
          {
            setPreferredSize(new Dimension(0, TASKBAR_HEIGHT));
            setOpaque(false);
            setLayout(null);

            btnMinimize = new DIconButton(
                    loadIcon("/com/dan/icon/Minimize.png", 16, 16),
                    new Color(0x2A, 0xAA, 0xAD),
                    new Color(0x00, 0x50, 0x52),
                    new Color(0x00, 0x3A, 0x3C)
            );
            btnMaximize = new DIconButton(
                    loadIcon("/com/dan/icon/Maximize.png", 16, 16),
                    new Color(0x2A, 0xAA, 0xAD),
                    new Color(0x00, 0x50, 0x52),
                    new Color(0x00, 0x3A, 0x3C)
            );
            btnClose = new DIconButton(
                    loadIcon("/com/dan/icon/Close.png", 16, 16),
                    new Color(0xC0, 0x39, 0x2B),
                    new Color(0x96, 0x28, 0x1E),
                    new Color(0x6E, 0x1A, 0x13)
            );

            btnMinimize.addActionListener(e -> DFrame.this.setState(JFrame.ICONIFIED));
            btnMaximize.addActionListener(e -> toggleMaximize());
            btnClose.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(btnClose);
                if (w != null) {
                    w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING));
                }
            });

            add(btnMinimize);
            add(btnMaximize);
            add(btnClose);

            addMouseListener(new MouseAdapter()
              {
                @Override
                public void mousePressed(MouseEvent e)
                  {
                    dragStart = e.getPoint();
                  }

                @Override
                public void mouseReleased(MouseEvent e)
                  {
                    dragStart = null;
                  }
              });

            addMouseMotionListener(new MouseMotionAdapter()
              {
                @Override
                public void mouseDragged(MouseEvent e)
                  {
                    if (dragStart != null) {
                        Point loc = DFrame.this.getLocation();
                        DFrame.this.setLocation(
                                loc.x + e.getX() - dragStart.x,
                                loc.y + e.getY() - dragStart.y
                        );
                    }
                  }
              });
          }

        @Override
        protected void paintComponent(Graphics g)
          {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            if (w <= 1) {
                w = DFrame.this.getWidth();
            }
            int h = getHeight();

            GradientPaint gp = new GradientPaint(0, 0, taskbarColor1, w, 0, taskbarColor2);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h + ARC, ARC * 2, ARC * 2);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, w - 2, h + ARC, ARC * 2, ARC * 2);

            int iconX = 8;
            if (logoIcon != null) {
                Image img = logoIcon.getImage();
                g2.drawImage(img, iconX, (h - logoIcon.getIconHeight()) / 2,
                        logoIcon.getIconWidth(), logoIcon.getIconHeight(), null);
                iconX += logoIcon.getIconWidth() + 6;
            }

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(frameTitle, iconX, textY);

            g2.dispose();
          }

        @Override
        public void doLayout()
          {
            super.doLayout();
            int h = getHeight();
            int btnW = 28, btnH = 22;
            int gap = 4;
            int y = (h - btnH) / 2;
            int x = getWidth() - (btnW + gap) * 3 - gap;

            btnMinimize.setBounds(x, y, btnW, btnH);
            x += btnW + gap;
            btnMaximize.setBounds(x, y, btnW, btnH);
            x += btnW + gap;
            btnClose.setBounds(x, y, btnW, btnH);
          }

        private boolean maximized = false;
        private Rectangle restoreBounds;

        private void toggleMaximize()
          {
            if (!maximized) {
                restoreBounds = DFrame.this.getBounds();
                GraphicsConfiguration gc = DFrame.this.getGraphicsConfiguration();
                Rectangle screen = gc.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                DFrame.this.setBounds(
                        screen.x + insets.left,
                        screen.y + insets.top,
                        screen.width - insets.left - insets.right,
                        screen.height - insets.top - insets.bottom
                );
                maximized = true;
            } else {
                DFrame.this.setBounds(restoreBounds);
                maximized = false;
            }
          }
      }

    // ────────────────────────────────────────────────────────────────
    //  DIconButton
    // ────────────────────────────────────────────────────────────────
    static class DIconButton extends JButton
      {

        private final Color normalColor;
        private final Color hoverColor;
        private final Color pressColor;

        private boolean hovered = false;
        private boolean pressed = false;

        DIconButton(ImageIcon icon, Color normal, Color hover, Color press)
          {
            super(icon);
            this.normalColor = normal;
            this.hoverColor = hover;
            this.pressColor = press;

            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter()
              {
                @Override
                public void mouseEntered(MouseEvent e)
                  {
                    hovered = true;
                    repaint();
                  }

                @Override
                public void mouseExited(MouseEvent e)
                  {
                    hovered = false;
                    repaint();
                  }

                @Override
                public void mousePressed(MouseEvent e)
                  {
                    pressed = true;
                    repaint();
                  }

                @Override
                public void mouseReleased(MouseEvent e)
                  {
                    pressed = false;
                    repaint();
                  }
              });
          }

        @Override
        protected void paintComponent(Graphics g)
          {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            Color base = pressed ? pressColor : (hovered ? hoverColor : normalColor);

            if (hovered || pressed) {
                g2.setColor(base.darker());
                g2.fillRoundRect(1, 1, w - 1, h - 1, 8, 8);

                int offset = pressed ? 1 : 0;
                g2.setColor(base);
                g2.fillRoundRect(0, 0, w - 1 - offset, h - 1 - offset, 8, 8);

                if (!pressed) {
                    g2.setColor(new Color(255, 255, 255, 50));
                    g2.fillRoundRect(0, 0, w - 2, h / 2, 8, 8);
                }
            }

            g2.dispose();

            int shift = pressed ? 1 : 0;
            if (getIcon() != null) {
                Icon icon = getIcon();
                int ix = (w - icon.getIconWidth()) / 2 + shift;
                int iy = (h - icon.getIconHeight()) / 2 + shift;
                icon.paintIcon(this, g, ix, iy);
            }
          }
      }

    // ────────────────────────────────────────────────────────────────
    //  DResizer
    // ────────────────────────────────────────────────────────────────
    private static class DResizer extends MouseAdapter
      {

        private final JFrame frame;
        private int direction = 0;
        private Point dragStart = null;
        private Rectangle startBounds = null;

        private static final int N = 1, S = 2, W = 4, E = 8;

        DResizer(JFrame frame)
          {
            this.frame = frame;
          }

        @Override
        public void mouseMoved(MouseEvent e)
          {
            direction = getDirection(e.getPoint(), frame);
            frame.setCursor(getCursor(direction));
          }

        @Override
        public void mousePressed(MouseEvent e)
          {
            direction = getDirection(e.getPoint(), frame);
            if (direction != 0) {
                dragStart = e.getLocationOnScreen();
                startBounds = frame.getBounds();
            }
          }

        @Override
        public void mouseDragged(MouseEvent e)
          {
            if (direction == 0 || dragStart == null) {
                return;
            }

            Point cur = e.getLocationOnScreen();
            int dx = cur.x - dragStart.x;
            int dy = cur.y - dragStart.y;

            int x = startBounds.x, y = startBounds.y;
            int w = startBounds.width, h = startBounds.height;
            int minW = frame.getMinimumSize().width;
            int minH = frame.getMinimumSize().height;

            if ((direction & E) != 0) {
                w = Math.max(minW, w + dx);
            }
            if ((direction & S) != 0) {
                h = Math.max(minH, h + dy);
            }
            if ((direction & W) != 0) {
                int nw = Math.max(minW, w - dx);
                x = x + w - nw;
                w = nw;
            }
            if ((direction & N) != 0) {
                int nh = Math.max(minH, h - dy);
                y = y + h - nh;
                h = nh;
            }

            frame.setBounds(x, y, w, h);
          }

        @Override
        public void mouseReleased(MouseEvent e)
          {
            dragStart = null;
            startBounds = null;
            direction = 0;
            frame.setCursor(Cursor.getDefaultCursor());
          }

        private static int getDirection(Point p, JFrame f)
          {
            int m = RESIZE_MARGIN;
            int w = f.getWidth(), h = f.getHeight();
            int dir = 0;
            if (p.y < m) {
                dir |= N;
            }
            if (p.y > h - m) {
                dir |= S;
            }
            if (p.x < m) {
                dir |= W;
            }
            if (p.x > w - m) {
                dir |= E;
            }
            return dir;
          }

        private static Cursor getCursor(int dir)
          {
            switch (dir) {
                case N:
                    return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case S:
                    return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case W:
                    return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case E:
                    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case N | W:
                    return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case N | E:
                    return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case S | W:
                    return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case S | E:
                    return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default:
                    return Cursor.getDefaultCursor();
            }
          }
      }

    // ────────────────────────────────────────────────────────────────
    //  Helper
    // ────────────────────────────────────────────────────────────────
    private static ImageIcon loadIcon(String path, int w, int h)
      {
        try {
            java.net.URL url = DFrame.class.getResource(path);
            if (url == null) {
                return null;
            }
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
      }

    private static void AWTUtilities_setWindowOpaque(Window w, boolean opaque)
      {
        // no-op for modern Java
      }
  }
