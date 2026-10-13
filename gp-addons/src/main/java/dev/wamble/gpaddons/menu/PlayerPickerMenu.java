package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Pick a player by clicking their head, so nobody has to type names. Players who are offline can still be
 * picked by typing their name (the book in the corner).
 */
public final class PlayerPickerMenu extends PagedMenu<Player> {

    private final String purpose;
    private final Predicate<Player> shown;
    private final Consumer<OfflinePlayer> picked;

    /**
     * @param purpose guest, ban, entry or transfer: picks the title and the chat prompt
     * @param shown   which online players to offer
     * @param picked  what to do with the chosen player
     */
    public PlayerPickerMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @Nullable Menu back,
                            @NotNull String purpose, @NotNull Predicate<Player> shown,
                            @NotNull Consumer<OfflinePlayer> picked) {
        super(plugin, viewer, back);
        this.purpose = purpose;
        this.shown = shown;
        this.picked = picked;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.picker." + purpose + ".title");
    }

    @Override
    protected boolean valid() {
        return back == null || back.valid();
    }

    @Override
    protected @NotNull List<Player> entries() {
        final List<Player> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player != viewer && viewer.canSee(player) && shown.test(player)) {
                players.add(player);
            }
        }
        players.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return players;
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Player player) {
        return Items.head(player, messages().itemName("menu.picker.player.name", Messages.text("player", player.getName())),
                messages().lore("menu.picker.player.click"));
    }

    @Override
    protected @Nullable Action action(@NotNull Player player) {
        return click -> picked.accept(player);
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        return Items.item("cobweb", messages().itemName("menu.picker.empty.name"), messages().lore("menu.picker.empty.lore"));
    }

    @Override
    protected void extras() {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.picker.type.lore"));
        lore.addAll(messages().lore("menu.picker.type.click"));
        set(bottom(CORNER), Items.item("writable_book", messages().itemName("menu.picker.type.name"), lore),
                click -> plugin.menus().prompt(viewer, "prompt." + purpose, this, name -> {
                    final OfflinePlayer target = Gp.findPlayer(name);
                    if (target == null) {
                        messages().send(viewer, "general.player-not-found", Messages.text("player", name));
                        open();
                        return;
                    }
                    picked.accept(target);
                }));
    }

}
