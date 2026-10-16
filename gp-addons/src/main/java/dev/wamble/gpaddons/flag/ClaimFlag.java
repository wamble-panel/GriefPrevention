package dev.wamble.gpaddons.flag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What everyone (not just trusted players) may do, or what may happen, in a claim.
 * These are the same flags HuskClaims has. A player flag that is on lets untrusted players do that thing;
 * an environment flag that is off stops it happening.
 */
public enum ClaimFlag {

    // Player actions: off means only trusted players may do them, as GriefPrevention normally does
    BLOCK_PLACE(Category.PLAYER, "bricks", false),
    BLOCK_BREAK(Category.PLAYER, "cracked_stone_bricks", false),
    BLOCK_INTERACT(Category.PLAYER, "oak_door", false),
    REDSTONE_INTERACT(Category.PLAYER, "lever", false),
    CONTAINER_OPEN(Category.PLAYER, "chest", false),
    FARM_BLOCK_BREAK(Category.PLAYER, "wheat", false),
    FARM_BLOCK_PLACE(Category.PLAYER, "wheat_seeds", false),
    FARM_BLOCK_INTERACT(Category.PLAYER, "bone_meal", false),
    FILL_BUCKET(Category.PLAYER, "bucket", false),
    EMPTY_BUCKET(Category.PLAYER, "water_bucket", false),
    PLACE_HANGING_ENTITY(Category.PLAYER, "item_frame", false),
    BREAK_HANGING_ENTITY(Category.PLAYER, "painting", false),
    PLACE_VEHICLE(Category.PLAYER, "minecart", false),
    BREAK_VEHICLE(Category.PLAYER, "oak_boat", false),
    ENTITY_INTERACT(Category.PLAYER, "lead", false),
    USE_SPAWN_EGG(Category.PLAYER, "allay_spawn_egg,pig_spawn_egg", false),
    ENDER_PEARL_TELEPORT(Category.PLAYER, "ender_pearl", false),
    START_RAID(Category.PLAYER, "ominous_bottle,bell", false),
    PLAYER_DAMAGE_PLAYER(Category.PLAYER, "shield", false),
    PLAYER_DAMAGE_MONSTER(Category.PLAYER, "zombie_head", true),
    PLAYER_DAMAGE_ENTITY(Category.PLAYER, "leather", false),
    PLAYER_DAMAGE_PERSISTENT_ENTITY(Category.PLAYER, "name_tag", false),

    // Things that happen on their own
    MONSTER_SPAWN(Category.ENVIRONMENT, "spawner", true),
    PASSIVE_MOB_SPAWN(Category.ENVIRONMENT, "egg", true),
    MONSTER_DAMAGE_TERRAIN(Category.ENVIRONMENT, "creeper_head", false),
    EXPLOSION_DAMAGE_TERRAIN(Category.ENVIRONMENT, "tnt", false),
    EXPLOSION_DAMAGE_ENTITY(Category.ENVIRONMENT, "gunpowder", true),
    FIRE_BURN(Category.ENVIRONMENT, "campfire", false),
    FIRE_SPREAD(Category.ENVIRONMENT, "flint_and_steel", false),
    REDSTONE_ACTUATE(Category.ENVIRONMENT, "piston", true);

    public enum Category {
        PLAYER,
        ENVIRONMENT
    }

    private final Category category;
    private final String icon;
    private final boolean builtInDefault;

    ClaimFlag(@NotNull Category category, @NotNull String icon, boolean builtInDefault) {
        this.category = category;
        this.icon = icon;
        this.builtInDefault = builtInDefault;
    }

    @NotNull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    public Category category() {
        return category;
    }

    @NotNull
    public String icon() {
        return icon;
    }

    public boolean builtInDefault() {
        return builtInDefault;
    }

    @NotNull
    public String permission() {
        return "gpaddons.flag." + id();
    }

    @Nullable
    public static ClaimFlag byId(@NotNull String id) {
        for (ClaimFlag flag : values()) {
            if (flag.id().equalsIgnoreCase(id)) {
                return flag;
            }
        }
        return null;
    }

}
