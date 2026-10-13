package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Players banned from a claim (and so from its subdivisions). Click a head to unban.
 */
public final class BansMenu extends PagedMenu<Map.Entry<UUID, ClaimData.Ban>> {

    private final Claim claim;

    public BansMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.bans.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    private boolean canModerate() {
        return plugin.entry().canModerate(viewer, claim, "gpaddons.ban");
    }

    @Override
    protected @NotNull List<Map.Entry<UUID, ClaimData.Ban>> entries() {
        final ClaimData data = plugin.store().get(claim);
        return data == null ? List.of() : List.copyOf(data.bans().entrySet());
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Map.Entry<UUID, ClaimData.Ban> entry) {
        final ClaimData.Ban ban = entry.getValue();
        final String by = ban.by() == null ? "?" : Gp.name(ban.by());
        final List<Component> lore = new ArrayList<>(messages().lore("menu.bans.item.lore",
                Messages.text("by", by), Messages.text("date", ban.at() > 0 ? plugin.info().date(ban.at()) : "?")));
        lore.addAll(messages().lore(canModerate() ? "menu.bans.item.click" : "menu.locked"));
        final OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
        return Items.head(player, messages().itemName("menu.bans.item.name",
                Messages.text("player", Gp.name(entry.getKey()))), lore);
    }

    @Override
    protected @Nullable Action action(@NotNull Map.Entry<UUID, ClaimData.Ban> entry) {
        if (!canModerate()) {
            return null;
        }
        return click -> {
            plugin.actions().unban(viewer, claim, entry.getKey());
            open();
        };
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        return Items.item("lily_of_the_valley", messages().itemName("menu.bans.empty.name"),
                messages().lore("menu.bans.empty.lore"));
    }

    @Override
    protected void extras() {
        set(HEADER, plugin.info().item(claim, viewer, false));
        if (!canModerate()) {
            return;
        }
        final List<Component> lore = new ArrayList<>(messages().lore("menu.bans.add.lore"));
        lore.addAll(messages().lore("menu.bans.add.click"));
        set(bottom(CORNER), Items.item("iron_bars", messages().itemName("menu.bans.add.name"), lore),
                click -> plugin.menus().prompt(viewer, "prompt.ban", this, name -> {
                    final OfflinePlayer target = Gp.findPlayer(name);
                    if (target == null) {
                        messages().send(viewer, "general.player-not-found", Messages.text("player", name));
                    } else if (Gp.exists(claim)) {
                        plugin.actions().ban(viewer, claim, target);
                    }
                    open();
                }));
    }

}
