package dev.poldi.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Einstellungen + gespeicherter Account in %APPDATA%\PoldiLauncher\config.json */
public final class Config {
    public static final String CLIENT_MC = "26.2"; // Hauptversion: Poldi Client Mod ist dafuer gebaut
    public static String mc = CLIENT_MC;           // gewählte Minecraft-Version
    public static final String LAUNCHER_REPO = "ventlukas-web/toasty-launcher";
    public static final String CLIENT_REPO = LAUNCHER_REPO; // Poldi-Mod liegt im Release poldi-launcher-latest
    public static final String RELEASE_TAG = "poldi-launcher-latest";

    public static final Path DIR = baseDir();
    public static final Path FILE = DIR.resolve("config.json");
    public static final Path GAME = DIR.resolve("game");
    public static Path instance() { return GAME.resolve("instances").resolve(mc); }

    public int ramMb = 4096;
    public boolean closeOnLaunch = false;
    public String clientId = "";
    public String repo = CLIENT_REPO;
    public String ownerToken = "";  // GitHub-Token nur fuer den Owner (News schreiben)
    public static final String OWNER = "xToasty_";
    // Account
    public String accName = "", accUuid = "", accRefresh = "";
    public boolean offline = false;
    /** Gespeicherte Accounts zum Wechseln: je {name, uuid, refresh, offline}. */
    public java.util.List<Map<String, Object>> accounts = new java.util.ArrayList<>();

    /** Aktuellen Account in die Liste uebernehmen (neu oder aktualisiert). */
    public void stash() {
        if (accName == null || accName.isBlank()) return;
        accounts.removeIf(a -> Json.str(a.get("name")).equalsIgnoreCase(accName) && Boolean.TRUE.equals(a.get("offline")) == offline);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", accName); m.put("uuid", accUuid); m.put("refresh", accRefresh); m.put("offline", offline);
        accounts.add(m);
    }
    /** Gespeicherten Account zum aktuellen machen. */
    public void use(Map<String, Object> a) {
        stash();
        accName = Json.str(a.get("name")); accUuid = Json.str(a.get("uuid")); accRefresh = Json.str(a.get("refresh")); offline = Boolean.TRUE.equals(a.get("offline"));
        save();
    }

    public boolean isOwner() { return accName != null && accName.equalsIgnoreCase(OWNER); }

    private static Path baseDir() {
        String ad = System.getenv("APPDATA");
        Path p = ad != null && !ad.isBlank() ? Paths.get(ad, "PoldiLauncher") : Paths.get(System.getProperty("user.home"), ".poldi-launcher");
        return p;
    }

    public static Config load() {
        Config c = new Config();
        try {
            if (Files.isRegularFile(FILE)) {
                Map<String, Object> m = Json.obj(Json.parse(Files.readString(FILE, StandardCharsets.UTF_8)));
                if (m.get("ramMb") instanceof Double d) c.ramMb = Math.max(1024, Math.min(32768, d.intValue()));
                if (m.get("closeOnLaunch") instanceof Boolean b) c.closeOnLaunch = b;
                if (m.get("offline") instanceof Boolean b) c.offline = b;
                String v = Json.str(m.get("mc")); if (!v.isBlank()) mc = v;
                c.clientId = Json.str(m.get("clientId")); c.accName = Json.str(m.get("accName")); c.accUuid = Json.str(m.get("accUuid")); c.accRefresh = Json.str(m.get("accRefresh")); c.ownerToken = Json.str(m.get("ownerToken"));
                for (Object o : Json.arr(m.get("accounts"))) c.accounts.add(Json.obj(o));
                
            }
        } catch (Exception ignored) {}
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ramMb", (double) ramMb); m.put("mc", mc); m.put("closeOnLaunch", closeOnLaunch); m.put("offline", offline); m.put("clientId", clientId);
            m.put("repo", repo); m.put("accName", accName); m.put("accUuid", accUuid); m.put("accRefresh", accRefresh); m.put("ownerToken", ownerToken);
            m.put("accounts", new java.util.ArrayList<Object>(accounts));
            Files.writeString(FILE, Json.write(m), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
