package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Rank;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamNames;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Pick a rank (below the viewer's own) for a member.
 */
public final class TeamRankPickerMenu extends PagedMenu<Rank> {

    private final Team team;
    private final UUID member;

    public TeamRankPickerMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @NotNull UUID member,
                              @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
        this.member = member;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.pick-rank-title");
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team && team.isMember(member);
    }

    @Override
    protected @NotNull List<Rank> entries() {
        final int own = team.position(viewer.getUniqueId());
        return team.ranks().stream().filter(rank -> team.ranks().indexOf(rank) > own).toList();
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Rank rank) {
        final ItemStack item = Items.item(TeamRanksMenu.icon(team, rank), messages().itemName("menu.team.pick-rank.name",
                Messages.component("rank", TeamNames.rank(rank.name()))), messages().lore("menu.team.pick-rank.click"));
        return rank == team.rankOf(member) ? Items.glint(item) : item;
    }

    @Override
    protected @Nullable Action action(@NotNull Rank rank) {
        return click -> {
            plugin.teams().setRank(viewer, member, rank);
            if (back != null && back.valid()) {
                back.open();
            }
        };
    }

}
