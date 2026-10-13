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
import java.util.Map;
import java.util.UUID;

/**
 * The entry whitelist: the on/off switch at the top, players let in without trust (click to remove), and
 * the trusted players who can always enter.
 */
public final class WhitelistMenu extends PagedMenu<WhitelistMenu.Row> {

    /** A player who may enter: on the whitelist itself, or trusted */
    public record Row(@NotNull UUID uuid, @Nullable ClaimPermission level, boolean manager, boolean listed) {
    }

    private final Claim claim;

    public WhitelistMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.whitelist.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    private boolean canModerate() {
        return plugin.entry().canModerate(viewer, claim, "gpaddons.whitelist");
    }

    @Override
    protected @NotNull List<Row> entries() {
        final List<Row> rows = new ArrayList<>();
        final ClaimData data = plugin.store().get(claim);
        if (data != null) {
            data.entry().forEach(uuid -> rows.add(new Row(uuid, null, false, true)));
        }
        for (Map.Entry<String, ClaimPermission> entry : Gp.trust(claim).entrySet()) {
            try {
                final UUID uuid = UUID.fromString(entry.getKey());
                if (rows.stream().noneMatch(r -> r.uuid().equals(uuid))) {
                    rows.add(new Row(uuid, entry.getValue(), Gp.isManager(claim, entry.getKey()), false));
                }
            } catch (IllegalArgumentException ignored) {
                // "public" and [permission] groups aren't players
            }
        }
        return rows;
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Row row) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(row.uuid());
        final String name = Gp.name(row.uuid());
        if (row.listed()) {
            final List<Component> lore = new ArrayList<>(messages().lore("menu.whitelist.entry.lore"));
            lore.addAll(messages().lore(canModerate() ? "menu.whitelist.entry.click" : "menu.locked"));
            return Items.head(player, messages().itemName("menu.whitelist.entry.name", Messages.text("player", name)), lore);
        }
        return Items.head(player, messages().itemName("menu.whitelist.trusted.name", Messages.text("player", name)),
                messages().lore("menu.whitelist.trusted.lore",
                        Messages.component("level", plugin.info().level(row.level(), row.manager()))));
    }

    @Override
    protected @Nullable Action action(@NotNull Row row) {
        if (!row.listed() || !canModerate()) {
            return null;
        }
        return click -> {
            plugin.actions().removeEntry(viewer, claim, row.uuid());
            open();
        };
    }

    @Override
    protected void extras() {
        final boolean moderate = canModerate();
        final boolean on = plugin.store().get(claim) != null && plugin.store().get(claim).whitelist();
        final String key = on ? "menu.whitelist.on" : "menu.whitelist.off";
        final List<Component> lore = new ArrayList<>(messages().lore(key + ".lore"));
        lore.addAll(messages().lore(moderate ? key + ".click" : "menu.locked"));
        ItemStack toggle = Items.item(on ? "lime_dye" : "gray_dye", messages().itemName(key + ".name"), lore);
        if (on) {
            toggle = Items.glint(toggle);
        }
        set(HEADER, toggle, moderate ? click -> {
            plugin.actions().whitelist(viewer, claim, !on);
            refresh();
        } : null);

        if (moderate) {
            final List<Component> addLore = new ArrayList<>(messages().lore("menu.whitelist.add.lore"));
            addLore.addAll(messages().lore("menu.whitelist.add.click"));
            set(bottom(CORNER), Items.item("emerald", messages().itemName("menu.whitelist.add.name"), addLore),
                    click -> new PlayerPickerMenu(plugin, viewer, this, "entry",
                            player -> !plugin.entry().isBanned(claim, player.getUniqueId())
                                    && !plugin.entry().hasEntry(claim, player.getUniqueId())
                                    && !Gp.isTrusted(claim, player),
                            target -> {
                                if (Gp.exists(claim)) {
                                    plugin.actions().addEntry(viewer, claim, target);
                                }
                                open();
                            }).open());
        }
    }

}
