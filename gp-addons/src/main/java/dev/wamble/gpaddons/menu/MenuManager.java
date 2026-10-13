package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Routes clicks to menus and handles "type it in chat" prompts.
 */
public final class MenuManager implements Listener {

    private static final long CLICK_COOLDOWN_MILLIS = 150L;
    private static final long PROMPT_TIMEOUT_MILLIS = 60_000L;

    private record Prompt(@NotNull Consumer<String> answer, @Nullable Menu reopen, long expires) {
    }

    private final GPAddons plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private boolean dialogsWork = true;

    public MenuManager(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    /**
     * Close the player's menu and ask them to type something in chat. Typing "cancel" (or waiting a minute)
     * gives up; cancelling re-opens {@code reopen}.
     */
    public void prompt(@NotNull Player player, @NotNull String messageKey, @Nullable Menu reopen,
                       @NotNull Consumer<String> answer) {
        // Minecraft's own input screen where the server has it (1.21.7+), otherwise typing in chat
        if (dialogsWork && plugin.getConfig().getBoolean("menus.use-input-screens", true)) {
            final String purpose = messageKey.substring(messageKey.lastIndexOf('.') + 1);
            final int maxLength = purpose.equals("rename") ? plugin.getConfig().getInt("names.max-length", 32) : 16;
            try {
                InputDialog.show(plugin, player, purpose, maxLength, reopen, answer);
                return;
            } catch (LinkageError | RuntimeException e) {
                dialogsWork = false;
                plugin.getLogger().warning("This server can't show input screens, so players will type in chat instead (" + e + ")");
            }
        }
        prompts.put(player.getUniqueId(), new Prompt(answer, reopen, System.currentTimeMillis() + PROMPT_TIMEOUT_MILLIS));
        player.closeInventory();
        plugin.messages().send(player, messageKey);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || prompt.expires() < System.currentTimeMillis()) {
            return;
        }
        event.setCancelled(true);
        final String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (text.isEmpty() || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "prompt.cancelled");
                if (prompt.reopen() != null) {
                    prompt.reopen().open();
                }
                return;
            }
            prompt.answer().accept(text);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        // Nothing moves in or out of a menu, including shift-clicks and double-clicks from the player's inventory
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || player != menu.viewer()) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MILLIS) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        // Opening and closing inventories isn't safe inside a click event, so buttons run on the next tick
        final ClickType click = event.getClick();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) {
                menu.click(slot, click);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

}
