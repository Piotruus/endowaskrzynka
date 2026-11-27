package pl.skrzynka;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SkrzynkaCommand implements CommandExecutor, TabCompleter {

    private static final String P = CrateGui.PREFIX;
    private static final List<String> ADMIN_SUBS =
            List.of("edytor", "dodaj", "szansa", "usun", "lista", "klucz", "reload");

    private final CrateManager mgr;
    private final CrateGui gui;
    private final MenuGui menu;

    public SkrzynkaCommand(CrateManager mgr, CrateGui gui, MenuGui menu) {
        this.mgr = mgr;
        this.gui = gui;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("pomoc")) {
            sendHelp(s);
            return true;
        }
        if (sub.equals("menu") || sub.equals("otworz")) {
            if (!(s instanceof Player p)) {
                s.sendMessage(P + "§cTa komenda jest tylko dla graczy.");
                return true;
            }
            if (!p.hasPermission("skrzynka.use")) {
                s.sendMessage(P + "§cNie masz uprawnień.");
                return true;
            }
            if (sub.equals("menu")) {
                menu.open(p);
            } else {
                gui.open(p);
            }
            return true;
        }
        if (sub.equals("animacje") || sub.equals("animacja") || sub.equals("historia")) {
            if (!(s instanceof Player p)) {
                s.sendMessage(P + "§cTa komenda jest tylko dla graczy.");
                return true;
            }
            if (sub.equals("historia")) {
                menu.openHistory(p);
            } else {
                menu.openAnimations(p);
            }
            return true;
        }
        if (!s.hasPermission("skrzynka.admin")) {
            s.sendMessage(P + "§cNie masz uprawnień do edycji skrzynki.");
            return true;
        }

        switch (sub) {
            case "edytor" -> {
                if (s instanceof Player p) {
                    menu.openEditor(p);
                } else {
                    s.sendMessage(P + "§cEdytor jest tylko dla graczy.");
                }
            }
            case "dodaj" -> add(s, a);
            case "szansa" -> setChance(s, a);
            case "usun" -> remove(s, a);
            case "lista" -> list(s);
            case "klucz" -> giveKey(s, a);
            case "reload" -> {
                mgr.load();
                s.sendMessage(P + "§aPrzeładowano nagrody (" + mgr.entries().size() + ").");
            }
            default -> sendHelp(s);
        }
        return true;
    }

    // ---------- pomoc z klikalnymi komendami ----------

    public static void sendHelp(CommandSender s) {
        s.sendMessage("§5§lSkrzynka §7- komendy §8(kliknij, aby wstawić do czatu)");
        line(s, "/skrzynka", "menu główne ze wszystkimi opcjami", "/skrzynka");
        line(s, "/skrzynka otworz", "od razu otwiera skrzynkę", "/skrzynka otworz");
        line(s, "/skrzynka animacje", "wybierz animację otwierania", "/skrzynka animacje");
        line(s, "/skrzynka historia", "ostatnie wygrane graczy", "/skrzynka historia");
        if (s.hasPermission("skrzynka.admin")) {
            line(s, "/skrzynka edytor", "edytor nagród w oknie", "/skrzynka edytor");
            line(s, "/skrzynka dodaj <szansa>", "dodaje przedmiot z ręki", "/skrzynka dodaj ");
            line(s, "/skrzynka szansa <id> <szansa>", "zmienia szansę", "/skrzynka szansa ");
            line(s, "/skrzynka usun <id>", "usuwa nagrodę", "/skrzynka usun ");
            line(s, "/skrzynka lista", "lista nagród i szans", "/skrzynka lista");
            line(s, "/skrzynka klucz <gracz> [ilość]", "daje klucz (Hak potykacza)", "/skrzynka klucz ");
            line(s, "/skrzynka reload", "wczytuje ponownie nagrody", "/skrzynka reload");
        }
    }

    private static void line(CommandSender s, String cmd, String desc, String suggest) {
        String text = "§e" + cmd + " §7- " + desc;
        if (s instanceof Player p) {
            BaseComponent[] parts = TextComponent.fromLegacyText(text);
            for (BaseComponent b : parts) {
                b.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest));
            }
            p.spigot().sendMessage(parts);
        } else {
            s.sendMessage(text);
        }
    }

    // ---------- komendy admina ----------

    private void add(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(P + "§cTylko gracz może dodać przedmiot z ręki.");
            return;
        }
        if (a.length < 2) {
            s.sendMessage(P + "§cUżycie: /skrzynka dodaj <szansa>");
            return;
        }
        Double chance = parsePositive(a[1]);
        if (chance == null) {
            s.sendMessage(P + "§cSzansa musi być liczbą większą od zera.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            s.sendMessage(P + "§cWeź przedmiot do ręki.");
            return;
        }
        CrateManager.Entry e = mgr.add(hand, chance);
        s.sendMessage(P + "§aDodano §f" + CrateManager.nameOf(e.item) + " §7(ID " + e.id + ") §ado szansą §e"
                + CrateManager.fmt(mgr.percent(e)) + "%");
    }

    private void setChance(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(P + "§cUżycie: /skrzynka szansa <id> <szansa>");
            return;
        }
        Integer id = parseInt(a[1]);
        Double chance = parsePositive(a[2]);
        if (id == null || chance == null) {
            s.sendMessage(P + "§cPodaj poprawne ID i szansę (liczba większa od zera).");
            return;
        }
        if (!mgr.setChance(id, chance)) {
            s.sendMessage(P + "§cNie ma nagrody o ID " + id + ".");
            return;
        }
        s.sendMessage(P + "§aUstawiono wagę §e" + CrateManager.fmt(chance) + " §a(szansa §e"
                + CrateManager.fmt(mgr.percent(mgr.get(id))) + "%§a).");
    }

    private void remove(CommandSender s, String[] a) {
        Integer id = a.length >= 2 ? parseInt(a[1]) : null;
        if (id == null) {
            s.sendMessage(P + "§cUżycie: /skrzynka usun <id>");
            return;
        }
        s.sendMessage(mgr.remove(id) ? P + "§aUsunięto nagrodę " + id + "." : P + "§cNie ma nagrody o ID " + id + ".");
    }

    private void list(CommandSender s) {
        if (mgr.isEmpty()) {
            s.sendMessage(P + "§7Brak nagród. Dodaj: §e/skrzynka dodaj <szansa>");
            return;
        }
        s.sendMessage("§5§lNagrody §7(suma wag: " + CrateManager.fmt(mgr.totalWeight()) + ")");
        for (CrateManager.Entry e : mgr.entries()) {
            s.sendMessage("§8#" + e.id + " §f" + e.item.getAmount() + "x " + CrateManager.nameOf(e.item)
                    + " §7waga §e" + CrateManager.fmt(e.chance) + " §7(§e" + CrateManager.fmt(mgr.percent(e)) + "%§7)");
        }
    }

    private void giveKey(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(P + "§cUżycie: /skrzynka klucz <gracz> [ilość]");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            s.sendMessage(P + "§cGracz " + a[1] + " nie jest online.");
            return;
        }
        int amount = 1;
        if (a.length >= 3) {
            Integer n = parseInt(a[2]);
            if (n == null || n < 1 || n > 64) {
                s.sendMessage(P + "§cIlość musi być od 1 do 64.");
                return;
            }
            amount = n;
        }
        mgr.giveKeys(target, amount);
        s.sendMessage(P + "§aDano §f" + amount + "x §6klucz §agraczowi §f" + target.getName() + "§a.");
        target.sendMessage(P + "§aOtrzymałeś §f" + amount + "x §6Klucz do skrzynki§a.");
    }

    private static Integer parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parsePositive(String v) {
        try {
            double d = Double.parseDouble(v.replace(',', '.'));
            return d > 0 && Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("skrzynka.admin");
        if (a.length == 1) {
            out.addAll(List.of("menu", "otworz", "animacje", "historia", "pomoc"));
            if (admin) {
                out.addAll(ADMIN_SUBS);
            }
        } else if (admin && a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "klucz" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "szansa", "usun" -> mgr.entries().forEach(e -> out.add(String.valueOf(e.id)));
                case "dodaj" -> out.addAll(List.of("1", "5", "10", "25", "50"));
                default -> { }
            }
        } else if (admin && a.length == 3 && a[0].equalsIgnoreCase("szansa")) {
            out.addAll(List.of("1", "5", "10", "25", "50"));
        }
        String cur = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
