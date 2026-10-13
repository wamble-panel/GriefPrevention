package dev.wamble.gpaddons.entry;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps banned and non-whitelisted players out of claims: walking, riding, teleporting and logging in.
 */
public final class EntryListener implements Listener {

    private final GPAddons plugin;
    private final Map<UUID, Claim> lastClaim = new HashMap<>();

    public EntryListener(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private static boolean sameBlock(@NotNull Location a, @NotNull Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ()
                && a.getWorld() == b.getWorld();
    }

    /** Whether moving from one spot to another takes the player into a claim they may not enter */
    private boolean blocked(@NotNull Player player, @NotNull Location from, @NotNull Location to) {
        final Claim toClaim = Gp.claimAt(to, lastClaim.get(player.getUniqueId()));
        if (toClaim == null) {
            return false;
        }
        lastClaim.put(player.getUniqueId(), toClaim);
        final Claim fromClaim = Gp.claimAt(from, toClaim);
        if (fromClaim == toClaim || plugin.entry().canEnter(player, toClaim)) {
            return false;
        }
        // Someone already stuck inside (e.g. banned while standing there) may still walk out
        return fromClaim == null || plugin.entry().canEnter(player, fromClaim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        final Player player = event.getPlayer();
        if (blocked(player, event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            notify(player, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        final Location to = event.getTo();
        final Player player = event.getPlayer();
        final Claim claim = Gp.claimAt(to, null);
        if (claim != null && !plugin.entry().canEnter(player, claim)) {
            event.setCancelled(true);
            notify(player, to);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(@NotNull VehicleMoveEvent event) {
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && blocked(player, event.getFrom(), event.getTo())) {
                event.getVehicle().removePassenger(player);
                player.teleportAsync(event.getFrom());
                notify(player, event.getTo());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            final Claim claim = Gp.claimAt(player.getLocation(), null);
            if (claim != null && !plugin.entry().canEnter(player, claim)) {
                plugin.entry().eject(player, claim);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        lastClaim.remove(event.getPlayer().getUniqueId());
        plugin.entry().forget(event.getPlayer().getUniqueId());
    }

    // A deleted claim's name, bans, whitelist and flags go with it
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimDeleted(@NotNull ClaimDeletedEvent event) {
        final Claim claim = event.getClaim();
        if (claim.getID() != null) {
            plugin.store().remove(claim.getID());
        }
        for (Claim child : claim.children) {
            if (child.getID() != null) {
                plugin.store().remove(child.getID());
            }
        }
        lastClaim.values().removeIf(cached -> cached == claim || cached.parent == claim);
    }

    private void notify(@NotNull Player player, @NotNull Location to) {
        final Claim claim = Gp.claimAt(to, null);
        if (claim != null) {
            plugin.entry().notifyBlocked(player, claim);
        }
    }

}
