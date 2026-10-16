package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Rank;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamNames;
import dev.wamble.gpaddons.team.TeamPermission;
import dev.wamble.gpaddons.team.TeamService;
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
import java.util.List;
import java.util.UUID;

/**
 * One member: promote, demote, pick a rank, kick, or (for the leader) hand over the team.
 */
public final class TeamMemberMenu extends Menu {

    private final Team team;
    private final UUID member;

    public TeamMemberMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @NotNull UUID member,
                          @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
        this.member = member;
    }

    private TeamService teams() {
        return plugin.teams();
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.member-title", Messages.text("player", Gp.name(member)));
    }

    @Override
    protected boolean valid() {
        return teams().enabled() && teams().teamOf(viewer.getUniqueId()) == team && team.isMember(member);
    }

    @NotNull
    private ItemStack button(@NotNull String material, @NotNull String key, boolean usable,
                             @NotNull net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team." + key + ".lore", placeholders));
        lore.addAll(messages().lore(usable ? "menu.team." + key + ".click" : "menu.locked"));
        return Items.item(material, messages().itemName("menu.team." + key + ".name"), lore);
    }

    @NotNull
    private Component rankName(@Nullable Rank rank) {
        return rank == null ? Component.text("—") : TeamNames.rank(rank.name());
    }

    @Override
    protected void draw() {
        final UUID actor = viewer.getUniqueId();
        set(HEADER, Items.head(Bukkit.getOfflinePlayer(member),
                messages().itemName("menu.team.member.name", Messages.text("player", Gp.name(member))),
                messages().lore("menu.team.member.lore",
                        Messages.component("rank", teams().rankDisplay(team, member)),
                        Messages.component("online", messages().get(Bukkit.getPlayer(member) != null
                                ? "menu.team.online" : "menu.team.offline")))));

        final Rank up = teams().step(team, actor, member, -1);
        set(19, button("lime_dye", "promote", up != null, Messages.component("rank", rankName(up))),
                up != null ? click -> {
                    teams().promote(viewer, member);
                    refresh();
                } : null);

        final Rank down = teams().step(team, actor, member, 1);
        set(21, button("red_dye", "demote", down != null, Messages.component("rank", rankName(down))),
                down != null ? click -> {
                    teams().demote(viewer, member);
                    refresh();
                } : null);

        final boolean canSet = teams().canActOn(team, actor, member, TeamPermission.PROMOTE);
        set(23, button("golden_helmet", "set-rank", canSet), canSet
                ? click -> new TeamRankPickerMenu(plugin, viewer, team, member, this).open() : null);

        final boolean canKick = teams().canActOn(team, actor, member, TeamPermission.KICK);
        set(25, button("barrier", "kick", canKick), canKick ? click -> new ConfirmMenu(plugin, viewer, this,
                messages().get("menu.team.member-title", Messages.text("player", Gp.name(member))),
                Items.head(Bukkit.getOfflinePlayer(member), messages().itemName("menu.team.kick.name"),
                        messages().lore("menu.team.kick.lore")),
                this::valid, () -> {
                    teams().kick(viewer, member);
                    if (back != null && back.valid()) {
                        back.open();
                    }
                }).open() : null);

        if (team.leader().equals(actor)) {
            set(31, button("golden_helmet", "make-leader", true), click -> new ConfirmMenu(plugin, viewer, this,
                    messages().get("menu.team.member-title", Messages.text("player", Gp.name(member))),
                    Items.head(Bukkit.getOfflinePlayer(member), messages().itemName("menu.team.make-leader.name"),
                            messages().lore("menu.team.make-leader.lore")),
                    this::valid, () -> {
                        teams().transfer(viewer, member);
                        if (back != null && back.valid()) {
                            back.open();
                        }
                    }).open());
        }
    }

}
