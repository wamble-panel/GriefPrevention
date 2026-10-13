package dev.wamble.cushionguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protects cushions (Minecraft 26.3) in WorldGuard regions. WorldGuard only protects the entity types it knows,
 * and cushions are new, so this asks WorldGuard the same questions it asks for blocks: placing needs build or
 * the block-place flag, breaking needs build or the block-break flag, and sitting needs build or the interact
 * flag. Members, owners and players with region bypass are never stopped.
 * <p>
 * Cushions are recognised by name, so this does nothing on versions without them.
 */
public final class CushionGuard extends JavaPlugin implements Listener {

    private static final long NOTICE_COOLDOWN_MILLIS = 1500L;

    private final Map<UUID, Long> lastNotice = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private static boolean isCushion(@Nullable Entity entity) {
        return entity != null && ((Keyed) entity.getType()).getKey().getKey().contains("cushion");
    }

    private static boolean isCushionItem(@Nullable ItemStack item) {
        return item != null && ((Keyed) item.getType()).getKey().getKey().endsWith("_cushion");
    }

    @Nullable
    private static Player playerBehind(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** Whether WorldGuard would stop this player doing this here; tells them why, like WorldGuard does */
    private boolean denied(@Nullable Player player, @NotNull Location location, @NotNull StateFlag flag,
                           @NotNull String what) {
        if (player == null || location.getWorld() == null) {
            return false;
        }
        final LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, BukkitAdapter.adapt(location.getWorld()))) {
            return false;
        }
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final com.sk89q.worldedit.util.Location at = BukkitAdapter.adapt(location);
        if (query.testBuild(at, local, flag)) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > NOTICE_COOLDOWN_MILLIS) {
            lastNotice.put(player.getUniqueId(), now);
            final String message = query.queryValue(at, local, Flags.DENY_MESSAGE);
            if (message != null && !message.isEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("%what%", what)));
            }
        }
        return true;
    }

    private void check(@NotNull Cancellable event, @Nullable Player player, @NotNull Location location,
                       @NotNull StateFlag flag, @NotNull String what) {
        if (denied(player, location, flag, what)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlaceClick(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !isCushionItem(event.getItem())) {
            return;
        }
        final Location target = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        if (denied(event.getPlayer(), target, Flags.BLOCK_PLACE, "place that")) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(@NotNull EntityPlaceEvent event) {
        if (isCushion(event.getEntity())) {
            check(event, event.getPlayer(), event.getEntity().getLocation(), Flags.BLOCK_PLACE, "place that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAttack(@NotNull PrePlayerAttackEntityEvent event) {
        if (isCushion(event.getAttacked())) {
            check(event, event.getPlayer(), event.getAttacked().getLocation(), Flags.BLOCK_BREAK, "break that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDamage(@NotNull EntityDamageByEntityEvent event) {
        if (isCushion(event.getEntity())) {
            check(event, playerBehind(event.getDamager()), event.getEntity().getLocation(), Flags.BLOCK_BREAK, "break that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(@NotNull HangingBreakByEntityEvent event) {
        if (isCushion(event.getEntity())) {
            check(event, playerBehind(event.getRemover()), event.getEntity().getLocation(), Flags.BLOCK_BREAK, "break that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamage(@NotNull VehicleDamageEvent event) {
        if (isCushion(event.getVehicle())) {
            check(event, playerBehind(event.getAttacker()), event.getVehicle().getLocation(), Flags.BLOCK_BREAK, "break that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(@NotNull VehicleDestroyEvent event) {
        if (isCushion(event.getVehicle())) {
            check(event, playerBehind(event.getAttacker()), event.getVehicle().getLocation(), Flags.BLOCK_BREAK, "break that");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSit(@NotNull PlayerInteractEntityEvent event) {
        if (isCushion(event.getRightClicked())) {
            check(event, event.getPlayer(), event.getRightClicked().getLocation(), Flags.INTERACT, "sit there");
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
    }

}
