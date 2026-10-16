package dev.wamble.gpaddons.team;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Team quests. Each is finished once per team and gives team points. Goals can be changed in config.yml
 * (teams.quests.&lt;quest&gt;.goal), as can the points they give.
 */
public enum TeamQuest {

    GATHERING_THE_CREW("player_head", 3),
    A_PLACE_TO_CALL_HOME("red_bed", 1),
    SHARED_GROUND("oak_sapling", 1),
    INTO_THE_UNKNOWN("netherrack", 3),
    THE_FINAL_FRONTIER("end_stone", 3),
    FAMILY_REUNION("cake", 4),
    STRENGTH_IN_NUMBERS("iron_sword", 100),
    HARVEST_FESTIVAL("wheat", 500),
    MASTER_BUILDERS("bricks", 2000),
    DEEP_DELVERS("diamond_pickaxe", 250);

    private final String icon;
    private final int goal;

    TeamQuest(@NotNull String icon, int goal) {
        this.icon = icon;
        this.goal = goal;
    }

    @NotNull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    public String icon() {
        return icon;
    }

    public int defaultGoal() {
        return goal;
    }

    @Nullable
    public static TeamQuest byId(@NotNull String id) {
        for (TeamQuest quest : values()) {
            if (quest.id().equalsIgnoreCase(id)) {
                return quest;
            }
        }
        return null;
    }

}
