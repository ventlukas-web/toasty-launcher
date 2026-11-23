package dev.poldi.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Eigene News des Owners: Datei news.json im Launcher-Repo. Lesen ohne Anmeldung, Schreiben mit Owner-Token. */
public final class Posts {
    private Posts() {}
    public record Post(String date, String title, String text) {}

    static String rawUrl() { return "https://raw.githubusercontent.com/" + Config.LAUNCHER_REPO + "/main/poldi-launcher/news.json"; }
    static String apiUrl() { return "https://api.github.com/repos/" + Config.LAUNCHER_REPO + "/contents/poldi-launcher/news.json"; }

    public static List<Post> fetch() {
        try { return parse(Http.get(rawUrl())); } catch (Exception e) { return List.of(); }
    }

    static List<Post> parse(String json) {
        List<Post> out = new ArrayList<>();
        for (Object o : Json.arr(Json.obj(Json.parse(json)).get("posts"))) {
            Map<String, Object> p = Json.obj(o);
            out.add(new Post(Json.str(p.get("date")), Json.str(p.get("title")), Json.str(p.get("text"))));
        }
        return out;
    }

    static String write(List<Post> posts) {
        List<Object> arr = new ArrayList<>();
        for (Post p : posts) { Map<String, Object> m = new LinkedHashMap<>(); m.put("date", p.date()); m.put("title", p.title()); m.put("text", p.text()); arr.add(m); }
        Map<String, Object> root = new LinkedHashMap<>(); root.put("posts", arr);
        return Json.write(root);
    }

    /** Neuen Beitrag ganz nach oben schreiben (max. 30 behalten). */
    public static void publish(String token, String title, String text) throws IOException {
        if (token == null || token.isBlank()) throw new IOException("Owner-Token fehlt.");
        Map<String, String> auth = Map.of("Authorization", "Bearer " + token.trim(), "Accept", "application/vnd.github+json");
        List<Post> posts = new ArrayList<>(); String sha = null;
        try {
            Map<String, Object> f = Json.obj(Json.parse(Http.get(apiUrl() + "?ref=main", auth)));
            sha = Json.str(f.get("sha"));
            String content = new String(Base64.getMimeDecoder().decode(Json.str(f.get("content"))), StandardCharsets.UTF_8);
            posts.addAll(parse(content));
        } catch (IOException e) { if (!String.valueOf(e.getMessage()).contains("404")) throw e; }
        posts.add(0, new Post(LocalDate.now().toString(), title.trim(), text.trim()));
        while (posts.size() > 30) posts.remove(posts.size() - 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "News: " + title.trim());
        body.put("content", Base64.getEncoder().encodeToString(write(posts).getBytes(StandardCharsets.UTF_8)));
        body.put("branch", "main");
        if (sha != null && !sha.isBlank()) body.put("sha", sha);
        Http.put(apiUrl(), Json.write(body), auth);
    }

    public static String format(List<Post> posts) {
        StringBuilder sb = new StringBuilder();
        for (Post p : posts) sb.append("▸ ").append(p.title()).append("   ").append(p.date()).append("\n").append(p.text()).append("\n\n");
        return sb.toString();
    }
}
