package dev.wamble.gpaddons.menu;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.entry.EntryService;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Claim changes made from menus and commands, following GriefPrevention's own rules for who may do what.
 * Each method tells the player how it went.
 */
public final class ClaimActions {

    private final GPAddons plugin;

    public ClaimActions(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Nullable
    private static UUID uuid(@NotNull String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether a trust change may go ahead: other plugins can cancel it, as with /trust */
    private static boolean allowed(@NotNull Player actor, @NotNull Claim claim, @Nullable ClaimPermission level,
                                   boolean given, @NotNull String id) {
        final TrustChangedEvent event = new TrustChangedEvent(actor, claim, level, given, id);
        actor.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    // ---- Trust ----

    /** Give a player, [permission] group or everyone ("public") a trust level, like /trust */
    public boolean trust(@NotNull Player actor, @NotNull Claim claim, @NotNull String id, @NotNull String shown,
                         @NotNull ClaimPermission level) {
        if (!Gp.canManage(actor, claim)) {
            messages().send(actor, "general.cannot-manage");
            return false;
        }
        if (!Gp.isAdmin(actor) && !Gp.has(actor, claim, level)) {
            messages().send(actor, "trust.too-high");
            return false;
        }
        final UUID target = uuid(id);
        if (target != null && plugin.entry().isBanned(claim, target)) {
            messages().send(actor, "trust.banned-target", Messages.text("player", shown));
            return false;
        }
        if (!allowed(actor, claim, level, true, id)) {
            return false;
        }
        claim.setPermission(id, level);
        if (target != null) {
            removeEntry(claim, target);
        }
        Gp.save(claim);
        messages().send(actor, "trust.set", Messages.text("player", shown),
                Messages.component("level", plugin.info().level(level, false)));
        return true;
    }

    /** Make a player a manager (/permissiontrust) or stop them being one. Only the owner may */
    public boolean manager(@NotNull Player actor, @NotNull Claim claim, @NotNull String id, @NotNull String shown,
                           boolean manager) {
        if (!Gp.canEdit(actor, claim)) {
            messages().send(actor, "trust.manager-owner-only");
            return false;
        }
        final UUID target = uuid(id);
        if (manager && target != null && plugin.entry().isBanned(claim, target)) {
            messages().send(actor, "trust.banned-target", Messages.text("player", shown));
            return false;
        }
        if (!allowed(actor, claim, null, manager, id)) {
            return false;
        }
        if (manager) {
            if (!Gp.isManager(claim, id)) {
                claim.managers.add(id);
            }
        } else {
            claim.managers.removeIf(m -> m.equalsIgnoreCase(id));
        }
        Gp.save(claim);
        messages().send(actor, manager ? "trust.manager-on" : "trust.manager-off", Messages.text("player", shown));
        return true;
    }

    /** Take away all of someone's trust here, like /untrust. Only the owner may remove a manager */
    public boolean untrust(@NotNull Player actor, @NotNull Claim claim, @NotNull String id, @NotNull String shown) {
        if (!Gp.canManage(actor, claim)) {
            messages().send(actor, "general.cannot-manage");
            return false;
        }
        if (Gp.isManager(claim, id) && !Gp.canEdit(actor, claim)) {
            messages().send(actor, "trust.manager-owner-only");
            return false;
        }
        final UUID target = uuid(id);
        if (Gp.trust(claim).containsKey(id.toLowerCase(java.util.Locale.ROOT))) {
            if (!allowed(actor, claim, null, false, id)) {
                return false;
            }
            claim.dropPermission(id);
            Gp.save(claim);
        }
        if (target != null) {
            removeEntry(claim, target);
            final Player online = plugin.getServer().getPlayer(target);
            if (online != null) {
                plugin.entry().ejectIfInside(online, claim);
            }
        }
        messages().send(actor, "trust.removed", Messages.text("player", shown));
        return true;
    }

    /** Swap a player's trust for whitelist entry: they may come in while the whitelist is on, nothing more */
    public boolean entryOnly(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        final String id = target.getUniqueId().toString();
        final String shown = target.getName() == null ? Gp.name(target.getUniqueId()) : target.getName();
        if (Gp.isManager(claim, id) && !Gp.canEdit(actor, claim)) {
            messages().send(actor, "trust.manager-owner-only");
            return false;
        }
        final EntryService.Result result = plugin.entry().addEntry(actor, claim, target);
        switch (result) {
            case NOT_ALLOWED -> {
                messages().send(actor, "general.cannot-manage");
                return false;
            }
            case NOT_FOUND -> {
                messages().send(actor, "entry.entry-banned", Messages.text("player", shown));
                return false;
            }
            default -> {
            }
        }
        if (Gp.trust(claim).containsKey(id) && allowed(actor, claim, null, false, id)) {
            claim.dropPermission(id);
            Gp.save(claim);
        }
        messages().send(actor, "trust.entry-set", Messages.text("player", shown));
        return true;
    }

    private void removeEntry(@NotNull Claim claim, @NotNull UUID target) {
        final ClaimData data = plugin.store().get(claim);
        if (data != null && data.entry().remove(target)) {
            plugin.store().changed();
        }
    }

    // ---- Bans and whitelist ----

    public boolean ban(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        final String shown = target.getName() == null ? Gp.name(target.getUniqueId()) : target.getName();
        final EntryService.Result result = plugin.entry().ban(actor, claim, target);
        switch (result) {
            case DONE -> messages().send(actor, "entry.banned", Messages.text("player", shown));
            case NOT_ALLOWED -> messages().send(actor, denied(actor, "gpaddons.ban"));
            case SELF -> messages().send(actor, "entry.cannot-ban-self");
            case OWNER -> messages().send(actor, "entry.cannot-ban-owner");
            case ALREADY -> messages().send(actor, "entry.already-banned", Messages.text("player", shown));
            default -> {
            }
        }
        return result == EntryService.Result.DONE;
    }

    public boolean unban(@NotNull Player actor, @NotNull Claim claim, @NotNull UUID target) {
        final String shown = Gp.name(target);
        final EntryService.Result result = plugin.entry().unban(actor, claim, target);
        switch (result) {
            case DONE -> messages().send(actor, "entry.unbanned", Messages.text("player", shown));
            case NOT_ALLOWED -> messages().send(actor, denied(actor, "gpaddons.ban"));
            default -> messages().send(actor, "entry.not-banned", Messages.text("player", shown));
        }
        return result == EntryService.Result.DONE;
    }

    public boolean whitelist(@NotNull Player actor, @NotNull Claim claim, boolean on) {
        final EntryService.Result result = plugin.entry().setWhitelist(actor, claim, on);
        if (result == EntryService.Result.DONE) {
            messages().send(actor, on ? "entry.whitelist-on" : "entry.whitelist-off");
            return true;
        }
        messages().send(actor, denied(actor, "gpaddons.whitelist"));
        return false;
    }

    public boolean addEntry(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        final String shown = target.getName() == null ? Gp.name(target.getUniqueId()) : target.getName();
        final EntryService.Result result = plugin.entry().addEntry(actor, claim, target);
        switch (result) {
            case DONE -> messages().send(actor, "entry.entry-added", Messages.text("player", shown));
            case NOT_ALLOWED -> messages().send(actor, denied(actor, "gpaddons.whitelist"));
            case NOT_FOUND -> messages().send(actor, "entry.entry-banned", Messages.text("player", shown));
            case ALREADY -> messages().send(actor, "entry.entry-already", Messages.text("player", shown));
            default -> {
            }
        }
        return result == EntryService.Result.DONE;
    }

    public boolean removeEntry(@NotNull Player actor, @NotNull Claim claim, @NotNull UUID target) {
        final String shown = Gp.name(target);
        final EntryService.Result result = plugin.entry().removeEntry(actor, claim, target);
        switch (result) {
            case DONE -> messages().send(actor, "entry.entry-removed", Messages.text("player", shown));
            case NOT_ALLOWED -> messages().send(actor, denied(actor, "gpaddons.whitelist"));
            default -> messages().send(actor, "entry.entry-not-found", Messages.text("player", shown));
        }
        return result == EntryService.Result.DONE;
    }

    /** Why a player can't ban or whitelist: missing the permission, or not a manager of the claim */
    @NotNull
    private static String denied(@NotNull Player actor, @NotNull String permission) {
        return actor.hasPermission(permission) ? "general.cannot-manage" : "general.no-permission";
    }

    // ---- Name ----

    /** Name a claim, or clear its name with {@code null} */
    public boolean rename(@NotNull Player actor, @NotNull Claim claim, @Nullable String name) {
        if (!Gp.isAdmin(actor) && !actor.hasPermission("gpaddons.rename")) {
            messages().send(actor, "general.no-permission");
            return false;
        }
        if (!Gp.canManage(actor, claim)) {
            messages().send(actor, "general.cannot-manage");
            return false;
        }
        final int max = Math.max(1, plugin.getConfig().getInt("names.max-length", 32));
        final String clean = name == null ? null : name.strip();
        if (clean != null && (clean.isEmpty() || clean.length() > max)) {
            messages().send(actor, "names.invalid", Messages.text("max", Integer.toString(max)));
            return false;
        }
        plugin.store().edit(claim).name(clean);
        if (clean == null) {
            messages().send(actor, "names.cleared");
        } else {
            messages().send(actor, "names.renamed", Messages.text("name", clean));
        }
        return true;
    }

    // ---- Abandon ----

    /** Delete a claim (or subdivision) the way /abandonclaim does, refunding its blocks */
    public boolean abandon(@NotNull Player actor, @NotNull Claim claim) {
        if (!Gp.canEdit(actor, claim)) {
            messages().send(actor, "general.owner-only");
            return false;
        }
        final String name = plugin.info().name(claim);
        final boolean subdivision = claim.parent != null;
        try {
            // Like /abandonclaim on 16.18: drain water and lava sitting on the surface (later versions dropped this)
            claim.removeSurfaceFluids(null);
        } catch (NoSuchMethodError ignored) {
        }
        Gp.data().deleteClaim(claim);
        final PlayerData data = Gp.playerData(actor.getUniqueId());
        // Same as /abandonclaim: only part of the blocks come back if the server says so
        final double ratio = Gp.abandonReturnRatio();
        if (!subdivision && ratio != 1.0D && actor.getUniqueId().equals(claim.getOwnerID())) {
            data.setAccruedClaimBlocks(data.getAccruedClaimBlocks() - (int) Math.ceil(claim.getArea() * (1 - ratio)));
            Gp.data().savePlayerData(actor.getUniqueId(), data);
        }
        data.setVisibleBoundaries(null);
        if (subdivision) {
            messages().send(actor, "abandon.subdivision-done", Messages.text("claim", name));
        } else {
            messages().send(actor, "abandon.done", Messages.text("claim", name),
                    Messages.text("remaining", Integer.toString(data.getRemainingClaimBlocks())));
        }
        return true;
    }

    // ---- Transfer ----

    /** Whether the claim may be given to this player; tells the actor why not */
    public boolean canTransfer(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        if (claim.parent != null) {
            messages().send(actor, "transfer.main-only");
            return false;
        }
        if (!Gp.isAdmin(actor) && (!actor.hasPermission("gpaddons.transfer") || claim.isAdminClaim())) {
            messages().send(actor, "general.no-permission");
            return false;
        }
        if (!Gp.canEdit(actor, claim)) {
            messages().send(actor, "general.owner-only");
            return false;
        }
        if (Gp.isOwner(claim, target.getUniqueId())) {
            messages().send(actor, "transfer.self");
            return false;
        }
        if (plugin.getConfig().getBoolean("transfer.require-claim-blocks", true)) {
            final int have = Gp.playerData(target.getUniqueId()).getRemainingClaimBlocks();
            if (have < claim.getArea()) {
                messages().send(actor, "transfer.not-enough-blocks",
                        Messages.text("player", Gp.name(target.getUniqueId())),
                        Messages.text("needed", Integer.toString(claim.getArea())),
                        Messages.text("have", Integer.toString(Math.max(0, have))));
                return false;
            }
        }
        return true;
    }

    public boolean transfer(@NotNull Player actor, @NotNull Claim claim, @NotNull OfflinePlayer target) {
        if (!canTransfer(actor, claim, target)) {
            return false;
        }
        if (!Gp.transfer(claim, target.getUniqueId())) {
            messages().send(actor, "transfer.failed");
            return false;
        }
        // The new owner shouldn't be banned from or locked out of their own claim
        final ClaimData data = plugin.store().get(claim);
        if (data != null && data.bans().remove(target.getUniqueId()) != null) {
            plugin.store().changed();
        }
        messages().send(actor, "transfer.done", Messages.text("player", Gp.name(target.getUniqueId())));
        return true;
    }

    // ---- Outline ----

    /** Show the whole claim's outline, as if inspected with a stick */
    public void outline(@NotNull Player actor, @NotNull Claim claim) {
        final Claim top = Gp.top(claim);
        if (actor.getWorld() != top.getLesserBoundaryCorner().getWorld()) {
            messages().send(actor, "general.other-world");
            return;
        }
        actor.closeInventory();
        BoundaryVisualization.visualizeClaim(actor, top,
                top.isAdminClaim() ? VisualizationType.ADMIN_CLAIM : VisualizationType.CLAIM);
    }

}
