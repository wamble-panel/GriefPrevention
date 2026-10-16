package dev.wamble.gpaddons.outline;

import com.griefprevention.events.BoundaryVisualizationEvent;
import dev.wamble.gpaddons.GPAddons;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Swaps GriefPrevention's claim outline for one drawn above the ground (and optionally glowing). If drawing it
 * fails, GriefPrevention falls back to its own outline by itself.
 */
public final class OutlineListener implements Listener {

    /** Config names for GriefPrevention's kinds of outline */
    private static final Map<String, List<String>> KINDS = Map.of(
            "claim", List.of("CLAIM"),
            "admin-claim", List.of("ADMIN_CLAIM"),
            "subdivision", List.of("SUBDIVISION"),
            "new-claim", List.of("INITIALIZE_ZONE", "NATURE_RESTORATION_ZONE"),
            "conflict", List.of("CONFLICT_ZONE"));

    private final GPAddons plugin;
    private final Set<String> warned = new HashSet<>();

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
                "CLAIM", color(config, "claim", 0xA2D2FF),
                "ADMIN_CLAIM", color(config, "admin-claim", 0xFFB5A7),
                "SUBDIVISION", color(config, "subdivision", 0xCDB4DB),
                "CONFLICT_ZONE", color(config, "conflict", 0xFF9AA2),
                "OTHER", color(config, "other", 0xF2F2F2));
        final Map<String, BlockData> blocks = blocks(config);
        event.setProvider((world, visualizeFrom, height) ->
                new OutlineVisualization(plugin, world, visualizeFrom, height, offset, glow, colors, blocks));
    }

    /** The configured corner and side blocks, keyed like "CLAIM.corner"; anything missing or invalid uses the default */
    @NotNull
    private Map<String, BlockData> blocks(@NotNull FileConfiguration config) {
        final Map<String, BlockData> blocks = new HashMap<>();
        for (Map.Entry<String, List<String>> kind : KINDS.entrySet()) {
            for (String part : List.of("corner", "side")) {
                final String value = config.getString("outline.blocks." + kind.getKey() + "." + part, "").trim();
                if (value.isEmpty()) {
                    continue;
                }
                try {
                    final BlockData data = plugin.getServer().createBlockData(value.toLowerCase(Locale.ROOT));
                    kind.getValue().forEach(type -> blocks.put(type + "." + part, data));
                } catch (IllegalArgumentException e) {
                    if (warned.add(value)) {
                        plugin.getLogger().warning("outline.blocks." + kind.getKey() + "." + part + ": '" + value
                                + "' isn't a block, using the default");
                    }
                }
            }
        }
        return blocks;
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
