package dev.wamble.gpaddons.protect;

import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Stops a sponge soaking up water inside a claim that isn't the sponge's own claim (or another claim of the same
 * owner). GriefPrevention handles water flowing in, but not sponges.
 */
public final class SpongeListener implements Listener {

    private final Plugin plugin;

    public SpongeListener(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpongeAbsorb(@NotNull SpongeAbsorbEvent event) {
        if (!plugin.getConfig().getBoolean("protection.sponges", true)) {
            return;
        }
        final Claim source = Gp.claimAt(event.getBlock().getLocation(), null);
        event.getBlocks().removeIf(state -> crossesInto(source, Gp.claimAt(state.getLocation(), source)));
    }

    private static boolean crossesInto(@Nullable Claim source, @Nullable Claim target) {
        if (target == null) {
            return false;
        }
        if (source == null) {
            return true;
        }
        return Gp.top(source) != Gp.top(target) && !Objects.equals(source.getOwnerID(), target.getOwnerID());
    }

}
