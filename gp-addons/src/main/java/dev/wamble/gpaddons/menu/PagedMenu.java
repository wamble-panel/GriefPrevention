package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A menu listing entries in the four middle rows, 36 to a page, with page arrows in the bottom row.
 */
public abstract class PagedMenu<T> extends Menu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;

    protected int page;

    protected PagedMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @Nullable Menu back) {
        super(plugin, viewer, back);
    }

    @NotNull
    protected abstract List<T> entries();

    @NotNull
    protected abstract ItemStack icon(@NotNull T entry);

    /** What clicking an entry does, or null if nothing */
    @Nullable
    protected abstract Action action(@NotNull T entry);

    /** Shown in the middle when there are no entries */
    @Nullable
    protected ItemStack emptyIcon() {
        return null;
    }

    /** Buttons besides the entries and page arrows */
    protected void extras() {
    }

    protected final int pages() {
        return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected final void draw() {
        final List<T> entries = entries();
        final int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        final int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            final T entry = entries.get(start + i);
            set(FIRST + i, icon(entry), action(entry));
        }
        if (entries.isEmpty()) {
            final ItemStack empty = emptyIcon();
            if (empty != null) {
                set(22, empty);
            }
        }
        if (page > 0) {
            set(bottom(PREVIOUS), Items.item("arrow", messages().itemName("menu.previous.name"),
                    messages().lore("menu.previous.lore")), click -> {
                page--;
                open();
            });
        }
        if (page < pages - 1) {
            set(bottom(NEXT), Items.item("arrow", messages().itemName("menu.next.name"),
                    messages().lore("menu.next.lore")), click -> {
                page++;
                open();
            });
        }
        extras();
    }

}
