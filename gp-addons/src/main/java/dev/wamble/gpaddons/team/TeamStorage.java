package dev.wamble.gpaddons.team;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Team Storage upgrade: a shared chest of one to three rows. Only one member can have it open at a time,
 * so items can't be duplicated, and it's saved with the team when closed.
 */
public final class TeamStorage implements Listener {

    /** Marks a storage inventory and remembers whose it is */
    private static final class Holder implements InventoryHolder {

        private final Team team;
        private Inventory inventory;

        private Holder(@NotNull Team team) {
            this.team = team;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

    }

    private final GPAddons plugin;
    /** Open storage by team ID */
    private final Map<String, Inventory> open = new HashMap<>();

    public TeamStorage(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    public void open(@NotNull Player player) {
        final TeamService teams = plugin.teams();
        final Team team = teams.enabled() ? teams.teamOf(player.getUniqueId()) : null;
        if (team == null) {
            plugin.messages().send(player, teams.enabled() ? "team.no-team" : "team.disabled");
            return;
        }
        final int rows = Math.min(3, team.level(TeamUpgrade.TEAM_STORAGE));
        if (rows == 0) {
            plugin.messages().send(player, "team.storage-locked");
            return;
        }
        final Inventory already = open.get(team.id());
        if (already != null && !already.getViewers().isEmpty()) {
            plugin.messages().send(player, "team.storage-in-use",
                    Messages.text("player", already.getViewers().get(0).getName()));
            return;
        }
        final Holder holder = new Holder(team);
        final Component title = plugin.messages().get("team.storage-title", Messages.component("team", teams.display(team)));
        final Inventory inventory = plugin.getServer().createInventory(holder, rows * 9, title);
        holder.inventory = inventory;
        for (int i = 0; i < rows * 9; i++) {
            inventory.setItem(i, team.storage()[i]);
        }
        open.put(team.id(), inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            save(holder.team, event.getInventory());
            open.remove(holder.team.id());
        }
    }

    private void save(@NotNull Team team, @NotNull Inventory inventory) {
        for (int i = 0; i < inventory.getSize() && i < team.storage().length; i++) {
            final ItemStack item = inventory.getItem(i);
            team.storage()[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        plugin.teams().changed();
    }

    /** Close (and save) a team's storage, e.g. before the team is disbanded */
    public void close(@NotNull Team team) {
        final Inventory inventory = open.get(team.id());
        if (inventory != null) {
            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                viewer.closeInventory();
            }
            save(team, inventory);
            open.remove(team.id());
        }
    }

    /** Close the storage for a player who is leaving the team */
    public void closeFor(@NotNull Player player, @NotNull Team team) {
        final Inventory inventory = open.get(team.id());
        if (inventory != null && inventory.getViewers().contains(player)) {
            player.closeInventory();
        }
    }

    /** Close and save every open storage (when the server stops) */
    public void closeAll() {
        final List<String> ids = new ArrayList<>(open.keySet());
        for (String id : ids) {
            final Team team = plugin.teams().teams().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
            if (team != null) {
                close(team);
            }
        }
    }

}
