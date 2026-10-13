package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Everyone trusted in a claim: players, [permission] groups and players only let in by the whitelist.
 * Left-click to change someone's trust, right-click to remove it. "Everyone" sits at the bottom.
 */
public final class GuestsMenu extends PagedMenu<GuestsMenu.Guest> {

    /**
     * Someone trusted here
     *
     * @param id        a UUID or "[permission]"
     * @param level     their trust level, or null if they're only on the whitelist
     * @param manager   whether they're a manager
     */
    public record Guest(@NotNull String id, @Nullable ClaimPermission level, boolean manager) {

        @Nullable
        UUID uuid() {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        int rank() {
            if (manager) {
                return 0;
            }
            if (level == null) {
                return 4;
            }
            return switch (level.name()) {
                case "Build", "Edit" -> 1;
                case "Container", "Inventory" -> 2;
                default -> 3;
            };
        }

    }

    private final Claim claim;

    public GuestsMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.guests.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    @NotNull
    private String shown(@NotNull Guest guest) {
        final UUID uuid = guest.uuid();
        return uuid == null ? guest.id() : Gp.name(uuid);
    }

    @Override
    protected @NotNull List<Guest> entries() {
        final List<Guest> guests = new ArrayList<>();
        for (Map.Entry<String, ClaimPermission> entry : Gp.trust(claim).entrySet()) {
            if (!entry.getKey().equals(Gp.PUBLIC)) {
                guests.add(new Guest(entry.getKey(), entry.getValue(), Gp.isManager(claim, entry.getKey())));
            }
        }
        final ClaimData data = plugin.store().get(claim);
        if (data != null) {
            for (UUID uuid : data.entry()) {
                final String id = uuid.toString();
                if (guests.stream().noneMatch(g -> g.id().equals(id))) {
                    guests.add(new Guest(id, null, false));
                }
            }
        }
        guests.sort(Comparator.comparingInt(Guest::rank)
                .thenComparing(g -> shown(g).toLowerCase(Locale.ROOT)));
        return guests;
    }

    @NotNull
    private Component levelOf(@NotNull Guest guest) {
        if (guest.level() == null) {
            return messages().get("levels.entry");
        }
        return plugin.info().level(guest.level(), guest.manager());
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Guest guest) {
        final boolean manage = Gp.canManage(viewer, claim);
        final List<Component> lore = new ArrayList<>();
        final UUID uuid = guest.uuid();
        final String key = uuid == null ? "menu.guests.group" : "menu.guests.player";
        lore.addAll(messages().lore(key + ".lore", Messages.component("level", levelOf(guest))));
        if (guest.manager() && guest.level() != ClaimPermission.Manage) {
            lore.add(Messages.plain(messages().get("menu.guests.manager-badge")));
        }
        lore.addAll(manage ? messages().lore("menu.guests.click") : messages().lore("menu.locked"));
        if (uuid == null) {
            final String group = guest.id().length() > 2 ? guest.id().substring(1, guest.id().length() - 1) : guest.id();
            return Items.item("bookshelf", messages().itemName(key + ".name", Messages.text("group", group)), lore);
        }
        final OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return Items.head(player, messages().itemName(key + ".name", Messages.text("player", shown(guest))), lore);
    }

    @Override
    protected @Nullable Action action(@NotNull Guest guest) {
        if (!Gp.canManage(viewer, claim)) {
            return null;
        }
        return click -> {
            if (click.isRightClick()) {
                if (guest.level() == null && guest.uuid() != null) {
                    plugin.actions().removeEntry(viewer, claim, guest.uuid());
                } else {
                    plugin.actions().untrust(viewer, claim, guest.id(), shown(guest));
                }
                open();
            } else {
                new TrustMenu(plugin, viewer, claim, guest.id(), shown(guest), this).open();
            }
        };
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.guests.empty.lore"));
        if (Gp.canManage(viewer, claim)) {
            lore.addAll(messages().lore("menu.guests.empty.click"));
        }
        return Items.item("cobweb", messages().itemName("menu.guests.empty.name"), lore);
    }

    @Override
    protected @Nullable Action emptyAction() {
        if (!Gp.canManage(viewer, claim)) {
            return null;
        }
        return click -> plugin.menus().prompt(viewer, "prompt.guest", this, name -> {
            final OfflinePlayer target = Gp.findPlayer(name);
            if (target == null) {
                messages().send(viewer, "general.player-not-found", Messages.text("player", name));
                open();
                return;
            }
            chooseGuest(target);
        });
    }

    @Override
    protected void extras() {
        final boolean manage = Gp.canManage(viewer, claim);
        set(HEADER, plugin.info().item(claim, viewer, false));

        // Everyone
        final ClaimPermission everyone = Gp.trust(claim).get(Gp.PUBLIC);
        final List<Component> lore = new ArrayList<>(messages().lore("menu.guests.everyone.lore",
                Messages.component("level", plugin.info().level(everyone, false))));
        lore.addAll(manage ? messages().lore("menu.guests.everyone.click") : messages().lore("menu.locked"));
        set(bottom(CENTER), Items.item("oak_sign", messages().itemName("menu.guests.everyone.name"), lore),
                manage ? click -> new TrustMenu(plugin, viewer, claim, Gp.PUBLIC,
                        messages().raw("general.everyone"), this).open() : null);

        // Add a guest
        if (manage) {
            set(bottom(CORNER), Items.item("emerald", messages().itemName("menu.guests.add.name"),
                    withClick("menu.guests.add")), click -> new PlayerPickerMenu(plugin, viewer, this, "guest",
                    player -> !Gp.isOwner(claim, player.getUniqueId()), this::chooseGuest).open());
        }
    }

    private void chooseGuest(@NotNull OfflinePlayer target) {
        if (!Gp.exists(claim)) {
            viewer.closeInventory();
            messages().send(viewer, "general.claim-gone");
            return;
        }
        final String shown = target.getName() == null ? Gp.name(target.getUniqueId()) : target.getName();
        if (plugin.entry().isBanned(claim, target.getUniqueId())) {
            messages().send(viewer, "trust.banned-target", Messages.text("player", shown));
            open();
            return;
        }
        new TrustMenu(plugin, viewer, claim, target.getUniqueId().toString(), shown, this).open();
    }

    @NotNull
    private List<Component> withClick(@NotNull String key) {
        final List<Component> lore = new ArrayList<>(messages().lore(key + ".lore"));
        lore.addAll(messages().lore(key + ".click"));
        return lore;
    }

}
