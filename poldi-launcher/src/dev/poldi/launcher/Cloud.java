package dev.poldi.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/** Wolken-Look: weiche, durchsichtige Flaechen, Pillen-Buttons, Toggle, Textfeld. */
public final class Cloud {
    private Cloud() {}
    public static final Color PURPLE = new Color(0xDC, 0x26, 0x26), BLUE = new Color(0xFC, 0x8A, 0x8A), GOOD = new Color(0x4A, 0xDE, 0x80), BAD = new Color(0xFB, 0x71, 0x85);
    public static final Color TEXT = new Color(0xFF, 0xFF, 0xFF), MUTED = new Color(0xFF, 0xFF, 0xFF, 170);
    public static Font font(int style, float size) { return new Font("Segoe UI", style, 1).deriveFont(style, size); }

    public static Color a(Color c, int al) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), al); }
    public static Color mix(Color x, Color y, float t) { return new Color((int) (x.getRed() + (y.getRed() - x.getRed()) * t), (int) (x.getGreen() + (y.getGreen() - x.getGreen()) * t), (int) (x.getBlue() + (y.getBlue() - x.getBlue()) * t)); }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB == null ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Wolken-Silhouette: Rundrechteck + Huegel oben. */
    public static Shape cloud(float x, float y, float w, float h, float r, boolean bumps) {
        Area a = new Area(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
        if (bumps && w > 160) {
            float b = Math.min(r * 1.25f, h * 0.45f);
            a.add(new Area(new Ellipse2D.Float(x + w * 0.16f, y - b * 0.55f, b * 1.6f, b * 1.4f)));
            a.add(new Area(new Ellipse2D.Float(x + w * 0.34f, y - b * 0.85f, b * 2.0f, b * 1.8f)));
            a.add(new Area(new Ellipse2D.Float(x + w * 0.62f, y - b * 0.5f, b * 1.5f, b * 1.3f)));
        }
        return a;
    }

    /** Durchsichtige Wolken-Karte: weicher Schatten, heller Verlauf, Lichtkante. */
    public static void card(Graphics2D g, float x, float y, float w, float h, float r, boolean bumps, int alpha) {
        Shape s = cloud(x, y, w, h, r, bumps);
        for (int i = 3; i >= 1; i--) { // weicher Schatten
            g.setColor(new Color(60, 8, 8, 14));
            g.setStroke(new BasicStroke(i * 5f)); g.translate(0, 5); g.draw(s); g.translate(0, -5);
        }
        g.setPaint(new GradientPaint(0, y, new Color(255, 255, 255, alpha + 26), 0, y + h, new Color(255, 228, 228, Math.max(8, alpha - 18))));
        g.fill(s);
        g.setPaint(new GradientPaint(0, y, new Color(255, 255, 255, 150), 0, y + h, new Color(255, 255, 255, 40)));
        g.setStroke(new BasicStroke(1.4f)); g.draw(s);
    }

    // ---------- Button ----------
    public static class Btn extends JComponent {
        String text; final Runnable action; boolean primary; float hov; boolean enabled = true; Timer tm;
        public void setPrimary(boolean p) { primary = p; repaint(); }
        public Btn(String text, boolean primary, Runnable action) {
            this.text = text; this.primary = primary; this.action = action; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { anim(1); } public void mouseExited(MouseEvent e) { anim(0); }
                public void mouseClicked(MouseEvent e) { if (enabled && e.getButton() == MouseEvent.BUTTON1) action.run(); }
            });
        }
        void anim(float to) { if (tm != null) tm.stop(); tm = new Timer(16, e -> { hov += (to - hov) * 0.3f; if (Math.abs(to - hov) < 0.02) { hov = to; ((Timer) e.getSource()).stop(); } repaint(); }); tm.start(); }
        public void setText(String t) { text = t; repaint(); }
        public void setEnabled(boolean b) { enabled = b; setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            float w = getWidth() - 2, h = getHeight() - 2; float r = h / 2;
            RoundRectangle2D.Float s = new RoundRectangle2D.Float(1, 1, w, h, r * 2, r * 2);
            int fade = enabled ? 255 : 110;
            if (primary) {
                Color c1 = mix(PURPLE, Color.WHITE, 0.08f * hov * 2), c2 = mix(BLUE, Color.WHITE, 0.08f * hov * 2);
                g.setColor(a(PURPLE, 40)); g.fill(new RoundRectangle2D.Float(-1, 3, w + 4, h + 4, r * 2 + 4, r * 2 + 4));
                g.setPaint(new GradientPaint(0, 0, a(c1, fade), getWidth(), 0, a(c2, fade))); g.fill(s);
                g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(1.2f)); g.draw(s);
            } else {
                g.setColor(new Color(255, 255, 255, (int) (34 + 40 * hov) * fade / 255)); g.fill(s);
                g.setColor(new Color(255, 255, 255, (int) (90 + 80 * hov))); g.setStroke(new BasicStroke(1.2f)); g.draw(s);
            }
            g.setFont(font(Font.BOLD, primary && getHeight() >= 40 ? 20f : 14f)); FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(0, 0, 0, 50)); g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f + 1);
            g.setColor(new Color(255, 255, 255, fade)); g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g.dispose();
        }
    }

    // ---------- Toggle ----------
    public static class Toggle extends JComponent {
        public boolean on; final String label; final java.util.function.Consumer<Boolean> change; float k;
        public Toggle(String label, boolean on, java.util.function.Consumer<Boolean> change) {
            this.label = label; this.on = on; this.k = on ? 1 : 0; this.change = change; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { setOn(!Toggle.this.on); change.accept(Toggle.this.on); } });
        }
        public void setOn(boolean v) { on = v; Timer t = new Timer(16, e -> { k += ((on ? 1 : 0) - k) * 0.3f; if (Math.abs((on ? 1 : 0) - k) < 0.03) { k = on ? 1 : 0; ((Timer) e.getSource()).stop(); } repaint(); }); t.start(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            g.setFont(font(Font.PLAIN, 14f)); g.setColor(TEXT); g.drawString(label, 0, getHeight() / 2f + 5);
            int tw = 46, th = 24, x = getWidth() - tw - 2, y = (getHeight() - th) / 2;
            g.setColor(on || k > 0.5f ? new Color(0xDC, 0x26, 0x26, 210) : new Color(255, 255, 255, 60)); g.fillRoundRect(x, y, tw, th, th, th);
            g.setColor(new Color(255, 255, 255, 130)); g.setStroke(new BasicStroke(1.2f)); g.drawRoundRect(x, y, tw, th, th, th);
            g.setColor(Color.WHITE); int kx = (int) (x + 3 + k * (tw - th)); g.fillOval(kx, y + 3, th - 6, th - 6);
            g.dispose();
        }
    }

    // ---------- Textfeld ----------
    public static class Field extends JTextField {
        public Field(String text) {
            super(text); setOpaque(false); setForeground(Color.WHITE); setCaretColor(Color.WHITE); setFont(font(Font.PLAIN, 14f));
            setBorder(new EmptyBorder(6, 14, 6, 14));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g);
            g.setColor(new Color(255, 255, 255, 38)); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.setColor(new Color(255, 255, 255, hasFocus() ? 190 : 100)); g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.dispose(); super.paintComponent(g0);
        }
    }

    /** Fortschrittsbalken als Wolken-Pille. */
    public static class Bar extends JComponent {
        public double v;
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); hints(g); int w = getWidth() - 2, h = getHeight() - 2;
            g.setColor(new Color(255, 255, 255, 40)); g.fillRoundRect(1, 1, w, h, h, h);
            int fw = (int) Math.max(h, w * Math.min(1, v));
            if (v > 0) { g.setPaint(new GradientPaint(0, 0, PURPLE, w, 0, BLUE)); g.fillRoundRect(1, 1, fw, h, h, h); }
            g.setColor(new Color(255, 255, 255, 110)); g.setStroke(new BasicStroke(1.2f)); g.drawRoundRect(1, 1, w, h, h, h); g.dispose();
        }
    }
}
