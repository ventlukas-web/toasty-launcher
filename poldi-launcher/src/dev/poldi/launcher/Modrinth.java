package dev.poldi.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Mods ueber die Modrinth-API suchen, installieren, entfernen. Immer passend zu Config.mc + Fabric. */
public final class Modrinth {
    public record Hit(String id, String slug, String title, String author, String desc, long downloads) {}

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static List<Hit> search(String query) throws IOException {
        String facets = "[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:" + Config.mc + "\"]]";
        String url = "https://api.modrinth.com/v2/search?limit=30&index=" + (query.isBlank() ? "downloads" : "relevance")
            + "&query=" + enc(query) + "&facets=" + enc(facets);
        Map<String, Object> r = Json.obj(Json.parse(Http.get(url)));
        List<Hit> out = new ArrayList<>();
        for (Object o : Json.arr(r.get("hits"))) {
            Map<String, Object> h = Json.obj(o);
            long dl = h.get("downloads") instanceof Double d ? d.longValue() : 0;
            out.add(new Hit(Json.str(h.get("project_id")), Json.str(h.get("slug")), Json.str(h.get("title")), Json.str(h.get("author")), Json.str(h.get("description")), dl));
        }
        return out;
    }

    /** Installiert die neueste passende Version samt Pflicht-Abhaengigkeiten. Gibt Dateinamen zurueck. */
    public static String install(String projectId, Set<String> seen) throws IOException {
        if (!seen.add(projectId)) return "";
        String q = "https://api.modrinth.com/v2/project/" + enc(projectId) + "/version?game_versions=" + enc("[\"" + Config.mc + "\"]") + "&loaders=" + enc("[\"fabric\"]");
        List<Object> vs = Json.arr(Json.parse(Http.get(q)));
        if (vs.isEmpty()) throw new IOException("Keine Fabric-Version für Minecraft " + Config.mc + ".");
        Map<String, Object> ver = Json.obj(vs.get(0));
        // Pflicht-Abhaengigkeiten zuerst (Fabric API ist meist schon da)
        for (Object d : Json.arr(ver.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(d);
            String pid = Json.str(dep.get("project_id"));
            if ("required".equals(Json.str(dep.get("dependency_type"))) && !pid.isBlank() && !hasProject(pid)) {
                try { install(pid, seen); } catch (IOException ignored) {}
            }
        }
        List<Object> files = Json.arr(ver.get("files"));
        Map<String, Object> file = null;
        for (Object f : files) { Map<String, Object> m = Json.obj(f); if (Boolean.TRUE.equals(m.get("primary"))) file = m; }
        if (file == null && !files.isEmpty()) file = Json.obj(files.get(0));
        if (file == null) throw new IOException("Keine Datei gefunden.");
        String name = Json.str(file.get("filename"));
        Path mods = Installer.mods(); Files.createDirectories(mods);
        Http.download(Json.str(file.get("url")), mods.resolve(name), Json.str(Json.obj(file.get("hashes")).get("sha1")));
        Map<String, Object> idx = readIndex(); idx.put(name, projectId); writeIndex(idx);
        return name;
    }

    // Merkt sich, welche Datei zu welchem Modrinth-Projekt gehoert
    static Path indexFile() { return Installer.mods().resolve(".modrinth.json"); }
    static Map<String, Object> readIndex() {
        try { if (Files.isRegularFile(indexFile())) return new LinkedHashMap<>(Json.obj(Json.parse(Files.readString(indexFile())))); } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }
    static void writeIndex(Map<String, Object> m) { try { Files.writeString(indexFile(), Json.write(m)); } catch (Exception ignored) {} }
    static boolean hasProject(String pid) {
        Map<String, Object> idx = readIndex();
        for (var e : idx.entrySet()) if (pid.equals(e.getValue()) && Files.isRegularFile(Installer.mods().resolve(e.getKey()))) return true;
        return pid.equals("P7dR8mSH"); // Fabric API wird vom Launcher selbst installiert
    }

    public static List<String> installed() {
        try (var s = Files.list(Installer.mods())) { return s.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".jar")).sorted().toList(); }
        catch (IOException e) { return List.of(); }
    }

    public static void remove(String file) throws IOException {
        Files.deleteIfExists(Installer.mods().resolve(file));
        Map<String, Object> idx = readIndex(); idx.remove(file); writeIndex(idx);
    }
}
