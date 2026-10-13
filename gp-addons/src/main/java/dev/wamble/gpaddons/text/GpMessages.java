package dev.wamble.gpaddons.text;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.DataStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Restyles GriefPrevention's own messages (plugins/GriefPreventionData/messages.yml) in the GPAddons palette.
 * <p>
 * A message is only replaced if it's still GriefPrevention's default or the text GPAddons put there before, so
 * messages an admin changed by hand are kept (unless forced). The original file is backed up once, and
 * GriefPrevention reloads its messages straight away, like /gpreload.
 */
public final class GpMessages {

    private static final String RESOURCE = "griefprevention-messages.yml";
    private static final String BACKUP = "messages.yml.before-gpaddons";

    private final GPAddons plugin;

    public GpMessages(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    @NotNull
    private File gpFile() {
        return new File(plugin.getDataFolder().getParentFile(), "GriefPreventionData" + File.separator + "messages.yml");
    }

    /** What GPAddons last wrote for each message, to tell its own text from an admin's edits */
    @NotNull
    private File record() {
        return new File(plugin.getDataFolder(), "griefprevention-messages-installed.yml");
    }

    /**
     * @param force also replace messages that were changed by hand
     * @return a short summary
     */
    @NotNull
    public String restyle(boolean force) {
        final ConfigurationSection styled = styled();
        if (styled == null) {
            return "The restyled GriefPrevention messages are missing from the GPAddons jar";
        }
        final File file = gpFile();
        final YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        final YamlConfiguration installed = YamlConfiguration.loadConfiguration(record());
        int changed = 0;
        int kept = 0;
        for (String key : styled.getKeys(false)) {
            final String text = styled.getString(key + ".Text");
            final String original = styled.getString(key + ".Default");
            if (text == null) {
                continue;
            }
            final String path = "Messages." + key + ".Text";
            final String now = current.getString(path);
            if (text.equals(now)) {
                installed.set(key, text);
            } else if (force || now == null || now.equals(original) || now.equals(installed.getString(key))) {
                current.set(path, text);
                installed.set(key, text);
                changed++;
            } else {
                kept++;
            }
        }
        if (changed > 0) {
            try {
                backup(file);
                current.save(file);
                installed.save(record());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't save GriefPrevention's messages.yml", e);
                return "Couldn't save GriefPrevention's messages.yml: " + e.getMessage();
            }
            reloadGriefPrevention();
        }
        String summary = "Restyled " + changed + " GriefPrevention messages";
        if (kept > 0) {
            summary += "; kept " + kept + " you changed yourself (/gpaddons gp-messages force restyles those too)";
        }
        return summary;
    }

    /** Put GriefPrevention's messages back as they were before GPAddons restyled them, and stop restyling */
    @NotNull
    public String restore() {
        final File file = gpFile();
        final File backup = new File(file.getParentFile(), BACKUP);
        plugin.getConfig().set("griefprevention-messages.restyle", false);
        plugin.saveConfig();
        if (!backup.exists()) {
            return "There's no backup of GriefPrevention's messages to restore (" + backup.getPath() + ")";
        }
        try {
            Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(record().toPath());
        } catch (IOException e) {
            return "Couldn't restore GriefPrevention's messages: " + e.getMessage();
        }
        reloadGriefPrevention();
        return "Restored GriefPrevention's original messages and turned restyling off";
    }

    private static void backup(@NotNull File file) throws IOException {
        final File backup = new File(file.getParentFile(), BACKUP);
        if (file.exists() && !backup.exists()) {
            Files.copy(file.toPath(), backup.toPath());
        }
    }

    private ConfigurationSection styled() {
        try (InputStream in = plugin.getResource(RESOURCE)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getConfigurationSection("Messages");
        } catch (IOException e) {
            return null;
        }
    }

    /** The same as /gpreload does for messages */
    private void reloadGriefPrevention() {
        try {
            final Method load = DataStore.class.getDeclaredMethod("loadMessages");
            load.setAccessible(true);
            load.invoke(Gp.data());
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Restyled GriefPrevention's messages; run /gpreload or restart to see them (" + e + ")");
        }
    }

}
