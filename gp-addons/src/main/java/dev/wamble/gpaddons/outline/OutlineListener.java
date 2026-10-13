package dev.wamble.gpaddons.outline;

import com.griefprevention.events.BoundaryVisualizationEvent;
import dev.wamble.gpaddons.GPAddons;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

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
        final FileConfiguration config = plugin.getConfig();
        final boolean glow = config.getString("outline.style", "glow").toLowerCase(Locale.ROOT).equals("glow");
        final int offset = Math.max(0, Math.min(8, config.isInt("outline.height-offset")
                ? config.getInt("outline.height-offset")
                : config.getInt("outline.height-offset." + (glow ? "glow" : "blocks"), glow ? 0 : 1)));
        final Map<String, Color> colors = Map.of(
                "CLAIM", color(config, "claim", 0xFFC8DD),
                "ADMIN_CLAIM", color(config, "admin-claim", 0xFFB5A7),
                "SUBDIVISION", color(config, "subdivision", 0xCDB4DB),
                "CONFLICT_ZONE", color(config, "conflict", 0xFF9AA2),
                "OTHER", color(config, "other", 0xA2D2FF));
        event.setProvider((world, visualizeFrom, height) ->
                new OutlineVisualization(plugin, world, visualizeFrom, height, offset, glow, colors));
    }

    @NotNull
    private static Color color(@NotNull FileConfiguration config, @NotNull String key, int fallback) {
        final String hex = config.getString("outline.glow-colors." + key, "");
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return Color.fromRGB(fallback);
        }
    }

}
