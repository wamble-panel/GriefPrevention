package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A claim's subdivisions. Click one to manage it like a claim.
 */
public final class SubdivisionsMenu extends PagedMenu<Claim> {

    private final Claim claim;

    public SubdivisionsMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Claim claim, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.claim = claim;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.subdivisions.title", Messages.text("name", plugin.info().name(claim)));
    }

    @Override
    protected boolean valid() {
        return Gp.exists(claim);
    }

    @Override
    protected @NotNull List<Claim> entries() {
        return new ArrayList<>(claim.children);
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Claim child) {
        final Location center = Gp.center(child);
        final List<Component> lore = new ArrayList<>(messages().lore("menu.subdivisions.item.lore",
                Messages.text("area", Integer.toString(child.getArea())),
                Messages.text("width", Integer.toString(child.getWidth())),
                Messages.text("length", Integer.toString(child.getHeight())),
                Messages.text("x", Integer.toString(center.getBlockX())),
                Messages.text("z", Integer.toString(center.getBlockZ())),
                Messages.text("guests", Integer.toString(Gp.trust(child).size()))));
        lore.addAll(messages().lore("menu.subdivisions.item.click"));
        return Items.item("filled_map", messages().itemName("menu.subdivisions.item.name",
                Messages.text("name", plugin.info().name(child))), lore);
    }

    @Override
    protected @Nullable Action action(@NotNull Claim child) {
        return click -> new ClaimMenu(plugin, viewer, child, this).open();
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        return Items.item("map", messages().itemName("menu.subdivisions.empty.name"),
                messages().lore("menu.subdivisions.empty.lore"));
    }

    @Override
    protected void extras() {
        set(HEADER, plugin.info().item(claim, viewer, false));
    }

}
