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
 * The team's ranks, highest first, with what each may do. Click a rank below yours to change it.
 */
public final class TeamRanksMenu extends PagedMenu<Rank> {

    /** Helmets from the top rank down */
    private static final String[] ICONS = {"netherite_helmet", "diamond_helmet", "golden_helmet", "iron_helmet",
            "chainmail_helmet", "leather_helmet"};

    private final Team team;

    public TeamRanksMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    @NotNull
    static String icon(@NotNull Team team, @NotNull Rank rank) {
        return ICONS[Math.min(ICONS.length - 1, Math.max(0, team.ranks().indexOf(rank)))];
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.ranks-title", Messages.component("team", plugin.teams().display(team)));
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team;
    }

    @Override
    protected @NotNull List<Rank> entries() {
        return new ArrayList<>(team.ranks());
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Rank rank) {
        final long members = team.members().values().stream().filter(rank.id()::equals).count();
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team.rank.lore",
                Messages.text("position", "#" + (team.ranks().indexOf(rank) + 1)),
                Messages.text("members", Long.toString(members))));
        for (TeamPermission permission : TeamPermission.values()) {
            lore.add(Messages.plain(messages().get("menu.team.rank-perm-line",
                    plugin.messages().formatted("state", rank.permissions().contains(permission)
                            ? "menu.team.yes" : "menu.team.no"),
                    plugin.messages().formatted("perm", "team-perms." + permission.id() + ".name"))));
        }
        if (plugin.teams().canManageRank(team, viewer.getUniqueId(), rank)) {
            lore.addAll(messages().lore("menu.team.rank.click"));
        }
        return Items.item(icon(team, rank), messages().itemName("menu.team.rank.name",
                Messages.component("rank", TeamNames.rank(rank.name()))), lore);
    }

    @Override
    protected @Nullable Action action(@NotNull Rank rank) {
        if (!plugin.teams().canManageRank(team, viewer.getUniqueId(), rank)) {
            return null;
        }
        return click -> new TeamRankMenu(plugin, viewer, team, rank, this).open();
    }

    @Override
    protected void extras() {
        if (!team.can(viewer.getUniqueId(), TeamPermission.MANAGE_RANKS)) {
            return;
        }
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team.create-rank.lore"));
        lore.addAll(messages().lore("menu.team.create-rank.click"));
        set(bottom(CORNER), Items.item("emerald", messages().itemName("menu.team.create-rank.name"), lore),
                click -> plugin.menus().prompt(viewer, "prompt.rank-create", this, name -> {
                    plugin.teams().createRank(viewer, name);
                    if (valid()) {
                        open();
                    }
                }));
    }

}
