package dev.wamble.gpaddons.protect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Keeps claims out of WorldGuard regions: creating or resizing a claim so it overlaps a region is cancelled.
 * Only loaded when WorldGuard is installed (see {@link #register}).
 */
public final class RegionClaimGuard implements Listener {

    private final GPAddons plugin;

    private RegionClaimGuard(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    /** Start guarding if WorldGuard is installed */
    public static void register(@NotNull GPAddons plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            plugin.getServer().getPluginManager().registerEvents(new RegionClaimGuard(plugin), plugin);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Couldn't hook into WorldGuard, so claims aren't kept out of regions (" + e + ")");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreate(@NotNull ClaimCreatedEvent event) {
        check(event, event.getClaim(), event.getCreator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResize(@NotNull ClaimResizeEvent event) {
        check(event, event.getTo(), event.getModifier());
    }

    private void check(@NotNull Cancellable event, @NotNull Claim claim, @Nullable CommandSender who) {
        // Subdivisions sit inside a claim that was already allowed; only players are stopped
        if (claim.parent != null || !(who instanceof Player player)
                || !plugin.getConfig().getBoolean("worldguard.block-claims-in-regions", true)
                || player.hasPermission("gpaddons.claim-in-regions")) {
            return;
        }
        final String region = overlappingRegion(claim);
        if (region != null) {
            event.setCancelled(true);
            plugin.messages().send(player, "general.in-region", Messages.text("region", region));
        }
    }

    /** The first region the claim overlaps that isn't on the allowed list, if any */
    @Nullable
    private String overlappingRegion(@NotNull Claim claim) {
        final Location lesser = claim.getLesserBoundaryCorner();
        final Location greater = claim.getGreaterBoundaryCorner();
        final World world = lesser.getWorld();
        if (world == null) {
            return null;
        }
        final RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regions == null) {
            return null;
        }
        final ProtectedCuboidRegion area = new ProtectedCuboidRegion("gpaddons_claim_check",
                BlockVector3.at(Math.min(lesser.getBlockX(), greater.getBlockX()), world.getMinHeight(),
                        Math.min(lesser.getBlockZ(), greater.getBlockZ())),
                BlockVector3.at(Math.max(lesser.getBlockX(), greater.getBlockX()), world.getMaxHeight() - 1,
                        Math.max(lesser.getBlockZ(), greater.getBlockZ())));
        final var allowed = plugin.getConfig().getStringList("worldguard.allowed-regions").stream()
                .map(id -> id.toLowerCase(Locale.ROOT)).toList();
        for (ProtectedRegion region : regions.getApplicableRegions(area)) {
            final String id = region.getId();
            if (!id.equalsIgnoreCase(ProtectedRegion.GLOBAL_REGION) && !allowed.contains(id.toLowerCase(Locale.ROOT))) {
                return id;
            }
        }
        return null;
    }

}
