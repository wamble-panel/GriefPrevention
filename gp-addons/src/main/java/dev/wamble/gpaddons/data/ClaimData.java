package dev.wamble.gpaddons.data;

import dev.wamble.gpaddons.flag.ClaimFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What GPAddons stores for one GriefPrevention claim. GriefPrevention keeps the claim itself, its owner and trust.
 */
public final class ClaimData {

    /** A ban: who banned the player and when (epoch millis) */
    public record Ban(@Nullable UUID by, long at) {
    }

    /** Where players arrive when teleporting to the claim */
    public record Home(@NotNull String world, double x, double y, double z, float yaw, float pitch) {
    }

    @Nullable
    private String name;
    @Nullable
    private String icon;
    @Nullable
    private Home home;
    private boolean whitelist;
    private final Set<UUID> entry = new LinkedHashSet<>();
    private final Map<UUID, Ban> bans = new LinkedHashMap<>();
    private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);

    @Nullable
    public String name() {
        return name;
    }

    public void name(@Nullable String name) {
        this.name = name == null || name.isBlank() ? null : name;
    }

    /** The item shown for this claim in menus, as a material key, or null for the default */
    @Nullable
    public String icon() {
        return icon;
    }

    public void icon(@Nullable String icon) {
        this.icon = icon == null || icon.isBlank() ? null : icon;
    }

    @Nullable
    public Home home() {
        return home;
    }

    public void home(@Nullable Home home) {
        this.home = home;
    }

    public boolean whitelist() {
        return whitelist;
    }

    public void whitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    /** Players let in while the whitelist is on, without any other trust */
    public Set<UUID> entry() {
        return entry;
    }

    public Map<UUID, Ban> bans() {
        return bans;
    }

    /** Flags the owner has set here; anything missing uses the main claim's setting, then the default */
    public Map<ClaimFlag, Boolean> flags() {
        return flags;
    }

    public boolean isEmpty() {
        return name == null && icon == null && home == null && !whitelist && entry.isEmpty() && bans.isEmpty() && flags.isEmpty();
    }

}
