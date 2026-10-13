package dev.wamble.gpaddons.entry;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claim bans and the entry whitelist. Both cover a claim's subdivisions: a player banned from a claim can't
 * enter any part of it, and a whitelisted claim's subdivisions are whitelisted too.
 */
public final class EntryService {

    public enum Result {
        DONE,
        NOT_ALLOWED,
        SELF,
        OWNER,
        ALREADY,
        NOT_FOUND
    }

    private static final long NOTICE_COOLDOWN_MILLIS = 2000L;

    private final GPAddons plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public EntryService(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @Nullable
    private ClaimData data(@NotNull Claim claim) {
        return plugin.store().get(claim);
    }

    /** The ban that applies to a player here, from this claim or its main claim */
    @Nullable
    public ClaimData.Ban banOf(@NotNull Claim claim, @NotNull UUID uuid) {
        for (Claim c = claim; c != null; c = c.parent) {
            final ClaimData data = data(c);
            if (data != null && data.bans().containsKey(uuid)) {
                return data.bans().get(uuid);
            }
        }
        return null;
    }

    public boolean isBanned(@NotNull Claim claim, @NotNull UUID uuid) {
        return banOf(claim, uuid) != null;
    }

    public boolean isWhitelistOn(@NotNull Claim claim) {
        for (Claim c = claim; c != null; c = c.parent) {
            final ClaimData data = data(c);
            if (data != null && data.whitelist()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEntry(@NotNull Claim claim, @NotNull UUID uuid) {
        for (Claim c = claim; c != null; c = c.parent) {
            final ClaimData data = data(c);
            if (data != null && data.entry().contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bypasses(@NotNull Player player) {
        return player.hasPermission("gpaddons.bypass") || GriefPrevention.instance.dataStore
                .getPlayerData(player.getUniqueId()).ignoreClaims;
    }

    /** Whether the player may be inside this claim */
    public boolean canEnter(@NotNull Player player, @NotNull Claim claim) {
        if (bypasses(player) || Gp.isOwner(claim, player.getUniqueId())) {
            return true;
        }
        if (isBanned(claim, player.getUniqueId())) {
            return false;
        }
        return !isWhitelistOn(claim) || Gp.isTrusted(claim, player) || hasEntry(claim, player.getUniqueId());
    }

    /** Tell a player why they can't enter, at most every couple of seconds */
    public void notifyBlocked(@NotNull Player player, @NotNull Claim claim) {
        final long now = System.currentTimeMillis();
        final Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        final String key = isBanned(claim, player.getUniqueId()) ? "entry.banned-enter" : "entry.whitelist-enter";
        plugin.messages().send(player, key, Messages.text("owner", Gp.ownerName(claim)));
    }

    public void forget(@NotNull UUID uuid) {
        lastNotice.remove(uuid);
    }

    // ---- Changing bans, whitelist and entry ----

    @NotNull
    public Result ban(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        if (!canModerate(actor, claim, "gpaddons.ban")) {
            return Result.NOT_ALLOWED;
        }
        if (target.getUniqueId().equals(actor.getUniqueId())) {
            return Result.SELF;
        }
        if (Gp.isOwner(claim, target.getUniqueId())) {
            return Result.OWNER;
        }
        final ClaimData data = plugin.store().edit(claim);
        if (data.bans().containsKey(target.getUniqueId())) {
            return Result.ALREADY;
        }
        data.bans().put(target.getUniqueId(), new ClaimData.Ban(actor.getUniqueId(), System.currentTimeMillis()));
        data.entry().remove(target.getUniqueId());
        // A banned player loses their trust here (and in this claim's subdivisions)
        claim.dropPermission(target.getUniqueId().toString());
        Gp.save(claim);
        final Player online = target.getPlayer();
        if (online != null) {
            ejectIfInside(online, claim);
        }
        return Result.DONE;
    }

    @NotNull
    public Result unban(@NotNull Player actor, @NotNull Claim claim, @NotNull UUID target) {
        if (!canModerate(actor, claim, "gpaddons.ban")) {
            return Result.NOT_ALLOWED;
        }
        final ClaimData data = plugin.store().get(claim);
        if (data == null || data.bans().remove(target) == null) {
            return Result.NOT_FOUND;
        }
        plugin.store().changed();
        return Result.DONE;
    }

    @NotNull
    public Result setWhitelist(@NotNull Player actor, @NotNull Claim claim, boolean on) {
        if (!canModerate(actor, claim, "gpaddons.whitelist")) {
            return Result.NOT_ALLOWED;
        }
        plugin.store().edit(claim).whitelist(on);
        if (on) {
            for (Player player : actor.getServer().getOnlinePlayers()) {
                ejectIfInside(player, claim);
            }
        }
        return Result.DONE;
    }

    @NotNull
    public Result addEntry(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        if (!canModerate(actor, claim, "gpaddons.whitelist")) {
            return Result.NOT_ALLOWED;
        }
        if (isBanned(claim, target.getUniqueId())) {
            return Result.NOT_FOUND;
        }
        return plugin.store().edit(claim).entry().add(target.getUniqueId()) ? Result.DONE : Result.ALREADY;
    }

    @NotNull
    public Result removeEntry(@NotNull Player actor, @NotNull Claim claim, @NotNull UUID target) {
        if (!canModerate(actor, claim, "gpaddons.whitelist")) {
            return Result.NOT_ALLOWED;
        }
        final ClaimData data = plugin.store().get(claim);
        if (data == null || !data.entry().remove(target)) {
            return Result.NOT_FOUND;
        }
        plugin.store().changed();
        final Player online = actor.getServer().getPlayer(target);
        if (online != null) {
            ejectIfInside(online, claim);
        }
        return Result.DONE;
    }

    /** Owners and managers can ban and whitelist, if they have the permission for it */
    public boolean canModerate(@NotNull Player player, @NotNull Claim claim, @NotNull String permission) {
        return Gp.isAdmin(player) || player.hasPermission(permission) && Gp.canManage(player, claim);
    }

    // ---- Ejecting ----

    /** Move a player out of a claim (or its subdivisions) if they're in it and aren't allowed to be */
    public void ejectIfInside(@NotNull Player player, @NotNull Claim claim) {
        final Claim at = Gp.claimAt(player.getLocation(), null);
        if (at == null || Gp.top(at) != Gp.top(claim) || canEnter(player, at)) {
            return;
        }
        eject(player, at);
    }

    /** Send a player to the nearest safe spot just outside the whole claim, preferring unclaimed land */
    public void eject(@NotNull Player player, @NotNull Claim claim) {
        final Claim top = Gp.top(claim);
        final Location lesser = top.getLesserBoundaryCorner();
        final Location greater = top.getGreaterBoundaryCorner();
        final Location at = player.getLocation();
        final World world = at.getWorld();
        final List<int[]> spots = new ArrayList<>();
        for (int distance : new int[]{1, 2, 3, 6, 12, 24}) {
            spots.add(new int[]{lesser.getBlockX() - distance, at.getBlockZ()});
            spots.add(new int[]{greater.getBlockX() + distance, at.getBlockZ()});
            spots.add(new int[]{at.getBlockX(), lesser.getBlockZ() - distance});
            spots.add(new int[]{at.getBlockX(), greater.getBlockZ() + distance});
        }
        spots.sort(Comparator.<int[], Boolean>comparing(s -> Gp.claimAt(new Location(world, s[0], at.getY(), s[1]), null) != null)
                .thenComparingDouble(s -> Math.pow(s[0] - at.getX(), 2) + Math.pow(s[1] - at.getZ(), 2)));
        plugin.messages().send(player, "entry.ejected", Messages.text("owner", Gp.ownerName(claim)));
        tryEject(player, world, spots, 0);
    }

    private void tryEject(@NotNull Player player, @NotNull World world, @NotNull List<int[]> spots, int index) {
        if (!player.isOnline()) {
            return;
        }
        if (index >= spots.size()) {
            player.teleportAsync(world.getSpawnLocation());
            return;
        }
        final int[] spot = spots.get(index);
        world.getChunkAtAsync(spot[0] >> 4, spot[1] >> 4).thenAccept(chunk -> {
            final Location safe = safeSpot(player, world, spot[0], spot[1]);
            if (safe == null) {
                tryEject(player, world, spots, index + 1);
                return;
            }
            player.teleportAsync(safe);
        });
    }

    @Nullable
    private Location safeSpot(@NotNull Player player, @NotNull World world, int x, int z) {
        final Block ground = world.getHighestBlockAt(x, z);
        final Material type = ground.getType();
        if (!type.isSolid() || type.name().contains("MAGMA") || type.name().contains("CACTUS")
                || type.name().contains("CAMPFIRE")) {
            return null;
        }
        final Block feet = ground.getRelative(BlockFace.UP);
        final Block head = feet.getRelative(BlockFace.UP);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        final Location location = feet.getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        final Claim claim = Gp.claimAt(location, null);
        return claim == null || canEnter(player, claim) ? location : null;
    }

}
