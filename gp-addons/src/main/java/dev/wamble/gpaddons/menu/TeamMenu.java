package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamPermission;
import dev.wamble.gpaddons.team.TeamService;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The viewer's team: members, ranks, inviting, renaming, and leaving or disbanding.
 */
public final class TeamMenu extends Menu {

    private final Team team;

    public TeamMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    private TeamService teams() {
        return plugin.teams();
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.title", Messages.component("team", teams().display(team)));
    }

    @Override
    protected boolean valid() {
        return teams().enabled() && teams().teamOf(viewer.getUniqueId()) == team;
    }

    @NotNull
    private List<Component> lore(@NotNull String key, boolean usable, @NotNull net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.team." + key + ".lore", placeholders));
        lore.addAll(messages().lore(usable ? "menu.team." + key + ".click" : "menu.locked"));
        return lore;
    }

    @NotNull
    private ItemStack button(@NotNull String material, @NotNull String key, boolean usable) {
        return Items.item(material, messages().itemName("menu.team." + key + ".name"), lore(key, usable));
    }

    @Override
    protected void draw() {
        final boolean leader = team.leader().equals(viewer.getUniqueId());
        final int max = plugin.getConfig().getInt("teams.max-members", 0);
        set(HEADER, Items.glint(Items.item("pink_banner,white_banner",
                Messages.plain(messages().get("menu.team.header.name", Messages.component("team", teams().display(team)))),
                messages().lore("menu.team.header.lore",
                        Messages.text("leader", Gp.name(team.leader())),
                        Messages.text("members", team.size() + (max > 0 ? "/" + max : "")),
                        Messages.component("rank", teams().rankDisplay(team, viewer.getUniqueId())),
                        Messages.text("created", plugin.info().date(team.created()))))));

        set(19, Items.head(viewer, messages().itemName("menu.team.members.name"), lore("members", true)),
                click -> new TeamMembersMenu(plugin, viewer, team, this).open());
        set(21, button("golden_helmet", "ranks", true), click -> new TeamRanksMenu(plugin, viewer, team, this).open());

        final boolean canInvite = team.can(viewer.getUniqueId(), TeamPermission.INVITE);
        set(23, button("emerald", "invite", canInvite), canInvite ? click -> new PlayerPickerMenu(plugin, viewer, this,
                "team-invite", player -> teams().teamOf(player.getUniqueId()) == null, target -> {
            teams().invite(viewer, target);
            open();
        }).open() : null);

        final boolean canRename = team.can(viewer.getUniqueId(), TeamPermission.RENAME);
        set(25, Items.item("name_tag", messages().itemName("menu.team.rename.name"),
                        lore("rename", canRename, Messages.component("team", teams().display(team)))),
                canRename ? click -> plugin.menus().prompt(viewer, "prompt.team-rename", this, text -> {
                    teams().rename(viewer, text);
                    if (valid()) {
                        open();
                    }
                }) : null);

        if (leader) {
            set(bottom(CORNER), button("tnt", "disband", true), click -> new ConfirmMenu(plugin, viewer, this,
                    messages().get("menu.team.title", Messages.component("team", teams().display(team))),
                    Items.item("tnt", messages().itemName("menu.team.disband.name"), messages().lore("menu.team.disband.lore")),
                    this::valid, () -> teams().disband(viewer, team)).open());
        } else {
            set(bottom(CORNER), button("oak_door", "leave", true), click -> new ConfirmMenu(plugin, viewer, this,
                    messages().get("menu.team.title", Messages.component("team", teams().display(team))),
                    Items.item("oak_door", messages().itemName("menu.team.leave.name"), messages().lore("menu.team.leave.lore")),
                    this::valid, () -> teams().leave(viewer)).open());
        }
    }

}
