package dev.wamble.gpaddons.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Team and rank names. Names may be coloured with hex codes (&amp;#FFC8DD or &lt;#FFC8DD&gt;), gradients and
 * bold/italic, but nothing else, so a name can't carry click or hover actions.
 */
public final class TeamNames {

    private static final MiniMessage STYLED = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.gradient(), StandardTags.rainbow(),
                    StandardTags.decorations(), StandardTags.reset()))
            .build();
    private static final Pattern LEGACY_HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_]+");
    private static final TextColor DEFAULT_TEAM = TextColor.color(0xFFC8DD);
    private static final TextColor DEFAULT_RANK = TextColor.color(0xCDB4DB);

    private TeamNames() {
    }

    /** Turn &amp;#RRGGBB into MiniMessage colours */
    @NotNull
    public static String normalize(@NotNull String raw) {
        return LEGACY_HEX.matcher(raw.strip()).replaceAll("<#$1>");
    }

    /** The name without colours */
    @NotNull
    public static String plain(@NotNull String raw) {
        return PlainTextComponentSerializer.plainText().serialize(STYLED.deserialize(raw));
    }

    public static boolean isStyled(@NotNull String raw) {
        return !plain(raw).equals(raw);
    }

    /** Whether the plain name is only letters, numbers and underscores, and the right length */
    public static boolean valid(@NotNull String raw, int min, int max) {
        final String plain = plain(raw);
        return plain.length() >= min && plain.length() <= max && PLAIN.matcher(plain).matches();
    }

    @NotNull
    public static Component team(@NotNull String raw) {
        return isStyled(raw) ? STYLED.deserialize(raw) : Component.text(raw, DEFAULT_TEAM);
    }

    @NotNull
    public static Component rank(@NotNull String raw) {
        return isStyled(raw) ? STYLED.deserialize(raw) : Component.text(raw, DEFAULT_RANK);
    }

}
