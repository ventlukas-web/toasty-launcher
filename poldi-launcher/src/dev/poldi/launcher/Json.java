package dev.poldi.launcher;

import java.util.*;

/** Minimaler JSON-Parser/-Schreiber (Map, List, String, Double, Boolean, null). */
public final class Json {
    private final String s; private int i;
    private Json(String s) { this.s = s; }

    public static Object parse(String text) { Json j = new Json(text); j.ws(); Object v = j.val(); return v; }
    @SuppressWarnings("unchecked") public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked") public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    public static String str(Object o) { return o == null ? "" : o instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(o); }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private Object val() {
        char c = s.charAt(i);
        if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) { ws(); String k = string(); ws(); i++; ws(); m.put(k, val()); ws();
                char d = s.charAt(i++); if (d == '}') return m; } }
        if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) { ws(); l.add(val()); ws(); char d = s.charAt(i++); if (d == ']') return l; } }
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.parseDouble(s.substring(st, i));
    }
    private String string() {
        i++; StringBuilder b = new StringBuilder();
        while (true) { char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') { char e = s.charAt(i++);
                switch (e) { case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r'); case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; } default -> b.append(e); }
            } else b.append(c); }
    }

    public static String write(Object o) { StringBuilder b = new StringBuilder(); w(b, o, 0); return b.toString(); }
    private static void w(StringBuilder b, Object o, int ind) {
        if (o == null) b.append("null");
        else if (o instanceof String t) { b.append('"'); for (char c : t.toCharArray()) { switch (c) { case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); } } } b.append('"'); }
        else if (o instanceof Map<?, ?> m) { b.append("{"); boolean f = true; for (var e : m.entrySet()) { if (!f) b.append(","); f = false; b.append("\n").append("  ".repeat(ind + 1)); w(b, String.valueOf(e.getKey()), 0); b.append(": "); w(b, e.getValue(), ind + 1); } if (!m.isEmpty()) b.append("\n").append("  ".repeat(ind)); b.append("}"); }
        else if (o instanceof List<?> l) { b.append("["); boolean f = true; for (Object x : l) { if (!f) b.append(", "); f = false; w(b, x, ind); } b.append("]"); }
        else if (o instanceof Double d && d == Math.floor(d)) b.append(d.longValue());
        else b.append(o);
    }
}
