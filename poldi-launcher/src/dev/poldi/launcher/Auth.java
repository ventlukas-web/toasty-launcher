package dev.poldi.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Microsoft-Login per Device-Code (Code im Browser eingeben) -> Xbox Live -> XSTS -> Minecraft. */
public final class Auth {
    private Auth() {}
    private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";

    public record Session(String name, String uuid, String accessToken, String refreshToken) {}

    private static String form(Map<String, String> m) {
        StringBuilder b = new StringBuilder();
        for (var e : m.entrySet()) { if (b.length() > 0) b.append('&'); b.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)); }
        return b.toString();
    }

    /** Neuer Login. onCode(url, code) zeigt dem Nutzer, was er im Browser eingeben soll. Blockiert bis fertig. */
    public static Session login(String clientId, BiConsumerEx onCode, Consumer<String> status, java.util.function.BooleanSupplier cancelled) throws IOException {
        if (clientId == null || clientId.isBlank()) throw new IOException("Keine Azure Client-ID eingetragen (Einstellungen).");
        Map<String, Object> dc = Json.obj(Json.parse(Http.post(TENANT + "devicecode", form(Map.of("client_id", clientId, "scope", SCOPE)),
            "application/x-www-form-urlencoded", Map.of())));
        if (dc.get("device_code") == null) throw new IOException("Microsoft: " + Json.str(dc.get("error_description")));
        String devCode = Json.str(dc.get("device_code"));
        onCode.accept(Json.str(dc.get("verification_uri")), Json.str(dc.get("user_code")));
        long interval = Math.max(3, (long) (dc.get("interval") instanceof Double d ? d : 5));
        long deadline = System.currentTimeMillis() + (long) ((dc.get("expires_in") instanceof Double d ? d : 900) * 1000);
        status.accept("Warte auf Anmeldung im Browser …");
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.getAsBoolean()) throw new IOException("Abgebrochen");
            try { Thread.sleep(interval * 1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
            Map<String, Object> t = Json.obj(Json.parse(Http.post(TENANT + "token", form(Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", clientId, "device_code", devCode)), "application/x-www-form-urlencoded", Map.of())));
            if (t.get("access_token") != null) return finish(Json.str(t.get("access_token")), Json.str(t.get("refresh_token")), status);
            String err = Json.str(t.get("error"));
            if (err.equals("authorization_pending")) continue;
            if (err.equals("slow_down")) { interval += 5; continue; }
            throw new IOException("Microsoft: " + Json.str(t.get("error_description")));
        }
        throw new IOException("Zeit abgelaufen – bitte erneut anmelden.");
    }

    @FunctionalInterface public interface BiConsumerEx { void accept(String a, String b); }

    /** Angemeldet bleiben: Refresh-Token einloesen. */
    public static Session refresh(String clientId, String refreshToken, Consumer<String> status) throws IOException {
        Map<String, Object> t = Json.obj(Json.parse(Http.post(TENANT + "token", form(Map.of("grant_type", "refresh_token", "client_id", clientId,
            "refresh_token", refreshToken, "scope", SCOPE)), "application/x-www-form-urlencoded", Map.of())));
        if (t.get("access_token") == null) throw new IOException("Login abgelaufen – bitte neu anmelden.");
        return finish(Json.str(t.get("access_token")), Json.str(t.get("refresh_token")), status);
    }

    private static Session finish(String msAccess, String msRefresh, Consumer<String> status) throws IOException {
        status.accept("Xbox Live …");
        Map<String, Object> xbl = Json.obj(Json.parse(Http.post("https://user.auth.xboxlive.com/user/authenticate",
            Json.write(Map.of("Properties", Map.of("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + msAccess),
                "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT")), "application/json", Map.of())));
        String xblToken = Json.str(xbl.get("Token"));
        if (xblToken.isEmpty()) throw new IOException("Xbox-Login fehlgeschlagen.");
        status.accept("XSTS …");
        String xr = Http.post("https://xsts.auth.xboxlive.com/xsts/authorize",
            Json.write(Map.of("Properties", Map.of("SandboxId", "RETAIL", "UserTokens", List.of(xblToken)),
                "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT")), "application/json", Map.of());
        Map<String, Object> xsts = Json.obj(Json.parse(xr));
        if (xsts.get("Token") == null) {
            long code = xsts.get("XErr") instanceof Double d ? d.longValue() : 0;
            throw new IOException(code == 2148916233L ? "Dieses Microsoft-Konto hat kein Xbox-Profil." : code == 2148916238L ? "Kinder-Konto: Familie muss es freigeben." : "XSTS-Fehler " + code);
        }
        String uhs = Json.str(Json.obj(Json.arr(Json.obj(xsts.get("DisplayClaims")).get("xui")).get(0)).get("uhs"));
        status.accept("Minecraft …");
        Map<String, Object> mc = Json.obj(Json.parse(Http.post("https://api.minecraftservices.com/authentication/login_with_xbox",
            Json.write(Map.of("identityToken", "XBL3.0 x=" + uhs + ";" + Json.str(xsts.get("Token")))), "application/json", Map.of())));
        String token = Json.str(mc.get("access_token"));
        if (token.isEmpty()) throw new IOException("Minecraft-Login fehlgeschlagen (App von Mojang freigegeben?).");
        Map<String, Object> pr;
        try { pr = Json.obj(Json.parse(Http.get("https://api.minecraftservices.com/minecraft/profile", Map.of("Authorization", "Bearer " + token)))); }
        catch (IOException e) { throw new IOException("Dieses Konto besitzt Minecraft Java nicht (" + e.getMessage() + ")."); }
        if (pr.get("id") == null) throw new IOException("Dieses Konto besitzt Minecraft Java nicht.");
        return new Session(Json.str(pr.get("name")), Json.str(pr.get("id")), token, msRefresh);
    }

    /** UUID fuer Offline-Namen (wie der Server es bei online-mode=false macht). */
    public static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
