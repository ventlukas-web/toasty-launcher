package dev.poldi.launcher;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Minimaler NBT-Leser (gzip). Compound -> Map, List -> List, Arrays -> byte[]/int[]/long[]. */
public final class Nbt {
    private Nbt() {}

    public static Map<String, Object> read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(raw), 1 << 16), 1 << 16))) {
            int t = in.readUnsignedByte();
            if (t != 10) throw new IOException("Keine NBT-Datei.");
            in.readUTF();
            return compound(in);
        }
    }

    @SuppressWarnings("unchecked") public static Map<String, Object> map(Object o) { return o instanceof Map ? (Map<String, Object>) o : new HashMap<>(); }
    @SuppressWarnings("unchecked") public static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    public static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        while (true) {
            int t = in.readUnsignedByte();
            if (t == 0) return m;
            String k = in.readUTF();
            m.put(k, payload(in, t));
        }
    }

    private static Object payload(DataInputStream in, int t) throws IOException {
        switch (t) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: {
                int it = in.readUnsignedByte(), n = in.readInt(); List<Object> l = new ArrayList<>(Math.max(0, Math.min(n, 1 << 20)));
                for (int i = 0; i < n; i++) l.add(payload(in, it));
                return l;
            }
            case 10: return compound(in);
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("Unbekannter NBT-Tag " + t);
        }
    }
}
