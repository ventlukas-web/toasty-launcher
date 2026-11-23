package dev.poldi.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Laedt Minecraft (Hauptversion 26.2) + Fabric + Poldi Client Mod + Fabric API und baut den Startbefehl. */
public final class Installer {
    private Installer() {}

    public static final Path LIB = Config.GAME.resolve("libraries");
    public static final Path ASSETS = Config.GAME.resolve("assets");
    public static Path mods() { return Config.instance().resolve("mods"); }
    public static final Path VERS = Config.GAME.resolve("versions");

    public record Spec(List<Path> classpath, String mainClass, List<Object> jvm, List<Object> game, String assetIndex, String versionId, int javaMajor) {}
    public record News(String tag, String title, String body) {}

    private static final String OS = System.getProperty("os.name", "").toLowerCase().contains("win") ? "windows"
        : System.getProperty("os.name", "").toLowerCase().contains("mac") ? "osx" : "linux";

    // ---------- Regeln ----------
    /** Mojang-Regeln: ohne Regeln erlaubt; sonst letzte passende Regel gewinnt. Features gelten als nicht vorhanden. */
    public static boolean allowed(Object rules) {
        List<Object> l = Json.arr(rules);
        if (l.isEmpty()) return true;
        boolean ok = false;
        for (Object o : l) {
            Map<String, Object> r = Json.obj(o);
            boolean match = true;
            if (r.containsKey("os")) {
                Map<String, Object> os = Json.obj(r.get("os"));
                if (os.containsKey("name") && !Json.str(os.get("name")).equals(OS)) match = false;
                if (os.containsKey("arch") && !Json.str(os.get("arch")).equals(System.getProperty("os.arch", "").contains("64") ? "x86_64" : "x86")
                    && !(Json.str(os.get("arch")).equals("x86") == false && System.getProperty("os.arch", "").contains("64"))) match = false;
            }
            if (r.containsKey("features")) match = false;
            if (match) ok = Json.str(r.get("action")).equals("allow");
        }
        return ok;
    }

    private static String mavenPath(String name) {
        String[] p = name.split(":"); String ext = "jar";
        String last = p[p.length - 1]; if (last.contains("@")) { ext = last.substring(last.indexOf('@') + 1); p[p.length - 1] = last.substring(0, last.indexOf('@')); }
        String g = p[0].replace('.', '/'), a = p[1], v = p[2], c = p.length > 3 ? "-" + p[3] : "";
        return g + "/" + a + "/" + v + "/" + a + "-" + v + c + "." + ext;
    }

    private static String libKey(String name) {
        String[] p = name.split(":"); return p[0] + ":" + p[1] + (p.length > 3 ? ":" + p[3] : "");
    }

    // ---------- Version + Fabric ----------
    private static Map<String, Object> cachedJson(String url, Path cache) throws IOException {
        try {
            String t = Http.get(url);
            Files.createDirectories(cache.getParent()); Files.writeString(cache, t, StandardCharsets.UTF_8);
            return Json.obj(Json.parse(t));
        } catch (IOException e) {
            if (Files.isRegularFile(cache)) return Json.obj(Json.parse(Files.readString(cache, StandardCharsets.UTF_8)));
            throw e;
        }
    }

    /** Alles laden/pruefen und den Startbefehl bauen. progress(text, 0..1) */
    public static Spec prepare(Config cfg, BiConsumer<String, Double> progress) throws IOException {
        progress.accept("Versionen abfragen …", 0.02);
        Map<String, Object> manifest = cachedJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", VERS.resolve("manifest.json"));
        String vurl = null;
        for (Object o : Json.arr(manifest.get("versions"))) { Map<String, Object> v = Json.obj(o); if (Json.str(v.get("id")).equals(Config.mc)) vurl = Json.str(v.get("url")); }
        if (vurl == null) throw new IOException("Minecraft " + Config.mc + " nicht gefunden.");
        Map<String, Object> vanilla = cachedJson(vurl, VERS.resolve(Config.mc).resolve(Config.mc + ".json"));

        List<Object> loaders = Json.arr(Json.parse(cachedText("https://meta.fabricmc.net/v2/versions/loader/" + Config.mc, VERS.resolve("fabric-loaders-" + Config.mc + ".json"))));
        String loader = null;
        for (Object o : loaders) { Map<String, Object> e = Json.obj(Json.obj(o).get("loader")); if (Boolean.TRUE.equals(e.get("stable"))) { loader = Json.str(e.get("version")); break; } }
        if (loader == null && !loaders.isEmpty()) loader = Json.str(Json.obj(Json.obj(loaders.get(0)).get("loader")).get("version"));
        if (loader == null) throw new IOException("Kein Fabric-Loader für " + Config.mc + " gefunden.");
        String fid = "fabric-loader-" + loader + "-" + Config.mc;
        Map<String, Object> fabric = cachedJson("https://meta.fabricmc.net/v2/versions/loader/" + Config.mc + "/" + loader + "/profile/json", VERS.resolve(fid).resolve(fid + ".json"));

        int javaMajor = (int) (Json.obj(vanilla.get("javaVersion")).get("majorVersion") instanceof Double d ? d : 21);

        // Bibliotheken (Fabric zuerst, Duplikate: Fabric gewinnt)
        List<Object[]> dl = new ArrayList<>(); // {url, path, sha1}
        List<Path> cp = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Map<String, Object> prof : List.of(fabric, vanilla)) {
            for (Object o : Json.arr(prof.get("libraries"))) {
                Map<String, Object> lib = Json.obj(o);
                if (!allowed(lib.get("rules"))) continue;
                String name = Json.str(lib.get("name"));
                Map<String, Object> art = Json.obj(Json.obj(lib.get("downloads")).get("artifact"));
                String path, url, sha;
                if (!art.isEmpty()) { path = Json.str(art.get("path")); url = Json.str(art.get("url")); sha = Json.str(art.get("sha1")); }
                else { path = mavenPath(name); String base = Json.str(lib.get("url")); if (base.isEmpty()) base = "https://libraries.minecraft.net/"; if (!base.endsWith("/")) base += "/"; url = base + path; sha = Json.str(lib.get("sha1")); }
                if (!seen.add(libKey(name) + (art.isEmpty() ? "" : ""))) continue;
                dl.add(new Object[]{url, path, sha}); cp.add(LIB.resolve(path));
            }
        }
        Map<String, Object> client = Json.obj(Json.obj(vanilla.get("downloads")).get("client"));
        Path clientJar = VERS.resolve(Config.mc).resolve(Config.mc + ".jar");
        dl.add(new Object[]{Json.str(client.get("url")), null, Json.str(client.get("sha1")), clientJar});
        cp.add(clientJar);

        int n = dl.size(); AtomicInteger done = new AtomicInteger();
        runParallel(dl, 8, x -> {
            Path dest = x.length > 3 ? (Path) x[3] : LIB.resolve((String) x[1]);
            Http.download((String) x[0], dest, (String) x[2]);
            progress.accept("Bibliotheken " + done.incrementAndGet() + "/" + n, 0.05 + 0.25 * done.get() / n);
        });

        // Assets
        Map<String, Object> ai = Json.obj(vanilla.get("assetIndex"));
        String aid = Json.str(ai.get("id"));
        Path idxFile = ASSETS.resolve("indexes").resolve(aid + ".json");
        Http.download(Json.str(ai.get("url")), idxFile, Json.str(ai.get("sha1")));
        Map<String, Object> objs = Json.obj(Json.obj(Json.parse(Files.readString(idxFile, StandardCharsets.UTF_8))).get("objects"));
        List<Object[]> al = new ArrayList<>();
        for (var e : objs.entrySet()) { String h = Json.str(Json.obj(e.getValue()).get("hash")); al.add(new Object[]{"https://resources.download.minecraft.net/" + h.substring(0, 2) + "/" + h, h.substring(0, 2) + "/" + h, h}); }
        int an = al.size(); AtomicInteger ad = new AtomicInteger();
        runParallel(al, 16, x -> {
            Path dest = ASSETS.resolve("objects").resolve((String) x[1]);
            if (!Files.isRegularFile(dest)) Http.download((String) x[0], dest, null);
            int k = ad.incrementAndGet(); if (k % 25 == 0 || k == an) progress.accept("Assets " + k + "/" + an, 0.30 + 0.45 * k / an);
        });

        // Mods
        progress.accept((Config.mc.equals(Config.CLIENT_MC) ? "Poldi Client & " : "") + "Fabric API …", 0.78);
        installMods(cfg, progress);

        List<Object> jvm = new ArrayList<>(Json.arr(Json.obj(vanilla.get("arguments")).get("jvm")));
        jvm.addAll(Json.arr(Json.obj(fabric.get("arguments")).get("jvm")));
        List<Object> game = new ArrayList<>(Json.arr(Json.obj(vanilla.get("arguments")).get("game")));
        game.addAll(Json.arr(Json.obj(fabric.get("arguments")).get("game")));
        progress.accept("Bereit", 1.0);
        return new Spec(cp, Json.str(fabric.get("mainClass")), jvm, game, aid, fid, javaMajor);
    }

    private static String cachedText(String url, Path cache) throws IOException {
        try { String t = Http.get(url); Files.createDirectories(cache.getParent()); Files.writeString(cache, t, StandardCharsets.UTF_8); return t; }
        catch (IOException e) { if (Files.isRegularFile(cache)) return Files.readString(cache, StandardCharsets.UTF_8); throw e; }
    }

    private interface Job { void run(Object[] x) throws IOException; }

    private static void runParallel(List<Object[]> items, int threads, Job job) throws IOException {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (Object[] x : items) fs.add(ex.submit(() -> { job.run(x); return null; }));
            for (Future<?> f : fs) {
                try { f.get(); }
                catch (ExecutionException e) { for (Future<?> g : fs) g.cancel(true); throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
            }
        } finally { ex.shutdownNow(); }
    }

    // ---------- Mods ----------
    public static void installMods(Config cfg, BiConsumer<String, Double> progress) throws IOException {
        Files.createDirectories(mods());
        // Toasty Client gehoert nicht in den Poldi Launcher
        try (var s = Files.list(mods())) { for (Path p : (Iterable<Path>) s::iterator) if (p.getFileName().toString().startsWith("toasty-client")) Files.deleteIfExists(p); }
        // Poldi Client Mod: nur fuer die Hauptversion (26.2); eingebaute Jar sofort, Update aus dem GitHub-Release
        if (Config.mc.equals(Config.CLIENT_MC)) {
            if (!hasJar(MOD)) extractBundled();
            try {
                Map<String, Object> rel = Json.obj(Json.parse(Http.get("https://api.github.com/repos/" + Config.CLIENT_REPO + "/releases/tags/" + Config.RELEASE_TAG, Map.of("Accept", "application/vnd.github+json"))));
                for (Object o : Json.arr(rel.get("assets"))) {
                    Map<String, Object> a = Json.obj(o); String nm = Json.str(a.get("name"));
                    if (nm.startsWith(MOD) && nm.endsWith(".jar") && !nm.contains("sources") && !nm.contains("dev")) {
                        Path dest = mods().resolve(nm);
                        long size = a.get("size") instanceof Double d ? d.longValue() : -1;
                        if (!Files.isRegularFile(dest) || (size > 0 && Files.size(dest) != size)) {
                            Http.download(Json.str(a.get("browser_download_url")), dest, null);
                            try (var s = Files.list(mods())) { for (Path p : (Iterable<Path>) s::iterator) { String fn = p.getFileName().toString(); if (fn.startsWith(MOD) && !fn.equals(nm)) Files.deleteIfExists(p); } }
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                if (!hasJar(MOD) && !extractBundled()) throw new IOException("Poldi Client Mod konnte nicht geladen werden: " + e.getMessage());
            }
            if (!hasJar(MOD)) throw new IOException("Poldi Client Mod fehlt (noch keine Jar im Release " + Config.RELEASE_TAG + ").");
        }
        // Fabric API (Modrinth)
        try {
            String q = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=" + URLEncoder.encode("[\"" + Config.mc + "\"]", StandardCharsets.UTF_8)
                + "&loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
            List<Object> vs = Json.arr(Json.parse(Http.get(q)));
            if (!vs.isEmpty()) {
                for (Object fo : Json.arr(Json.obj(vs.get(0)).get("files"))) {
                    Map<String, Object> f = Json.obj(fo);
                    if (!Boolean.TRUE.equals(f.get("primary")) && Json.arr(Json.obj(vs.get(0)).get("files")).size() > 1) continue;
                    String nm = Json.str(f.get("filename"));
                    try (var s = Files.list(mods())) { for (Path p : (Iterable<Path>) s::iterator) { String fn = p.getFileName().toString(); if (fn.startsWith("fabric-api") && !fn.equals(nm)) Files.deleteIfExists(p); } }
                    Http.download(Json.str(f.get("url")), mods().resolve(nm), Json.str(Json.obj(f.get("hashes")).get("sha1")));
                    break;
                }
            }
        } catch (IOException e) {
            if (!hasJar("fabric-api")) throw new IOException("Fabric API konnte nicht geladen werden: " + e.getMessage());
        }
    }

    static final String MOD = "poldi-client";

    /** Im Launcher eingebaute Poldi-Client-Jar (bundled/poldi-client.jar) in den Mods-Ordner kopieren. */
    private static boolean extractBundled() {
        try (var in = Installer.class.getResourceAsStream("/bundled/poldi-client.jar")) {
            if (in == null) return false;
            Files.createDirectories(mods());
            Files.copy(in, mods().resolve("poldi-client-bundled.jar"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) { return false; }
    }

    private static boolean hasJar(String prefix) {
        try (var s = Files.list(mods())) { return s.anyMatch(p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".jar")); } catch (IOException e) { return false; }
    }

    /** Alle Release-Versionen ab 1.18 (Fabric vorinstalliert), neueste zuerst; offline: gespeicherte Liste. */
    public static List<String> versions() {
        List<String> out = new ArrayList<>();
        try {
            Map<String, Object> m = cachedJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", VERS.resolve("manifest.json"));
            for (Object o : Json.arr(m.get("versions"))) {
                Map<String, Object> v = Json.obj(o);
                if (!"release".equals(Json.str(v.get("type")))) continue;
                String id = Json.str(v.get("id")); String[] p = id.split("\\.");
                try { if (p.length >= 2 && Integer.parseInt(p[0]) == 1 && Integer.parseInt(p[1]) < 18) continue; } catch (NumberFormatException e) { continue; }
                out.add(id);
            }
        } catch (Exception ignored) {}
        out.remove(Config.CLIENT_MC); out.add(0, Config.CLIENT_MC); // Hauptversion immer ganz oben
        return out;
    }

    /** Installierte Poldi-Version (Dateiname) oder "nicht installiert". */
    public static String installedClient() {
        if (!Config.mc.equals(Config.CLIENT_MC)) return "Poldi Client nur für " + Config.CLIENT_MC;
        try (var s = Files.list(mods())) { return s.map(p -> p.getFileName().toString()).filter(f -> f.startsWith(MOD) && f.endsWith(".jar")).findFirst().orElse("nicht installiert"); }
        catch (IOException e) { return "nicht installiert"; }
    }

    /** Changelog vom neuesten GitHub-Release. */
    public static News news(Config cfg) {
        try {
            Map<String, Object> rel = Json.obj(Json.parse(Http.get("https://api.github.com/repos/" + Config.CLIENT_REPO + "/releases/tags/" + Config.RELEASE_TAG, Map.of("Accept", "application/vnd.github+json"))));
            String body = Json.str(rel.get("body")).replace("\r", "");
            return new News(Json.str(rel.get("tag_name")), Json.str(rel.get("name")), body.isBlank() ? "Keine Beschreibung zu diesem Release." : body);
        } catch (Exception e) { return new News("", "Keine News", "News konnten nicht geladen werden.\n(" + e.getMessage() + ")"); }
    }
}
