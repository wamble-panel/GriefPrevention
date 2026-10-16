package dev.wamble.gpaddons.flag;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Works out a claim's flags. A subdivision uses its own setting, then its main claim's, then the default.
 */
public final class FlagService {

    /** Where a claim's flag value comes from */
    public enum Source {
        CLAIM,
        MAIN_CLAIM,
        DEFAULT
    }

    private final GPAddons plugin;
    private final Map<ClaimFlag, Boolean> defaults = new EnumMap<>(ClaimFlag.class);

    public FlagService(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        for (ClaimFlag flag : ClaimFlag.values()) {
            defaults.put(flag, plugin.getConfig().getBoolean("flags.defaults." + flag.id(), flag.builtInDefault()));
        }
    }

    @Nullable
    private Boolean override(@NotNull Claim claim, @NotNull ClaimFlag flag) {
        final ClaimData data = plugin.store().get(claim);
        return data == null ? null : data.flags().get(flag);
    }

    /** Whether hostile mobs spawn here: the flag is on and, for player claims, the team has the Monster Lure */
    public boolean monstersSpawn(@NotNull Claim claim) {
        return get(claim, ClaimFlag.MONSTER_SPAWN) && plugin.perks().monsterSpawningUnlocked(claim);
    }

    public boolean get(@NotNull Claim claim, @NotNull ClaimFlag flag) {
        for (Claim c = claim; c != null; c = c.parent) {
            final Boolean value = override(c, flag);
            if (value != null) {
                return value;
            }
        }
        return defaults.getOrDefault(flag, flag.builtInDefault());
    }

    @NotNull
    public Source source(@NotNull Claim claim, @NotNull ClaimFlag flag) {
        if (override(claim, flag) != null) {
            return Source.CLAIM;
        }
        for (Claim c = claim.parent; c != null; c = c.parent) {
            if (override(c, flag) != null) {
                return Source.MAIN_CLAIM;
            }
        }
        return Source.DEFAULT;
    }

    /** Whether an owner has set this flag here or on the main claim (as opposed to using the default) */
    public boolean isSet(@NotNull Claim claim, @NotNull ClaimFlag flag) {
        return source(claim, flag) != Source.DEFAULT;
    }

    /**
     * Whether a player may change this flag here: owners and managers with gpaddons.flags, unless the flag's own
     * permission (gpaddons.flag.&lt;flag&gt;) has been set to false for them.
     */
    public boolean canChange(@NotNull Player player, @NotNull Claim claim, @NotNull ClaimFlag flag) {
        if (Gp.isAdmin(player)) {
            return true;
        }
        if (flag == ClaimFlag.MONSTER_SPAWN && !plugin.perks().monsterSpawningUnlocked(claim)) {
            return false;
        }
        return player.hasPermission("gpaddons.flags") && Gp.canManage(player, claim)
                && (!player.isPermissionSet(flag.permission()) || player.hasPermission(flag.permission()));
    }

    /** Set a flag on a claim, or reset it to the default with {@code null} */
    public void set(@NotNull Claim claim, @NotNull ClaimFlag flag, @Nullable Boolean value) {
        if (value == null) {
            final ClaimData data = plugin.store().get(claim);
            if (data != null && data.flags().remove(flag) != null) {
                plugin.store().changed();
            }
        } else {
            plugin.store().edit(claim).flags().put(flag, value);
        }
        if (flag == ClaimFlag.EXPLOSION_DAMAGE_TERRAIN || flag == ClaimFlag.MONSTER_DAMAGE_TERRAIN) {
            applyExplosions(Gp.top(claim));
        }
    }

    /**
     * Whether this claim's owner has taken control of explosions with the flags. Otherwise GriefPrevention's own
     * setting (/claimexplosions) applies untouched.
     */
    public boolean controlsExplosions(@NotNull Claim claim) {
        return isSet(claim, ClaimFlag.EXPLOSION_DAMAGE_TERRAIN) || isSet(claim, ClaimFlag.MONSTER_DAMAGE_TERRAIN);
    }

    /**
     * GriefPrevention only lets explosions break blocks in a claim when its explosions switch is on (and doesn't
     * save that switch), so turn it on for claims whose flags allow either kind of explosion. The explosion
     * listener then removes the kind that isn't allowed.
     */
    public void applyExplosions(@NotNull Claim top) {
        applyExplosionSwitch(top);
        for (Claim child : top.children) {
            applyExplosionSwitch(child);
        }
    }

    private void applyExplosionSwitch(@NotNull Claim claim) {
        if (controlsExplosions(claim)) {
            claim.areExplosivesAllowed = get(claim, ClaimFlag.EXPLOSION_DAMAGE_TERRAIN)
                    || get(claim, ClaimFlag.MONSTER_DAMAGE_TERRAIN);
        } else {
            claim.areExplosivesAllowed = false;
        }
    }

    /** Re-apply explosion switches to every claim with explosion flags set (on startup) */
    public void applyAllExplosions() {
        for (Claim claim : Gp.data().getClaims()) {
            if (controlsExplosions(claim) || claim.children.stream().anyMatch(this::controlsExplosions)) {
                applyExplosions(claim);
            }
        }
    }

}
