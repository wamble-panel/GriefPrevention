package dev.wamble.gpaddons.outline;

import com.griefprevention.events.BoundaryVisualizationEvent;
import dev.wamble.gpaddons.GPAddons;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Swaps GriefPrevention's claim outline for one drawn above the ground (and optionally glowing). If drawing it
 * fails, GriefPrevention falls back to its own outline by itself.
 */
public final class OutlineListener implements Listener {

    private final GPAddons plugin;

    public OutlineListener(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVisualize(@NotNull BoundaryVisualizationEvent event) {
        if (!plugin.getConfig().getBoolean("outline.enabled", true)) {
            return;
        }
        final int offset = Math.max(0, Math.min(8, plugin.getConfig().getInt("outline.height-offset", 1)));
        final boolean glow = plugin.getConfig().getString("outline.style", "blocks").toLowerCase(Locale.ROOT).equals("glow");
        event.setProvider((world, visualizeFrom, height) ->
                new OutlineVisualization(plugin, world, visualizeFrom, height, offset, glow));
    }

}
