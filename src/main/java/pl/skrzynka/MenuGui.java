package pl.skrzynka;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menu główne, wybór animacji, historia, edytor nagród i dawanie kluczy. */
public final class MenuGui implements Listener {

    private static final String P = CrateGui.PREFIX;
    private static final int PER_PAGE = 45;
    private static final int BACK = 45, PREV = 47, INFO = 49, NEXT = 51;
    private static final int[] ANIM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};

    private final SkrzynkaPlugin plugin;
    private final CrateManager mgr;
    private final CrateGui crate;
    /** Potwierdzenie usuwania: gracz -> {id, czas}. */
    private final Map<UUID, long[]> pendingRemove = new HashMap<>();

    public MenuGui(SkrzynkaPlugin plugin, CrateManager mgr, CrateGui crate) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.crate = crate;
    }

    private static ItemStack simple(Material m, String n, String... l) {
        return CrateGui.simple(m, n, l);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static void fill(Inventory inv) {
        ItemStack f = simple(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, f);
            }
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) {
                out.add("§7" + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(w);
        }
        if (line.length() > 0) {
            out.add("§7" + line);
        }
        return out;
    }

    // =====================================================
    // MENU GŁÓWNE
    // =====================================================

    public void open(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.MENU);
        h.inv = Bukkit.createInventory(h, 36, "§8Skrzynka: menu");
        renderMenu(p, h);
        p.openInventory(h.inv);
    }

    private void renderMenu(Player p, GuiHolder h) {
        Inventory inv = h.inv;
        inv.clear();
        Animations.Type type = mgr.getAnimation(p.getUniqueId());
        inv.setItem(4, simple(Material.TRIPWIRE_HOOK, "§eTwoje klucze: §f" + mgr.countKeys(p),
                "§7Klucz to Hak potykacza", "§7z nazwą „Klucz do skrzynki”."));
        inv.setItem(10, simple(Material.CHEST, "§6§lOtwórz skrzynkę",
                "§7Nagród: §f" + mgr.entries().size(), "", "§eKliknij, aby przejść do skrzynki"));
        inv.setItem(12, simple(type.icon, "§d§lAnimacja: §f" + type.label,
                "§7Wybierz jedną z " + Animations.Type.values().length + " animacji", "", "§eKliknij, aby zmienić"));
        inv.setItem(14, simple(Material.CLOCK, "§b§lHistoria losowań",
                "§7Ostatnie wygrane graczy", "§7Zapisanych: §f" + mgr.history().size()));
        inv.setItem(16, simple(Material.BOOK, "§b§lPomoc", "§7Pokaż listę komend na czacie"));

        if (p.hasPermission("skrzynka.admin")) {
            inv.setItem(20, simple(Material.ANVIL, "§c§lEdytor nagród",
                    "§7Zmieniaj szanse, usuwaj i dodawaj", "§7nagrody w oknie.", "", "§eKliknij, aby otworzyć"));
            inv.setItem(22, simple(Material.TRIPWIRE_HOOK, "§6Daj sobie klucz",
                    "§7Lewy: §e1 §7| Shift+lewy: §e16 §7| Prawy: §e64"));
            inv.setItem(24, simple(Material.PLAYER_HEAD, "§6Daj klucz graczowi",
                    "§7Wybierz gracza z listy online"));
            inv.setItem(31, simple(Material.REDSTONE, "§cPrzeładuj nagrody", "§7Wczytaj ponownie pliki"));
        }
        fill(inv);
    }

    // =====================================================
    // WYBÓR ANIMACJI
    // =====================================================

    public void openAnimations(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.ANIMS);
        h.inv = Bukkit.createInventory(h, 36, "§8Wybierz animację");
        renderAnims(p, h);
        p.openInventory(h.inv);
    }

    private void renderAnims(Player p, GuiHolder h) {
        Inventory inv = h.inv;
        inv.clear();
        Animations.Type cur = mgr.getAnimation(p.getUniqueId());
        Animations.Type[] all = Animations.Type.values();
        for (int i = 0; i < all.length && i < ANIM_SLOTS.length; i++) {
            Animations.Type t = all[i];
            boolean sel = t == cur;
            List<String> lore = new ArrayList<>(wrap(t.desc, 36));
            lore.add("");
            lore.add(t.gui ? "§8Typ: w oknie" : "§8Typ: w świecie (widać ją wokół skrzyni)");
            lore.add("");
            lore.add(sel ? "§a✔ Wybrana" : "§eLewy klik: §7wybierz");
            lore.add("§bPrawy klik: §7podgląd bez klucza");
            ItemStack it = new ItemStack(t.icon);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName((sel ? "§a§l" : "§d§l") + t.label);
                m.setLore(lore);
                it.setItemMeta(m);
            }
            inv.setItem(ANIM_SLOTS[i], it);
        }
        inv.setItem(4, simple(Material.FIREWORK_STAR, "§d§lWybierz animację otwierania",
                "§7Twój wybór zapisuje się na stałe."));
        inv.setItem(31, simple(Material.ARROW, "§7« Wstecz do menu"));
        fill(inv);
    }

    private void animClick(int slot, ClickType ct, Player p, GuiHolder h) {
        if (slot == 31) {
            later(() -> open(p));
            return;
        }
        Animations.Type[] all = Animations.Type.values();
        for (int i = 0; i < all.length && i < ANIM_SLOTS.length; i++) {
            if (ANIM_SLOTS[i] != slot) {
                continue;
            }
            Animations.Type t = all[i];
            if (ct.isRightClick()) {
                later(() -> crate.startPreview(p, t));
            } else if (ct.isLeftClick()) {
                mgr.setAnimation(p.getUniqueId(), t);
                p.sendMessage(P + "§aWybrano animację: §f" + t.label);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                renderAnims(p, h);
            }
            return;
        }
    }

    // =====================================================
    // HISTORIA LOSOWAŃ
    // =====================================================

    public void openHistory(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.HISTORY);
        h.inv = Bukkit.createInventory(h, 54, "§8Historia losowań");
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm");
        List<CrateManager.Hist> list = mgr.history();
        for (int i = 0; i < Math.min(PER_PAGE, list.size()); i++) {
            CrateManager.Hist e = list.get(i);
            ItemStack it = e.item.clone();
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                List<String> lore = m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("§7Gracz: §f" + e.player);
                lore.add("§7Szansa: §e" + CrateManager.fmt(e.percent) + "%");
                lore.add("§7Kiedy: §f" + fmt.format(new Date(e.time)));
                m.setLore(lore);
                it.setItemMeta(m);
            }
            h.inv.setItem(i, it);
        }
        h.inv.setItem(BACK, simple(Material.ARROW, "§7« Wstecz do menu"));
        if (list.isEmpty()) {
            h.inv.setItem(22, simple(Material.BARRIER, "§7Brak wpisów", "§7Nikt jeszcze nic nie wylosował."));
        }
        fill(h.inv);
        p.openInventory(h.inv);
    }

    // =====================================================
    // EDYTOR NAGRÓD
    // =====================================================

    public void openEditor(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.EDITOR);
        h.inv = Bukkit.createInventory(h, 54, "§8Edytor nagród");
        renderEditor(h);
        p.openInventory(h.inv);
    }

    private int editorPages() {
        return Math.max(1, (mgr.entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void renderEditor(GuiHolder h) {
        Inventory inv = h.inv;
        inv.clear();
        List<CrateManager.Entry> list = mgr.entries();
        int pages = editorPages();
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = h.page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            inv.setItem(i, editorItem(list.get(idx)));
        }
        inv.setItem(BACK, simple(Material.ARROW, "§7« Wstecz do menu"));
        if (h.page > 0) {
            inv.setItem(PREV, simple(Material.ARROW, "§7« Poprzednia strona §8(" + (h.page + 1) + "/" + pages + ")"));
        }
        if (h.page < pages - 1) {
            inv.setItem(NEXT, simple(Material.ARROW, "§7Następna strona » §8(" + (h.page + 1) + "/" + pages + ")"));
        }
        inv.setItem(INFO, simple(Material.WRITABLE_BOOK, "§e§lJak edytować",
                "§aLewy §7+1 §8| §cPrawy §7-1",
                "§aShift+lewy §7+10 §8| §cShift+prawy §7-10",
                "§cQ §7- usuń nagrodę (naciśnij 2 razy)",
                "",
                "§7Kliknij przedmiot w swoim ekwipunku,",
                "§7aby dodać go jako nagrodę (waga 10).",
                "§8Suma wag: " + CrateManager.fmt(mgr.totalWeight())));
        fill(inv);
    }

    private ItemStack editorItem(CrateManager.Entry e) {
        ItemStack it = e.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§8ID: " + e.id);
            lore.add("§7Waga: §e" + CrateManager.fmt(e.chance) + " §7(§e" + CrateManager.fmt(mgr.percent(e)) + "%§7)");
            lore.add("§aLewy +1 §8| §cPrawy -1 §8| §aShift ±10 §8| §cQ usuń");
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    private void editorClick(InventoryClickEvent e, GuiHolder h, Player p) {
        boolean top = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());

        if (!top) {
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) {
                return;
            }
            if (mgr.isKey(cur)) {
                p.sendMessage(P + "§cKlucza nie można dodać jako nagrody.");
                return;
            }
            CrateManager.Entry added = mgr.add(cur, 10);
            p.sendMessage(P + "§aDodano §f" + CrateManager.nameOf(added.item) + " §7(ID " + added.id + ", waga 10).");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            renderEditor(h);
            return;
        }

        int slot = e.getRawSlot();
        if (slot == BACK) {
            later(() -> open(p));
            return;
        }
        if (slot == PREV && h.page > 0) {
            h.page--;
            renderEditor(h);
            return;
        }
        if (slot == NEXT && h.page < editorPages() - 1) {
            h.page++;
            renderEditor(h);
            return;
        }
        if (slot >= PER_PAGE) {
            return;
        }
        int idx = h.page * PER_PAGE + slot;
        if (idx >= mgr.entries().size()) {
            return;
        }
        CrateManager.Entry entry = mgr.entries().get(idx);
        ClickType ct = e.getClick();

        if (ct == ClickType.DROP || ct == ClickType.CONTROL_DROP) {
            long[] pend = pendingRemove.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (pend != null && pend[0] == entry.id && now - pend[1] < 5000) {
                pendingRemove.remove(p.getUniqueId());
                mgr.remove(entry.id);
                p.sendMessage(P + "§cUsunięto nagrodę §f" + CrateManager.nameOf(entry.item) + "§c.");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                renderEditor(h);
            } else {
                pendingRemove.put(p.getUniqueId(), new long[]{entry.id, now});
                p.sendMessage(P + "§eNaciśnij §cQ§e jeszcze raz w ciągu 5 s, aby usunąć §f"
                        + CrateManager.nameOf(entry.item) + "§e.");
            }
            return;
        }

        double delta;
        switch (ct) {
            case LEFT -> delta = 1;
            case RIGHT -> delta = -1;
            case SHIFT_LEFT -> delta = 10;
            case SHIFT_RIGHT -> delta = -10;
            default -> {
                return;
            }
        }
        double nw = Math.max(0.1, Math.round((entry.chance + delta) * 100.0) / 100.0);
        mgr.setChance(entry.id, nw);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, delta > 0 ? 1.3f : 0.8f);
        renderEditor(h);
    }

    // =====================================================
    // DAWANIE KLUCZY GRACZOM
    // =====================================================

    private void openKeys(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.KEYS);
        h.inv = Bukkit.createInventory(h, 54, "§8Daj klucz graczowi");
        int i = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (i >= PER_PAGE) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(t);
                sm.setDisplayName("§f" + t.getName());
                sm.setLore(List.of("§7Lewy: §e1 §7| Shift+lewy: §e8 §7| Prawy: §e64",
                        "§8Ma kluczy: " + mgr.countKeys(t)));
                head.setItemMeta(sm);
            }
            h.inv.setItem(i++, head);
            h.targets.add(t.getUniqueId());
        }
        h.inv.setItem(BACK, simple(Material.ARROW, "§7« Wstecz do menu"));
        fill(h.inv);
        p.openInventory(h.inv);
    }

    private void keysClick(InventoryClickEvent e, GuiHolder h, Player p) {
        int slot = e.getRawSlot();
        if (slot == BACK) {
            later(() -> open(p));
            return;
        }
        if (slot < 0 || slot >= h.targets.size()) {
            return;
        }
        int amount = switch (e.getClick()) {
            case LEFT -> 1;
            case SHIFT_LEFT -> 8;
            case RIGHT -> 64;
            default -> 0;
        };
        if (amount == 0) {
            return;
        }
        Player t = Bukkit.getPlayer(h.targets.get(slot));
        if (t == null) {
            p.sendMessage(P + "§cTen gracz wyszedł z serwera.");
            return;
        }
        mgr.giveKeys(t, amount);
        p.sendMessage(P + "§aDano §f" + amount + "x §6klucz §agraczowi §f" + t.getName() + "§a.");
        if (!t.equals(p)) {
            t.sendMessage(P + "§aOtrzymałeś §f" + amount + "x §6Klucz do skrzynki§a.");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    // =====================================================
    // ZDARZENIA
    // =====================================================

    private static GuiHolder holder(Inventory inv) {
        return inv.getHolder() instanceof GuiHolder h && h.type != GuiHolder.Type.CRATE ? h : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        GuiHolder h = holder(e.getView().getTopInventory());
        if (h == null) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean admin = p.hasPermission("skrzynka.admin");
        boolean inTop = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
        switch (h.type) {
            case MENU -> {
                if (inTop) {
                    menuClick(e.getRawSlot(), e.getClick(), p, h, admin);
                }
            }
            case ANIMS -> {
                if (inTop) {
                    animClick(e.getRawSlot(), e.getClick(), p, h);
                }
            }
            case HISTORY -> {
                if (inTop && e.getRawSlot() == BACK) {
                    later(() -> open(p));
                }
            }
            case EDITOR -> {
                if (!admin) {
                    p.closeInventory();
                    return;
                }
                editorClick(e, h, p);
            }
            case KEYS -> {
                if (!admin) {
                    p.closeInventory();
                    return;
                }
                if (inTop) {
                    keysClick(e, h, p);
                }
            }
            default -> { }
        }
    }

    private void menuClick(int slot, ClickType ct, Player p, GuiHolder h, boolean admin) {
        switch (slot) {
            case 10 -> later(() -> crate.open(p));
            case 12 -> later(() -> openAnimations(p));
            case 14 -> later(() -> openHistory(p));
            case 16 -> {
                p.closeInventory();
                SkrzynkaCommand.sendHelp(p);
            }
            case 20 -> {
                if (admin) {
                    later(() -> openEditor(p));
                }
            }
            case 22 -> {
                if (!admin) {
                    return;
                }
                int amount = switch (ct) {
                    case LEFT -> 1;
                    case SHIFT_LEFT -> 16;
                    case RIGHT -> 64;
                    default -> 0;
                };
                if (amount > 0) {
                    mgr.giveKeys(p, amount);
                    p.sendMessage(P + "§aDostałeś §f" + amount + "x §6Klucz do skrzynki§a.");
                    renderMenu(p, h);
                }
            }
            case 24 -> {
                if (admin) {
                    later(() -> openKeys(p));
                }
            }
            case 31 -> {
                if (admin) {
                    mgr.load();
                    p.sendMessage(P + "§aPrzeładowano nagrody (" + mgr.entries().size() + ").");
                    renderMenu(p, h);
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (holder(e.getView().getTopInventory()) != null) {
            e.setCancelled(true);
        }
    }
}
