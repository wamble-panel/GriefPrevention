package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Everyone in the team, highest rank first. Click someone below you to manage them.
 */
public final class TeamMembersMenu extends PagedMenu<UUID> {

    private final Team team;

    public TeamMembersMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.members-title", Messages.component("team", plugin.teams().display(team)));
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team;
    }

    @Override
    protected @NotNull List<UUID> entries() {
        final List<UUID> members = new ArrayList<>(team.members().keySet());
        members.add(team.leader());
        members.sort(Comparator.<UUID>comparingInt(team::position)
                .thenComparing(uuid -> Gp.name(uuid).toLowerCase(Locale.ROOT)));
        return members;
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull UUID member) {
        final boolean online = Bukkit.getPlayer(member) != null;
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team.member.lore",
                Messages.component("rank", plugin.teams().rankDisplay(team, member)),
                Messages.component("online", messages().get(online ? "menu.team.online" : "menu.team.offline"))));
        if (plugin.teams().canManageMember(team, viewer.getUniqueId(), member)) {
            lore.addAll(messages().lore("menu.team.member.click"));
        }
        return Items.head(Bukkit.getOfflinePlayer(member),
                messages().itemName("menu.team.member.name", Messages.text("player", Gp.name(member))), lore);
    }

    @Override
    protected @Nullable Action action(@NotNull UUID member) {
        if (!plugin.teams().canManageMember(team, viewer.getUniqueId(), member)) {
            return null;
        }
        return click -> new TeamMemberMenu(plugin, viewer, team, member, this).open();
    }

}
