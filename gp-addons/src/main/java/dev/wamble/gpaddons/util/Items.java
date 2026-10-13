package dev.wamble.gpaddons.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Builds menu items. Materials are looked up by name (with fallbacks after commas), so an icon that a later
 * Minecraft version renames can't break a menu.
 */
public final class Items {

    private Items() {
    }

    @NotNull
    public static ItemStack item(@NotNull String material, @NotNull Component name, @NotNull List<Component> lore) {
        final ItemStack item = new ItemStack(material(material));
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @NotNull
    public static ItemStack head(@NotNull OfflinePlayer owner, @NotNull Component name, @NotNull List<Component> lore) {
        final ItemStack item = item("player_head", name, lore);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }
        return item;
    }

    /** A blank pane for menu borders */
    @NotNull
    public static ItemStack pane(@NotNull String material) {
        return item(material, Component.text(" "), List.of());
    }

    /** Make an item shimmer, e.g. a flag that's on */
    @NotNull
    public static ItemStack glint(@NotNull ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            } catch (NoSuchMethodError ignored) {
                // Older servers: no glint, the status line still says it's on
            }
        }
        return item;
    }

    @NotNull
    public static Material material(@NotNull String names) {
        for (String name : names.split(",")) {
            final Material material = Material.matchMaterial(name.trim());
            if (material != null && material.isItem() && !material.isAir()) {
                return material;
            }
        }
        return Material.PAPER;
    }

}
