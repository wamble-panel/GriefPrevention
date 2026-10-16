package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A chest menu: a black border along the top and bottom rows, content in between, and a back button in the
 * bottom-left corner when there's a menu to go back to. Every click is cancelled; buttons run actions.
 */
public abstract class Menu implements InventoryHolder {

    /** The middle of the top row, for a heading item */
    protected static final int HEADER = 4;

    /** Columns in the bottom row (use with {@link #bottom(int)}) */
    protected static final int BACK = 0;
    protected static final int PREVIOUS = 3;
    protected static final int CENTER = 4;
    protected static final int NEXT = 5;
    protected static final int CORNER = 8;

    private static final String BORDER = "black_stained_glass_pane";

    /** What a button does when clicked */
    @FunctionalInterface
    public interface Action {
        void run(@NotNull ClickType click);
    }

    protected final GPAddons plugin;
    protected final Player viewer;
    @Nullable
    protected final Menu back;
    private final Map<Integer, Action> actions = new HashMap<>();
    @Nullable
    private Inventory inventory;

    protected Menu(@NotNull GPAddons plugin, @NotNull Player viewer, @Nullable Menu back) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.back = back;
    }

    protected int rows() {
        return 6;
    }

    @NotNull
    protected abstract Component title();

    /** Put the buttons in place; called on every open and refresh */
    protected abstract void draw();

    /** Whether the menu still makes sense, e.g. its claim still exists. If not, it closes with a message */
    protected boolean valid() {
        return true;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = plugin.getServer().createInventory(this, rows() * 9, title());
        }
        return inventory;
    }

    /** Open (or re-open, with a fresh title) this menu */
    public final void open() {
        if (!valid()) {
            viewer.closeInventory();
            messages().send(viewer, "general.claim-gone");
            return;
        }
        inventory = plugin.getServer().createInventory(this, rows() * 9, title());
        render();
        viewer.openInventory(inventory);
    }

    /** Redraw the buttons without re-opening (the title stays the same) */
    public final void refresh() {
        if (!valid()) {
            viewer.closeInventory();
            messages().send(viewer, "general.claim-gone");
            return;
        }
        if (inventory == null) {
            open();
            return;
        }
        render();
    }

    private void render() {
        final Inventory inv = getInventory();
        actions.clear();
        inv.clear();
        final ItemStack pane = Items.pane(BORDER);
        for (int column = 0; column < 9; column++) {
            inv.setItem(column, pane);
            inv.setItem(bottom(column), pane);
        }
        if (back != null) {
            set(bottom(BACK), Items.item("arrow", messages().itemName("menu.back.name"),
                    messages().lore("menu.back.lore")), click -> back.open());
        }
        draw();
    }

    final void click(int slot, @NotNull ClickType click) {
        final Action action = actions.get(slot);
        if (action == null) {
            return;
        }
        if (!valid()) {
            viewer.closeInventory();
            messages().send(viewer, "general.claim-gone");
            return;
        }
        viewer.playSound(viewer.getLocation(), "minecraft:ui.button.click", 0.4F, 1.2F);
        action.run(click);
    }

    protected final void set(int slot, @NotNull ItemStack item) {
        getInventory().setItem(slot, item);
        actions.remove(slot);
    }

    protected final void set(int slot, @NotNull ItemStack item, @Nullable Action action) {
        getInventory().setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    /** A slot in the bottom row */
    protected final int bottom(int column) {
        return (rows() - 1) * 9 + column;
    }

    @NotNull
    protected final Messages messages() {
        return plugin.messages();
    }

    @NotNull
    public final Player viewer() {
        return viewer;
    }

}
