package dev.wamble.gpaddons.team;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a team rank may do. The leader may always do everything.
 */
public enum TeamPermission {

    INVITE("emerald"),
    KICK("iron_door"),
    PROMOTE("golden_helmet"),
    MANAGE_RANKS("name_tag"),
    RENAME("writable_book"),
    CLAIM_ACCESS("grass_block");

    private final String icon;

    TeamPermission(@NotNull String icon) {
        this.icon = icon;
    }

    @NotNull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    public String icon() {
        return icon;
    }

    @Nullable
    public static TeamPermission byId(@NotNull String id) {
        for (TeamPermission permission : values()) {
            if (permission.id().equalsIgnoreCase(id.replace('-', '_'))) {
                return permission;
            }
        }
        return null;
    }

}
