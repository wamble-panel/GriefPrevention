package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Names, icons and text shared by the claim menus and commands.
 */
public final class ClaimInfo {

    private final GPAddons plugin;

    public ClaimInfo(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    /** The claim's name, or a stand-in if it has none */
    @NotNull
    public String name(@NotNull Claim claim) {
        final ClaimData data = plugin.store().get(claim);
        if (data != null && data.name() != null) {
            return data.name();
        }
        if (claim.parent != null) {
            return messages().raw("general.unnamed-subdivision");
        }
        return messages().raw(claim.isAdminClaim() ? "general.admin-claim" : "general.unnamed-claim");
    }

    /** The icon the owner picked, falling back to one for the claim's dimension */
    @NotNull
    public String icon(@NotNull Claim claim) {
        final ClaimData data = plugin.store().get(claim);
        return data != null && data.icon() != null ? data.icon() + "," + defaultIcon(claim) : defaultIcon(claim);
    }

    /** An icon for the claim's dimension */
    @NotNull
    public static String defaultIcon(@NotNull Claim claim) {
        if (claim.isAdminClaim()) {
            return "beacon";
        }
        final World world = claim.getLesserBoundaryCorner().getWorld();
        if (world == null) {
            return "grass_block";
        }
        return switch (world.getEnvironment().name()) {
            case "NETHER" -> "crimson_nylium,netherrack";
            case "THE_END" -> "end_stone";
            default -> "grass_block";
        };
    }

    /** The claim, as shown at the top of its menu and in the claims list */
    @NotNull
    public ItemStack item(@NotNull Claim claim, @NotNull Player viewer, boolean clickable) {
        final Location center = Gp.center(claim);
        final ClaimData data = plugin.store().get(claim);
        final Component ownerLine;
        if (claim.isAdminClaim()) {
            ownerLine = messages().get("menu.claims.owner-admin");
        } else if (Gp.isOwner(claim, viewer.getUniqueId())) {
            ownerLine = messages().get("menu.claims.owner-you");
        } else {
            ownerLine = messages().get("menu.claims.owner-other", Messages.text("owner", Gp.ownerName(claim)));
        }
        final List<Component> lore = new ArrayList<>(messages().lore("menu.claims.item.lore",
                Messages.component("owner_line", ownerLine),
                Messages.text("world", center.getWorld() == null ? "?" : center.getWorld().getName()),
                Messages.text("x", Integer.toString(center.getBlockX())),
                Messages.text("z", Integer.toString(center.getBlockZ())),
                Messages.text("area", Integer.toString(claim.getArea())),
                Messages.text("width", Integer.toString(claim.getWidth())),
                Messages.text("length", Integer.toString(claim.getHeight())),
                Messages.text("subdivisions", Integer.toString(claim.children.size())),
                Messages.text("guests", Integer.toString(Gp.trust(claim).size())),
                Messages.text("bans", Integer.toString(data == null ? 0 : data.bans().size())),
                Messages.component("whitelist", messages().get(plugin.entry().isWhitelistOn(claim)
                        ? "menu.claims.on" : "menu.claims.off"))));
        if (clickable) {
            lore.addAll(messages().lore("menu.claims.item.click"));
            if (plugin.teleports().canTeleport(viewer, claim)) {
                lore.addAll(messages().lore("menu.claims.item.teleport"));
            }
        }
        return Items.item(icon(claim), messages().itemName("menu.claims.item.name",
                Messages.text("name", name(claim))), lore);
    }

    /** A trust level, coloured, for {@code <level>} placeholders */
    @NotNull
    public Component level(@Nullable ClaimPermission level, boolean manager) {
        if (manager && (level == null || level == ClaimPermission.Manage)) {
            return messages().get("levels.manager");
        }
        if (level == null) {
            return messages().get("levels.none");
        }
        return switch (level.name()) {
            case "Edit", "Build" -> messages().get("levels.build");
            case "Container", "Inventory" -> messages().get("levels.container");
            case "Manage" -> messages().get("levels.manager");
            default -> messages().get("levels.access");
        };
    }

    @NotNull
    public String date(long millis) {
        String pattern = messages().raw("general.date-format");
        try {
            return new SimpleDateFormat(pattern.isEmpty() ? "yyyy-MM-dd" : pattern).format(new Date(millis));
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("yyyy-MM-dd").format(new Date(millis));
        }
    }

    /** The lines shown under a button the viewer can't use */
    @NotNull
    public List<Component> locked(@NotNull List<Component> lore) {
        final List<Component> lines = new ArrayList<>(lore);
        lines.addAll(messages().lore("menu.locked"));
        return lines;
    }

}
