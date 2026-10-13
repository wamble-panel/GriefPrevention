package dev.wamble.gpaddons.data;

import dev.wamble.gpaddons.flag.ClaimFlag;
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

    @Nullable
    private String name;
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
        return name == null && !whitelist && entry.isEmpty() && bans.isEmpty() && flags.isEmpty();
    }

}
