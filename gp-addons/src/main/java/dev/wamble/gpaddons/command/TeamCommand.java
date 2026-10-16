package dev.wamble.gpaddons.command;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.menu.ConfirmMenu;
import dev.wamble.gpaddons.menu.TeamMenu;
import dev.wamble.gpaddons.team.Rank;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamNames;
import dev.wamble.gpaddons.team.TeamPermission;
import dev.wamble.gpaddons.team.TeamService;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /team: the team menu, and commands for everything in it.
 */
public final class TeamCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("create", "invite", "add", "accept", "deny", "leave",
            "kick", "promote", "demote", "transfer", "rename", "disband", "rank", "home", "sethome", "setclaim", "storage",
            "quests", "upgrades", "info", "list", "help");
    private static final List<String> RANK_SUBCOMMANDS = List.of("create", "delete", "rename", "perm", "set", "list");

    private final GPAddons plugin;

    public TeamCommand(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public void register() {
        final PluginCommand command = plugin.getCommand("team");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private TeamService teams() {
        return plugin.teams();
    }

    private Messages messages() {
        return plugin.messages();
    }

    private void usage(@NotNull CommandSender sender, @NotNull String usage) {
        messages().send(sender, "general.usage", Messages.text("usage", usage));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!teams().enabled()) {
            messages().send(sender, "team.disabled");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            info(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("gpaddons.team")) {
            messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            final Team team = teams().teamOf(player.getUniqueId());
            if (team != null) {
                new TeamMenu(plugin, player, team, null).open();
            } else {
                help(player);
            }
            return true;
        }
        final String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (rest.isEmpty()) {
                    usage(player, "/" + label + " create <name>");
                } else {
                    teams().create(player, rest);
                }
            }
            case "invite", "add" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " invite <player>");
                    return true;
                }
                final OfflinePlayer target = Gp.findPlayer(args[1]);
                if (target == null) {
                    messages().send(player, "general.player-not-found", Messages.text("player", args[1]));
                } else {
                    teams().invite(player, target);
                }
            }
            case "accept", "join" -> teams().accept(player, args.length > 1 ? args[1] : null);
            case "deny", "decline" -> teams().deny(player, args.length > 1 ? args[1] : null);
            case "leave" -> teams().leave(player);
            case "kick", "promote", "demote", "transfer" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " " + args[0].toLowerCase(Locale.ROOT) + " <player>");
                    return true;
                }
                final UUID target = member(player, args[1]);
                if (target == null) {
                    return true;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "kick" -> teams().kick(player, target);
                    case "promote" -> teams().promote(player, target);
                    case "demote" -> teams().demote(player, target);
                    default -> teams().transfer(player, target);
                }
            }
            case "rename" -> {
                if (rest.isEmpty()) {
                    usage(player, "/" + label + " rename <name>");
                } else {
                    teams().rename(player, rest);
                }
            }
            case "disband" -> disband(player, args.length > 1 ? args[1] : null);
            case "rank", "ranks" -> rank(player, label, Arrays.copyOfRange(args, 1, args.length));
            case "home" -> teams().home(player);
            case "sethome" -> teams().setHome(player);
            case "setclaim" -> teams().setTeamClaim(player, dev.wamble.gpaddons.util.Gp.claimHere(player),
                    args.length > 1 && args[1].equalsIgnoreCase("confirm"));
            case "storage", "vault", "chest" -> plugin.storage().open(player);
            case "quests", "upgrades" -> {
                final Team team = teams().teamOf(player.getUniqueId());
                if (team == null) {
                    messages().send(player, "team.no-team");
                } else if (args[0].equalsIgnoreCase("quests")) {
                    new dev.wamble.gpaddons.menu.TeamQuestsMenu(plugin, player, team, new TeamMenu(plugin, player, team, null)).open();
                } else {
                    new dev.wamble.gpaddons.menu.TeamUpgradesMenu(plugin, player, team, new TeamMenu(plugin, player, team, null)).open();
                }
            }
            default -> help(player);
        }
        return true;
    }

    private void help(@NotNull CommandSender sender) {
        for (var line : messages().lore("team.help")) {
            sender.sendMessage(line);
        }
    }

    /** A member of the player's team by name; tells them if there's no such member */
    @Nullable
    private UUID member(@NotNull Player player, @NotNull String name) {
        final Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            messages().send(player, "team.no-team");
            return null;
        }
        final List<UUID> everyone = new ArrayList<>(team.members().keySet());
        everyone.add(team.leader());
        for (UUID uuid : everyone) {
            if (Gp.name(uuid).equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        messages().send(player, "team.not-member", Messages.text("player", name));
        return null;
    }

    private void disband(@NotNull Player player, @Nullable String teamName) {
        if (teamName != null && player.hasPermission("gpaddons.team.admin")) {
            final Team team = teams().byName(teamName);
            if (team == null) {
                messages().send(player, "team.not-found", Messages.text("team", teamName));
            } else {
                teams().disband(player, team);
            }
            return;
        }
        final Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            messages().send(player, "team.no-team");
            return;
        }
        if (!team.leader().equals(player.getUniqueId())) {
            messages().send(player, "team.leader-only");
            return;
        }
        new ConfirmMenu(plugin, player, null,
                messages().get("menu.team.title", Messages.component("team", teams().display(team))),
                Items.item("tnt", messages().itemName("menu.team.disband.name"), messages().lore("menu.team.disband.lore")),
                () -> teams().teamOf(player.getUniqueId()) == team, () -> teams().disband(player, team)).open();
    }

    private void rank(@NotNull Player player, @NotNull String label, @NotNull String[] args) {
        final Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            messages().send(player, "team.no-team");
            return;
        }
        final String usage = "/" + label + " rank <create|delete|rename|perm|set|list>";
        if (args.length == 0) {
            usage(player, usage);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                for (Rank rank : team.ranks()) {
                    player.sendMessage(messages().get("team.info-line",
                            Messages.text("label", "#" + (team.ranks().indexOf(rank) + 1)),
                            Messages.component("value", TeamNames.rank(rank.name()))));
                }
            }
            case "create" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " rank create <name>");
                } else {
                    teams().createRank(player, args[1]);
                }
            }
            case "delete" -> {
                final Rank rank = args.length < 2 ? null : findRank(player, team, args[1]);
                if (args.length < 2) {
                    usage(player, "/" + label + " rank delete <rank>");
                } else if (rank != null) {
                    teams().deleteRank(player, rank);
                }
            }
            case "rename" -> {
                final Rank rank = args.length < 3 ? null : findRank(player, team, args[1]);
                if (args.length < 3) {
                    usage(player, "/" + label + " rank rename <rank> <new name>");
                } else if (rank != null) {
                    teams().renameRank(player, rank, args[2]);
                }
            }
            case "perm", "permission" -> {
                if (args.length < 3) {
                    usage(player, "/" + label + " rank perm <rank> <permission> [true|false]");
                    return;
                }
                final Rank rank = findRank(player, team, args[1]);
                final TeamPermission permission = TeamPermission.byId(args[2]);
                if (rank == null) {
                    return;
                }
                if (permission == null) {
                    usage(player, "/" + label + " rank perm <rank> <" + String.join("|",
                            Arrays.stream(TeamPermission.values()).map(TeamPermission::id).toList()) + ">");
                    return;
                }
                final Boolean value = args.length > 3 ? Boolean.valueOf(args[3].equalsIgnoreCase("true")
                        || args[3].equalsIgnoreCase("on") || args[3].equalsIgnoreCase("yes")) : null;
                teams().togglePermission(player, rank, permission, value);
            }
            case "set" -> {
                if (args.length < 3) {
                    usage(player, "/" + label + " rank set <player> <rank>");
                    return;
                }
                final UUID target = member(player, args[1]);
                final Rank rank = target == null ? null : findRank(player, team, args[2]);
                if (rank != null) {
                    teams().setRank(player, target, rank);
                }
            }
            default -> usage(player, usage);
        }
    }

    @Nullable
    private Rank findRank(@NotNull Player player, @NotNull Team team, @NotNull String name) {
        final Rank rank = teams().rankByName(team, name);
        if (rank == null) {
            messages().send(player, "team.rank-not-found", Messages.text("rank", name));
        }
        return rank;
    }

    private void info(@NotNull CommandSender sender, @Nullable String teamName) {
        Team team = teamName == null ? null : teams().byName(teamName);
        if (teamName == null && sender instanceof Player player) {
            team = teams().teamOf(player.getUniqueId());
            if (team == null) {
                messages().send(sender, "team.no-team");
                return;
            }
        }
        if (team == null) {
            messages().send(sender, "team.not-found", Messages.text("team", teamName == null ? "" : teamName));
            return;
        }
        sender.sendMessage(messages().get("team.info-header", Messages.component("team", teams().display(team))));
        line(sender, "leader", Gp.name(team.leader()));
        line(sender, "members", Integer.toString(team.size()));
        line(sender, "created", plugin.info().date(team.created()));
        final List<String> names = new ArrayList<>();
        team.members().keySet().forEach(uuid -> names.add(Gp.name(uuid)));
        if (!names.isEmpty()) {
            sender.sendMessage(messages().get("team.info-line", Messages.text("label", ""),
                    Messages.text("value", String.join(", ", names))));
        }
    }

    private void line(@NotNull CommandSender sender, @NotNull String label, @NotNull String value) {
        sender.sendMessage(messages().get("team.info-line", Messages.text("label", messages().raw("team.labels." + label)),
                Messages.component("value", net.kyori.adventure.text.Component.text(value,
                        net.kyori.adventure.text.format.TextColor.color(0xFFE5EC)))));
    }

    private void list(@NotNull CommandSender sender) {
        messages().send(sender, "team.list-header");
        teams().teams().stream().sorted(Comparator.comparingInt(Team::size).reversed()).limit(30)
                .forEach(team -> sender.sendMessage(messages().get("team.list-entry",
                        Messages.component("team", teams().display(team)),
                        Messages.text("members", Integer.toString(team.size())))));
    }

    // ---- Tab completion ----

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!teams().enabled()) {
            return List.of();
        }
        final Team team = sender instanceof Player player ? teams().teamOf(player.getUniqueId()) : null;
        final List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite", "add" -> plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> teams().teamOf(p.getUniqueId()) == null).map(Player::getName).forEach(options::add);
                case "kick", "promote", "demote", "transfer" -> options.addAll(memberNames(team));
                case "accept", "join", "deny", "decline" -> {
                    if (sender instanceof Player player) {
                        teams().invitesOf(player.getUniqueId()).forEach(t -> options.add(teams().plainName(t)));
                    }
                }
                case "info" -> teams().teams().forEach(t -> options.add(teams().plainName(t)));
                case "setclaim" -> options.add("confirm");
                case "disband" -> {
                    if (sender.hasPermission("gpaddons.team.admin")) {
                        teams().teams().forEach(t -> options.add(teams().plainName(t)));
                    }
                }
                case "rank", "ranks" -> options.addAll(RANK_SUBCOMMANDS);
                default -> {
                }
            }
        } else if (args[0].equalsIgnoreCase("rank") && team != null) {
            final String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3) {
                if (sub.equals("set")) {
                    options.addAll(memberNames(team));
                } else if (List.of("delete", "rename", "perm", "permission").contains(sub)) {
                    team.ranks().forEach(rank -> options.add(TeamNames.plain(rank.name())));
                }
            } else if (args.length == 4) {
                if (sub.equals("set")) {
                    team.ranks().forEach(rank -> options.add(TeamNames.plain(rank.name())));
                } else if (sub.startsWith("perm")) {
                    Arrays.stream(TeamPermission.values()).map(TeamPermission::id).forEach(options::add);
                }
            } else if (args.length == 5 && sub.startsWith("perm")) {
                options.addAll(List.of("true", "false"));
            }
        }
        final String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @NotNull
    private static List<String> memberNames(@Nullable Team team) {
        if (team == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        team.members().keySet().forEach(uuid -> names.add(Gp.name(uuid)));
        names.add(Gp.name(team.leader()));
        return names;
    }

}
