package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamQuest;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The team's quests with progress bars. Finished quests shimmer.
 */
public final class TeamQuestsMenu extends Menu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32};
    private static final TextColor DONE = TextColor.color(0xB5EAD7);
    private static final TextColor LEFT = TextColor.color(0x8A8A8A);
    private static final TextColor VALUE = TextColor.color(0xFFE5EC);

    private final Team team;

    public TeamQuestsMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.quests-title", Messages.component("team", plugin.teams().display(team)));
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team;
    }

    /** ■■■■■□□□□□ 50/100 */
    @NotNull
    static Component bar(long done, long goal) {
        final int filled = (int) Math.min(10, Math.round(10.0 * done / Math.max(1, goal)));
        return Component.text("■".repeat(filled), DONE)
                .append(Component.text("■".repeat(10 - filled), LEFT))
                .append(Component.text("  " + done + "/" + goal, VALUE));
    }

    @Override
    protected void draw() {
        set(HEADER, TeamUpgradesMenu.pointsItem(plugin, team));
        final TeamQuest[] quests = TeamQuest.values();
        for (int i = 0; i < quests.length && i < SLOTS.length; i++) {
            final TeamQuest quest = quests[i];
            final boolean done = team.completed().contains(quest.id());
            final int goal = plugin.perks().goal(quest);
            final List<Component> lore = new ArrayList<>(messages().lore("team-quests." + quest.id() + ".description",
                    Messages.text("goal", Integer.toString(goal))));
            lore.add(Component.empty());
            lore.add(Messages.plain(messages().get("menu.team.quest.progress",
                    Messages.component("bar", bar(plugin.perks().progress(team, quest), goal)))));
            lore.add(Messages.plain(messages().get("menu.team.quest.reward",
                    Messages.text("points", Integer.toString(plugin.perks().reward(quest))))));
            lore.addAll(messages().lore(done ? "menu.team.quest.done" : "menu.team.quest.open"));
            final ItemStack item = Items.item(quest.icon(), messages().itemName("menu.team.quest.name",
                    plugin.messages().formatted("quest", "team-quests." + quest.id() + ".name")), lore);
            set(SLOTS[i], done ? Items.glint(item) : item);
        }
    }

}
