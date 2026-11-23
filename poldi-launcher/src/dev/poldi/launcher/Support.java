package dev.poldi.launcher;

import java.io.IOException;
import java.util.*;

/** Support: Tickets, Vorschlaege und Wuensche gehen per Discord-Webhook an den Owner. Die Webhook-URL steht in support.json im Launcher-Repo. */
public final class Support {
    private Support() {}
    static String rawUrl() { return "https://raw.githubusercontent.com/" + Config.LAUNCHER_REPO + "/main/support.json"; }

    static String webhook() throws IOException {
        String u = Json.str(Json.obj(Json.parse(Http.get(rawUrl()))).get("webhook")).trim();
        if (!u.startsWith("https://discord.com/api/webhooks/") && !u.startsWith("https://discordapp.com/api/webhooks/")) throw new IOException("support.json im Launcher-Repo fehlt oder hat keine Webhook-URL");
        return u;
    }

    /** type: Ticket / Vorschlag / Wunsch */
    public static void send(Config cfg, String type, String reason, String text) throws IOException {
        int color = type.equals("Ticket") ? 0xEF4444 : type.equals("Vorschlag") ? 0xF87171 : 0xFCA5A5;
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "[" + type + "] " + cut(reason, 200));
        embed.put("description", cut(text, 3500));
        embed.put("color", (double) color);
        List<Object> fields = new ArrayList<>();
        fields.add(field("Von", cfg.accName.isBlank() ? "(kein Name)" : cfg.accName + (cfg.offline ? " (Offline)" : "")));
        fields.add(field("Minecraft", Config.mc));
        fields.add(field("System", System.getProperty("os.name") + " / Java " + System.getProperty("java.version")));
        embed.put("fields", fields);
        embed.put("timestamp", java.time.Instant.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "Poldi Client");
        body.put("embeds", new ArrayList<Object>(List.of(embed)));
        int code = Http.postStatus(webhook(), Json.write(body));
        if (code / 100 != 2) throw new IOException("Discord antwortete mit HTTP " + code);
    }

    private static Map<String, Object> field(String n, String v) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("name", n); m.put("value", cut(v, 900)); m.put("inline", true); return m;
    }
    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }
}
