package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * A Minecraft input screen (1.21.7+) with one text box and confirm/cancel buttons, used instead of typing in
 * chat. Only touched when the server supports dialogs; {@link MenuManager} falls back to chat otherwise.
 */
final class InputDialog {

    private static final String KEY = "value";

    private InputDialog() {
    }

    /**
     * @param purpose guest, ban, entry, transfer or rename: picks the title, text and label
     */
    static void show(@NotNull GPAddons plugin, @NotNull Player player, @NotNull String purpose, int maxLength,
                     @Nullable Menu reopen, @NotNull Consumer<String> answer) {
        final var messages = plugin.messages();
        final String label = purpose.equals("rename") ? "dialog.name-label" : "dialog.player-label";
        final ClickCallback.Options once = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(5))
                .build();

        final ActionButton confirm = ActionButton.create(messages.get("dialog.confirm"), null, 120,
                DialogAction.customClick((view, audience) -> {
                    final String text = view.getText(KEY);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (text == null || text.isBlank()) {
                            if (reopen != null) {
                                reopen.open();
                            }
                            return;
                        }
                        answer.accept(text.strip());
                    });
                }, once));
        final ActionButton cancel = ActionButton.create(messages.get("dialog.cancel"), null, 120,
                DialogAction.customClick((view, audience) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && reopen != null) {
                        reopen.open();
                    }
                }), once));

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(messages.get("dialog." + purpose + ".title"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("dialog." + purpose + ".body"))))
                        .inputs(List.of(DialogInput.text(KEY, messages.get(label))
                                .width(250)
                                .maxLength(Math.max(1, maxLength))
                                .build()))
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));
        player.closeInventory();
        player.showDialog(dialog);
    }

}
