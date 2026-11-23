package dev.poldi.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static dev.poldi.launcher.Cloud.*;

/** Poldi Client Launcher: alle Minecraft-Versionen mit Fabric, Hauptversion 26.2 mit Poldi Client Mod, rotes Design. */
public final class Main extends JPanel {
    static final int W = 980, H = 620;
    final Config cfg = Config.load();
    BufferedImage logo;
    int tab = 0;
    final List<float[]> clouds = new ArrayList<>(); // x, y, scale, speed, alpha
    final JPanel play = new JPanel(null), mods = new JPanel(null), schem = new JPanel(null), news = new JPanel(null), settings = new JPanel(null), support = new JPanel(null);
    final Bar bar = new Bar();
    final JLabel status = new JLabel("", SwingConstants.CENTER), installed = new JLabel("", SwingConstants.CENTER), accLabel = new JLabel();
    Btn playBtn, accBtn, verBtn, updBtn;
    final JLabel greet = new JLabel("");
    String greetFull = "Hi, Player"; int greetN = 0;
    JTextArea newsText;
    volatile boolean busy = false, cancel = false;
    volatile Process game;
    Frame frame;

    Main() {
        setLayout(null); setOpaque(false); setPreferredSize(new Dimension(W, H));
        try { logo = ImageIO.read(Main.class.getResourceAsStream("/logo.png")); } catch (Exception ignored) {}
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 9; i++) clouds.add(new float[]{r.nextInt(W), 60 + r.nextInt(H - 120), 0.7f + r.nextFloat() * 1.3f, 0.12f + r.nextFloat() * 0.25f, 28 + r.nextInt(30)});
        buildNav(); buildPlay(); buildMods(); buildSchem(); buildNews(); buildOwner(); buildSettings(); buildSupport(); buildTitleButtons();
        for (JPanel p : new JPanel[]{play, mods, schem, news, settings, support}) { p.setOpaque(false); p.setBounds(262, 78, 696, 520); add(p); }
        show(0); refreshAccount(); startGreeting(); checkLauncherUpdate();
        // Wolken-Animation: nur wenn sie sichtbar etwas bringt (nicht beim Fensterziehen, nicht im Schematic-Tab, nicht im Hintergrund), 20 fps
        new Timer(50, e -> {
            if (dragging || tab == 2 || frame == null || !frame.isActive()) return;
            for (float[] c : clouds) { c[0] += c[3] * 1.5f; if (c[0] > W + 200) c[0] = -260; }
            repaint();
        }).start();
        reloadNews();
    }

    // ---------- Aufbau ----------
    void label(JPanel p, String t, int x, int y, int w, int h, float size, int style, Color c, int align) {
        JLabel l = new JLabel(t, align); l.setFont(font(style, size)); l.setForeground(c); l.setBounds(x, y, w, h); p.add(l);
    }

    void reloadNews() {
        new Thread(() -> {
            String posts = Posts.format(Posts.fetch());
            Installer.News n = Installer.news(cfg);
            String rel = (n.title().isBlank() ? "" : "Poldi Client: " + n.title() + (n.tag().isBlank() ? "" : "  (" + n.tag() + ")") + "\n\n") + n.body();
            String all = posts.isBlank() ? rel : posts + "――――――――――\n\n" + rel;
            SwingUtilities.invokeLater(() -> newsText.setText(all));
        }, "news").start();
    }

    // Owner-Bereich: News schreiben (nur sichtbar fuer xToasty_)
    JPanel ownerBox; Field postTitle; JTextArea postText; JLabel postInfo; JScrollPane newsScroll;
    void buildOwner() {
        ownerBox = new JPanel(null); ownerBox.setOpaque(false); ownerBox.setBounds(0, 0, 696, 520); ownerBox.setVisible(false);
        label(ownerBox, "Owner: News schreiben", 40, 316, 300, 22, 13f, Font.BOLD, TEXT, SwingConstants.LEFT);
        postInfo = new JLabel(""); postInfo.setFont(font(Font.PLAIN, 12f)); postInfo.setForeground(MUTED); postInfo.setBounds(250, 316, 406, 22); postInfo.setHorizontalAlignment(SwingConstants.RIGHT); ownerBox.add(postInfo);
        postTitle = new Field(""); postTitle.setBounds(40, 342, 616, 34); ownerBox.add(postTitle);
        postText = new JTextArea(); postText.setOpaque(false); postText.setForeground(TEXT); postText.setCaretColor(Color.WHITE); postText.setFont(font(Font.PLAIN, 14f)); postText.setLineWrap(true); postText.setWrapStyleWord(true);
        postText.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane sp = new JScrollPane(postText) { @Override protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); hints(g); g.setColor(new Color(255, 255, 255, 40)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); g.setColor(new Color(255, 255, 255, 80)); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20); g.dispose(); super.paintComponent(g0); } };
        sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null); styleScroll(sp); sp.setBounds(40, 384, 486, 96); ownerBox.add(sp);
        Btn send = new Btn("Posten", true, this::postNews); send.setBounds(540, 384, 116, 44); ownerBox.add(send);
        Btn tok = new Btn("Token", false, this::askToken); tok.setBounds(540, 436, 116, 34); ownerBox.add(tok);
        news.add(ownerBox, 0);
    }

    void updateOwner() {
        SwingUtilities.invokeLater(() -> {
            boolean o = cfg.isOwner();
            ownerBox.setVisible(o);
            newsScroll.setBounds(40, 84, 616, o ? 222 : 396);
            if (o) postInfo.setText(cfg.ownerToken.isBlank() ? "Token fehlt (Knopf „Token“)" : "Token gespeichert");
        });
    }

    void askToken() {
        JPasswordField pf = new JPasswordField(cfg.ownerToken, 34);
        int r = JOptionPane.showConfirmDialog(this, new Object[]{"GitHub-Token mit Schreibrecht (Contents: Read and write) für das Launcher-Repo:", pf}, "Owner-Token", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION) { cfg.ownerToken = new String(pf.getPassword()).trim(); cfg.save(); updateOwner(); }
    }

    void postNews() {
        if (!cfg.isOwner()) return;
        String t = postTitle.getText().trim(), x = postText.getText().trim();
        if (t.isEmpty() || x.isEmpty()) { postInfo.setText("Titel und Text eingeben."); return; }
        postInfo.setText("Sende …");
        new Thread(() -> {
            try {
                Posts.publish(cfg.ownerToken, t, x);
                SwingUtilities.invokeLater(() -> { postTitle.setText(""); postText.setText(""); postInfo.setText("Veröffentlicht ✓"); });
                reloadNews();
            } catch (Exception e) { SwingUtilities.invokeLater(() -> postInfo.setText("Fehler: " + e.getMessage())); }
        }, "post-news").start();
    }

    void checkLauncherUpdate() {
        new Thread(() -> {
            try {
                String stamp = new String(Main.class.getResourceAsStream("/build.txt").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                java.time.Instant built = java.time.Instant.parse(stamp);
                var rel = Json.obj(Json.parse(Http.get("https://api.github.com/repos/" + Config.LAUNCHER_REPO + "/releases/tags/" + Config.RELEASE_TAG, java.util.Map.of("Accept", "application/vnd.github+json"))));
                java.time.Instant pub = java.time.Instant.parse(Json.str(rel.get("published_at")));
                if (pub.isAfter(built.plusSeconds(900))) SwingUtilities.invokeLater(() -> updBtn.setVisible(true));
            } catch (Exception ignored) {} // Entwicklungsstand ohne build.txt oder offline: nichts anzeigen
        }, "launcher-update").start();
    }

    /** Neue Launcher-Jar laden, nach dem Beenden austauschen und neu starten (Windows, jpackage-App-Ordner). */
    void selfUpdate() {
        updBtn.setVisible(false); status.setText("Launcher wird aktualisiert ...");
        new Thread(() -> {
            try {
                java.nio.file.Path jar = java.nio.file.Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!jar.toString().endsWith(".jar")) throw new java.io.IOException("keine installierte Version");
                java.nio.file.Path nw = jar.resolveSibling("poldi-launcher.jar.new");
                Http.download("https://github.com/" + Config.LAUNCHER_REPO + "/releases/download/" + Config.RELEASE_TAG + "/poldi-launcher.jar", nw, null);
                if (java.nio.file.Files.size(nw) < 100_000) throw new java.io.IOException("Download unvollstaendig");
                java.nio.file.Path exe = jar.getParent().getParent().resolve("PoldiClient.exe");
                java.nio.file.Path bat = java.nio.file.Files.createTempFile("poldi-update", ".bat");
                String script = "@echo off\r\nping 127.0.0.1 -n 3 >nul\r\n"
                    + ":retry\r\nmove /y \"" + nw + "\" \"" + jar + "\" >nul 2>&1\r\nif exist \"" + nw + "\" (ping 127.0.0.1 -n 2 >nul & goto retry)\r\n"
                    + "start \"\" \"" + exe + "\"\r\n(goto) 2>nul & del \"%~f0\"\r\n";
                java.nio.file.Files.writeString(bat, script);
                new ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", bat.toString()).start();
                System.exit(0);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { status.setText("Auto-Update fehlgeschlagen, Seite wird geoeffnet."); updBtn.setVisible(true); });
                try { Desktop.getDesktop().browse(new URI("https://github.com/" + Config.LAUNCHER_REPO + "/releases/tag/" + Config.RELEASE_TAG)); } catch (Exception ignored) {}
            }
        }, "self-update").start();
    }

    static final String DISCORD = "https://discord.gg/VyqAXBGEn";
    void openDiscord() { try { Desktop.getDesktop().browse(new URI(DISCORD)); } catch (Exception ignored) {} }

    void buildTitleButtons() {
        updBtn = new Btn("Launcher-Update", true, this::selfUpdate);
        updBtn.setBounds(W - 380, 16, 160, 28); updBtn.setVisible(false); add(updBtn);
        Btn dc = new Btn("Discord", false, this::openDiscord); dc.setBounds(W - 204, 16, 84, 28); add(dc);
        Btn min = new Btn("–", false, () -> { if (frame != null) frame.setState(Frame.ICONIFIED); }); min.setBounds(W - 110, 16, 38, 28); add(min);
        Btn close = new Btn("✕", false, () -> { cancel = true; System.exit(0); }); close.setBounds(W - 64, 16, 38, 28); add(close);
    }

    void buildNav() {
        String[] names = {"Spielen", "Mods", "Schematics", "News", "Einstellungen", "Support"};
        for (int i = 0; i < 6; i++) { final int k = i; Btn b = new Btn(names[i], false, () -> show(k)); b.setBounds(42, 120 + i * 54, 180, 42); add(b); }
        accLabel.setFont(font(Font.BOLD, 15f)); accLabel.setForeground(TEXT); accLabel.setBounds(42, 476, 180, 24); add(accLabel);
        accBtn = new Btn("Anmelden", false, this::accountAction); accBtn.setBounds(42, 510, 180, 38); add(accBtn);
    }

    void buildPlay() {
        installed.setFont(font(Font.PLAIN, 14f)); installed.setForeground(MUTED); installed.setBounds(0, 208, 696, 22); play.add(installed);
        greet.setFont(font(Font.BOLD, 34f)); greet.setForeground(TEXT); greet.setHorizontalAlignment(SwingConstants.CENTER); greet.setBounds(0, 160, 696, 44); play.add(greet);
        verBtn = new Btn("Version " + Config.mc + "  ▾", false, this::pickVersion); verBtn.setBounds(248, 244, 200, 30); play.add(verBtn);
        playBtn = new Btn("SPIELEN", true, this::play); playBtn.setBounds(198, 296, 300, 64); play.add(playBtn);
        bar.setBounds(128, 392, 440, 14); play.add(bar);
        status.setFont(font(Font.PLAIN, 14f)); status.setForeground(TEXT); status.setBounds(40, 414, 616, 28); play.add(status);
        Btn log = new Btn("Log öffnen", false, () -> openFile(Config.DIR.resolve("launcher-game.log").toFile())); log.setBounds(40, 458, 130, 32); play.add(log);
        Btn dir = new Btn("Spielordner", false, () -> openFile(Config.GAME.toFile())); dir.setBounds(526, 458, 130, 32); play.add(dir);
        refreshInstalled();
    }

    // ---------- Support ----------
    String supType = "Ticket"; Field supReason; JTextArea supText; JLabel supInfo; Btn[] supTypeBtns; long lastSupport = 0;
    void buildSupport() {
        label(support, "Support", 40, 34, 400, 36, 26f, Font.BOLD, TEXT, SwingConstants.LEFT);
        label(support, "Ticket öffnen, Verbesserung vorschlagen oder etwas wünschen. Geht direkt an den Owner.", 40, 74, 616, 22, 13f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        String[] types = {"Ticket", "Vorschlag", "Wunsch"};
        supTypeBtns = new Btn[3];
        for (int i = 0; i < 3; i++) {
            final String t = types[i]; final int k = i;
            supTypeBtns[i] = new Btn(t, i == 0, () -> { supType = t; for (int j = 0; j < 3; j++) supTypeBtns[j].setPrimary(j == k); });
            supTypeBtns[i].setBounds(40 + i * 130, 110, 120, 36); support.add(supTypeBtns[i]);
        }
        label(support, "Grund / Betreff", 40, 160, 300, 20, 13f, Font.BOLD, TEXT, SwingConstants.LEFT);
        supReason = new Field(""); supReason.setBounds(40, 184, 616, 36); support.add(supReason);
        label(support, "Beschreibung", 40, 230, 300, 20, 13f, Font.BOLD, TEXT, SwingConstants.LEFT);
        supText = new JTextArea(); supText.setOpaque(false); supText.setForeground(TEXT); supText.setCaretColor(Color.WHITE); supText.setFont(font(Font.PLAIN, 14f));
        supText.setLineWrap(true); supText.setWrapStyleWord(true); supText.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane sp = new JScrollPane(supText) { @Override protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); hints(g); g.setColor(new Color(255, 255, 255, 40)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); g.setColor(new Color(255, 255, 255, 80)); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20); g.dispose(); super.paintComponent(g0); } };
        sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null); styleScroll(sp); sp.setBounds(40, 254, 616, 160); support.add(sp);
        supInfo = new JLabel(""); supInfo.setFont(font(Font.PLAIN, 13f)); supInfo.setForeground(MUTED); supInfo.setBounds(40, 430, 420, 36); support.add(supInfo);
        Btn send = new Btn("Absenden", true, this::sendSupport); send.setBounds(496, 424, 160, 44); support.add(send);
    }

    void sendSupport() {
        String reason = supReason.getText().trim(), text = supText.getText().trim();
        if (reason.length() < 3 || text.length() < 5) { supInfo.setText("Bitte Grund und Beschreibung ausfüllen."); return; }
        if (System.currentTimeMillis() - lastSupport < 60_000) { supInfo.setText("Bitte kurz warten (1 Nachricht pro Minute)."); return; }
        lastSupport = System.currentTimeMillis(); supInfo.setText("Sende …");
        final String type = supType;
        new Thread(() -> {
            try {
                Support.send(cfg, type, reason, text);
                SwingUtilities.invokeLater(() -> { supInfo.setText(type + " gesendet. Danke!"); supReason.setText(""); supText.setText(""); });
            } catch (Exception e) {
                lastSupport = 0;
                SwingUtilities.invokeLater(() -> supInfo.setText("Senden fehlgeschlagen: " + e.getMessage()));
            }
        }, "support").start();
    }

    void buildNews() {
        label(news, "Was ist neu?", 40, 34, 400, 36, 26f, Font.BOLD, TEXT, SwingConstants.LEFT);
        newsText = new JTextArea("Lade …"); newsText.setOpaque(false); newsText.setForeground(TEXT); newsText.setFont(font(Font.PLAIN, 14f));
        newsText.setLineWrap(true); newsText.setWrapStyleWord(true); newsText.setEditable(false); newsText.setCaretColor(new Color(0, 0, 0, 0));
        JScrollPane sp = new JScrollPane(newsText); newsScroll = sp; sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null);
        styleScroll(sp);
        sp.setBounds(40, 84, 616, 396); news.add(sp);
    }

    static void styleScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setOpaque(false); sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {}
            @Override protected JButton createDecreaseButton(int o) { return zero(); } @Override protected JButton createIncreaseButton(int o) { return zero(); }
            JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) { Graphics2D g2 = (Graphics2D) g.create(); hints(g2); g2.setColor(new Color(255, 255, 255, 110)); g2.fillRoundRect(r.x + 2, r.y, 6, r.height, 6, 6); g2.dispose(); }
        });
    }

    Field nameField, idField; Toggle tClose, tOff; JLabel ramLabel;
    void buildSettings() {
        label(settings, "Einstellungen", 40, 34, 400, 36, 26f, Font.BOLD, TEXT, SwingConstants.LEFT);
        ramLabel = new JLabel(); ramLabel.setFont(font(Font.PLAIN, 14f)); ramLabel.setForeground(TEXT); ramLabel.setBounds(40, 92, 300, 34); settings.add(ramLabel);
        Btn minus = new Btn("−", false, () -> ram(-512)); minus.setBounds(500, 92, 50, 34); settings.add(minus);
        Btn plus = new Btn("+", false, () -> ram(512)); plus.setBounds(560, 92, 50, 34); settings.add(plus);
        tClose = new Toggle("Launcher nach dem Start schließen", cfg.closeOnLaunch, v -> { cfg.closeOnLaunch = v; cfg.save(); }); tClose.setBounds(40, 142, 570, 34); settings.add(tClose);
        tOff = new Toggle("Offline-Modus (nur Singleplayer, kein Server)", cfg.offline, v -> { cfg.offline = v; cfg.save(); refreshAccount(); }); tOff.setBounds(40, 186, 570, 34); settings.add(tOff);
        label(settings, "Offline-Name", 40, 236, 200, 22, 13f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        nameField = new Field(cfg.accName); nameField.setBounds(40, 260, 300, 36); settings.add(nameField);
        nameField.getDocument().addDocumentListener(simple(() -> { if (cfg.offline || cfg.accRefresh.isBlank()) { cfg.accName = nameField.getText().trim(); cfg.save(); refreshAccount(); } }));
        label(settings, "Azure Client-ID (für Microsoft-Login)", 40, 312, 400, 22, 13f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        idField = new Field(cfg.clientId); idField.setBounds(40, 336, 570, 36); settings.add(idField);
        idField.getDocument().addDocumentListener(simple(() -> { cfg.clientId = idField.getText().trim(); cfg.save(); }));
        label(settings, "Eigene App unter portal.azure.com registrieren (Öffentlicher Client, persönliche Konten).", 40, 378, 616, 20, 12f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        label(settings, "Mojang muss neue Apps für den Minecraft-Login freigeben.", 40, 396, 616, 20, 12f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        Btn out = new Btn("Abmelden", false, () -> { cfg.accRefresh = ""; cfg.accUuid = ""; if (!cfg.offline) cfg.accName = ""; cfg.save(); refreshAccount(); }); out.setBounds(40, 440, 160, 38); settings.add(out);
        ram(0);
    }

    static javax.swing.event.DocumentListener simple(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); } };
    }

    void ram(int d) { cfg.ramMb = Math.max(1024, Math.min(16384, cfg.ramMb + d)); cfg.save(); ramLabel.setText(String.format("Arbeitsspeicher: %.1f GB", cfg.ramMb / 1024.0)); }

    static volatile boolean dragging = false;

    void show(int t) { tab = t; play.setVisible(t == 0); mods.setVisible(t == 1); schem.setVisible(t == 2); news.setVisible(t == 3); settings.setVisible(t == 4); support.setVisible(t == 5); if (t == 1) modsRefresh(); repaint(); }

    void refreshInstalled() { installed.setText("Minecraft " + Config.mc + "  ·  Fabric  ·  " + Installer.installedClient()); }

    void startGreeting() {
        greetFull = "Hi, " + (cfg.accName.isBlank() ? "Player" : cfg.accName); greetN = 0;
        new Timer(70, e -> { greetN++; greet.setText(greetFull.substring(0, Math.min(greetN, greetFull.length()))); if (greetN >= greetFull.length()) ((Timer) e.getSource()).stop(); }).start();
    }

    void refreshAccount() {
        if (ownerBox != null) updateOwner();
        SwingUtilities.invokeLater(() -> {
            if (greetN >= greetFull.length()) { greetFull = "Hi, " + (cfg.accName.isBlank() ? "Player" : cfg.accName); greet.setText(greetFull); }
            if (cfg.offline) { accLabel.setText("Offline: " + (cfg.accName.isBlank() ? "Player" : cfg.accName)); accBtn.setText("Account wechseln"); }
            else if (!cfg.accRefresh.isBlank()) { accLabel.setText(cfg.accName); accBtn.setText("Account wechseln"); }
            else { accLabel.setText("Nicht angemeldet"); accBtn.setText("Account wechseln"); }
        });
    }

    /** Account-Wechsler: gespeicherte Accounts, neues Microsoft-Konto, Offline-Account. */
    void accountAction() {
        if (busy) return;
        cfg.stash(); cfg.save();
        JPopupMenu pm = new JPopupMenu(); pm.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 90)));
        pm.setBackground(new Color(0x3B, 0x0A, 0x0A));
        for (java.util.Map<String, Object> a : new java.util.ArrayList<>(cfg.accounts)) {
            boolean cur = Json.str(a.get("name")).equalsIgnoreCase(cfg.accName) && Boolean.TRUE.equals(a.get("offline")) == cfg.offline;
            String n = Json.str(a.get("name")) + (Boolean.TRUE.equals(a.get("offline")) ? "  (Offline)" : "") + (cur ? "  ✓" : "");
            JMenuItem it = menuItem(n, () -> { if (!cur) { cfg.use(a); refreshAccount(); startGreeting(); setStatus("Account gewechselt: " + cfg.accName, 0); } });
            pm.add(it);
            JMenuItem rm = menuItem("   ✕ entfernen", () -> { cfg.accounts.remove(a); if (cur) { cfg.accName = ""; cfg.accUuid = ""; cfg.accRefresh = ""; cfg.offline = false; } cfg.save(); refreshAccount(); });
            rm.setFont(font(Font.PLAIN, 11f)); rm.setForeground(new Color(0xFFC4C4)); pm.add(rm);
        }
        pm.addSeparator();
        pm.add(menuItem("+ Microsoft-Konto hinzufügen", () -> { show(0); login(); }));
        pm.add(menuItem("+ Offline-Account hinzufügen", () -> {
            String n = JOptionPane.showInputDialog(this, "Name für den Offline-Account (3–16 Zeichen):", "Offline-Account", JOptionPane.PLAIN_MESSAGE);
            if (n == null) return; n = n.trim();
            if (!n.matches("[A-Za-z0-9_]{3,16}")) { setStatus("Ungültiger Name (3–16 Zeichen, Buchstaben/Zahlen/_).", 0); return; }
            cfg.stash(); cfg.accName = n; cfg.accUuid = Auth.offlineUuid(n); cfg.accRefresh = ""; cfg.offline = true; cfg.stash(); cfg.save();
            refreshAccount(); startGreeting();
        }));
        pm.show(accBtn, 0, -pm.getPreferredSize().height);
    }

    JMenuItem menuItem(String t, Runnable r) {
        JMenuItem it = new JMenuItem(t); it.setFont(font(Font.PLAIN, 14f)); it.setForeground(Color.WHITE); it.setBackground(new Color(0x3B, 0x0A, 0x0A));
        it.setOpaque(true); it.addActionListener(e -> r.run()); return it;
    }

    void pickVersion() {
        if (busy) return;
        verBtn.setText("Lade Versionen …");
        new Thread(() -> {
            List<String> vs = Installer.versions();
            SwingUtilities.invokeLater(() -> {
                verBtn.setText("Version " + Config.mc + "  ▾");
                JPopupMenu pm = new JPopupMenu(); pm.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 90)));
                JList<String> l = new JList<>(vs.toArray(new String[0])); l.setFont(font(Font.PLAIN, 14f));
                l.setBackground(new Color(0x3B, 0x0A, 0x0A)); l.setForeground(Color.WHITE); l.setSelectionBackground(PURPLE); l.setVisibleRowCount(9);
                l.setSelectedValue(Config.mc, true);
                JScrollPane sp = new JScrollPane(l); sp.setBorder(null); sp.setPreferredSize(new Dimension(200, 220)); pm.add(sp);
                l.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) {
                    String v = l.getSelectedValue(); if (v == null) return; pm.setVisible(false);
                    Config.mc = v; cfg.save(); verBtn.setText("Version " + v + "  ▾"); refreshInstalled(); setStatus("Version " + v + " gewählt – beim Start wird Fabric automatisch installiert.", 0);
                }});
                pm.show(verBtn, 0, verBtn.getHeight());
            });
        }, "versions").start();
    }

    // ---------- Mods (Modrinth) ----------
    Field modSearch; JPanel modList; JLabel modInfo; boolean modsInstalledView = false;
    void buildMods() {
        label(mods, "Mods", 40, 30, 300, 36, 26f, Font.BOLD, TEXT, SwingConstants.LEFT);
        modInfo = new JLabel(""); modInfo.setFont(font(Font.PLAIN, 12f)); modInfo.setForeground(MUTED); modInfo.setBounds(130, 38, 400, 22); mods.add(modInfo);
        Btn inst = new Btn("Installiert", false, () -> { modsInstalledView = !modsInstalledView; modsRefresh(); }); inst.setBounds(526, 34, 130, 32); mods.add(inst);
        modSearch = new Field(""); modSearch.setBounds(40, 80, 460, 36); mods.add(modSearch);
        modSearch.addActionListener(e -> { modsInstalledView = false; modsRefresh(); });
        Btn go = new Btn("Suchen", true, () -> { modsInstalledView = false; modsRefresh(); }); go.setBounds(516, 80, 140, 36); mods.add(go);
        modList = new JPanel(); modList.setOpaque(false); modList.setLayout(new BoxLayout(modList, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.add(modList, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap); sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null); styleScroll(sp);
        sp.getVerticalScrollBar().setUnitIncrement(18); sp.setBounds(40, 130, 616, 370); mods.add(sp);
    }

    JPanel modRow(String title, String sub, String btn, Runnable act) {
        JPanel r = new JPanel(null) {
            @Override protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); hints(g); g.setColor(new Color(255, 255, 255, 34)); g.fillRoundRect(0, 0, getWidth() - 8, getHeight() - 6, 22, 22); g.setColor(new Color(255, 255, 255, 60)); g.drawRoundRect(0, 0, getWidth() - 9, getHeight() - 7, 22, 22); g.dispose(); }
        };
        r.setOpaque(false); r.setPreferredSize(new Dimension(600, 64)); r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        JLabel t = new JLabel(title); t.setFont(font(Font.BOLD, 14f)); t.setForeground(TEXT); t.setBounds(16, 8, 400, 22); r.add(t);
        JLabel s = new JLabel(sub); s.setFont(font(Font.PLAIN, 12f)); s.setForeground(MUTED); s.setBounds(16, 31, 410, 20); r.add(s);
        Btn b = new Btn(btn, false, act); b.setBounds(448, 14, 130, 32); r.add(b);
        return r;
    }

    void modsRefresh() {
        modInfo.setText("Minecraft " + Config.mc + " · Fabric");
        modList.removeAll();
        if (modsInstalledView) {
            for (String f : Modrinth.installed()) {
                modList.add(modRow(f, "Installiert", "Entfernen", () -> { try { Modrinth.remove(f); } catch (Exception e) { setStatus("Fehler: " + e.getMessage(), -1); } modsRefresh(); }));
            }
            if (modList.getComponentCount() == 0) modList.add(hint("Noch keine Mods installiert."));
            modList.revalidate(); modList.repaint(); return;
        }
        modList.add(hint("Suche …")); modList.revalidate(); modList.repaint();
        final String q = modSearch.getText().trim();
        new Thread(() -> {
            try {
                List<Modrinth.Hit> hits = Modrinth.search(q);
                SwingUtilities.invokeLater(() -> {
                    modList.removeAll();
                    for (Modrinth.Hit h : hits) {
                        String d = h.desc().length() > 62 ? h.desc().substring(0, 60) + "…" : h.desc();
                        modList.add(modRow(h.title() + "  ·  " + h.author(), d, "Installieren", () -> installMod(h)));
                    }
                    if (hits.isEmpty()) modList.add(hint("Nichts gefunden."));
                    modList.revalidate(); modList.repaint();
                });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> { modList.removeAll(); modList.add(hint("Modrinth nicht erreichbar: " + e.getMessage())); modList.revalidate(); modList.repaint(); }); }
        }, "modrinth").start();
    }

    JLabel hint(String t) { JLabel l = new JLabel(t); l.setFont(font(Font.PLAIN, 14f)); l.setForeground(MUTED); l.setBorder(BorderFactory.createEmptyBorder(10, 6, 0, 0)); return l; }

    void installMod(Modrinth.Hit h) {
        modInfo.setText(h.title() + " wird geladen …");
        new Thread(() -> {
            try { String f = Modrinth.install(h.id(), new java.util.HashSet<>()); SwingUtilities.invokeLater(() -> modInfo.setText("✓ " + f)); }
            catch (Exception e) { SwingUtilities.invokeLater(() -> modInfo.setText("Fehler: " + e.getMessage())); }
        }, "modinstall").start();
    }

    // ---------- Schematics (3D) ----------
    SchemView sview; JSlider sLo, sHi; JLabel sInfo;
    void buildSchem() {
        label(schem, "Schematics", 40, 30, 260, 36, 26f, Font.BOLD, TEXT, SwingConstants.LEFT);
        sInfo = new JLabel("Keine Datei geladen"); sInfo.setFont(font(Font.PLAIN, 12f)); sInfo.setForeground(MUTED); sInfo.setBounds(200, 38, 230, 22); schem.add(sInfo);
        Btn open = new Btn("Datei öffnen", true, this::openSchem); open.setBounds(416, 32, 140, 34); schem.add(open);
        Btn rst = new Btn("Reset", false, () -> sview.reset()); rst.setBounds(566, 32, 90, 34); schem.add(rst);
        sview = new SchemView(); sview.setBounds(40, 80, 616, 360); schem.add(sview);
        sview.onLoaded = () -> {
            Schem s = sview.model();
            if (s == null) { sInfo.setText("Fehler beim Laden"); return; }
            sInfo.setText(s.sx + "×" + s.sy + "×" + s.sz + " · " + s.blockCount + " Blöcke");
            sLo.setMaximum(Math.max(1, s.sy - 1)); sLo.setValue(0); sHi.setMinimum(1); sHi.setMaximum(Math.max(1, s.sy)); sHi.setValue(s.sy);
        };
        label(schem, "Von Ebene", 40, 452, 90, 22, 12f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        label(schem, "Bis Ebene", 40, 482, 90, 22, 12f, Font.PLAIN, MUTED, SwingConstants.LEFT);
        sLo = new JSlider(0, 1, 0); sHi = new JSlider(1, 1, 1);
        for (JSlider sl : new JSlider[]{sLo, sHi}) { sl.setOpaque(false); sl.setFocusable(false); }
        sLo.setBounds(130, 450, 526, 26); sHi.setBounds(130, 480, 526, 26); schem.add(sLo); schem.add(sHi);
        sLo.addChangeListener(e -> { if (sLo.getValue() >= sHi.getValue()) sHi.setValue(sLo.getValue() + 1); sview.setRange(sLo.getValue(), sHi.getValue()); });
        sHi.addChangeListener(e -> { if (sHi.getValue() <= sLo.getValue()) sLo.setValue(sHi.getValue() - 1); sview.setRange(sLo.getValue(), sHi.getValue()); });
    }

    void openSchem() {
        java.nio.file.Path start = Config.DIR.resolve("schematics");
        String ad = System.getenv("APPDATA");
        if (ad != null && java.nio.file.Files.isDirectory(java.nio.file.Paths.get(ad, ".minecraft", "schematics"))) start = java.nio.file.Paths.get(ad, ".minecraft", "schematics");
        JFileChooser fc = new JFileChooser(start.toFile());
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Litematica (*.litematic)", "litematic"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { sInfo.setText("Lade …"); sview.loadAsync(fc.getSelectedFile()); }
    }

    // ---------- Aktionen ----------
    void setStatus(String t, double p) { SwingUtilities.invokeLater(() -> { status.setText(t); if (p >= 0) { bar.v = p; bar.repaint(); } }); }

    void login() {
        if (busy) return; busy = true; cancel = false; playBtn.setEnabled(false);
        new Thread(() -> {
            try {
                Auth.Session s = Auth.login(cfg.clientId, (uri, code) -> {
                    setStatus("Code: " + code + "   (kopiert – im Browser eingeben)", -1);
                    try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(code), null); } catch (Exception ignored) {}
                    try { Desktop.getDesktop().browse(new URI(uri)); } catch (Exception ignored) {}
                }, t -> setStatus(t, -1), () -> cancel);
                cfg.accName = s.name(); cfg.accUuid = s.uuid(); cfg.accRefresh = s.refreshToken(); cfg.offline = false; cfg.stash(); cfg.save();
                refreshAccount(); setStatus("Angemeldet als " + s.name(), 0);
            } catch (Exception e) { setStatus("Login fehlgeschlagen: " + e.getMessage(), 0); }
            finally { busy = false; SwingUtilities.invokeLater(() -> playBtn.setEnabled(true)); }
        }, "login").start();
    }

    void play() {
        if (game != null && game.isAlive()) { setStatus("Minecraft läuft schon.", -1); return; }
        if (busy) return;
        if (!cfg.offline && cfg.accRefresh.isBlank()) { setStatus("Erst anmelden (links unten) oder Offline-Modus einschalten.", 0); return; }
        busy = true; playBtn.setEnabled(false); playBtn.setText("LÄDT …");
        new Thread(() -> {
            try {
                String name, uuid, token;
                if (cfg.offline) { name = cfg.accName.isBlank() ? "Player" : cfg.accName; uuid = Auth.offlineUuid(name); token = ""; }
                else {
                    setStatus("Anmeldung erneuern …", 0.01);
                    Auth.Session s = Auth.refresh(cfg.clientId, cfg.accRefresh, t -> setStatus(t, -1));
                    cfg.accRefresh = s.refreshToken(); cfg.accName = s.name(); cfg.accUuid = s.uuid(); cfg.stash(); cfg.save(); refreshAccount();
                    name = s.name(); uuid = s.uuid(); token = s.accessToken();
                }
                Installer.Spec spec = Installer.prepare(cfg, (t, p) -> setStatus(t, p));
                if (Runtime.version().feature() < spec.javaMajor()) throw new IOException("Java " + spec.javaMajor() + " nötig, im Launcher steckt Java " + Runtime.version().feature() + ".");
                SwingUtilities.invokeLater(this::refreshInstalled);
                setStatus("Minecraft startet …", 1.0);
                game = Launch.start(spec, cfg, name, uuid, token);
                if (cfg.closeOnLaunch) { Thread.sleep(4000); System.exit(0); }
                SwingUtilities.invokeLater(() -> { playBtn.setText("LÄUFT"); if (frame != null) frame.setState(Frame.ICONIFIED); });
                int code = game.waitFor();
                setStatus(code == 0 ? "Minecraft beendet." : "Minecraft beendet (Code " + code + ") – siehe „Log öffnen“.", 0);
            } catch (Exception e) { setStatus("Fehler: " + e.getMessage(), 0); }
            finally { busy = false; SwingUtilities.invokeLater(() -> { playBtn.setEnabled(true); playBtn.setText("SPIELEN"); }); }
        }, "play").start();
    }

    static void openFile(File f) { try { if (f.isDirectory() || f.exists()) Desktop.getDesktop().open(f); } catch (Exception ignored) {} }

    // ---------- Zeichnen ----------
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create(); hints(g);
        Shape win = new RoundRectangle2D.Float(0, 0, W, H, 34, 34);
        g.setClip(win);
        g.setPaint(new GradientPaint(0, 0, new Color(0x3B, 0x0A, 0x0A), 0, H, new Color(0xC2, 0x2B, 0x2B)));
        g.fill(win);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(W * 0.25f, H * 0.2f), 520, new float[]{0, 1}, new Color[]{new Color(248, 113, 113, 120), new Color(248, 113, 113, 0)}));
        g.fillRect(0, 0, W, H);
        for (float[] c : clouds) blob(g, c[0], c[1], c[2], (int) c[4]);
        // Titel
        if (logo != null) g.drawImage(logo, 22, 10, 38, 38, null);
        g.setFont(font(Font.BOLD, 20f)); String t = "Poldi Client"; int x = 70;
        for (int i = 0; i < t.length(); i++) { g.setColor(mix(PURPLE, BLUE, i / (float) (t.length() - 1))); g.drawString(String.valueOf(t.charAt(i)), x, 36); x += g.getFontMetrics().charWidth(t.charAt(i)); }
        // Karten
        card(g, 22, 96, 220, 502, 30, true, 40);
        card(g, 262, 96, 696, 502, 34, true, 36);
        g.setColor(new Color(255, 255, 255, 60)); g.drawLine(42, 462, 222, 462);
        if (tab == 0 && logo != null) { g.drawImage(logo, 698 / 2 + 262 - 60 - 2, 96 + 24, 120, 120, null); }
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 80)); g.setStroke(new BasicStroke(1.5f)); g.draw(win);
        g.dispose();
    }

    static void blob(Graphics2D g, float x, float y, float s, int al) {
        float[][] p = {{0, 0, 120, 70}, {70, -26, 130, 90}, {150, 0, 110, 66}, {50, 18, 170, 60}};
        for (float[] q : p) {
            float cx = x + q[0] * s + q[2] * s / 2, cy = y + q[1] * s + q[3] * s / 2, r = Math.max(q[2], q[3]) * s * 0.8f;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[]{0, 1}, new Color[]{new Color(255, 255, 255, al), new Color(255, 255, 255, 0)}));
            g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        }
    }

    // ---------- Start ----------
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--snapshot")) { snapshot(args[1]); return; }
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Poldi Client");
            f.setUndecorated(true); f.setBackground(new Color(0, 0, 0, 0));
            Main m = new Main(); m.frame = f;
            f.setContentPane(m); f.pack(); f.setLocationRelativeTo(null);
            try { f.setIconImage(m.logo); } catch (Exception ignored) {}
            MouseAdapter drag = new MouseAdapter() {
                Point o;
                public void mousePressed(MouseEvent e) { o = e.getPoint(); Main.dragging = e.getY() < 70; }
                public void mouseReleased(MouseEvent e) { Main.dragging = false; }
                public void mouseDragged(MouseEvent e) { if (o != null && o.y < 70) { Point p = f.getLocation(); f.setLocation(p.x + e.getX() - o.x, p.y + e.getY() - o.y); } }
            };
            m.addMouseListener(drag); m.addMouseMotionListener(drag);
            f.setVisible(true);
        });
    }

    static void snapshot(String dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(new File(dir).toPath());
        Main m = new Main(); m.setSize(W, H); m.doLayout();
        m.newsText.setText("Poldi Client\n\n- Rotes Design\n- Hauptversion 26.2");
        m.status.setText("Bibliotheken 214/320"); m.greet.setText("Hi, 6po");
        m.modInfo.setText("Minecraft 26.2 · Fabric"); m.modList.add(m.modRow("Sodium  ·  jellysquid3", "Moderne Rendering-Engine für bessere FPS", "Installieren", () -> {})); m.modList.add(m.modRow("Lithium  ·  jellysquid3", "Server- und Client-Optimierungen", "Installieren", () -> {})); m.modList.add(m.modRow("Iris Shaders  ·  coderbot", "Shader-Unterstützung", "Installieren", () -> {})); m.bar.v = 0.42; m.accLabel.setText("6po"); m.accBtn.setText("Abmelden");
        for (int t = 0; t < 5; t++) {
            m.show(t);
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics(); m.printAll(g); g.dispose();
            ImageIO.write(img, "png", new File(dir, "tab" + t + ".png"));
        }
        System.exit(0);
    }
}
