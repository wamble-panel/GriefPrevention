package dev.wamble.gpaddons.util;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Small helpers over GriefPrevention's API, using only parts that are the same in 16.18 and newer releases.
 */
public final class Gp {

    public static final String PUBLIC = "public";

    private Gp() {
    }

    @NotNull
    public static DataStore data() {
        return GriefPrevention.instance.dataStore;
    }

    /** The claim (or subdivision) at a location, like GriefPrevention's own protection checks */
    @Nullable
    public static Claim claimAt(@NotNull Location location, @Nullable Claim cached) {
        return data().getClaimAt(location, false, cached);
    }

    /** The claim a player is standing in, at any height, like GriefPrevention's commands */
    @Nullable
    public static Claim claimHere(@NotNull Player player) {
        return data().getClaimAt(player.getLocation(), true, null);
    }

    @NotNull
    public static Claim top(@NotNull Claim claim) {
        Claim top = claim;
        while (top.parent != null) {
            top = top.parent;
        }
        return top;
    }

    public static boolean exists(@NotNull Claim claim) {
        return claim.inDataStore && (claim.parent == null || top(claim).inDataStore && top(claim).children.contains(claim));
    }

    public static boolean isOwner(@NotNull Claim claim, @NotNull UUID uuid) {
        return uuid.equals(claim.getOwnerID());
    }

    public static boolean isAdmin(@NotNull Player player) {
        return player.hasPermission("gpaddons.admin");
    }

    /** Owner-level control: the owner, an admin with GriefPrevention's admin claim rights, or a GPAddons admin */
    public static boolean canEdit(@NotNull Player player, @NotNull Claim claim) {
        return isAdmin(player) || claim.checkPermission(player, ClaimPermission.Edit, null) == null;
    }

    /** Manager-level control: owner, players with /permissiontrust, or admins */
    public static boolean canManage(@NotNull Player player, @NotNull Claim claim) {
        return isAdmin(player) || claim.checkPermission(player, ClaimPermission.Manage, null) == null;
    }

    /** Whether the player may give and take trust here (GriefPrevention's own rule) */
    public static boolean canGrant(@NotNull Player player, @NotNull Claim claim) {
        return isAdmin(player) || claim.allowGrantPermission(player) == null;
    }

    public static boolean has(@NotNull Player player, @NotNull Claim claim, @NotNull ClaimPermission level) {
        return claim.checkPermission(player, level, null) == null;
    }

    /**
     * Whether the player is personally trusted here or in the main claim: owner, any trust level, a manager, or
     * through a [permission] group. "Everyone" (public) trust doesn't count, so a whitelist still means something.
     */
    public static boolean isTrusted(@NotNull Claim claim, @NotNull Player player) {
        for (Claim c = claim; c != null; c = c.parent) {
            if (player.getUniqueId().equals(c.getOwnerID())) {
                return true;
            }
            for (Map.Entry<String, ClaimPermission> entry : trust(c).entrySet()) {
                final String id = entry.getKey();
                if (id.equalsIgnoreCase(player.getUniqueId().toString())) {
                    return true;
                }
                if (id.length() > 2 && id.startsWith("[") && id.endsWith("]")
                        && player.hasPermission(id.substring(1, id.length() - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Everyone trusted in exactly this claim, by trust ID (a UUID, "public" or "[permission]"), with their level.
     * Managers have {@link ClaimPermission#Manage} unless they also have another level.
     */
    @NotNull
    public static Map<String, ClaimPermission> trust(@NotNull Claim claim) {
        final ArrayList<String> builders = new ArrayList<>();
        final ArrayList<String> containers = new ArrayList<>();
        final ArrayList<String> accessors = new ArrayList<>();
        final ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);
        final Map<String, ClaimPermission> trust = new LinkedHashMap<>();
        managers.forEach(id -> trust.put(id.toLowerCase(Locale.ROOT), ClaimPermission.Manage));
        builders.forEach(id -> trust.put(id.toLowerCase(Locale.ROOT), ClaimPermission.Build));
        containers.forEach(id -> trust.put(id.toLowerCase(Locale.ROOT), ClaimPermission.Container));
        accessors.forEach(id -> trust.put(id.toLowerCase(Locale.ROOT), ClaimPermission.Access));
        return trust;
    }

    public static boolean isManager(@NotNull Claim claim, @NotNull String id) {
        return claim.managers.stream().anyMatch(m -> m.equalsIgnoreCase(id));
    }

    @NotNull
    public static String ownerName(@NotNull Claim claim) {
        final String name = claim.getOwnerName();
        return name == null ? "?" : name;
    }

    @NotNull
    public static String name(@NotNull UUID uuid) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        final String name = player.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    /** A player who has joined before, by name */
    @Nullable
    public static OfflinePlayer findPlayer(@NotNull String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @NotNull
    public static PlayerData playerData(@NotNull UUID uuid) {
        return data().getPlayerData(uuid);
    }

    /** A player's own top-level claims */
    @NotNull
    public static List<Claim> claimsOf(@NotNull UUID uuid) {
        return new ArrayList<>(playerData(uuid).getClaims());
    }

    @NotNull
    public static List<Claim> adminClaims() {
        return data().getClaims().stream().filter(Claim::isAdminClaim).toList();
    }

    @NotNull
    public static Location center(@NotNull Claim claim) {
        final Location a = claim.getLesserBoundaryCorner();
        final Location b = claim.getGreaterBoundaryCorner();
        final World world = a.getWorld();
        return new Location(world, (a.getBlockX() + b.getBlockX()) / 2.0, a.getBlockY(), (a.getBlockZ() + b.getBlockZ()) / 2.0);
    }

    /** Save a claim's trust changes (subdivisions are saved on their own, as GriefPrevention's /trust does) */
    public static void save(@NotNull Claim claim) {
        data().saveClaim(claim);
    }

    /**
     * Give a claim to a new owner. GriefPrevention changed this method's return type between versions,
     * so it's called by name to work with both. Another plugin can cancel the transfer, so success is
     * judged by who owns the claim afterwards.
     */
    public static boolean transfer(@NotNull Claim claim, @NotNull UUID newOwner) {
        try {
            final Method method = DataStore.class.getMethod("changeClaimOwner", Claim.class, UUID.class);
            final Object result = method.invoke(data(), claim, newOwner);
            return !(result instanceof Boolean ok && !ok) && newOwner.equals(claim.getOwnerID());
        } catch (ReflectiveOperationException e) {
            GriefPrevention.instance.getLogger().warning("GPAddons couldn't transfer claim " + claim.getID() + ": " + e);
            return false;
        }
    }

    /** The share of claim blocks refunded when a claim is abandoned (AbandonReturnRatio) */
    public static double abandonReturnRatio() {
        try {
            final Field field = GriefPrevention.class.getField("config_claims_abandonReturnRatio");
            return field.getDouble(GriefPrevention.instance);
        } catch (ReflectiveOperationException e) {
            return 1.0D;
        }
    }

}
