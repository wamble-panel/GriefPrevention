package dev.wamble.gpaddons.team;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Team upgrades, bought with team points. Costs per level can be changed in config.yml
 * (teams.upgrades.&lt;upgrade&gt;.costs).
 */
public enum TeamUpgrade {

    BOUNTIFUL_HARVEST("wheat", 1, 2, 3),
    ANIMAL_HUSBANDRY("hay_block", 1, 2, 3),
    TEAM_VAULT("ender_chest", 1, 2, 3),
    BIGGER_TEAM("player_head", 1, 2, 3),
    LAND_GRANT("golden_shovel", 1, 2, 3),
    WISDOM("experience_bottle", 1, 2, 3),
    SWIFT_RETURN("ender_pearl", 1, 2, 3),
    MONSTER_LURE("zombie_head", 2);

    private final String icon;
    private final int[] costs;

    TeamUpgrade(@NotNull String icon, int... costs) {
        this.icon = icon;
        this.costs = costs;
    }

    @NotNull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    public String icon() {
        return icon;
    }

    public int maxLevel() {
        return costs.length;
    }

    /** The built-in cost of reaching a level (1-based) */
    public int defaultCost(int level) {
        return costs[Math.max(0, Math.min(costs.length, level) - 1)];
    }

}
