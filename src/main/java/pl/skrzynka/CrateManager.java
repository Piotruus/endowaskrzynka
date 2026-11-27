package pl.skrzynka;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Przechowuje nagrody, ich szanse oraz obsługuje klucze. */
public final class CrateManager {

    public static final class Entry {
        public final int id;
        public final ItemStack item;
        public double chance;

        Entry(int id, ItemStack item, double chance) {
            this.id = id;
            this.item = item;
            this.chance = chance;
        }
    }

    private final SkrzynkaPlugin plugin;
    private final File file;
    private final NamespacedKey keyTag;
    private final List<Entry> entries = new ArrayList<>();
    private int nextId = 1;
    private final File playersFile;
    private final File historyFile;
    private final Map<UUID, Animations.Type> selected = new HashMap<>();
    private final List<Hist> history = new ArrayList<>();

    /** Wpis w historii losowań. */
    public static final class Hist {
        public final String player;
        public final ItemStack item;
        public final double percent;
        public final long time;

        Hist(String player, ItemStack item, double percent, long time) {
            this.player = player;
            this.item = item;
            this.percent = percent;
            this.time = time;
        }
    }

    public CrateManager(SkrzynkaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nagrody.yml");
        this.keyTag = new NamespacedKey(plugin, "klucz_skrzynki");
        this.playersFile = new File(plugin.getDataFolder(), "gracze.yml");
        this.historyFile = new File(plugin.getDataFolder(), "historia.yml");
    }

    // ---------- zapis i odczyt ----------

    public void load() {
        plugin.reloadConfig();
        loadPlayers();
        loadHistory();
        entries.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("items");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(k);
                ItemStack it = sec.getItemStack(k + ".item");
                double ch = sec.getDouble(k + ".chance", 1.0);
                if (it == null || ch <= 0) {
                    continue;
                }
                entries.add(new Entry(id, it, ch));
                nextId = Math.max(nextId, id + 1);
            } catch (NumberFormatException ignored) {
                // pomijamy błędne wpisy
            }
        }
        entries.sort(Comparator.comparingInt(e -> e.id));
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Entry e : entries) {
            cfg.set("items." + e.id + ".item", e.item);
            cfg.set("items." + e.id + ".chance", e.chance);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udało się zapisać nagrody: " + ex.getMessage());
        }
    }

    // ---------- edycja zawartości ----------

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Entry add(ItemStack item, double chance) {
        Entry e = new Entry(nextId++, item.clone(), chance);
        entries.add(e);
        save();
        return e;
    }

    public Entry get(int id) {
        for (Entry e : entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    public boolean setChance(int id, double chance) {
        Entry e = get(id);
        if (e == null) {
            return false;
        }
        e.chance = chance;
        save();
        return true;
    }

    public boolean remove(int id) {
        Entry e = get(id);
        if (e == null) {
            return false;
        }
        entries.remove(e);
        save();
        return true;
    }

    public double totalWeight() {
        double sum = 0;
        for (Entry e : entries) {
            sum += e.chance;
        }
        return sum;
    }

    public double percent(Entry e) {
        double total = totalWeight();
        return total <= 0 ? 0 : e.chance / total * 100.0;
    }

    /** Losuje nagrodę z uwzględnieniem szans (wag). */
    public Entry roll() {
        double total = totalWeight();
        if (entries.isEmpty() || total <= 0) {
            return null;
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double acc = 0;
        for (Entry e : entries) {
            acc += e.chance;
            if (r < acc) {
                return e;
            }
        }
        return entries.get(entries.size() - 1);
    }

    // ---------- nagrody dla gracza ----------

    public void give(Player p, Entry e) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(e.item.clone());
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------- klucze (Hak potykacza) ----------

    public ItemStack createKey(int amount) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§6§lKlucz do skrzynki");
            m.setLore(List.of("§7Użyj w skrzynce (/skrzynka),", "§7aby ją otworzyć."));
            m.getPersistentDataContainer().set(keyTag, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    public void giveKeys(Player p, int amount) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(createKey(amount));
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    public boolean isKey(ItemStack it) {
        if (it == null || it.getType() != Material.TRIPWIRE_HOOK || !it.hasItemMeta()) {
            return false;
        }
        return it.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.BYTE);
    }

    public int countKeys(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isKey(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** Zabiera jeden klucz graczowi. Zwraca false, jeśli go nie ma. */
    public boolean takeKey(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (isKey(it)) {
                if (it.getAmount() <= 1) {
                    p.getInventory().setItem(i, null);
                } else {
                    it.setAmount(it.getAmount() - 1);
                }
                return true;
            }
        }
        return false;
    }

    // ---------- wybrana animacja gracza ----------

    public Animations.Type getAnimation(UUID id) {
        Animations.Type t = selected.get(id);
        if (t != null) {
            return t;
        }
        try {
            return Animations.Type.valueOf(plugin.getConfig().getString("default-animation", "ROULETTE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Animations.Type.ROULETTE;
        }
    }

    public void setAnimation(UUID id, Animations.Type t) {
        selected.put(id, t);
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, Animations.Type> e : selected.entrySet()) {
            c.set("animation." + e.getKey(), e.getValue().name());
        }
        try {
            plugin.getDataFolder().mkdirs();
            c.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udało się zapisać gracze.yml: " + ex.getMessage());
        }
    }

    private void loadPlayers() {
        selected.clear();
        if (!playersFile.exists()) {
            return;
        }
        ConfigurationSection s = YamlConfiguration.loadConfiguration(playersFile).getConfigurationSection("animation");
        if (s == null) {
            return;
        }
        for (String k : s.getKeys(false)) {
            try {
                selected.put(UUID.fromString(k), Animations.Type.valueOf(s.getString(k, "ROULETTE")));
            } catch (IllegalArgumentException ignored) {
                // pomijamy błędne wpisy
            }
        }
    }

    // ---------- historia losowań i ogłoszenia ----------

    public List<Hist> history() {
        return Collections.unmodifiableList(history);
    }

    /** Zapisuje losowanie w historii i ogłasza rzadkie nagrody na czacie. */
    public void record(Player p, Entry e) {
        FileConfiguration cfg = plugin.getConfig();
        double pct = percent(e);
        history.add(0, new Hist(p.getName(), e.item.clone(), pct, System.currentTimeMillis()));
        int max = Math.max(5, cfg.getInt("history-size", 50));
        while (history.size() > max) {
            history.remove(history.size() - 1);
        }
        saveHistory();
        if (cfg.getBoolean("broadcast-rare", true) && pct <= cfg.getDouble("rare-threshold-percent", 5.0)) {
            Bukkit.broadcastMessage("§5[Skrzynka] §6" + p.getName() + " §7wylosował rzadką nagrodę: §f"
                    + e.item.getAmount() + "x " + nameOf(e.item) + " §8(" + fmt(pct) + "%)");
        }
    }

    private void saveHistory() {
        YamlConfiguration c = new YamlConfiguration();
        for (int i = 0; i < history.size(); i++) {
            Hist h = history.get(i);
            c.set("h." + i + ".player", h.player);
            c.set("h." + i + ".item", h.item);
            c.set("h." + i + ".percent", h.percent);
            c.set("h." + i + ".time", h.time);
        }
        try {
            plugin.getDataFolder().mkdirs();
            c.save(historyFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udało się zapisać historia.yml: " + ex.getMessage());
        }
    }

    private void loadHistory() {
        history.clear();
        if (!historyFile.exists()) {
            return;
        }
        ConfigurationSection s = YamlConfiguration.loadConfiguration(historyFile).getConfigurationSection("h");
        if (s == null) {
            return;
        }
        for (int i = 0; s.isConfigurationSection(String.valueOf(i)); i++) {
            ItemStack it = s.getItemStack(i + ".item");
            if (it != null) {
                history.add(new Hist(s.getString(i + ".player", "?"), it, s.getDouble(i + ".percent"), s.getLong(i + ".time")));
            }
        }
    }

    // ---------- pomocnicze ----------

    public static String nameOf(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            return m.getDisplayName();
        }
        String[] parts = it.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    public static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
