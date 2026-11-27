package pl.skrzynka;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Znacznik okien pluginu; pozwala rozpoznać, które okno jest otwarte. */
public final class GuiHolder implements InventoryHolder {

    public enum Type { MENU, CRATE, EDITOR, KEYS, ANIMS, HISTORY }

    public final Type type;
    public Inventory inv;
    public int page;
    public boolean animating;
    public boolean preview;
    public final List<UUID> targets = new ArrayList<>();

    public GuiHolder(Type type) {
        this.type = type;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
