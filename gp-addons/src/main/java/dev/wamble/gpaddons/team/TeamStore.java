package dev.wamble.gpaddons.team;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps teams in teams.yml. Like the claim data, changes are saved a few seconds later off the main thread,
 * writing a temporary file and swapping it in so a crash can't leave a half-written file.
 */
public final class TeamStore {

    private static final long SAVE_INTERVAL_TICKS = 100L;

    private final GPAddons plugin;
    private final Path file;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private boolean dirty;

    public TeamStore(@NotNull GPAddons plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("teams.yml");
    }

    @NotNull
    public Map<String, Team> teams() {
        return teams;
    }

    public void changed() {
        dirty = true;
    }

    public void load() {
        teams.clear();
        final File source = file.toFile();
        if (!source.exists()) {
            return;
        }
        final ConfigurationSection section = YamlConfiguration.loadConfiguration(source).getConfigurationSection("teams");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            try {
                final Team team = read(id, section.getConfigurationSection(id));
                if (team != null) {
                    teams.put(id, team);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Skipped unreadable team " + id, e);
            }
        }
    }

    @Nullable
    private static Team read(@NotNull String id, @Nullable ConfigurationSection t) {
        if (t == null || t.getString("leader") == null || t.getString("name") == null) {
            return null;
        }
        final Team team = new Team(id, t.getString("name"), UUID.fromString(t.getString("leader")), t.getLong("created"));
        for (Map<?, ?> map : t.getMapList("ranks")) {
            final Object rankId = map.get("id");
            final Object name = map.get("name");
            if (rankId == null || name == null) {
                continue;
            }
            final Rank rank = new Rank(rankId.toString(), name.toString());
            if (map.get("permissions") instanceof List<?> permissions) {
                for (Object permission : permissions) {
                    final TeamPermission parsed = TeamPermission.byId(String.valueOf(permission));
                    if (parsed != null) {
                        rank.permissions().add(parsed);
                    }
                }
            }
            team.ranks().add(rank);
        }
        if (team.ranks().isEmpty()) {
            team.ranks().add(new Rank("member", "Member"));
        }
        final ConfigurationSection members = t.getConfigurationSection("members");
        if (members != null) {
            for (String uuid : members.getKeys(false)) {
                final String rankId = members.getString(uuid, "");
                team.members().put(UUID.fromString(uuid),
                        team.rank(rankId) != null ? rankId : team.lowestRank().id());
            }
        }
        team.members().remove(team.leader());
        final ConfigurationSection home = t.getConfigurationSection("home");
        if (home != null && home.getString("world") != null) {
            team.home(new ClaimData.Home(home.getString("world"), home.getDouble("x"), home.getDouble("y"),
                    home.getDouble("z"), (float) home.getDouble("yaw"), (float) home.getDouble("pitch")));
        }
        team.points(t.getInt("points"));
        team.completed().addAll(t.getStringList("quests.completed"));
        final ConfigurationSection progress = t.getConfigurationSection("quests.progress");
        if (progress != null) {
            progress.getKeys(false).forEach(key -> team.progress().put(key, progress.getLong(key)));
        }
        final ConfigurationSection upgrades = t.getConfigurationSection("upgrades");
        if (upgrades != null) {
            // The Team Vault upgrade is now Team Storage
            upgrades.getKeys(false).forEach(key -> team.upgrades().put(key.equals("team_vault") ? "team_storage" : key,
                    upgrades.getInt(key)));
        }
        if (t.isLong("claim") || t.isInt("claim")) {
            team.teamClaim(t.getLong("claim"));
        }
        // Saved as "vault" before it was renamed
        final ConfigurationSection storage = t.isConfigurationSection("storage") ? t.getConfigurationSection("storage")
                : t.getConfigurationSection("vault");
        if (storage != null) {
            for (String slot : storage.getKeys(false)) {
                try {
                    final int index = Integer.parseInt(slot);
                    final String data = storage.getString(slot);
                    if (index >= 0 && index < team.storage().length && data != null) {
                        team.storage()[index] = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
                    }
                } catch (RuntimeException e) {
                    // One unreadable item shouldn't lose the rest of the storage
                }
            }
        }
        return team;
    }

    public void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                dirty = false;
                final String text = serialize();
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(text));
            }
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    public void saveNow() {
        dirty = false;
        write(serialize());
    }

    @NotNull
    private String serialize() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (Team team : teams.values()) {
            final String path = "teams." + team.id();
            yaml.set(path + ".name", team.name());
            yaml.set(path + ".leader", team.leader().toString());
            yaml.set(path + ".created", team.created());
            final List<Map<String, Object>> ranks = new ArrayList<>();
            for (Rank rank : team.ranks()) {
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rank.id());
                map.put("name", rank.name());
                map.put("permissions", rank.permissions().stream().map(TeamPermission::id).toList());
                ranks.add(map);
            }
            yaml.set(path + ".ranks", ranks);
            team.members().forEach((uuid, rank) -> yaml.set(path + ".members." + uuid, rank));
            final ClaimData.Home home = team.home();
            if (home != null) {
                yaml.set(path + ".home.world", home.world());
                yaml.set(path + ".home.x", home.x());
                yaml.set(path + ".home.y", home.y());
                yaml.set(path + ".home.z", home.z());
                yaml.set(path + ".home.yaw", (double) home.yaw());
                yaml.set(path + ".home.pitch", (double) home.pitch());
            }
            yaml.set(path + ".points", team.points());
            yaml.set(path + ".quests.completed", new ArrayList<>(team.completed()));
            team.progress().forEach((quest, value) -> yaml.set(path + ".quests.progress." + quest, value));
            team.upgrades().forEach((upgrade, level) -> yaml.set(path + ".upgrades." + upgrade, level));
            if (team.teamClaim() != null) {
                yaml.set(path + ".claim", team.teamClaim());
            }
            for (int i = 0; i < team.storage().length; i++) {
                final ItemStack item = team.storage()[i];
                if (item != null && !item.getType().isAir()) {
                    yaml.set(path + ".storage." + i, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                }
            }
        }
        return yaml.saveToString();
    }

    private synchronized void write(@NotNull String text) {
        try {
            Files.createDirectories(file.getParent());
            final Path temp = file.resolveSibling("teams.yml.tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save teams.yml", e);
            dirty = true;
        }
    }

}
