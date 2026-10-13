package dev.wamble.gpaddons.command;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.menu.ClaimMenu;
import dev.wamble.gpaddons.menu.ClaimsMenu;
import dev.wamble.gpaddons.menu.GuestsMenu;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Opens the menus for GriefPrevention commands that would otherwise print lists or need typing:
 * /claimslist, /trustlist, /trust (and the other trust commands) with no name, and /abandonclaim.
 * Anything the menus don't cover, and anything the player isn't allowed to do, is left to GriefPrevention.
 */
public final class GpCommandMenus implements Listener {

    private final GPAddons plugin;

    public GpCommandMenus(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("menus.replace-griefprevention-commands", true)) {
            return;
        }
        final String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        final PluginCommand command = plugin.getServer().getPluginCommand(parts[0].toLowerCase(Locale.ROOT));
        if (command == null || !command.getPlugin().getName().equals("GriefPrevention")) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.hasPermission("griefprevention.claims") || !player.hasPermission("gpaddons.menu")) {
            return;
        }
        final int args = parts.length - 1;
        final boolean opened = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "claimslist" -> args == 0 ? claims(player) : args == 1 && otherClaims(player, parts[1]);
            case "trustlist", "trust", "untrust", "accesstrust", "containertrust", "permissiontrust" -> args == 0 && guests(player);
            case "abandonclaim" -> args == 0 && abandon(player, false);
            case "abandontoplevelclaim" -> args == 0 && abandon(player, true);
            default -> false;
        };
        if (opened) {
            event.setCancelled(true);
        }
    }

    private boolean claims(@NotNull Player player) {
        new ClaimsMenu(plugin, player, player).open();
        return true;
    }

    private boolean otherClaims(@NotNull Player player, @NotNull String name) {
        if (!player.hasPermission("griefprevention.claimslistother") && !player.hasPermission("gpaddons.other")) {
            return false;
        }
        final OfflinePlayer target = Gp.findPlayer(name);
        if (target == null) {
            return false;
        }
        new ClaimsMenu(plugin, player, target).open();
        return true;
    }

    private boolean guests(@NotNull Player player) {
        final Claim claim = Gp.claimHere(player);
        if (claim == null || !Gp.canManage(player, claim)) {
            return false;
        }
        new GuestsMenu(plugin, player, claim, new ClaimMenu(plugin, player, claim, null)).open();
        return true;
    }

    private boolean abandon(@NotNull Player player, boolean wholeClaim) {
        Claim claim = Gp.claimHere(player);
        if (claim == null) {
            return false;
        }
        if (wholeClaim) {
            claim = Gp.top(claim);
        }
        if (!Gp.canEdit(player, claim)) {
            return false;
        }
        new ClaimMenu(plugin, player, claim, null).confirmAbandon();
        return true;
    }

}
