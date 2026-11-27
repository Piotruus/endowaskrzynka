package pl.skrzynka;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Wszystkie animacje otwierania skrzynki. */
public final class Animations {

    private Animations() { }

    public enum Type {
        ROULETTE("Klasyczna ruletka", Material.HOPPER, true,
                "Poziomy pasek nagród przesuwa się, zwalnia i zatrzymuje na wygranej."),
        VERTICAL("Wodospad", Material.WATER_BUCKET, true,
                "Pionowa kolumna nagród spada w dół i zatrzymuje się na środku."),
        RING("Karuzela", Material.ENDER_CHEST, true,
                "Nagrody kręcą się dookoła skrzynki, coraz wolniej, aż jedna zatrzyma się na górze."),
        DUAL("Podwójne koło", Material.NETHER_STAR, true,
                "Dwa pierścienie nagród kręcą się w przeciwne strony wokół skrzynki."),
        SLOT("Jednoręki bandyta", Material.GOLD_INGOT, true,
                "Trzy bębny zatrzymują się po kolei, a nagroda ląduje na środku."),
        ELIM("Eliminacja", Material.TNT, true,
                "Pole migoczących nagród, z którego odpadają kolejne, aż zostanie jedna."),
        ORBIT("Orbita", Material.ENDER_EYE, false,
                "Nagrody krążą w powietrzu wokół skrzyni, przyspieszają i zwalniają. Widać to w świecie."),
        HELIX("Spirala", Material.FIREWORK_ROCKET, false,
                "Nagrody wznoszą się spiralą nad skrzynią, a wygrana wylatuje na górę."),
        BURST("Wybuch", Material.FIRE_CHARGE, false,
                "Nagrody rozlatują się na wszystkie strony i wracają do skrzyni.");

        public final String label;
        public final Material icon;
        public final boolean gui;
        public final String desc;

        Type(String label, Material icon, boolean gui, String desc) {
            this.label = label;
            this.icon = icon;
            this.gui = gui;
            this.desc = desc;
        }
    }

    static Anim create(Type t, Ctx c) {
        return switch (t) {
            case ROULETTE -> new Roulette(c);
            case VERTICAL -> new Vertical(c);
            case RING -> new Ring(c);
            case DUAL -> new Dual(c);
            case SLOT -> new Slots(c);
            case ELIM -> new Elim(c);
            case ORBIT -> new Orbit(c);
            case HELIX -> new Helix(c);
            case BURST -> new Burst(c);
        };
    }

    // ---------- wspólne układy slotów ----------

    /** 8 slotów wokół środka (slot 22), zgodnie z ruchem wskazówek, od góry. */
    static final int[] RING = {13, 14, 23, 32, 31, 30, 21, 12};
    /** 16 slotów zewnętrznego pierścienia (5x5 wokół slotu 22). */
    static final int[] OUTER = outerRing();

    private static int[] outerRing() {
        List<Integer> l = new ArrayList<>();
        for (int c = 2; c <= 6; c++) l.add(c);
        for (int r = 1; r <= 4; r++) l.add(r * 9 + 6);
        for (int c = 5; c >= 2; c--) l.add(4 * 9 + c);
        for (int r = 3; r >= 1; r--) l.add(r * 9 + 2);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    static void decor(Anim a, int i, boolean top) {
        ItemStack x = Anim.pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack y = Anim.pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int s = 0; s < 9; s++) {
            ItemStack it = ((s + i) & 1) == 0 ? x : y;
            a.inv().setItem(45 + s, it);
            if (top) a.inv().setItem(s, it);
        }
    }

    // =====================================================
    // ANIMACJE W OKNIE
    // =====================================================

    /** 1. Klasyczna ruletka (poziomo). */
    static final class Roulette extends Anim {
        final CrateManager.Entry[] seq = new CrateManager.Entry[40];
        static final int LAST = 31;
        int i = 0, next = 0;

        Roulette(Ctx c) { super(c, true); }

        @Override void begin() {
            for (int k = 0; k < seq.length; k++) seq[k] = mgr.roll();
            seq[LAST + 4] = winner;
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            inv().setItem(13, CrateGui.simple(Material.HOPPER, "§e▼"));
            inv().setItem(31, CrateGui.simple(Material.HOPPER, "§e▲"));
        }

        @Override boolean step(int t) {
            if (t < next) return false;
            if (i > LAST) return true;
            for (int k = 0; k < 9; k++) inv().setItem(18 + k, show(seq[i + k]));
            click(1f + i * 0.02f);
            next = t + (i == LAST ? 30 : 1 + (i * i) / 150);
            i++;
            return false;
        }
    }

    /** 2. Wodospad (pionowo). */
    static final class Vertical extends Anim {
        static final int[] COL = {4, 13, 22, 31, 40};
        static final int LAST = 31;
        final CrateManager.Entry[] seq = new CrateManager.Entry[36];
        int i = 0, next = 0;

        Vertical(Ctx c) { super(c, true); }

        @Override void begin() {
            for (int k = 0; k < seq.length; k++) seq[k] = mgr.roll();
            seq[LAST + 2] = winner;
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            inv().setItem(21, CrateGui.simple(Material.LIME_STAINED_GLASS_PANE, "§a►"));
            inv().setItem(23, CrateGui.simple(Material.LIME_STAINED_GLASS_PANE, "§a◄"));
        }

        @Override boolean step(int t) {
            if (t < next) return false;
            if (i > LAST) return true;
            for (int r = 0; r < 5; r++) inv().setItem(COL[r], show(seq[i + 4 - r]));
            click(0.9f + i * 0.02f);
            next = t + (i == LAST ? 30 : 1 + (i * i) / 150);
            i++;
            return false;
        }
    }

    /** 3. Karuzela: 8 nagród kręci się wokół skrzynki. */
    static final class Ring extends Anim {
        final CrateManager.Entry[] items = new CrateManager.Entry[8];
        static final int F = 36;
        int i = 0, next = 0, phase = 0;

        Ring(Ctx c) { super(c, true); }

        @Override void begin() {
            for (int k = 0; k < 8; k++) items[k] = mgr.roll();
            items[((-F) % 8 + 8) % 8] = winner;
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            inv().setItem(22, CrateGui.simple(Material.ENDER_CHEST, "§5§l?"));
        }

        @Override boolean step(int t) {
            if (t < next) return false;
            if (phase == 0) {
                if (i > F) { phase = 1; next = t + 10; return false; }
                for (int j = 0; j < 8; j++) inv().setItem(RING[j], show(items[((j - i) % 8 + 8) % 8]));
                decor(this, i, true);
                click(0.8f + i * 0.03f);
                next = t + 1 + (i * i) / 200;
                i++;
                return false;
            }
            if (phase == 1) {
                for (int s : RING) inv().setItem(s, Anim.pane(Material.LIME_STAINED_GLASS_PANE));
                inv().setItem(22, show(winner));
                win();
                phase = 2;
                next = t + 30;
                return false;
            }
            return true;
        }
    }

    /** 4. Podwójne koło: dwa pierścienie w przeciwne strony. */
    static final class Dual extends Anim {
        final CrateManager.Entry[] in = new CrateManager.Entry[8];
        final CrateManager.Entry[] out = new CrateManager.Entry[16];
        static final int F = 36;
        int i = 0, next = 0, phase = 0;

        Dual(Ctx c) { super(c, true); }

        @Override void begin() {
            for (int k = 0; k < 8; k++) in[k] = mgr.roll();
            for (int k = 0; k < 16; k++) out[k] = mgr.roll();
            in[((-F) % 8 + 8) % 8] = winner;
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            inv().setItem(22, CrateGui.simple(Material.ENDER_CHEST, "§5§l?"));
        }

        @Override boolean step(int t) {
            if (t < next) return false;
            if (phase == 0) {
                if (i > F) { phase = 1; next = t + 10; return false; }
                for (int j = 0; j < 8; j++) inv().setItem(RING[j], show(in[((j - i) % 8 + 8) % 8]));
                for (int j = 0; j < 16; j++) inv().setItem(OUTER[j], show(out[(j + 2 * i) % 16]));
                decor(this, i, false);
                click(0.8f + i * 0.03f);
                next = t + 1 + (i * i) / 200;
                i++;
                return false;
            }
            if (phase == 1) {
                for (int s : RING) inv().setItem(s, Anim.pane(Material.LIME_STAINED_GLASS_PANE));
                for (int s : OUTER) inv().setItem(s, Anim.pane(Material.PURPLE_STAINED_GLASS_PANE));
                inv().setItem(22, show(winner));
                win();
                phase = 2;
                next = t + 30;
                return false;
            }
            return true;
        }
    }

    /** 5. Jednoręki bandyta: trzy bębny stają po kolei. */
    static final class Slots extends Anim {
        static final int[] COLS = {3, 4, 5};
        static final int[] U = {22, 30, 38};
        final CrateManager.Entry[][] seq = new CrateManager.Entry[3][];
        final int[][] times = new int[3][];
        final int[] c = new int[3];
        int endAt = 0;

        Slots(Ctx cx) { super(cx, true); }

        @Override void begin() {
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            for (int r = 0; r < 3; r++) {
                seq[r] = new CrateManager.Entry[U[r] + 5];
                for (int q = 0; q < seq[r].length; q++) seq[r][q] = mgr.roll();
                seq[r][U[r] + 2] = (r == 1) ? winner : mgr.roll();
                times[r] = new int[U[r] + 1];
                int tt = 0;
                for (int q = 0; q <= U[r]; q++) {
                    times[r][q] = tt;
                    tt += 1 + (q * q * 6) / (U[r] * U[r]);
                }
            }
            inv().setItem(20, CrateGui.simple(Material.LIME_STAINED_GLASS_PANE, "§a►"));
            inv().setItem(24, CrateGui.simple(Material.LIME_STAINED_GLASS_PANE, "§a◄"));
        }

        @Override boolean step(int t) {
            boolean allDone = true;
            for (int r = 0; r < 3; r++) {
                if (c[r] > U[r]) continue;
                allDone = false;
                if (t >= times[r][c[r]]) {
                    for (int k = 0; k < 5; k++) inv().setItem(k * 9 + COLS[r], show(seq[r][c[r] + 4 - k]));
                    click(0.9f + c[r] * 0.02f);
                    if (c[r] == U[r]) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                    c[r]++;
                }
            }
            if (allDone) {
                if (endAt == 0) { endAt = t + 30; win(); }
                return t >= endAt;
            }
            return false;
        }
    }

    /** 6. Eliminacja: z pola migoczących nagród odpadają kolejne. */
    static final class Elim extends Anim {
        final int[] cells = new int[28];
        final boolean[] gone = new boolean[28];
        final List<Integer> order = new ArrayList<>();
        int wc, removed = 0, endAt = 0;

        Elim(Ctx c) { super(c, true); }

        @Override void begin() {
            int n = 0;
            for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) cells[n++] = r * 9 + c;
            wc = ThreadLocalRandom.current().nextInt(28);
            for (int k = 0; k < 28; k++) if (k != wc) order.add(k);
            Collections.shuffle(order);
            fillAll(Material.BLACK_STAINED_GLASS_PANE);
            flicker();
        }

        void flicker() {
            for (int k = 0; k < 28; k++) {
                if (gone[k]) continue;
                inv().setItem(cells[k], (k == wc && removed > 0) ? show(winner) : show(mgr.roll()));
            }
        }

        @Override boolean step(int t) {
            if (t < 40) {
                if (t % 2 == 0) { flicker(); click(0.8f + t * 0.01f); }
                return false;
            }
            int e = t - 40;
            if (removed < order.size()) {
                if (e % 2 == 0) {
                    int k = order.get(removed++);
                    gone[k] = true;
                    inv().setItem(cells[k], Anim.pane(Material.RED_STAINED_GLASS_PANE));
                    if (removed == 1) inv().setItem(cells[wc], show(winner));
                    click(0.6f + removed * 0.03f);
                } else if (e % 4 == 1) {
                    flicker();
                }
                return false;
            }
            if (endAt == 0) {
                endAt = t + 30;
                inv().setItem(cells[wc], show(winner));
                for (int k = 0; k < 28; k++) if (gone[k]) inv().setItem(cells[k], Anim.pane(Material.LIME_STAINED_GLASS_PANE));
                win();
            }
            return t >= endAt;
        }
    }

    // =====================================================
    // ANIMACJE W ŚWIECIE (ItemDisplay wokół skrzyni przed graczem)
    // =====================================================

    abstract static class WorldAnim extends Anim {
        static final int REVEAL = 40;
        Location center;
        ItemDisplay chest;
        final List<ItemDisplay> disp = new ArrayList<>();
        int wi;
        Location from;

        WorldAnim(Ctx c) { super(c, false); }

        abstract int count();
        abstract int motion();
        abstract void move(int t);

        ItemDisplay spawn(Location l, ItemStack item, float scale, Display.Billboard b) {
            return l.getWorld().spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setBillboard(b);
                d.setTeleportDuration(2);
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
            });
        }

        Location up(double y) {
            return center.clone().add(0, y, 0);
        }

        @Override void begin() {
            Location pl = p.getLocation();
            Vector dir = pl.getDirection().setY(0);
            if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
            dir.normalize().multiply(3.2);
            center = pl.clone().add(dir);
            center.setYaw(pl.getYaw() + 180f);
            center.setPitch(0f);
            Location cl = up(0.9);
            chest = spawn(cl, new ItemStack(Material.ENDER_CHEST), 1.7f, Display.Billboard.FIXED);
            int n = count();
            wi = ThreadLocalRandom.current().nextInt(n);
            for (int k = 0; k < n; k++) {
                CrateManager.Entry e = (k == wi) ? winner : mgr.roll();
                disp.add(spawn(cl, show(e), 0.6f, Display.Billboard.CENTER));
            }
            center.getWorld().playSound(center, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        }

        void put(int k, Location l) {
            ItemDisplay d = disp.get(k);
            if (d.isValid()) d.teleport(l);
            center.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
        }

        @Override boolean step(int t) {
            int m = motion();
            if (t < m) {
                move(t);
                if (chest.isValid()) chest.teleport(up(0.9 + 0.08 * Math.sin(t * 0.2)));
                if (t % 6 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f + 1.2f * t / m);
                }
                return false;
            }
            ItemDisplay win = disp.get(wi);
            if (!win.isValid()) return true;
            if (t == m) {
                for (int k = 0; k < disp.size(); k++) {
                    if (k == wi) continue;
                    ItemDisplay d = disp.get(k);
                    if (d.isValid()) {
                        center.getWorld().spawnParticle(Particle.PORTAL, d.getLocation(), 12, 0.2, 0.2, 0.2, 0.3);
                        d.remove();
                    }
                }
                from = win.getLocation();
                win.setBillboard(Display.Billboard.FIXED);
                win.setInterpolationDelay(0);
                win.setInterpolationDuration(2);
                center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
            }
            int k = t - m;
            if (k < REVEAL) {
                double f = Math.min(1.0, k / 16.0);
                f = 1 - (1 - f) * (1 - f);
                Location top = up(2.2);
                Location l = from.clone().add(top.clone().subtract(from).toVector().multiply(f));
                win.teleport(l);
                float s = (float) (0.6 + 0.9 * f);
                win.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (k * 0.25), 0, 1, 0),
                        new Vector3f(s, s, s), new AxisAngle4f()));
                center.getWorld().spawnParticle(Particle.END_ROD, l, 2, 0.3, 0.3, 0.3, 0.01);
                if (k == 16) {
                    center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    center.getWorld().spawnParticle(Particle.END_ROD, l, 50, 0.6, 0.6, 0.6, 0.08);
                }
                return false;
            }
            return true;
        }

        @Override void cleanup() {
            for (ItemDisplay d : disp) d.remove();
            if (chest != null) chest.remove();
        }
    }

    /** 7. Orbita: nagrody krążą wokół skrzyni, przyspieszają i zwalniają. */
    static final class Orbit extends WorldAnim {
        Orbit(Ctx c) { super(c); }
        @Override int count() { return 8; }
        @Override int motion() { return 110; }

        @Override void move(int t) {
            double k = t / (double) motion();
            double e = 1 - (1 - k) * (1 - k);
            double base = e * 8 * Math.PI;
            double r = 1.8 + 0.25 * Math.sin(t * 0.25);
            int n = count();
            for (int i = 0; i < n; i++) {
                double a = base + i * 2 * Math.PI / n;
                double y = 0.9 + 0.35 * Math.sin(t * 0.12 + i);
                put(i, up(y).add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
        }
    }

    /** 8. Spirala: nagrody wznoszą się helisą nad skrzynią. */
    static final class Helix extends WorldAnim {
        Helix(Ctx c) { super(c); }
        @Override int count() { return 10; }
        @Override int motion() { return 100; }

        @Override void move(int t) {
            double k = t / (double) motion();
            int n = count();
            for (int i = 0; i < n; i++) {
                double prog = Math.max(0, Math.min(1, k * 1.5 - i * 0.06));
                double y = 0.3 + prog * 2.6;
                double a = t * 0.22 + i * 0.9 + prog * 6;
                double r = 1.5 - 0.4 * prog;
                put(i, up(y).add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
        }
    }

    /** 9. Wybuch: nagrody rozlatują się na boki i wracają do skrzyni. */
    static final class Burst extends WorldAnim {
        Burst(Ctx c) { super(c); }
        @Override int count() { return 10; }
        @Override int motion() { return 80; }

        @Override void move(int t) {
            double k = Math.min(1.0, t / (double) motion());
            double out = Math.sin(Math.PI * k);
            int n = count();
            for (int i = 0; i < n; i++) {
                double a = i * 2 * Math.PI / n + k * 2;
                double r = 3.0 * out;
                double y = 0.9 + 1.4 * out * (0.5 + 0.5 * Math.sin(i * 1.7)) + 0.1 * Math.sin(t * 0.3 + i);
                put(i, up(y).add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
        }
    }
}
