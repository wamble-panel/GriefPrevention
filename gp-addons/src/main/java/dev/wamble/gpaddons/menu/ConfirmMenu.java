package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * "Are you sure?" for abandoning and transferring claims. Cancelling goes back.
 */
public final class ConfirmMenu extends Menu {

    private final Component title;
    private final ItemStack subject;
    private final BooleanSupplier stillValid;
    private final Runnable confirm;

    /**
     * @param stillValid whether confirming still makes sense (e.g. the claim still exists)
     * @param confirm    what confirming does
     */
    public ConfirmMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @Nullable Menu back,
                       @NotNull Component title, @NotNull ItemStack subject,
                       @NotNull BooleanSupplier stillValid, @NotNull Runnable confirm) {
        super(plugin, viewer, back);
        this.title = title;
        this.subject = subject;
        this.stillValid = stillValid;
        this.confirm = confirm;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected @NotNull Component title() {
        return title;
    }

    @Override
    protected boolean valid() {
        return stillValid.getAsBoolean();
    }

    @Override
    protected void draw() {
        set(11, Items.item("lime_concrete", messages().itemName("menu.confirm.confirm.name"),
                messages().lore("menu.confirm.confirm.lore")), click -> {
            viewer.closeInventory();
            confirm.run();
        });
        set(13, subject);
        set(15, Items.item("red_concrete", messages().itemName("menu.confirm.cancel.name"),
                messages().lore("menu.confirm.cancel.lore")), click -> {
            if (back != null) {
                back.open();
            } else {
                viewer.closeInventory();
            }
        });
    }

}
