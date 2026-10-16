package dev.wamble.gpaddons.data;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.flag.ClaimFlag;
import me.ryanhamshire.GriefPrevention.Claim;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps every claim's {@link ClaimData} in {@code data.yml}, keyed by GriefPrevention claim ID.
 * <p>
 * Changes are made on the main thread and saved a few seconds later off it. Each save writes a temporary
 * file and then swaps it in, so a crash mid-save leaves the previous data intact.
 */
public final class ClaimStore {

    private static final long SAVE_INTERVAL_TICKS = 100L;

    private final GPAddons plugin;
    private final Path file;
    private final Map<Long, ClaimData> claims = new HashMap<>();
    private boolean importedBanFromClaim;
    private boolean dirty;

    public ClaimStore(@NotNull GPAddons plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
    }

    public void load() {
        claims.clear();
        final File source = file.toFile();
        if (!source.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        importedBanFromClaim = yaml.getBoolean("imported-banfromclaim", false);
        final ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                final ConfigurationSection c = section.getConfigurationSection(key);
                if (c != null) {
                    claims.put(Long.parseLong(key), read(c));
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Skipped unreadable data for claim " + key, e);
            }
        }
    }

    @NotNull
    private static ClaimData read(@NotNull ConfigurationSection c) {
        final ClaimData data = new ClaimData();
        data.name(c.getString("name"));
        data.icon(c.getString("icon"));
        final ConfigurationSection home = c.getConfigurationSection("home");
        if (home != null && home.getString("world") != null) {
            data.home(new ClaimData.Home(home.getString("world"), home.getDouble("x"), home.getDouble("y"),
                    home.getDouble("z"), (float) home.getDouble("yaw"), (float) home.getDouble("pitch")));
        }
        data.whitelist(c.getBoolean("whitelist", false));
        for (String uuid : c.getStringList("entry")) {
            parse(uuid).ifPresent(data.entry()::add);
        }
        final ConfigurationSection bans = c.getConfigurationSection("bans");
        if (bans != null) {
            for (String key : bans.getKeys(false)) {
                parse(key).ifPresent(banned -> data.bans().put(banned, new ClaimData.Ban(
                        parse(bans.getString(key + ".by", "")).orElse(null),
                        bans.getLong(key + ".at", 0L))));
            }
        }
        final ConfigurationSection flags = c.getConfigurationSection("flags");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                final ClaimFlag flag = ClaimFlag.byId(key);
                if (flag != null) {
                    data.flags().put(flag, flags.getBoolean(key));
                }
            }
        }
        return data;
    }

    private static java.util.Optional<UUID> parse(@Nullable String uuid) {
        try {
            return uuid == null || uuid.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    /** Start saving changes every few seconds */
    public void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    @Nullable
    public ClaimData get(@NotNull Claim claim) {
        return claim.getID() == null ? null : claims.get(claim.getID());
    }

    @NotNull
    public ClaimData edit(@NotNull Claim claim) {
        dirty = true;
        return claims.computeIfAbsent(claim.getID(), id -> new ClaimData());
    }

    public void changed() {
        dirty = true;
    }

    public void remove(long claimId) {
        if (claims.remove(claimId) != null) {
            dirty = true;
        }
    }

    public boolean importedBanFromClaim() {
        return importedBanFromClaim;
    }

    public void importedBanFromClaim(boolean imported) {
        this.importedBanFromClaim = imported;
        dirty = true;
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        final String text = serialize();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(text));
    }

    /** Save straight away, on this thread (used when the server stops) */
    public void saveNow() {
        dirty = false;
        write(serialize());
    }

    @NotNull
    private String serialize() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("imported-banfromclaim", importedBanFromClaim);
        claims.forEach((id, data) -> {
            if (data.isEmpty()) {
                return;
            }
            final String path = "claims." + id;
            yaml.set(path + ".name", data.name());
            yaml.set(path + ".icon", data.icon());
            final ClaimData.Home home = data.home();
            if (home != null) {
                yaml.set(path + ".home.world", home.world());
                yaml.set(path + ".home.x", home.x());
                yaml.set(path + ".home.y", home.y());
                yaml.set(path + ".home.z", home.z());
                yaml.set(path + ".home.yaw", (double) home.yaw());
                yaml.set(path + ".home.pitch", (double) home.pitch());
            }
            if (data.whitelist()) {
                yaml.set(path + ".whitelist", true);
            }
            if (!data.entry().isEmpty()) {
                yaml.set(path + ".entry", data.entry().stream().map(UUID::toString).toList());
            }
            data.bans().forEach((banned, ban) -> {
                yaml.set(path + ".bans." + banned + ".by", ban.by() == null ? null : ban.by().toString());
                yaml.set(path + ".bans." + banned + ".at", ban.at());
            });
            data.flags().forEach((flag, value) -> yaml.set(path + ".flags." + flag.id(), value));
        });
        return yaml.saveToString();
    }

    private synchronized void write(@NotNull String text) {
        try {
            Files.createDirectories(file.getParent());
            final Path temp = file.resolveSibling("data.yml.tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data.yml", e);
            dirty = true;
        }
    }

    /** IDs with stored data, for cleaning up claims GriefPrevention no longer has */
    @NotNull
    public List<Long> ids() {
        return List.copyOf(claims.keySet());
    }

}
