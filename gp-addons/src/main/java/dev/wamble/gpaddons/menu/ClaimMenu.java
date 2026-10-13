package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One claim: its guests, flags, bans, whitelist and subdivisions, plus renaming, transferring and abandoning.
 * Buttons the viewer can't use are shown locked.
 */
public final class ClaimMenu extends Menu {

    private final Claim claim;

    public ClaimMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.claim.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    @NotNull
    private List<Component> lore(@NotNull String key, boolean usable, @NotNull TagResolver... placeholders) {
        final List<Component> lore = new ArrayList<>(messages().lore("menu.claim.buttons." + key + ".lore", placeholders));
        lore.addAll(messages().lore(usable ? "menu.claim.buttons." + key + ".click" : "menu.locked", placeholders));
        return lore;
    }

    @NotNull
    private ItemStack button(@NotNull String material, @NotNull String key, boolean usable,
                             @NotNull TagResolver... placeholders) {
        return Items.item(material, messages().itemName("menu.claim.buttons." + key + ".name", placeholders),
                lore(key, usable, placeholders));
    }

    @Override
    protected void draw() {
        final ClaimData data = plugin.store().get(claim);
        final boolean top = claim.parent == null;
        final boolean edit = Gp.canEdit(viewer, claim);
        final boolean manage = Gp.canManage(viewer, claim);
        final boolean admin = Gp.isAdmin(viewer);

        set(HEADER, plugin.info().item(claim, viewer, false));

        // Guests
        final ItemStack guests = Items.head(viewer, messages().itemName("menu.claim.buttons.guests.name"),
                lore("guests", true));
        set(19, guests, click -> new GuestsMenu(plugin, viewer, claim, this).open());

        // Flags
        set(21, button("pink_banner,white_banner", "flags", true),
                click -> new FlagsMenu(plugin, viewer, claim, this).open());

        // Bans
        final int bans = data == null ? 0 : data.bans().size();
        set(23, button("barrier", "bans", true,
                        Messages.text("bans", Integer.toString(bans))),
                click -> new BansMenu(plugin, viewer, claim, this).open());

        // Entry whitelist
        final Component state = messages().get(plugin.entry().isWhitelistOn(claim) ? "menu.claims.on" : "menu.claims.off");
        set(25, button("cherry_fence_gate,oak_fence_gate", "whitelist", true,
                        Messages.component("state", state)),
                click -> new WhitelistMenu(plugin, viewer, claim, this).open());

        // Subdivisions, or the main claim when this is a subdivision
        if (top) {
            set(28, button("filled_map", "subdivisions", true,
                            Messages.text("count", Integer.toString(claim.children.size()))),
                    click -> new SubdivisionsMenu(plugin, viewer, claim, this).open());
        } else {
            final Claim parent = claim.parent;
            set(28, button("map", "main-claim", true,
                            Messages.text("main", plugin.info().name(parent))),
                    click -> new ClaimMenu(plugin, viewer, parent, this).open());
        }

        // Outline
        set(30, button("spyglass", "outline", true),
                click -> plugin.actions().outline(viewer, claim));

        // Rename
        final boolean canRename = manage && (admin || viewer.hasPermission("gpaddons.rename"));
        set(32, button("name_tag", "rename", canRename,
                        Messages.text("name", plugin.info().name(claim))),
                canRename ? click -> plugin.menus().prompt(viewer, "prompt.rename", this, text -> {
                    if (Gp.exists(claim)) {
                        plugin.actions().rename(viewer, claim, text);
                    }
                    open();
                }) : null);

        if (top) {
            // Transfer
            final boolean canTransfer = edit && (admin || viewer.hasPermission("gpaddons.transfer") && !claim.isAdminClaim());
            set(34, button("writable_book", "transfer", canTransfer),
                    canTransfer ? click -> new PlayerPickerMenu(plugin, viewer, this, "transfer",
                            player -> !Gp.isOwner(claim, player.getUniqueId()), this::confirmTransfer).open() : null);
        } else {
            // Whether the main claim's trust applies here too (GriefPrevention's /restrictsubclaim)
            final boolean inherits = !claim.getSubclaimRestrictions();
            final boolean canSwitch = Gp.canEdit(viewer, claim.parent);
            ItemStack inherit = button("iron_chain,chain", "inherit", canSwitch, Messages.component("state",
                    messages().get(inherits ? "menu.claims.on" : "menu.claims.off")));
            if (inherits) {
                inherit = Items.glint(inherit);
            }
            set(34, inherit, canSwitch ? click -> {
                // Restricted now means it will inherit after the switch
                plugin.actions().inherit(viewer, claim, claim.getSubclaimRestrictions());
                refresh();
            } : null);
        }

        // Abandon
        set(bottom(CORNER), button("tnt", "abandon", edit),
                edit ? click -> confirmAbandon() : null);
    }

    private void confirmTransfer(@NotNull OfflinePlayer target) {
        if (!Gp.exists(claim) || !plugin.actions().canTransfer(viewer, claim, target)) {
            open();
            return;
        }
        final String claimName = plugin.info().name(claim);
        final String player = target.getName() == null ? Gp.name(target.getUniqueId()) : target.getName();
        final ItemStack subject = Items.head(target,
                messages().itemName("menu.confirm.transfer.name", Messages.text("player", player)),
                messages().lore("menu.confirm.transfer.lore", Messages.text("name", claimName), Messages.text("player", player)));
        new ConfirmMenu(plugin, viewer, this,
                messages().get("menu.confirm.transfer.title", Messages.text("name", claimName), Messages.text("player", player)),
                subject, () -> Gp.exists(claim), () -> {
            if (plugin.actions().transfer(viewer, claim, target)) {
                if (back != null) {
                    back.open();
                }
            } else {
                open();
            }
        }).open();
    }

    /** Ask whether to abandon this claim (also used by /abandonclaim) */
    public void confirmAbandon() {
        final String claimName = plugin.info().name(claim);
        final ItemStack subject = Items.item("tnt",
                messages().itemName("menu.confirm.abandon.name", Messages.text("name", claimName)),
                messages().lore("menu.confirm.abandon.lore", Messages.text("name", claimName)));
        new ConfirmMenu(plugin, viewer, this,
                messages().get("menu.confirm.abandon.title", Messages.text("name", claimName)),
                subject, () -> Gp.exists(claim), () -> {
            if (plugin.actions().abandon(viewer, claim) && back != null && back.valid()) {
                back.open();
            }
        }).open();
    }

}
