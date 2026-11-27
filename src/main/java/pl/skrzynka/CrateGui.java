package pl.skrzynka;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skrzynka 6x9 (54 sloty).
 * 0-44: nagrody (strony). 45-46 (lewy dolny róg): otwórz bez animacji.
 * 52-53 (prawy dolny róg): otwórz z animacją (lewy klik) lub podgląd (prawy klik).
 * 47 / 51: strony, 48: menu, 49: klucze, 50: wybór animacji.
 */
public final class CrateGui implements Listener {

    public static final String PREFIX = "§5[Skrzynka] §r";
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int[] NO_ANIM = {45, 46};
    private static final int[] ANIM = {52, 53};
    private static final int PREV = 47, MENU = 48, INFO = 49, ANIMS = 50, NEXT = 51;

    private final SkrzynkaPlugin plugin;
    private final CrateManager mgr;
    private final Map<UUID, Anim> running = new HashMap<>();
    private MenuGui menu;

    public CrateGui(SkrzynkaPlugin plugin, CrateManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    public void setMenu(MenuGui menu) {
        this.menu = menu;
    }

    // ---------- otwieranie okna ----------

    public void open(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.CRATE);
        h.inv = Bukkit.createInventory(h, SIZE, "§8Skrzynka");
        render(p, h);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1f);
    }

    private int pages() {
        return Math.max(1, (mgr.entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void render(Player p, GuiHolder h) {
        Inventory inv = h.inv;
        inv.clear();

        List<CrateManager.Entry> list = mgr.entries();
        int pages = pages();
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        boolean admin = p.hasPermission("skrzynka.admin");
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = h.page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            inv.setItem(i, preview(list.get(idx), admin));
        }

        ItemStack filler = simple(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < SIZE; s++) {
            inv.setItem(s, filler);
        }
        Animations.Type type = mgr.getAnimation(p.getUniqueId());
        for (int s : NO_ANIM) {
            inv.setItem(s, simple(Material.LIME_CONCRETE, "§a§lOtwórz §7(bez animacji)",
                    "§7Koszt: §e1 klucz", "", "§eKliknij, aby otworzyć od razu"));
        }
        for (int s : ANIM) {
            inv.setItem(s, simple(Material.PURPLE_CONCRETE, "§d§lOtwórz §7(z animacją)",
                    "§7Animacja: §f" + type.label, "§7Koszt: §e1 klucz", "",
                    "§eLewy klik: §7otwórz", "§bPrawy klik: §7podgląd bez klucza"));
        }
        if (h.page > 0) {
            inv.setItem(PREV, simple(Material.ARROW, "§7« Poprzednia strona §8(" + (h.page + 1) + "/" + pages + ")"));
        }
        if (h.page < pages - 1) {
            inv.setItem(NEXT, simple(Material.ARROW, "§7Następna strona » §8(" + (h.page + 1) + "/" + pages + ")"));
        }
        inv.setItem(MENU, simple(Material.COMPASS, "§bMenu główne", "§7Wróć do wszystkich opcji"));
        inv.setItem(INFO, simple(Material.TRIPWIRE_HOOK, "§6Twoje klucze: §f" + mgr.countKeys(p),
                "§7Nagród w skrzynce: §f" + list.size(), "§7Strona: §f" + (h.page + 1) + "/" + pages));
        inv.setItem(ANIMS, simple(type.icon, "§dAnimacja: §f" + type.label, "§7" + type.desc, "", "§eKliknij, aby zmienić"));
    }

    private ItemStack preview(CrateManager.Entry e, boolean admin) {
        ItemStack it = e.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Szansa: §e" + CrateManager.fmt(mgr.percent(e)) + "%");
            if (admin) {
                lore.add("§8ID: " + e.id + " | waga: " + CrateManager.fmt(e.chance));
            }
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    static ItemStack simple(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) {
                m.setLore(List.of(lore));
            }
            it.setItemMeta(m);
        }
        return it;
    }

    // ---------- zdarzenia ----------

    private static GuiHolder crateHolder(Inventory inv) {
        return inv.getHolder() instanceof GuiHolder h && h.type == GuiHolder.Type.CRATE ? h : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        GuiHolder h = crateHolder(e.getView().getTopInventory());
        if (h == null) {
            return;
        }
        e.setCancelled(true);
        if (h.preview || e.getClickedInventory() == null
                || !e.getClickedInventory().equals(e.getView().getTopInventory())) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p) || h.animating) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == PREV && h.page > 0) {
            h.page--;
            render(p, h);
            return;
        }
        if (slot == NEXT && h.page < pages() - 1) {
            h.page++;
            render(p, h);
            return;
        }
        if (slot == MENU) {
            Bukkit.getScheduler().runTask(plugin, () -> menu.open(p));
            return;
        }
        if (slot == ANIMS) {
            Bukkit.getScheduler().runTask(plugin, () -> menu.openAnimations(p));
            return;
        }
        for (int s : NO_ANIM) {
            if (s == slot) {
                openInstant(p, h);
                return;
            }
        }
        for (int s : ANIM) {
            if (s == slot) {
                openAnimated(p, h, e.getClick().isRightClick());
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (crateHolder(e.getView().getTopInventory()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && crateHolder(e.getInventory()) != null) {
            Anim a = running.get(p.getUniqueId());
            if (a != null && a.guiBased) {
                a.finish(false);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Anim a = running.get(e.getPlayer().getUniqueId());
        if (a != null) {
            a.finish(false);
        }
    }

    // ---------- otwieranie bez animacji ----------

    private void openInstant(Player p, GuiHolder h) {
        if (!checkReady(p)) {
            return;
        }
        CrateManager.Entry won = mgr.roll();
        mgr.give(p, won);
        announce(p, won);
        mgr.record(p, won);
        render(p, h);
    }

    // ---------- otwieranie z animacją / podgląd ----------

    private void openAnimated(Player p, GuiHolder h, boolean preview) {
        if (running.containsKey(p.getUniqueId())) {
            return;
        }
        if (preview) {
            if (mgr.isEmpty()) {
                p.sendMessage(PREFIX + "§cSkrzynka jest pusta. Admin musi dodać nagrody.");
                return;
            }
        } else if (!checkReady(p)) {
            return;
        }
        startAnim(p, h, mgr.getAnimation(p.getUniqueId()), preview);
    }

    /** Podgląd wybranej animacji (bez klucza i bez nagrody), np. z okna wyboru animacji. */
    public void startPreview(Player p, Animations.Type type) {
        if (running.containsKey(p.getUniqueId())) {
            return;
        }
        if (mgr.isEmpty()) {
            p.sendMessage(PREFIX + "§cSkrzynka jest pusta. Admin musi dodać nagrody.");
            return;
        }
        GuiHolder h = new GuiHolder(GuiHolder.Type.CRATE);
        h.preview = true;
        if (type.gui) {
            h.inv = Bukkit.createInventory(h, SIZE, "§8Podgląd: " + type.label);
            p.openInventory(h.inv);
        }
        startAnim(p, h, type, true);
    }

    private void startAnim(Player p, GuiHolder h, Animations.Type type, boolean preview) {
        CrateManager.Entry winner = mgr.roll();
        Anim a = Animations.create(type, new Ctx(this, plugin, mgr, p, h, winner, preview));
        h.animating = true;
        running.put(p.getUniqueId(), a);
        if (type.gui) {
            a.start();
        } else {
            // animacje w świecie: zamykamy okno, żeby gracz widział scenę
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                if (!a.done) {
                    a.start();
                }
            });
        }
    }

    private boolean checkReady(Player p) {
        if (mgr.isEmpty()) {
            p.sendMessage(PREFIX + "§cSkrzynka jest pusta. Admin musi dodać nagrody.");
            return false;
        }
        if (!mgr.takeKey(p)) {
            p.sendMessage(PREFIX + "§cPotrzebujesz klucza (Hak potykacza), aby otworzyć skrzynkę.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        return true;
    }

    private void announce(Player p, CrateManager.Entry won) {
        p.sendMessage(PREFIX + "§aWylosowano: §f" + won.item.getAmount() + "x " + CrateManager.nameOf(won.item));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    /** Wywoływane przez Anim po zakończeniu animacji. */
    void animationEnded(Anim a, boolean restore) {
        Player p = a.p;
        running.remove(p.getUniqueId());
        a.h.animating = false;
        if (a.preview) {
            p.sendMessage(PREFIX + "§bTo był tylko podgląd, klucz nie został zużyty.");
            if (restore && a.guiBased) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.getOpenInventory().getTopInventory() == a.h.inv) {
                        p.closeInventory();
                    }
                }, 25L);
            }
            return;
        }
        mgr.give(p, a.winner);
        announce(p, a.winner);
        mgr.record(p, a.winner);
        if (restore && a.guiBased) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory() == a.h.inv) {
                    render(p, a.h);
                }
            }, 40L);
        }
    }

    public void finishAll() {
        for (Anim a : new ArrayList<>(running.values())) {
            a.finish(false);
        }
    }
}
