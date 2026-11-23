package dev.poldi.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Baut die Kommandozeile und startet Minecraft. */
public final class Launch {
    private Launch() {}

    public static Process start(Installer.Spec spec, Config cfg, String name, String uuid, String token) throws IOException {
        Path gameDir = Config.instance();
        Files.createDirectories(gameDir);
        Path natives = gameDir.resolve("natives"); Files.createDirectories(natives);
        String sep = File.pathSeparator;
        StringBuilder cp = new StringBuilder();
        for (Path p : spec.classpath()) { if (cp.length() > 0) cp.append(sep); cp.append(p.toAbsolutePath()); }

        Map<String, String> v = new HashMap<>();
        v.put("natives_directory", natives.toAbsolutePath().toString());
        v.put("launcher_name", "PoldiClient"); v.put("launcher_version", "1.0");
        v.put("classpath", cp.toString()); v.put("classpath_separator", sep); v.put("library_directory", Installer.LIB.toAbsolutePath().toString());
        v.put("auth_player_name", name); v.put("version_name", Config.mc); v.put("game_directory", gameDir.toAbsolutePath().toString());
        v.put("assets_root", Installer.ASSETS.toAbsolutePath().toString()); v.put("assets_index_name", spec.assetIndex());
        v.put("auth_uuid", uuid); v.put("auth_access_token", token == null || token.isEmpty() ? "0" : token);
        v.put("clientid", ""); v.put("auth_xuid", ""); v.put("user_type", token == null || token.isEmpty() ? "legacy" : "msa");
        v.put("version_type", "PoldiClient"); v.put("resolution_width", "854"); v.put("resolution_height", "480");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-Xmx" + cfg.ramMb + "M"); cmd.add("-Xms" + Math.min(cfg.ramMb, 1024) + "M");
        cmd.addAll(expand(spec.jvm(), v));
        if (!cmd.contains("-cp") && !cmd.contains("-classpath")) { cmd.add("-cp"); cmd.add(cp.toString()); }
        cmd.add(spec.mainClass());
        cmd.addAll(expand(spec.game(), v));

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(gameDir.toFile()).redirectErrorStream(true);
        Path log = Config.DIR.resolve("launcher-game.log");
        pb.redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
        return pb.start();
    }

    static String javaBin() {
        Path home = Paths.get(System.getProperty("java.home"));
        Path w = home.resolve("bin").resolve("javaw.exe");
        return Files.exists(w) ? w.toString() : home.resolve("bin").resolve("java").toString();
    }

    /** Argumentliste (Strings oder {rules,value}) -> fertige Strings mit ersetzten ${platzhaltern}. */
    public static List<String> expand(List<Object> args, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (Object a : args) {
            if (a instanceof String s) out.add(sub(s, vars));
            else if (a instanceof Map<?, ?> m) {
                if (!Installer.allowed(m.get("rules"))) continue;
                Object val = m.get("value");
                if (val instanceof String s) out.add(sub(s, vars));
                else if (val instanceof List<?> l) for (Object x : l) out.add(sub(String.valueOf(x), vars));
            }
        }
        return out;
    }

    private static String sub(String s, Map<String, String> vars) {
        for (var e : vars.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue());
        return s;
    }
}
