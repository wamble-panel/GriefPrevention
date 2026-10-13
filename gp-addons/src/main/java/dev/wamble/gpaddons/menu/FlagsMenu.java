package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.flag.ClaimFlag;
import dev.wamble.gpaddons.flag.FlagService;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A claim's flags: what untrusted players may do in the top three rows, the environment in the fourth.
 * Click to switch a flag, shift-click to put it back to the default. Flags that are on shimmer.
 */
public final class FlagsMenu extends Menu {

    private static final int FIRST_PLAYER_SLOT = 9;
    private static final int FIRST_ENVIRONMENT_SLOT = 36;

    private final Claim claim;

    public FlagsMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.flags.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    @Override
    protected void draw() {
        set(HEADER, plugin.info().item(claim, viewer, false));
        int player = FIRST_PLAYER_SLOT;
        int environment = FIRST_ENVIRONMENT_SLOT;
        for (ClaimFlag flag : ClaimFlag.values()) {
            final int slot = flag.category() == ClaimFlag.Category.PLAYER ? player++ : environment++;
            if (slot >= bottom(0)) {
                continue;
            }
            set(slot, icon(flag), action(flag));
        }
    }

    @NotNull
    private ItemStack icon(@NotNull ClaimFlag flag) {
        final FlagService flags = plugin.flags();
        final boolean on = flags.get(claim, flag);
        final String category = flag.category().name().toLowerCase(Locale.ROOT);
        final List<Component> lore = new ArrayList<>();
        lore.add(Messages.plain(messages().get("menu.flags.category." + category)));
        lore.add(Component.empty());
        lore.addAll(messages().lore("flag." + flag.id() + ".description"));
        lore.add(Component.empty());
        lore.add(Messages.plain(messages().get("menu.flags.status",
                Messages.component("state", messages().get(on ? "flags.state-on" : "flags.state-off")))));
        lore.add(Messages.plain(messages().get("menu.flags.source."
                + flags.source(claim, flag).name().toLowerCase(Locale.ROOT))));
        lore.addAll(messages().lore(flags.canChange(viewer, claim, flag) ? "menu.flags.click" : "menu.locked"));
        final ItemStack item = Items.item(flag.icon(), messages().itemName("menu.flags.item.name",
                Messages.component("flag", messages().get("flag." + flag.id() + ".name"))), lore);
        return on ? Items.glint(item) : item;
    }

    @Nullable
    private Action action(@NotNull ClaimFlag flag) {
        if (!plugin.flags().canChange(viewer, claim, flag)) {
            return null;
        }
        return click -> {
            if (!plugin.flags().canChange(viewer, claim, flag)) {
                return;
            }
            plugin.flags().set(claim, flag, click.isShiftClick() ? null : !plugin.flags().get(claim, flag));
            refresh();
        };
    }

}
