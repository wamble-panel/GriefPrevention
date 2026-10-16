package dev.wamble.gpaddons.entry;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleporting to a claim from the menus: to the teleport point the owner set, or a safe spot near the middle.
 * A short warm-up (cancelled by moving or getting hurt) and a cooldown keep it from being an escape button.
 */
public final class ClaimTeleports implements Listener {

    private record Pending(@NotNull BukkitTask task, @NotNull Location start) {
    }

    private final GPAddons plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public ClaimTeleports(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("teleport.enabled", true);
    }

    /** Owners, managers and admins may teleport to a claim */
    public boolean canTeleport(@NotNull Player player, @NotNull Claim claim) {
        return enabled() && (Gp.isAdmin(player) || player.hasPermission("gpaddons.teleport") && Gp.canManage(player, claim));
    }

    // ---- Teleport point ----

    /** Set where players arrive, to where the player stands (which must be inside the claim) */
    public boolean setHome(@NotNull Player player, @NotNull Claim claim) {
        if (!Gp.canManage(player, claim)) {
            plugin.messages().send(player, "general.cannot-manage");
            return false;
        }
        final Location at = player.getLocation();
        final Claim here = Gp.claimAt(at, null);
        if (here == null || Gp.top(here) != Gp.top(claim) || claim.parent != null && here != claim) {
            plugin.messages().send(player, "teleport.home-outside");
            return false;
        }
        plugin.store().edit(claim).home(new ClaimData.Home(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), at.getPitch()));
        plugin.messages().send(player, "teleport.home-set");
        return true;
    }

    public boolean hasHome(@NotNull Claim claim) {
        final ClaimData data = plugin.store().get(claim);
        return data != null && data.home() != null;
    }

    // ---- Teleporting ----

    public void teleport(@NotNull Player player, @NotNull Claim claim) {
        if (!canTeleport(player, claim)) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        if (Gp.playerData(player.getUniqueId()).inPvpCombat()) {
            plugin.messages().send(player, "teleport.in-combat");
            return;
        }
        final boolean bypass = player.hasPermission("gpaddons.teleport.instant");
        final long cooldown = Math.max(0, plugin.getConfig().getLong("teleport.cooldown-seconds", 30)) * 1000L;
        final Long last = lastTeleport.get(player.getUniqueId());
        if (!bypass && last != null && System.currentTimeMillis() - last < cooldown) {
            final long left = (cooldown - (System.currentTimeMillis() - last) + 999) / 1000;
            plugin.messages().send(player, "teleport.cooldown", Messages.text("seconds", Long.toString(left)));
            return;
        }
        cancel(player.getUniqueId());
        player.closeInventory();
        final String name = plugin.info().name(claim);
        final int warmup = bypass ? 0 : Math.max(0, plugin.getConfig().getInt("teleport.warmup-seconds", 3));
        if (warmup == 0) {
            go(player, claim, name);
            return;
        }
        plugin.messages().send(player, "teleport.warmup", Messages.text("claim", name),
                Messages.text("seconds", Integer.toString(warmup)));
        final BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (player.isOnline() && Gp.exists(claim)) {
                go(player, claim, name);
            }
        }, warmup * 20L);
        pending.put(player.getUniqueId(), new Pending(task, player.getLocation()));
    }

    private void go(@NotNull Player player, @NotNull Claim claim, @NotNull String name) {
        final Location home = home(claim);
        final Location center = Gp.center(claim);
        final Location anchor = home != null ? home : center;
        if (anchor.getWorld() == null) {
            plugin.messages().send(player, "teleport.no-safe-spot");
            return;
        }
        // Load the chunk without stopping the server, then find the spot
        anchor.getWorld().getChunkAtAsync(anchor).thenAccept(chunk -> {
            if (!player.isOnline() || !Gp.exists(claim)) {
                return;
            }
            Location target = home != null && safe(home) ? home : null;
            if (target == null) {
                target = middle(claim, center);
            }
            if (target == null) {
                plugin.messages().send(player, "teleport.no-safe-spot");
                return;
            }
            final Claim arriving = Gp.claimAt(target, null);
            if (arriving != null && !plugin.entry().canEnter(player, arriving)) {
                plugin.entry().notifyBlocked(player, arriving);
                return;
            }
            lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
            player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(done -> {
                if (done) {
                    plugin.messages().send(player, "teleport.done", Messages.text("claim", name));
                }
            });
        });
    }

    /** The owner's teleport point, if it's set and still inside the claim */
    @Nullable
    private Location home(@NotNull Claim claim) {
        final ClaimData data = plugin.store().get(claim);
        final ClaimData.Home home = data == null ? null : data.home();
        if (home == null) {
            return null;
        }
        final World world = plugin.getServer().getWorld(home.world());
        if (world == null) {
            return null;
        }
        final Location location = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        final Claim at = Gp.claimAt(location, null);
        return at != null && Gp.top(at) == Gp.top(claim) ? location : null;
    }

    /** A safe spot in the middle of the claim: on top of the ground, or under the roof in the nether */
    @Nullable
    private static Location middle(@NotNull Claim claim, @NotNull Location center) {
        final World world = center.getWorld();
        if (world == null) {
            return null;
        }
        final int x = center.getBlockX();
        final int z = center.getBlockZ();
        if (world.getEnvironment().name().equals("NETHER")) {
            for (int y = Math.max(world.getMinHeight() + 1, claim.getLesserBoundaryCorner().getBlockY()); y < 126; y++) {
                final Location spot = new Location(world, x + 0.5, y, z + 0.5);
                if (safe(spot)) {
                    return spot;
                }
            }
            return null;
        }
        final Location top = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
        return safe(top) ? top : null;
    }

    /** Solid, harmless ground with room to stand */
    private static boolean safe(@NotNull Location feet) {
        final Block feetBlock = feet.getBlock();
        final Block ground = feetBlock.getRelative(BlockFace.DOWN);
        final Material type = ground.getType();
        final String name = type.name();
        return type.isSolid() && !name.contains("MAGMA") && !name.contains("CACTUS") && !name.contains("CAMPFIRE")
                && feetBlock.isPassable() && !feetBlock.isLiquid()
                && feetBlock.getRelative(BlockFace.UP).isPassable() && !feetBlock.getRelative(BlockFace.UP).isLiquid();
    }

    private void cancel(@NotNull UUID player) {
        final Pending waiting = pending.remove(player);
        if (waiting != null) {
            waiting.task().cancel();
        }
    }

    // ---- Warm-up interruptions ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        final Pending waiting = pending.get(event.getPlayer().getUniqueId());
        if (waiting == null) {
            return;
        }
        final Location start = waiting.start();
        final Location to = event.getTo();
        if (to.getWorld() != start.getWorld() || to.distanceSquared(start) > 0.5 * 0.5) {
            cancel(event.getPlayer().getUniqueId());
            plugin.messages().send(event.getPlayer(), "teleport.cancelled");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancel(player.getUniqueId());
            plugin.messages().send(player, "teleport.cancelled");
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

}
