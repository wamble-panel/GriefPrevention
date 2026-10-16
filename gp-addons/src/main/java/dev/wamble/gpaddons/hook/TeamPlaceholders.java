package dev.wamble.gpaddons.hook;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.team.TeamService;
import dev.wamble.gpaddons.team.TeamUpgrade;
import dev.wamble.gpaddons.util.Gp;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI placeholders for teams, e.g. %gpaddons_team_points% on a scoreboard or in TAB.
 * Only loaded when PlaceholderAPI is installed (this class can't even be loaded without it).
 */
public final class TeamPlaceholders extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('§')
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final GPAddons plugin;

    public TeamPlaceholders(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gpaddons";
    }

    @Override
    public @NotNull String getAuthor() {
        return "wamble";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        final String key = params.toLowerCase(Locale.ROOT);
        final TeamService teams = plugin.teams();
        final Team team = player == null || !teams.enabled() ? null : teams.teamOf(player.getUniqueId());
        if (key.equals("has_team")) {
            return team != null ? "true" : "false";
        }
        if (key.startsWith("team_upgrade_")) {
            final String id = key.substring("team_upgrade_".length());
            for (TeamUpgrade upgrade : TeamUpgrade.values()) {
                if (upgrade.id().equals(id)) {
                    return Integer.toString(team == null ? 0 : team.level(upgrade));
                }
            }
            return null;
        }
        return switch (key) {
            case "team_points" -> Integer.toString(team == null ? 0 : team.points());
            case "team_name" -> team == null ? "" : LEGACY.serialize(teams.display(team));
            case "team_name_plain" -> team == null ? "" : teams.plainName(team);
            case "team_leader" -> team == null ? "" : Gp.name(team.leader());
            case "team_members" -> Integer.toString(team == null ? 0 : team.size());
            case "team_online" -> Integer.toString(team == null ? 0 : teams.online(team).size());
            case "team_rank" -> team == null ? "" : LEGACY.serialize(teams.rankDisplay(team, player.getUniqueId()));
            case "team_rank_plain" -> team == null ? "" : plain(teams.rankDisplay(team, player.getUniqueId()));
            case "team_quests_done" -> Integer.toString(team == null ? 0 : team.completed().size());
            case "team_claim" -> {
                final Claim claim = team == null ? null : teams.teamClaim(team);
                yield claim == null ? "" : plugin.info().name(claim);
            }
            default -> null;
        };
    }

    @NotNull
    private static String plain(@NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

}
