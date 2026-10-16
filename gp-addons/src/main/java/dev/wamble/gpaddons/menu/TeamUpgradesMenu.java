package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamPermission;
import dev.wamble.gpaddons.team.TeamUpgrade;
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
 * Spend team points on upgrades. Each shows its levels, what the next one costs, and shimmers once bought.
 */
public final class TeamUpgradesMenu extends Menu {

    private static final int[] SLOTS_8 = {19, 20, 21, 22, 23, 24, 25, 31};
    private static final int[] SLOTS_9 = {19, 20, 21, 22, 23, 24, 25, 30, 32};

    private final Team team;

    public TeamUpgradesMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    /** The team's points, shown at the top of the quests and upgrades menus */
    @NotNull
    static ItemStack pointsItem(@NotNull GPAddons plugin, @NotNull Team team) {
        return Items.glint(Items.item("nether_star", plugin.messages().itemName("menu.team.points.name",
                Messages.text("points", Integer.toString(team.points()))), plugin.messages().lore("menu.team.points.lore")));
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.upgrades-title", Messages.component("team", plugin.teams().display(team)));
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team;
    }

    @Override
    protected void draw() {
        set(HEADER, pointsItem(plugin, team));
        final boolean canSpend = team.can(viewer.getUniqueId(), TeamPermission.SPEND_POINTS);
        final List<TeamUpgrade> upgrades = java.util.Arrays.stream(TeamUpgrade.values())
                .filter(plugin.perks()::available).toList();
        final int[] slots = upgrades.size() <= SLOTS_8.length ? SLOTS_8 : SLOTS_9;
        for (int i = 0; i < upgrades.size() && i < slots.length; i++) {
            final TeamUpgrade upgrade = upgrades.get(i);
            final int level = team.level(upgrade);
            final List<Component> lore = new ArrayList<>(messages().lore("team-upgrades." + upgrade.id() + ".description"));
            lore.add(Component.empty());
            lore.add(Messages.plain(messages().get("menu.team.upgrade.level",
                    Messages.text("level", Integer.toString(level)), Messages.text("max", Integer.toString(upgrade.maxLevel())))));
            final List<Component> effects = messages().lore("team-upgrades." + upgrade.id() + ".levels");
            for (int l = 0; l < effects.size(); l++) {
                lore.add(Messages.plain(messages().get(l < level ? "menu.team.upgrade.reached" : "menu.team.upgrade.locked",
                        Messages.component("effect", effects.get(l)))));
            }
            final boolean maxed = level >= upgrade.maxLevel();
            final int cost = maxed ? 0 : plugin.perks().cost(upgrade, level + 1);
            lore.add(Component.empty());
            if (maxed) {
                lore.addAll(messages().lore("menu.team.upgrade.maxed"));
            } else {
                lore.add(Messages.plain(messages().get("menu.team.upgrade.cost", Messages.text("cost", Integer.toString(cost)))));
                lore.addAll(messages().lore(!canSpend ? "menu.locked"
                        : team.points() >= cost ? "menu.team.upgrade.click" : "menu.team.upgrade.short"));
            }
            final ItemStack item = Items.item(upgrade.icon(), messages().itemName("menu.team.upgrade.name",
                    plugin.messages().formatted("upgrade", "team-upgrades." + upgrade.id() + ".name")), lore);
            set(slots[i], level > 0 ? Items.glint(item) : item, canSpend && !maxed ? click -> {
                plugin.perks().buy(viewer, upgrade);
                refresh();
            } : null);
        }
    }

}
