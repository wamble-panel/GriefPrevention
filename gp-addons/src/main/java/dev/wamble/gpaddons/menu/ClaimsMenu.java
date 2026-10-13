package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's claims (or every admin claim), with their claim blocks at the top.
 */
public final class ClaimsMenu extends PagedMenu<Claim> {

    /** Whose claims, or null for admin claims */
    @Nullable
    private final OfflinePlayer owner;

    public ClaimsMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @Nullable OfflinePlayer owner) {
        super(plugin, viewer, null);
        this.owner = owner;
    }

    @Override
    protected @NotNull Component title() {
        final int pages = pages();
        final String page = Integer.toString(Math.min(this.page, pages - 1) + 1);
        if (owner == null) {
            return messages().get("menu.claims.admin-title", Messages.text("page", page),
                    Messages.text("pages", Integer.toString(pages)));
        }
        return messages().get("menu.claims.title", Messages.text("owner", ownerName()),
                Messages.text("page", page), Messages.text("pages", Integer.toString(pages)));
    }

    /** Switch the golden shovel between claiming, subdividing and (for admins) admin claims */
    private void tool() {
        final String mode = Gp.shovelMode(viewer);
        final String key = switch (mode) {
            case "Basic" -> "basic";
            case "Subdivide" -> "subdivide";
            case "Admin" -> "admin";
            default -> "other";
        };
        final boolean admin = viewer.hasPermission("griefprevention.adminclaims");
        final List<Component> lore = new ArrayList<>(messages().lore("menu.claims.tool.lore",
                Messages.component("mode", messages().get("menu.claims.modes." + key))));
        lore.addAll(messages().lore("menu.claims.tool.click"));
        final String icon = switch (key) {
            case "subdivide" -> "iron_shovel";
            case "admin" -> "netherite_shovel";
            default -> "golden_shovel";
        };
        set(bottom(CENTER), Items.item(icon, messages().itemName("menu.claims.tool.name"), lore), click -> {
            final String next = switch (key) {
                case "basic" -> "subdivideclaims";
                case "subdivide" -> admin ? "adminclaims" : "basicclaims";
                default -> "basicclaims";
            };
            viewer.performCommand(next);
            refresh();
        });
    }

    @NotNull
    private String ownerName() {
        return owner == null ? "" : owner.getName() == null ? Gp.name(owner.getUniqueId()) : owner.getName();
    }

    @Override
    protected @NotNull List<Claim> entries() {
        return owner == null ? Gp.adminClaims() : Gp.claimsOf(owner.getUniqueId());
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Claim claim) {
        return plugin.info().item(claim, viewer, true);
    }

    @Override
    protected @Nullable Action action(@NotNull Claim claim) {
        return click -> new ClaimMenu(plugin, viewer, claim, this).open();
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        return Items.item("golden_shovel", messages().itemName("menu.claims.empty.name"),
                messages().lore("menu.claims.empty.lore"));
    }

    @Override
    protected void extras() {
        if (owner == null) {
            return;
        }
        if (owner.getUniqueId().equals(viewer.getUniqueId())) {
            tool();
        }
        final PlayerData data = Gp.playerData(owner.getUniqueId());
        set(HEADER, Items.head(owner, messages().itemName("menu.claims.info.name",
                        Messages.text("player", ownerName())),
                messages().lore("menu.claims.info.lore",
                        Messages.text("claims", Integer.toString(data.getClaims().size())),
                        Messages.text("remaining", Integer.toString(data.getRemainingClaimBlocks())),
                        Messages.text("accrued", Integer.toString(data.getAccruedClaimBlocks())),
                        Messages.text("bonus", Integer.toString(data.getBonusClaimBlocks())))));
    }

}
