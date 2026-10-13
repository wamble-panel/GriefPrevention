package dev.wamble.gpaddons.protect;

import dev.wamble.gpaddons.util.Gp;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Protects cushions in claims, which GriefPrevention doesn't know about: placing and breaking them needs build
 * trust, sitting on them needs access trust. Owners can open these up with the decoration and entity flags.
 */
public final class CushionListener implements Listener {

    private static final long NOTICE_COOLDOWN_MILLIS = 1500L;

    private final Map<UUID, Long> lastNotice = new HashMap<>();

    /** Whether the player is stopped from doing this here; tells them why */
    private boolean denied(@Nullable Player player, @NotNull ClaimPermission level, @NotNull Location location,
                           @NotNull Event event) {
        if (player == null) {
            return false;
        }
        final Claim claim = Gp.claimAt(location, null);
        if (claim == null) {
            return false;
        }
        final Supplier<String> reason = claim.checkPermission(player, level, event);
        if (reason == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > NOTICE_COOLDOWN_MILLIS) {
            lastNotice.put(player.getUniqueId(), now);
            GriefPrevention.sendMessage(player, TextMode.Err, reason.get());
        }
        return true;
    }

    private <E extends Event & Cancellable> void check(@NotNull E event, @Nullable Player player,
                                                       @NotNull ClaimPermission level, @NotNull Location location) {
        if (denied(player, level, location, event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlaceClick(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !Cushions.isItem(event.getItem())) {
            return;
        }
        final Location target = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        if (denied(event.getPlayer(), ClaimPermission.Build, target, event)) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(@NotNull EntityPlaceEvent event) {
        if (Cushions.is(event.getEntity())) {
            check(event, event.getPlayer(), ClaimPermission.Build, event.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAttack(@NotNull PrePlayerAttackEntityEvent event) {
        if (Cushions.is(event.getAttacked())) {
            check(event, event.getPlayer(), ClaimPermission.Build, event.getAttacked().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDamage(@NotNull EntityDamageByEntityEvent event) {
        if (Cushions.is(event.getEntity())) {
            check(event, Cushions.playerBehind(event.getDamager()), ClaimPermission.Build, event.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(@NotNull HangingBreakByEntityEvent event) {
        if (Cushions.is(event.getEntity())) {
            check(event, Cushions.playerBehind(event.getRemover()), ClaimPermission.Build, event.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamage(@NotNull VehicleDamageEvent event) {
        if (Cushions.is(event.getVehicle())) {
            check(event, Cushions.playerBehind(event.getAttacker()), ClaimPermission.Build, event.getVehicle().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(@NotNull VehicleDestroyEvent event) {
        if (Cushions.is(event.getVehicle())) {
            check(event, Cushions.playerBehind(event.getAttacker()), ClaimPermission.Build, event.getVehicle().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSit(@NotNull PlayerInteractEntityEvent event) {
        if (Cushions.is(event.getRightClicked())) {
            check(event, event.getPlayer(), ClaimPermission.Access, event.getRightClicked().getLocation());
        }
    }

}
