package dev.poldi.launcher;

import java.io.*;
import java.util.*;

/** Laedt eine .litematic und baut daraus Quader (Boxen) mit Farbe, fuer den 3D-Viewer. */
public final class Schem {
    public int sx, sy, sz;
    public long blockCount;
    public String name = "";
    /** Boxen: x0,y0,z0,x1,y1,z1 (Weltkoordinaten, y oben). */
    public float[] box = new float[0];
    public int[] rgb = new int[0];       // 0xRRGGBB
    public float[] alpha = new float[0];
    public int[] cellY = new int[0];     // Zeile der Zelle
    public byte[] exposed = new byte[0]; // Bit0..5: -x,+x,-y,+y,-z,+z sichtbar (frei)
    public byte[] hidden = new byte[0];  // Gegenstueck: Seiten, die an feste Nachbarn grenzen (nur Vollbloecke)
    public boolean[] full = new boolean[0];
    public int count;

    static final class Pal { String name; Map<String, Object> props; int rgb; float a = 1; boolean air, opaqueFull; float[][] shapes; }

    public static Schem load(File f) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(f)) { root = Nbt.read(in); }
        Map<String, Object> regions = Nbt.map(root.get("Regions"));
        if (regions.isEmpty()) throw new IOException("Keine Regionen in der Datei.");
        // Gesamtgrenzen
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Object ro : regions.values()) {
            Map<String, Object> r = Nbt.map(ro), p = Nbt.map(r.get("Position")), s = Nbt.map(r.get("Size"));
            int sx = Nbt.num(s.get("x")), sy = Nbt.num(s.get("y")), sz = Nbt.num(s.get("z"));
            int x0 = Nbt.num(p.get("x")) + (sx < 0 ? sx + 1 : 0), y0 = Nbt.num(p.get("y")) + (sy < 0 ? sy + 1 : 0), z0 = Nbt.num(p.get("z")) + (sz < 0 ? sz + 1 : 0);
            minX = Math.min(minX, x0); minY = Math.min(minY, y0); minZ = Math.min(minZ, z0);
            maxX = Math.max(maxX, x0 + Math.abs(sx)); maxY = Math.max(maxY, y0 + Math.abs(sy)); maxZ = Math.max(maxZ, z0 + Math.abs(sz));
        }
        Schem m = new Schem();
        m.sx = maxX - minX; m.sy = maxY - minY; m.sz = maxZ - minZ;
        m.name = Nbt.map(root.get("Metadata")).get("Name") instanceof String n ? n : f.getName();
        long cells = (long) m.sx * m.sy * m.sz;
        if (cells <= 0 || cells > 80_000_000L) throw new IOException("Schematic zu groß für die Vorschau (" + m.sx + "×" + m.sy + "×" + m.sz + ").");
        // Globale Palette
        List<Pal> gp = new ArrayList<>(); Map<String, Integer> gi = new HashMap<>();
        int[] grid = new int[(int) cells]; Arrays.fill(grid, -1);
        for (Object ro : regions.values()) {
            Map<String, Object> r = Nbt.map(ro), p = Nbt.map(r.get("Position")), s = Nbt.map(r.get("Size"));
            int sx = Nbt.num(s.get("x")), sy = Nbt.num(s.get("y")), sz = Nbt.num(s.get("z"));
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            int ox = Nbt.num(p.get("x")) + (sx < 0 ? sx + 1 : 0) - minX, oy = Nbt.num(p.get("y")) + (sy < 0 ? sy + 1 : 0) - minY, oz = Nbt.num(p.get("z")) + (sz < 0 ? sz + 1 : 0) - minZ;
            List<Object> pal = Nbt.list(r.get("BlockStatePalette"));
            int[] map = new int[pal.size()];
            for (int i = 0; i < pal.size(); i++) {
                Map<String, Object> e = Nbt.map(pal.get(i));
                String nm = String.valueOf(e.get("Name")); Map<String, Object> props = Nbt.map(e.get("Properties"));
                String key = nm + new TreeMap<>(props);
                Integer id = gi.get(key);
                if (id == null) { id = gp.size(); gp.add(makePal(nm, props)); gi.put(key, id); }
                map[i] = id;
            }
            if (!(r.get("BlockStates") instanceof long[] longs)) continue;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, pal.size() - 1)));
            long mask = (1L << bits) - 1;
            for (int y = 0; y < ay; y++) for (int z = 0; z < az; z++) for (int x = 0; x < ax; x++) {
                long bi = ((long) (y * az + z) * ax + x) * bits; int li = (int) (bi >> 6), off = (int) (bi & 63);
                if (li >= longs.length) continue;
                long v = longs[li] >>> off;
                if (off + bits > 64 && li + 1 < longs.length) v |= longs[li + 1] << (64 - off);
                int pi = (int) (v & mask);
                if (pi >= map.length) continue;
                Pal q = gp.get(map[pi]);
                if (q.air) continue;
                grid[((oy + y) * m.sz + (oz + z)) * m.sx + (ox + x)] = map[pi];
            }
        }
        m.build(grid, gp);
        return m;
    }

    private void build(int[] grid, List<Pal> gp) {
        List<float[]> boxes = new ArrayList<>(); List<int[]> meta = new ArrayList<>(); // rgb, cellY, exposed, hidden, full
        List<Float> al = new ArrayList<>();
        long blocks = 0;
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            int g = grid[(y * sz + z) * sx + x]; if (g < 0) continue;
            blocks++;
            Pal p = gp.get(g);
            float[][] shapes = p.shapes;
            boolean fullCube = shapes.length == 1 && shapes[0][0] == 0 && shapes[0][1] == 0 && shapes[0][2] == 0 && shapes[0][3] == 1 && shapes[0][4] == 1 && shapes[0][5] == 1;
            int exp = 63, hid = 0;
            if (fullCube) {
                exp = 0;
                int[][] d = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
                for (int f = 0; f < 6; f++) {
                    int nx = x + d[f][0], ny = y + d[f][1], nz = z + d[f][2];
                    boolean solid = false;
                    if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
                        int ng = grid[(ny * sz + nz) * sx + nx];
                        if (ng >= 0) { Pal q = gp.get(ng); solid = q.opaqueFull || (ng == g && p.a < 1 && q.shapes.length == 1 && fullShape(q.shapes[0])); }
                    }
                    if (solid) hid |= 1 << f; else exp |= 1 << f;
                }
                if (exp == 0 && !(y == 0 || y == sy - 1)) { /* komplett verdeckt: trotzdem merken fuer Schnitt */ }
            }
            for (float[] s : shapes) {
                boxes.add(new float[]{x + s[0], y + s[1], z + s[2], x + s[3], y + s[4], z + s[5]});
                meta.add(new int[]{p.rgb, y, exp, hid, fullCube ? 1 : 0});
                al.add(p.a);
            }
        }
        blockCount = blocks; count = boxes.size();
        box = new float[count * 6]; rgb = new int[count]; alpha = new float[count]; cellY = new int[count]; exposed = new byte[count]; hidden = new byte[count]; full = new boolean[count];
        for (int i = 0; i < count; i++) {
            System.arraycopy(boxes.get(i), 0, box, i * 6, 6);
            int[] mt = meta.get(i); rgb[i] = mt[0]; cellY[i] = mt[1]; exposed[i] = (byte) mt[2]; hidden[i] = (byte) mt[3]; full[i] = mt[4] == 1; alpha[i] = al.get(i);
        }
        // Vollblöcke ohne irgendeine freie Seite nie zeichnen, ausser an Schnittkanten -> bleiben drin (hidden != 0)
    }

    private static boolean fullShape(float[] s) { return s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 1 && s[4] == 1 && s[5] == 1; }

    // ---------- Blockfarben / Formen ----------
    private static final Object[][] COLORS = {
        {"sea_lantern", 0xBEF0E6}, {"cyan_stained", 0x28BEC8}, {"light_blue_stained", 0x6EBEF0}, {"light_blue_concrete", 0x288CC8},
        {"red_stained", 0xDC3232}, {"black_stained", 0x282832}, {"black_concrete", 0x0E0E14}, {"gilded", 0x3C342C}, {"chiseled_polished_blackstone", 0x2C2832},
        {"polished_blackstone", 0x34303A}, {"blackstone", 0x242028}, {"deepslate", 0x46464C}, {"warped_planks", 0x1E786E}, {"warped", 0x188278},
        {"stripped_warped", 0x32A08C}, {"soul_sand", 0x544032}, {"honey", 0xF0AA28}, {"water", 0x325ADC}, {"bubble", 0x5082EB}, {"chest", 0x966428},
        {"hopper", 0x46464C}, {"end_rod", 0xF5F0FF}, {"chain", 0x3C4250}, {"copper", 0xC86E50}, {"nether_brick", 0x3C181C}, {"spawner", 0x1E243C},
        {"piston", 0x96825A}, {"mangrove", 0x783232}, {"redstone", 0xC81414}, {"obsidian", 0x140A28}, {"lava", 0xF06E14}, {"glass", 0xA0C8DC},
        {"hay", 0xC8AA28}, {"stone", 0x7D7D7D}, {"iron", 0xC8C8CD}, {"gold", 0xF0C83C}, {"diamond", 0x5ADCDC}, {"netherite", 0x3C343A},
        {"barrel", 0x785A32}, {"shulker", 0x8C5AA0}, {"sign", 0x783232}, {"button", 0x783232}, {"dropper", 0x646464}, {"dispenser", 0x646464},
        {"observer", 0x646464}, {"comparator", 0x969696}, {"repeater", 0x969696}, {"lever", 0x82643C}, {"rail", 0x96785A}, {"carpet", 0xB4B4B4},
        {"wool", 0xDCDCDC}, {"log", 0x645032}, {"planks", 0xAA8250}, {"sand", 0xDCCD96}, {"gravel", 0x827D78}, {"dirt", 0x6E5037}, {"grass", 0x64A03C},
        {"netherrack", 0x6E2828}, {"magma", 0x963C14}, {"quartz", 0xEBE6DE}, {"purpur", 0xAA78AA}, {"end_stone", 0xDCDCA0},
        {"white", 0xE8E8E8}, {"light_gray", 0x9D9D97}, {"gray", 0x474F52}, {"black", 0x1D1D21}, {"brown", 0x835432}, {"red", 0xB02E26},
        {"orange", 0xF9801D}, {"yellow", 0xFED83D}, {"lime", 0x80C71F}, {"green", 0x5E7C16}, {"cyan", 0x169C9C}, {"light_blue", 0x3AB3DA},
        {"blue", 0x3C44AA}, {"purple", 0x8932B8}, {"magenta", 0xC74EBD}, {"pink", 0xF38BAA}, {"oak", 0xAA8250}, {"spruce", 0x6B502F},
        {"birch", 0xC8B77A}, {"jungle", 0xA0723C}, {"acacia", 0xB86A3C}, {"dark_oak", 0x4A3520}, {"cherry", 0xE5B1B8}, {"bamboo", 0xC8B450},
        {"brick", 0x96584A}, {"sandstone", 0xDACB92}, {"prismarine", 0x64A096}, {"tuff", 0x6E6E64}, {"calcite", 0xE0E0DA}, {"amethyst", 0x8C64C8},
        {"ice", 0x8CB4F0}, {"snow", 0xF8FEFE}, {"clay", 0xA0A6B4}, {"terracotta", 0x985E43}, {"concrete", 0xA0A0A0}, {"andesite", 0x8A8A8A},
        {"granite", 0x9A6B58}, {"diorite", 0xC8C8C8}, {"cobble", 0x7A7A7A}, {"leaves", 0x3C7828}
    };

    private static int colorOf(String n) {
        for (Object[] c : COLORS) if (n.contains((String) c[0])) return (Integer) c[1];
        int h = n.hashCode(); float hue = ((h >>> 3) & 1023) / 1023f;
        return java.awt.Color.HSBtoRGB(hue, 0.22f, 0.66f) & 0xFFFFFF;
    }

    private static Pal makePal(String full, Map<String, Object> props) {
        Pal p = new Pal(); String n = full.startsWith("minecraft:") ? full.substring(10) : full; p.name = n; p.props = props;
        p.air = n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("structure_void") || n.equals("light");
        p.rgb = colorOf(n); p.a = 1;
        float[] sh = {0, 0, 0, 1, 1, 1};
        List<float[]> shapes = new ArrayList<>();
        if (n.contains("glass") && !n.contains("pane")) p.a = .42f;
        else if (n.contains("pane") || n.equals("iron_bars")) { p.a = .5f; sh = new float[]{.4f, 0, .4f, .6f, 1, .6f}; }
        if (n.equals("water") || n.equals("bubble_column")) p.a = .5f;
        if (n.equals("honey_block")) p.a = .85f;
        String type = String.valueOf(props.getOrDefault("type", "bottom"));
        if (n.endsWith("slab")) sh = switch (type) { case "top" -> new float[]{0, .5f, 0, 1, 1, 1}; case "double" -> sh; default -> new float[]{0, 0, 0, 1, .5f, 1}; };
        else if (n.contains("wall") && !n.contains("sign") && !n.contains("banner") && !n.contains("torch") && !n.contains("head") && !n.contains("skull")) sh = new float[]{.25f, 0, .25f, .75f, 1, .75f};
        else if (n.contains("fence") && !n.contains("gate")) sh = new float[]{.375f, 0, .375f, .625f, 1, .625f};
        else if (n.contains("chain") && !n.contains("chain_command")) sh = new float[]{.43f, 0, .43f, .57f, 1, .57f};
        else if (n.equals("end_rod") || n.contains("torch")) sh = new float[]{.4f, 0, .4f, .6f, .9f, .6f};
        else if (n.contains("trapdoor")) sh = new float[]{0, 0, 0, 1, .19f, 1};
        else if (n.contains("button")) sh = new float[]{.35f, 0, .35f, .65f, .2f, .65f};
        else if (n.contains("sign")) sh = new float[]{.1f, .3f, .8f, .9f, .9f, 1};
        else if (n.equals("redstone_wire") || n.contains("carpet") || n.contains("pressure_plate") || n.equals("repeater") || n.equals("comparator") || n.contains("rail")) sh = new float[]{0, 0, 0, 1, .08f, 1};
        else if (n.equals("hopper")) sh = new float[]{.1f, .2f, .1f, .9f, 1, .9f};
        else if (n.equals("chest") || n.equals("trapped_chest") || n.equals("ender_chest")) sh = new float[]{.06f, 0, .06f, .94f, .88f, .94f};
        else if (n.contains("lever") || n.contains("flower") || n.contains("sapling") || n.contains("fern") || n.contains("grass") && !n.contains("block") || n.contains("vine") || n.contains("candle") || n.contains("lantern")) sh = new float[]{.3f, 0, .3f, .7f, .6f, .7f};
        if (n.endsWith("stairs")) {
            boolean top = "top".equals(props.get("half")); String f = String.valueOf(props.getOrDefault("facing", "north"));
            float y0 = top ? .5f : 0, y1 = top ? 1 : .5f, by0 = top ? 0 : .5f, by1 = top ? .5f : 1;
            shapes.add(new float[]{0, y0, 0, 1, y1, 1});
            switch (f) { // Rueckenteil liegt gegenueber der Blickrichtung
                case "north" -> shapes.add(new float[]{0, by0, .5f, 1, by1, 1});
                case "south" -> shapes.add(new float[]{0, by0, 0, 1, by1, .5f});
                case "east" -> shapes.add(new float[]{0, by0, 0, .5f, by1, 1});
                default -> shapes.add(new float[]{.5f, by0, 0, 1, by1, 1});
            }
        } else shapes.add(sh);
        p.shapes = shapes.toArray(new float[0][]);
        p.opaqueFull = p.a >= 1 && p.shapes.length == 1 && fullShape(p.shapes[0]);
        return p;
    }
}
