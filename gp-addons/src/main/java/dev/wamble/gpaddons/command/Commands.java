package dev.wamble.gpaddons.command;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.BanFromClaimImporter;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.flag.ClaimFlag;
import dev.wamble.gpaddons.menu.BansMenu;
import dev.wamble.gpaddons.menu.ClaimMenu;
import dev.wamble.gpaddons.menu.ClaimsMenu;
import dev.wamble.gpaddons.menu.FlagsMenu;
import dev.wamble.gpaddons.menu.WhitelistMenu;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Every GPAddons command. Commands without arguments open the matching menu.
 */
public final class Commands implements TabExecutor {

    private static final List<String> COMMANDS = List.of("claims", "claimban", "claimunban", "claimbans",
            "claimwhitelist", "claimflags", "claimname", "gpaddons");

    private final GPAddons plugin;

    public Commands(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String name : COMMANDS) {
            final PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (command.getName().equals("gpaddons")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "general.players-only");
            return true;
        }
        switch (command.getName()) {
            case "claims" -> claims(player, args);
            case "claimban" -> withClaim(player, claim -> ban(player, claim, args, label));
            case "claimunban" -> withClaim(player, claim -> unban(player, claim, args, label));
            case "claimbans" -> withClaim(player, claim -> banList(player, claim));
            case "claimwhitelist" -> withClaim(player, claim -> whitelist(player, claim, args, label));
            case "claimflags" -> withClaim(player, claim -> flags(player, claim, args, label));
            case "claimname" -> withClaim(player, claim -> name(player, claim, args, label));
            default -> {
                return false;
            }
        }
        return true;
    }

    private interface ClaimCommand {
        void run(@NotNull Claim claim);
    }

    /** Run a command on the claim (or subdivision) the player is standing in */
    private void withClaim(@NotNull Player player, @NotNull ClaimCommand command) {
        final Claim claim = Gp.claimHere(player);
        if (claim == null) {
            messages().send(player, "general.not-in-claim");
            return;
        }
        command.run(claim);
    }

    private void usage(@NotNull CommandSender sender, @NotNull String usage) {
        messages().send(sender, "general.usage", Messages.text("usage", usage));
    }

    @Nullable
    private OfflinePlayer player(@NotNull CommandSender sender, @NotNull String name) {
        final OfflinePlayer target = Gp.findPlayer(name);
        if (target == null) {
            messages().send(sender, "general.player-not-found", Messages.text("player", name));
        }
        return target;
    }

    // ---- /claims ----

    private void claims(@NotNull Player player, @NotNull String[] args) {
        if (!player.hasPermission("gpaddons.menu")) {
            messages().send(player, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            new ClaimsMenu(plugin, player, player).open();
            return;
        }
        final String arg = args[0];
        if (arg.equalsIgnoreCase("here")) {
            final Claim claim = Gp.claimHere(player);
            if (claim == null) {
                messages().send(player, "general.not-in-claim");
            } else if (!Gp.canManage(player, claim) && !player.hasPermission("gpaddons.other")) {
                messages().send(player, "general.cannot-manage");
            } else {
                new ClaimMenu(plugin, player, claim, null).open();
            }
            return;
        }
        if (!player.hasPermission("gpaddons.other")) {
            messages().send(player, "general.no-permission");
            return;
        }
        if (arg.equalsIgnoreCase("admin")) {
            new ClaimsMenu(plugin, player, null).open();
            return;
        }
        final OfflinePlayer target = player(player, arg);
        if (target != null) {
            new ClaimsMenu(plugin, player, target).open();
        }
    }

    // ---- Bans ----

    private void ban(@NotNull Player player, @NotNull Claim claim, @NotNull String[] args, @NotNull String label) {
        if (args.length == 0) {
            new BansMenu(plugin, player, claim, null).open();
            return;
        }
        final OfflinePlayer target = player(player, args[0]);
        if (target != null) {
            plugin.actions().ban(player, claim, target);
        }
    }

    private void unban(@NotNull Player player, @NotNull Claim claim, @NotNull String[] args, @NotNull String label) {
        if (args.length == 0) {
            usage(player, "/" + label + " <player>");
            return;
        }
        // Look through the bans here and in the main claim first, so players the server has forgotten can
        // still be unbanned, and a ban on the main claim can be lifted from inside a subdivision
        for (Claim c = claim; c != null; c = c.parent) {
            final ClaimData data = plugin.store().get(c);
            if (data == null) {
                continue;
            }
            for (UUID banned : data.bans().keySet()) {
                if (Gp.name(banned).equalsIgnoreCase(args[0]) || banned.toString().equalsIgnoreCase(args[0])) {
                    plugin.actions().unban(player, c, banned);
                    return;
                }
            }
        }
        final OfflinePlayer target = player(player, args[0]);
        if (target != null) {
            plugin.actions().unban(player, claim, target.getUniqueId());
        }
    }

    private void banList(@NotNull Player player, @NotNull Claim claim) {
        messages().send(player, "entry.ban-list-header", Messages.text("claim", plugin.info().name(claim)));
        boolean any = false;
        for (Claim c = claim; c != null; c = c.parent) {
            final ClaimData data = plugin.store().get(c);
            if (data == null) {
                continue;
            }
            for (Map.Entry<UUID, ClaimData.Ban> ban : data.bans().entrySet()) {
                any = true;
                player.sendMessage(messages().get("entry.ban-list-entry",
                        Messages.text("player", Gp.name(ban.getKey())),
                        Messages.text("by", ban.getValue().by() == null ? "?" : Gp.name(ban.getValue().by())),
                        Messages.text("date", ban.getValue().at() > 0 ? plugin.info().date(ban.getValue().at()) : "?")));
            }
        }
        if (!any) {
            messages().send(player, "entry.ban-list-empty");
        }
    }

    // ---- Whitelist ----

    private void whitelist(@NotNull Player player, @NotNull Claim claim, @NotNull String[] args, @NotNull String label) {
        if (args.length == 0) {
            new WhitelistMenu(plugin, player, claim, null).open();
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> plugin.actions().whitelist(player, claim, true);
            case "off" -> plugin.actions().whitelist(player, claim, false);
            case "add" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " add <player>");
                    return;
                }
                final OfflinePlayer target = player(player, args[1]);
                if (target != null) {
                    plugin.actions().addEntry(player, claim, target);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " remove <player>");
                    return;
                }
                final OfflinePlayer target = player(player, args[1]);
                if (target != null) {
                    plugin.actions().removeEntry(player, claim, target.getUniqueId());
                }
            }
            default -> usage(player, "/" + label + " [on|off|add <player>|remove <player>]");
        }
    }

    // ---- Flags ----

    private void flags(@NotNull Player player, @NotNull Claim claim, @NotNull String[] args, @NotNull String label) {
        if (args.length == 0) {
            new FlagsMenu(plugin, player, claim, null).open();
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            messages().send(player, "flags.list-header", Messages.text("claim", plugin.info().name(claim)));
            for (ClaimFlag flag : ClaimFlag.values()) {
                player.sendMessage(messages().get("flags.list-entry", flagName(flag), state(claim, flag)));
            }
            return;
        }
        final ClaimFlag flag = ClaimFlag.byId(args[0]);
        if (flag == null) {
            messages().send(player, "flags.unknown", Messages.text("flag", args[0]));
            return;
        }
        if (!plugin.flags().canChange(player, claim, flag)) {
            messages().send(player, Gp.canManage(player, claim) ? "flags.no-flag-permission" : "general.cannot-manage",
                    flagName(flag));
            return;
        }
        final String value = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        switch (value) {
            case "on", "true", "allow", "yes" -> plugin.flags().set(claim, flag, true);
            case "off", "false", "deny", "no" -> plugin.flags().set(claim, flag, false);
            case "default", "reset" -> {
                plugin.flags().set(claim, flag, null);
                messages().send(player, "flags.reset", flagName(flag), state(claim, flag));
                return;
            }
            case "toggle" -> plugin.flags().set(claim, flag, !plugin.flags().get(claim, flag));
            default -> {
                usage(player, "/" + label + " " + flag.id() + " [on|off|default]");
                return;
            }
        }
        messages().send(player, "flags.set", flagName(flag), state(claim, flag));
    }

    @NotNull
    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver flagName(@NotNull ClaimFlag flag) {
        return messages().formatted("flag", "flag." + flag.id() + ".name");
    }

    @NotNull
    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver state(@NotNull Claim claim, @NotNull ClaimFlag flag) {
        return messages().formatted("state", plugin.flags().get(claim, flag) ? "flags.state-on" : "flags.state-off");
    }

    // ---- Name ----

    private void name(@NotNull Player player, @NotNull Claim claim, @NotNull String[] args, @NotNull String label) {
        if (args.length == 0) {
            usage(player, "/" + label + " <name|clear>");
            return;
        }
        final String name = String.join(" ", args);
        plugin.actions().rename(player, claim, name.equalsIgnoreCase("clear") ? null : name);
    }

    // ---- /gpaddons ----

    private void admin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender, "/gpaddons <reload|import-banfromclaim>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                messages().send(sender, "general.reloaded");
            }
            case "import-banfromclaim" -> messages().send(sender, "general.imported",
                    Messages.text("summary", new BanFromClaimImporter(plugin).run()));
            default -> usage(sender, "/gpaddons <reload|import-banfromclaim>");
        }
    }

    // ---- Tab completion ----

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        final String last = args.length == 0 ? "" : args[args.length - 1];
        final List<String> options = switch (command.getName()) {
            case "gpaddons" -> args.length == 1 ? List.of("reload", "import-banfromclaim") : List.of();
            case "claims" -> args.length == 1 ? claimsOptions(sender) : List.of();
            case "claimban" -> args.length == 1 ? onlineNames(sender) : List.of();
            case "claimunban" -> args.length == 1 ? bannedNames(sender) : List.of();
            case "claimwhitelist" -> args.length == 1 ? List.of("on", "off", "add", "remove")
                    : args.length == 2 && args[0].equalsIgnoreCase("add") ? onlineNames(sender)
                    : args.length == 2 && args[0].equalsIgnoreCase("remove") ? entryNames(sender) : List.of();
            case "claimflags" -> args.length == 1
                    ? Stream.concat(Stream.of("list"), Arrays.stream(ClaimFlag.values()).map(ClaimFlag::id)).toList()
                    : args.length == 2 && !args[0].equalsIgnoreCase("list") ? List.of("on", "off", "default") : List.of();
            case "claimname" -> args.length == 1 ? List.of("clear") : List.of();
            default -> List.of();
        };
        final String prefix = last.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @NotNull
    private List<String> claimsOptions(@NotNull CommandSender sender) {
        final List<String> options = new ArrayList<>(List.of("here"));
        if (sender.hasPermission("gpaddons.other")) {
            options.add("admin");
            options.addAll(onlineNames(sender));
        }
        return options;
    }

    @NotNull
    private List<String> onlineNames(@NotNull CommandSender sender) {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> !(sender instanceof Player self) || self.canSee(p) && p != self)
                .map(Player::getName).toList();
    }

    @NotNull
    private List<String> bannedNames(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player) || Gp.claimHere(player) == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        for (Claim c = Gp.claimHere(player); c != null; c = c.parent) {
            final ClaimData data = plugin.store().get(c);
            if (data != null) {
                data.bans().keySet().forEach(uuid -> names.add(Gp.name(uuid)));
            }
        }
        return names;
    }

    @NotNull
    private List<String> entryNames(@NotNull CommandSender sender) {
        final ClaimData data = here(sender);
        return data == null ? List.of() : data.entry().stream().map(Gp::name).toList();
    }

    @Nullable
    private ClaimData here(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        final Claim claim = Gp.claimHere(player);
        return claim == null ? null : plugin.store().get(claim);
    }

}
