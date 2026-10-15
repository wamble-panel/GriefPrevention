package dev.wamble.gpaddons.entry;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tells players whose claim they're walking into ("✿ Entering Steve's Claim"), and optionally when they leave.
 * Only the main claim counts, so walking between a claim's subdivisions says nothing.
 */
public final class ClaimNoticeListener implements Listener {

    private static final Title.Times TIMES = Title.Times.times(
            Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400));

    private final GPAddons plugin;
    /** The main claim each player is in, or no entry for the wilderness */
    private final Map<UUID, Claim> current = new HashMap<>();

    public ClaimNoticeListener(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld()) {
            return;
        }
        update(event.getPlayer(), to, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo(), true);
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation(), false);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        current.remove(event.getPlayer().getUniqueId());
    }

    private void update(@NotNull Player player, @NotNull Location location, boolean notify) {
        final Claim previous = current.get(player.getUniqueId());
        final Claim at = Gp.claimAt(location, previous);
        final Claim top = at == null ? null : Gp.top(at);
        if (top == previous) {
            return;
        }
        if (top == null) {
            current.remove(player.getUniqueId());
        } else {
            current.put(player.getUniqueId(), top);
        }
        if (!notify || !plugin.getConfig().getBoolean("claim-notices.enabled", true)) {
            return;
        }
        if (top != null) {
            show(player, entering(player, top));
        } else if (previous != null && plugin.getConfig().getBoolean("claim-notices.show-leaving", false)) {
            show(player, leaving(player, previous));
        }
    }

    @Nullable
    private String customName(@NotNull Claim claim) {
        final ClaimData data = plugin.store().get(claim);
        return data == null ? null : data.name();
    }

    @NotNull
    private Component entering(@NotNull Player player, @NotNull Claim claim) {
        final String name = customName(claim);
        if (claim.isAdminClaim()) {
            return name == null ? plugin.messages().get("notices.enter-admin")
                    : plugin.messages().get("notices.enter-admin-named", Messages.text("name", name));
        }
        if (Gp.isOwner(claim, player.getUniqueId())) {
            return plugin.messages().get("notices.enter-own");
        }
        final String owner = Gp.ownerName(claim);
        return name == null ? plugin.messages().get("notices.enter", Messages.text("owner", owner))
                : plugin.messages().get("notices.enter-named", Messages.text("name", name), Messages.text("owner", owner));
    }

    @NotNull
    private Component leaving(@NotNull Player player, @NotNull Claim claim) {
        if (claim.isAdminClaim()) {
            return plugin.messages().get("notices.leave-admin");
        }
        if (Gp.isOwner(claim, player.getUniqueId())) {
            return plugin.messages().get("notices.leave-own");
        }
        return plugin.messages().get("notices.leave", Messages.text("owner", Gp.ownerName(claim)));
    }

    private void show(@NotNull Player player, @NotNull Component message) {
        switch (plugin.getConfig().getString("claim-notices.display", "actionbar").toLowerCase(Locale.ROOT)) {
            case "title" -> player.showTitle(Title.title(Component.empty(), message, TIMES));
            case "chat" -> player.sendMessage(message);
            default -> player.sendActionBar(message);
        }
    }

}
