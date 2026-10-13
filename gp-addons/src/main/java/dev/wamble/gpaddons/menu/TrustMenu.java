package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One guest's trust, as a ladder from left to right: entry, access, container, build, then manager.
 */
public final class TrustMenu extends Menu {

    private final Claim claim;
    /** A UUID, "public" or "[permission]" */
    private final String id;
    private final String shown;
    @Nullable
    private final UUID uuid;

    public TrustMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @NotNull String id,
                     @NotNull String shown, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
        this.id = id.toLowerCase(Locale.ROOT).equals(Gp.PUBLIC) ? Gp.PUBLIC : id;
        this.shown = shown;
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            parsed = null;
        }
        this.uuid = parsed;
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.trust.title", Messages.text("player", shown));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    @Nullable
    private ClaimPermission current() {
        final ClaimPermission level = Gp.trust(claim).get(id.toLowerCase(Locale.ROOT));
        return level == ClaimPermission.Manage ? null : level;
    }

    private boolean entryOnly() {
        final ClaimData data = plugin.store().get(claim);
        return uuid != null && current() == null && !Gp.isManager(claim, id) && data != null && data.entry().contains(uuid);
    }

    @NotNull
    private ItemStack option(@NotNull String material, @NotNull String key, boolean isCurrent, boolean usable) {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.trust." + key + ".lore"));
        if (isCurrent) {
            lore.addAll(messages().lore("menu.trust.current"));
        } else {
            lore.addAll(messages().lore(usable ? "menu.trust.click" : "menu.locked"));
        }
        final ItemStack item = Items.item(material, messages().itemName("menu.trust." + key + ".name"), lore);
        return isCurrent ? Items.glint(item) : item;
    }

    @Override
    protected void draw() {
        final boolean manage = Gp.canManage(viewer, claim);
        final boolean admin = Gp.isAdmin(viewer);
        final ClaimPermission current = current();
        final boolean manager = Gp.isManager(claim, id);

        // Who this is, and their trust now
        final Component level = entryOnly() ? messages().get("levels.entry") : plugin.info().level(current, manager);
        final List<Component> lore = messages().lore("menu.guests.player.lore", Messages.component("level", level));
        if (uuid != null) {
            final OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            set(HEADER, Items.head(player, messages().itemName("menu.guests.player.name", Messages.text("player", shown)), lore));
        } else if (id.equals(Gp.PUBLIC)) {
            set(HEADER, Items.item("oak_sign", messages().itemName("menu.guests.everyone.name"), lore));
        } else {
            set(HEADER, Items.item("bookshelf", messages().itemName("menu.guests.group.name",
                    Messages.text("group", shown)), lore));
        }

        // Entry: only for players, it's part of the whitelist
        if (uuid != null) {
            final boolean usable = admin || viewer.hasPermission("gpaddons.whitelist") && manage;
            final boolean isCurrent = entryOnly();
            set(20, option("tripwire_hook", "entry", isCurrent, usable), usable && !isCurrent ? click -> {
                final OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                plugin.actions().entryOnly(viewer, claim, target);
                refresh();
            } : null);
        }

        level(21, "lever", "access", ClaimPermission.Access, current, manage, admin);
        level(22, "chest", "container", ClaimPermission.Container, current, manage, admin);
        level(23, "bricks", "build", ClaimPermission.Build, current, manage, admin);

        // Manager: the owner decides (not for "everyone")
        if (!id.equals(Gp.PUBLIC)) {
            final boolean usable = Gp.canEdit(viewer, claim);
            final List<Component> managerLore = new ArrayList<>(messages().lore("menu.trust.manager.lore",
                    Messages.component("state", messages().get(manager ? "menu.claims.on" : "menu.claims.off"))));
            managerLore.addAll(messages().lore(usable ? "menu.trust.toggle" : "menu.locked"));
            ItemStack item = Items.item("golden_helmet", messages().itemName("menu.trust.manager.name"), managerLore);
            if (manager) {
                item = Items.glint(item);
            }
            set(24, item, usable ? click -> {
                plugin.actions().manager(viewer, claim, id, shown, !manager);
                refresh();
            } : null);
        }

        // Remove all trust
        final boolean trusted = current != null || manager || entryOnly();
        if (trusted && manage) {
            final List<Component> removeLore = new ArrayList<>(messages().lore("menu.trust.remove.lore"));
            removeLore.addAll(messages().lore("menu.trust.click"));
            set(bottom(CORNER), Items.item("barrier", messages().itemName("menu.trust.remove.name"), removeLore), click -> {
                if (entryOnly()) {
                    plugin.actions().removeEntry(viewer, claim, uuid);
                } else {
                    plugin.actions().untrust(viewer, claim, id, shown);
                }
                if (back != null) {
                    back.open();
                } else {
                    refresh();
                }
            });
        }
    }

    private void level(int slot, @NotNull String material, @NotNull String key, @NotNull ClaimPermission level,
                       @Nullable ClaimPermission current, boolean manage, boolean admin) {
        final boolean isCurrent = current != null && current.name().equals(level.name())
                || level == ClaimPermission.Container && current != null && current.name().equals("Inventory");
        final boolean usable = manage && (admin || Gp.has(viewer, claim, level));
        set(slot, option(material, key, isCurrent, usable), usable && !isCurrent ? click -> {
            plugin.actions().trust(viewer, claim, id, shown, level);
            refresh();
        } : null);
    }

}
