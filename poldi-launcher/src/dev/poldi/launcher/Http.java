package dev.poldi.launcher;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/** HTTP-Helfer: JSON/Text holen, Dateien mit SHA-1-Pruefung laden. */
public final class Http {
    private Http() {}
    private static final HttpClient C = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
    static final String UA = "PoldiClient/1.0 (+https://github.com/ventlukas-web/toasty-launcher)";

    public static String get(String url) throws IOException { return get(url, Map.of()); }

    public static String get(String url, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent", UA).GET();
            headers.forEach(b::header);
            HttpResponse<String> r = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " bei " + url);
            return r.body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
    }

    public static Object getJson(String url) throws IOException { return Json.parse(get(url)); }

    /** POST (form oder json); gibt Body zurueck, auch bei 4xx (Fehlertext ist bei OAuth/XBL wichtig). */
    public static String post(String url, String body, String contentType, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent", UA)
                .header("Content-Type", contentType).header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(b::header);
            return C.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
    }

    /** POST JSON, liefert nur den Statuscode (Discord-Webhook antwortet mit 204 ohne Body). */
    public static int postStatus(String url, String json) throws IOException {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", UA)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
            return C.send(r, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
    }

    /** PUT (GitHub-Contents-API). Wirft bei Fehler mit Meldung des Servers. */
    public static String put(String url, String body, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent", UA)
                .header("Content-Type", "application/json").header("Accept", "application/vnd.github+json").PUT(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(b::header);
            HttpResponse<String> r = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + ": " + Json.str(Json.obj(Json.parse(r.body())).get("message")));
            return r.body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
    }

    public static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1"); byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder(); for (byte x : md.digest()) sb.append(String.format("%02x", x)); return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    /** Laedt url nach dest (falls fehlt oder SHA-1 falsch). sha1 darf null sein. 3 Versuche. */
    public static void download(String url, Path dest, String sha1) throws IOException {
        if (Files.isRegularFile(dest) && (sha1 == null || sha1.isEmpty() || sha1.equalsIgnoreCase(sha1(dest)))) return;
        IOException last = null;
        for (int t = 0; t < 3; t++) {
            try {
                Files.createDirectories(dest.getParent());
                Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
                HttpRequest rq = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).header("User-Agent", UA).GET().build();
                HttpResponse<Path> r = C.send(rq, HttpResponse.BodyHandlers.ofFile(tmp));
                if (r.statusCode() / 100 != 2) { Files.deleteIfExists(tmp); throw new IOException("HTTP " + r.statusCode() + " bei " + url); }
                if (sha1 != null && !sha1.isEmpty() && !sha1.equalsIgnoreCase(sha1(tmp))) { Files.deleteIfExists(tmp); throw new IOException("Pruefsumme falsch: " + url); }
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) { last = e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
        }
        throw last;
    }
}
