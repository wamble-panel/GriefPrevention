package dev.wamble.gpaddons.text;

import dev.wamble.gpaddons.GPAddons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Everything players see, read from messages.yml (MiniMessage format). Keys missing from the server's copy
 * fall back to the default file inside the jar, so new messages show up after an update. Lines whose meaning
 * changed in an update are replaced in the server's copy once (see {@link #UPDATED}).
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Keys whose text changed, by the messages-version that changed them */
    private static final Map<Integer, List<String>> UPDATED = Map.of(
            2, List.of("team.help", "menu.flags.lure-locked", "team-upgrades.bountiful_harvest.description",
                    "team-upgrades.animal_husbandry.description", "team-upgrades.monster_lure.description"),
            3, List.of("menu.team.home.lore"));

    private final GPAddons plugin;
    private YamlConfiguration config = new YamlConfiguration();

    public Messages(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        final File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't read the default messages", e);
        }
        update(file);
    }

    /** Bring changed lines in an older messages.yml up to date */
    private void update(@NotNull File file) {
        final Configuration defaults = config.getDefaults();
        final int version = config.isSet("messages-version") ? config.getInt("messages-version") : 1;
        final int latest = defaults == null ? version : defaults.getInt("messages-version", version);
        if (defaults == null || version >= latest) {
            return;
        }
        UPDATED.forEach((changedIn, keys) -> {
            if (changedIn > version && changedIn <= latest) {
                keys.stream().filter(defaults::contains).forEach(key -> config.set(key, defaults.get(key)));
            }
        });
        config.set("messages-version", latest);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't update messages.yml", e);
        }
    }

    /** The MiniMessage text of a key, or "" if it doesn't exist */
    @NotNull
    public String raw(@NotNull String key) {
        final String text = config.getString(key);
        return text == null ? "" : text;
    }

    @NotNull
    public Component get(@NotNull String key, @NotNull TagResolver... placeholders) {
        return MINI.deserialize(raw(key), placeholders);
    }

    /** Send a chat message with the prefix. Messages set to "" are not sent */
    public void send(@NotNull CommandSender to, @NotNull String key, @NotNull TagResolver... placeholders) {
        final String text = raw(key);
        if (!text.isEmpty()) {
            to.sendMessage(MINI.deserialize(raw("prefix") + text, placeholders));
        }
    }

    /** An item name: not italic, unlike Minecraft's default for custom names */
    @NotNull
    public Component itemName(@NotNull String key, @NotNull TagResolver... placeholders) {
        return plain(get(key, placeholders));
    }

    /** Item lore lines, from a YAML list or a single text with line breaks; "" lines are blank lines */
    @NotNull
    public List<Component> lore(@NotNull String key, @NotNull TagResolver... placeholders) {
        final List<String> lines = config.isList(key) ? config.getStringList(key) : Arrays.asList(raw(key).split("\n", -1));
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            return List.of();
        }
        return lines.stream().map(line -> line.isEmpty() ? Component.empty() : plain(MINI.deserialize(line, placeholders))).toList();
    }

    @NotNull
    public static Component plain(@NotNull Component component) {
        return Component.text().append(component)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    /** Text typed by players, shown exactly as typed */
    @NotNull
    public static TagResolver text(@NotNull String key, @NotNull String value) {
        return Placeholder.unparsed(key, value);
    }

    /** Text from messages.yml, with its formatting */
    @NotNull
    public TagResolver formatted(@NotNull String placeholder, @NotNull String messageKey, @NotNull TagResolver... inner) {
        return Placeholder.component(placeholder, get(messageKey, inner));
    }

    @NotNull
    public static TagResolver component(@NotNull String key, @NotNull Component value) {
        return Placeholder.component(key, value);
    }

    /** A tag that runs a command when its text is clicked, e.g. &lt;accept&gt;[accept]&lt;/accept&gt; */
    @NotNull
    public static TagResolver command(@NotNull String key, @NotNull String command) {
        return TagResolver.resolver(key, Tag.styling(ClickEvent.runCommand(command)));
    }

}
