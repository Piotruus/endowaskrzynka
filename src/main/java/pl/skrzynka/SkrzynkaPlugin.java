package pl.skrzynka;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkrzynkaPlugin extends JavaPlugin {

    private CrateManager manager;
    private CrateGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new CrateManager(this);
        manager.load();

        gui = new CrateGui(this, manager);
        MenuGui menu = new MenuGui(this, manager, gui);
        gui.setMenu(menu);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(menu, this);

        SkrzynkaCommand command = new SkrzynkaCommand(manager, gui, menu);
        PluginCommand pc = getCommand("skrzynka");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }
        getLogger().info("Wczytano " + manager.entries().size() + " nagród.");
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.finishAll();
        }
    }
}
