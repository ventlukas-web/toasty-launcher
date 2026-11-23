package dev.poldi.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

/** 3D-Ansicht fuer Schematics: Software-Renderer (Painter-Algorithmus), Orbit/Zoom/Verschieben, auch von innen. */
public final class SchemView extends JComponent {
    private Schem m;
    private float yaw = 0.8f, pitch = 0.5f, dist = 40, tx, ty, tz;
    public int yMin = 0, yMax = 1000;
    private String hint = "Datei öffnen oder .litematic hierher ziehen";
    private int lastX, lastY, button;
    private boolean fast = false;
    private java.awt.image.BufferedImage lo, full;
    private boolean dirty = true;
    private final javax.swing.Timer settle = new javax.swing.Timer(160, e -> { fast = false; dirty = true; repaint(); });
    { settle.setRepeats(false); }
    private void interact() { fast = true; dirty = true; settle.restart(); repaint(); }
    private int[] px = new int[4], py = new int[4];
    private long[] keys = new long[0];
    private float[] vx = new float[0], vy = new float[0];
    private int[] fcol = new int[0];

    public SchemView() {
        setOpaque(false);
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { lastX = e.getX(); lastY = e.getY(); button = e.getButton(); requestFocusInWindow(); }
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastX, dy = e.getY() - lastY; lastX = e.getX(); lastY = e.getY();
                if (button == MouseEvent.BUTTON1 && !e.isShiftDown()) { yaw -= dx * 0.008f; pitch = Math.max(-1.55f, Math.min(1.55f, pitch + dy * 0.008f)); }
                else pan(dx, dy);
                interact();
            }
            public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(0.25f, Math.min(1000, dist * (float) Math.pow(0.88, -e.getPreciseWheelRotation()))); interact(); }
        };
        addMouseListener(ma); addMouseMotionListener(ma); addMouseWheelListener(ma);
        setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor); }
            @SuppressWarnings("unchecked") public boolean importData(TransferSupport s) {
                try { var l = (java.util.List<java.io.File>) s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor); if (!l.isEmpty()) loadAsync(l.get(0)); return true; } catch (Exception e) { return false; }
            }
        });
    }

    public void testSet(Schem sm, float yw, float pt, float d, float x, float y, float z) { m = sm; yaw = yw; pitch = pt; dist = d; tx = x; ty = y; tz = z; yMin = 0; yMax = sm.sy; }

    public Runnable onLoaded = () -> {};

    public Schem model() { return m; }

    public void loadAsync(java.io.File f) {
        hint = "Lade " + f.getName() + " …"; repaint();
        new Thread(() -> {
            try {
                Schem s = Schem.load(f);
                SwingUtilities.invokeLater(() -> { m = s; yMin = 0; yMax = s.sy; tx = s.sx / 2f; ty = s.sy / 2f; tz = s.sz / 2f; dist = Math.max(s.sx, Math.max(s.sy, s.sz)) * 1.6f; yaw = 0.8f; pitch = 0.5f; onLoaded.run(); dirty = true; repaint(); });
            } catch (Throwable ex) { SwingUtilities.invokeLater(() -> { hint = "Fehler: " + ex.getMessage(); m = null; onLoaded.run(); repaint(); }); }
        }, "schem-load").start();
    }

    public void reset() { if (m == null) return; dirty = true; tx = m.sx / 2f; ty = m.sy / 2f; tz = m.sz / 2f; dist = Math.max(m.sx, Math.max(m.sy, m.sz)) * 1.6f; yaw = 0.8f; pitch = 0.5f; repaint(); }

    public void setRange(int lo, int hi) { yMin = lo; yMax = hi; dirty = true; repaint(); }

    private void pan(int dx, int dy) {
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch), cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float fx = -cp * sy, fy = -sp, fz = -cp * cy;
        float rx = -fz, rz = fx; float rl = (float) Math.hypot(rx, rz); rx /= rl; rz /= rl;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        float k = dist / (getHeight() * 0.9f);
        tx += (-dx * rx + dy * ux) * k; ty += dy * uy * k; tz += (-dx * rz + dy * uz) * k;
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        int W = getWidth(), H = getHeight();
        Shape clip = new RoundRectangle2D.Float(0, 0, W, H, 26, 26);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(12, 14, 40, 120), 0, H, new Color(30, 40, 90, 90)));
        g.fill(clip);
        g.setClip(clip);
        if (m == null || m.count == 0) {
            g.setFont(Cloud.font(Font.PLAIN, 15f)); g.setColor(new Color(255, 255, 255, 190));
            FontMetrics fm = g.getFontMetrics(); g.drawString(hint, (W - fm.stringWidth(hint)) / 2, H / 2);
        } else if (fast) {
            int lw = Math.max(1, W / 2), lh = Math.max(1, H / 2);
            if (lo == null || lo.getWidth() != lw || lo.getHeight() != lh) lo = new java.awt.image.BufferedImage(lw, lh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = lo.createGraphics(); lg.setComposite(AlphaComposite.Clear); lg.fillRect(0, 0, lw, lh); lg.setComposite(AlphaComposite.SrcOver);
            render(lg, lw, lh, false); lg.dispose();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(lo, 0, 0, W, H, null);
        } else {
            // Vollbild nur neu rechnen, wenn sich etwas geaendert hat (sonst gecachtes Bild zeichnen -> kein Flackern, keine Last)
            if (full == null || full.getWidth() != W || full.getHeight() != H) { full = new java.awt.image.BufferedImage(Math.max(1, W), Math.max(1, H), java.awt.image.BufferedImage.TYPE_INT_ARGB); dirty = true; }
            if (dirty) {
                Graphics2D fg = full.createGraphics(); fg.setComposite(AlphaComposite.Clear); fg.fillRect(0, 0, W, H); fg.setComposite(AlphaComposite.SrcOver);
                render(fg, W, H, true); fg.dispose(); dirty = false;
            }
            g.drawImage(full, 0, 0, null);
        }
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 70)); g.setStroke(new BasicStroke(1.2f)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, W - 1, H - 1, 26, 26));
        g.dispose();
    }

    private void render(Graphics2D g, int W, int H, boolean text) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch), cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float camX = tx + dist * cp * sy, camY = ty + dist * sp, camZ = tz + dist * cp * cy;
        float fx = -cp * sy, fy = -sp, fz = -cp * cy;
        float rx = -fz, rz = fx; float rl = (float) Math.hypot(rx, rz); rx /= rl; rz /= rl; // r = f x up, up=(0,1,0)
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // u = r x f (ry=0)
        float focal = H * 0.9f, cx = W / 2f, cyy = H / 2f, near = 0.05f;
        int n = m.count;
        if (keys.length < n * 6) { keys = new long[n * 6]; fcol = new int[n * 6]; vx = new float[n * 6 * 4]; vy = new float[n * 6 * 4]; }
        int fc = 0;
        final float[] B = m.box;
        for (int i = 0; i < n; i++) {
            int row = m.cellY[i]; if (row < yMin || row >= yMax) continue;
            int mask = m.exposed[i] & 63;
            if (m.full[i]) { if (row == yMax - 1) mask |= (m.hidden[i] & 8); if (row == yMin) mask |= (m.hidden[i] & 4); }
            if (mask == 0) continue;
            float x0 = B[i * 6], y0 = B[i * 6 + 1], z0 = B[i * 6 + 2], x1 = B[i * 6 + 3], y1 = B[i * 6 + 4], z1 = B[i * 6 + 5];
            for (int f = 0; f < 6; f++) {
                if ((mask & (1 << f)) == 0) continue;
                // Rueckseiten-Test
                float nx = f == 0 ? -1 : f == 1 ? 1 : 0, ny = f == 2 ? -1 : f == 3 ? 1 : 0, nz = f == 4 ? -1 : f == 5 ? 1 : 0;
                float pxp = f == 0 ? x0 : f == 1 ? x1 : (x0 + x1) / 2, pyp = f == 2 ? y0 : f == 3 ? y1 : (y0 + y1) / 2, pzp = f == 4 ? z0 : f == 5 ? z1 : (z0 + z1) / 2;
                if ((camX - pxp) * nx + (camY - pyp) * ny + (camZ - pzp) * nz <= 0) continue;
                float[] q = quad(f, x0, y0, z0, x1, y1, z1);
                float zs = 0; boolean ok = true; int base = fc * 4;
                for (int k = 0; k < 4; k++) {
                    float dx = q[k * 3] - camX, dy = q[k * 3 + 1] - camY, dz = q[k * 3 + 2] - camZ;
                    float z = dx * fx + dy * fy + dz * fz;
                    if (z < near) { ok = false; break; }
                    float xx = dx * rx + dz * rz, yy = dx * ux + dy * uy + dz * uz;
                    vx[base + k] = cx + xx / z * focal; vy[base + k] = cyy - yy / z * focal; zs += z;
                }
                if (!ok) continue;
                float mnx = Math.min(Math.min(vx[base], vx[base + 1]), Math.min(vx[base + 2], vx[base + 3])), mxx = Math.max(Math.max(vx[base], vx[base + 1]), Math.max(vx[base + 2], vx[base + 3]));
                float mny = Math.min(Math.min(vy[base], vy[base + 1]), Math.min(vy[base + 2], vy[base + 3])), mxy = Math.max(Math.max(vy[base], vy[base + 1]), Math.max(vy[base + 2], vy[base + 3]));
                if (mxx < 0 || mxy < 0 || mnx > W || mny > H || (mxx - mnx < 1f && mxy - mny < 1f)) continue;
                float light = f == 3 ? 1f : f == 2 ? 0.5f : (f < 2 ? 0.8f : 0.66f);
                int c = m.rgb[i]; int hsh = (i * 2654435761L > 0 ? (int) ((i * 2654435761L >>> 7) & 15) : 0);
                float j = 0.96f + hsh / 16f * 0.08f; light *= j;
                int a = Math.round(m.alpha[i] * 255);
                int r = Math.min(255, (int) (((c >> 16) & 255) * light)), gg = Math.min(255, (int) (((c >> 8) & 255) * light)), b = Math.min(255, (int) ((c & 255) * light));
                fcol[fc] = (a << 24) | (r << 16) | (gg << 8) | b;
                keys[fc] = ((long) Float.floatToIntBits(zs / 4f) << 32) | fc;
                fc++;
            }
        }
        Arrays.sort(keys, 0, fc);
        for (int k = fc - 1; k >= 0; k--) { // weit -> nah
            int id = (int) (keys[k] & 0xFFFFFFFFL), base = id * 4;
            for (int v = 0; v < 4; v++) { px[v] = Math.round(vx[base + v]); py[v] = Math.round(vy[base + v]); }
            int col = fcol[id];
            g.setColor(new Color(col, (col >>> 24) != 255));
            g.fillPolygon(px, py, 4);
        }
        if (!text) return;
        g.setFont(Cloud.font(Font.PLAIN, 12f)); g.setColor(new Color(255, 255, 255, 170));
        g.drawString("Ziehen: drehen · Shift/Rechtsklick: verschieben · Mausrad: zoomen (auch hinein)", 14, H - 12);
    }

    private final float[] qbuf = new float[12];
    private float[] quad(int f, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] q = qbuf;
        switch (f) {
            case 0 -> set(q, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            case 1 -> set(q, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
            case 2 -> set(q, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case 3 -> set(q, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            case 4 -> set(q, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            default -> set(q, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        }
        return q;
    }
    private static void set(float[] q, float... v) { System.arraycopy(v, 0, q, 0, 12); }
}
