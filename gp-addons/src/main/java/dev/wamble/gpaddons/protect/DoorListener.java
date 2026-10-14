package dev.wamble.gpaddons.protect;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.flag.ClaimFlag;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Locks doors, trapdoors and fence gates in claims. GriefPrevention leaves wooden doors and trapdoors open to
 * everyone unless LockWoodenDoors / LockTrapDoors are on, so the Use Blocks flag couldn't deny them. Here they
 * need access trust, unless the claim's Use Blocks flag lets everyone use them.
 */
public final class DoorListener implements Listener {

    private static final long NOTICE_COOLDOWN_MILLIS = 1500L;

    private final GPAddons plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public DoorListener(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private static boolean isDoor(@NotNull Material type) {
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isDoor(block.getType())
                || !plugin.getConfig().getBoolean("protection.lock-doors", true)) {
            return;
        }
        final Claim claim = Gp.claimAt(block.getLocation(), null);
        if (claim == null || plugin.flags().get(claim, ClaimFlag.BLOCK_INTERACT)) {
            return;
        }
        final Supplier<String> reason = claim.checkPermission(event.getPlayer(), ClaimPermission.Access, event);
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        final UUID id = event.getPlayer().getUniqueId();
        final long now = System.currentTimeMillis();
        final Long last = lastNotice.get(id);
        if (last == null || now - last > NOTICE_COOLDOWN_MILLIS) {
            lastNotice.put(id, now);
            GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, reason.get());
        }
    }

}
