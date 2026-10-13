package dev.wamble.gpaddons.protect;

import org.bukkit.Keyed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Cushions (Minecraft 26.3) are recognised by name, so nothing breaks on versions without them.
 */
public final class Cushions {

    private Cushions() {
    }

    public static boolean is(@Nullable Entity entity) {
        return entity != null && ((Keyed) entity.getType()).getKey().getKey().contains("cushion");
    }

    public static boolean isItem(@Nullable ItemStack item) {
        return item != null && ((Keyed) item.getType()).getKey().getKey().endsWith("_cushion");
    }

    /** The player who hit something, directly or with a projectile */
    @Nullable
    public static Player playerBehind(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

}
