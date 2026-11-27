package pl.skrzynka;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/** Bazowa klasa animacji otwierania. Działa co tick, aż step() zwróci true. */
abstract class Anim implements Runnable {

    final SkrzynkaPlugin plugin;
    final CrateGui gui;
    final CrateManager mgr;
    final Player p;
    final GuiHolder h;
    final CrateManager.Entry winner;
    final boolean preview;
    final boolean guiBased;
    int tick;
    BukkitTask task;
    boolean done;

    Anim(Ctx c, boolean guiBased) {
        this.plugin = c.plugin();
        this.gui = c.gui();
        this.mgr = c.mgr();
        this.p = c.p();
        this.h = c.h();
        this.winner = c.winner();
        this.preview = c.preview();
        this.guiBased = guiBased;
    }

    /** Przygotowanie (okno lub obiekty w świecie). */
    abstract void begin();

    /** Jeden tick animacji. Zwróć true, gdy animacja się skończyła. */
    abstract boolean step(int t);

    /** Sprzątanie (np. usunięcie obiektów ze świata). */
    void cleanup() { }

    final void start() {
        begin();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public final void run() {
        if (done) {
            return;
        }
        if (!p.isOnline()) {
            finish(false);
            return;
        }
        try {
            if (step(tick++)) {
                finish(true);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Błąd animacji: " + ex);
            finish(false);
        }
    }

    final void finish(boolean restore) {
        if (done) {
            return;
        }
        done = true;
        if (task != null) {
            task.cancel();
        }
        cleanup();
        gui.animationEnded(this, restore);
    }

    // ---------- pomocnicze ----------

    Inventory inv() {
        return h.inv;
    }

    void fillAll(Material m) {
        ItemStack g = pane(m);
        for (int s = 0; s < inv().getSize(); s++) {
            inv().setItem(s, g);
        }
    }

    static ItemStack pane(Material m) {
        return CrateGui.simple(m, " ");
    }

    static ItemStack show(CrateManager.Entry e) {
        ItemStack it = e.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setLore(null);
            it.setItemMeta(m);
        }
        return it;
    }

    void click(float pitch) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, Math.max(0.5f, Math.min(2f, pitch)));
    }

    void win() {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }
}

record Ctx(CrateGui gui, SkrzynkaPlugin plugin, CrateManager mgr, Player p, GuiHolder h,
           CrateManager.Entry winner, boolean preview) { }
