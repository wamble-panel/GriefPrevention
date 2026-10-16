package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Rank;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamNames;
import dev.wamble.gpaddons.team.TeamPermission;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One rank: switch its permissions, move it up or down, rename or delete it.
 */
public final class TeamRankMenu extends Menu {

    private static final int[] PERMISSION_SLOTS = {19, 20, 21, 23, 24, 25};

    private final Team team;
    private final Rank rank;

    public TeamRankMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @NotNull Rank rank,
                        @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
        this.rank = rank;
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.rank-title", Messages.component("rank", TeamNames.rank(rank.name())));
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team
                && team.ranks().contains(rank) && plugin.teams().canManageRank(team, viewer.getUniqueId(), rank);
    }

    @NotNull
    private ItemStack button(@NotNull String material, @NotNull String key, boolean usable) {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team." + key + ".lore"));
        lore.addAll(messages().lore(usable ? "menu.team." + key + ".click" : "menu.locked"));
        return Items.item(material, messages().itemName("menu.team." + key + ".name"), lore);
    }

    @Override
    protected void draw() {
        set(HEADER, Items.item(TeamRanksMenu.icon(team, rank), messages().itemName("menu.team.rank.name",
                Messages.component("rank", TeamNames.rank(rank.name()))), List.of()));

        final TeamPermission[] permissions = TeamPermission.values();
        for (int i = 0; i < permissions.length && i < PERMISSION_SLOTS.length; i++) {
            final TeamPermission permission = permissions[i];
            final boolean on = rank.permissions().contains(permission);
            final boolean usable = team.can(viewer.getUniqueId(), permission);
            final List<Component> lore = new ArrayList<>(messages().lore("team-perms." + permission.id() + ".description"));
            lore.add(Component.empty());
            lore.add(Messages.plain(messages().get("menu.team.perm.status",
                    plugin.messages().formatted("state", on ? "menu.team.yes" : "menu.team.no"))));
            lore.addAll(messages().lore(usable ? "menu.team.perm.click" : "menu.locked"));
            ItemStack item = Items.item(permission.icon(), messages().itemName("menu.team.perm.name",
                    plugin.messages().formatted("perm", "team-perms." + permission.id() + ".name")), lore);
            if (on) {
                item = Items.glint(item);
            }
            set(PERMISSION_SLOTS[i], item, usable ? click -> {
                plugin.teams().togglePermission(viewer, rank, permission, null);
                refresh();
            } : null);
        }

        final int index = team.ranks().indexOf(rank);
        final boolean canUp = index - 1 > team.position(viewer.getUniqueId());
        final boolean canDown = index + 1 < team.ranks().size();
        set(38, button("lime_dye", "move-up", canUp), canUp ? click -> {
            plugin.teams().moveRank(viewer, rank, -1);
            refresh();
        } : null);
        set(40, button("name_tag", "rename-rank", true), click -> plugin.menus().prompt(viewer, "prompt.rank-rename", this, name -> {
            plugin.teams().renameRank(viewer, rank, name);
            if (valid()) {
                open();
            }
        }));
        set(42, button("red_dye", "move-down", canDown), canDown ? click -> {
            plugin.teams().moveRank(viewer, rank, 1);
            refresh();
        } : null);

        final boolean canDelete = team.ranks().size() > 1;
        set(bottom(CORNER), button("barrier", "delete-rank", canDelete), canDelete ? click -> new ConfirmMenu(plugin, viewer,
                back, title(), Items.item("barrier", messages().itemName("menu.team.delete-rank.name"),
                messages().lore("menu.team.delete-rank.lore")), this::valid, () -> {
            plugin.teams().deleteRank(viewer, rank);
            if (back != null && back.valid()) {
                back.open();
            }
        }).open() : null);
    }

}
