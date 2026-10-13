package dev.wamble.gpaddons.flag;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Applies claim flags. Player flags that are on let untrusted players past GriefPrevention's permission check;
 * environment flags stop (or, where the owner set them, allow) things that happen on their own.
 */
public final class FlagListener implements Listener {

    private static final Set<String> SPAWN_REASONS = Set.of(
            "NATURAL", "PATROL", "REINFORCEMENTS", "TRAP", "RAID", "VILLAGE_INVASION", "JOCKEY", "MOUNT"
    );
    private static final Set<String> FIRE_IGNITE_CAUSES = Set.of("SPREAD", "LAVA", "LIGHTNING");

    private final GPAddons plugin;

    public FlagListener(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private FlagService flags() {
        return plugin.flags();
    }

    // Player flags: allow untrusted players to do what the claim's flags allow
    @EventHandler(priority = EventPriority.HIGH)
    public void onPermissionCheck(@NotNull ClaimPermissionCheckEvent event) {
        if (event.getDenialReason() == null) {
            return;
        }
        final ClaimPermission required = event.getRequiredPermission();
        if (required == ClaimPermission.Edit || required == ClaimPermission.Manage) {
            return;
        }
        final Player player = event.getCheckedPlayer();
        if (player == null) {
            return;
        }
        final ClaimFlag flag = ActionMapper.map(event.getTriggeringEvent(), player, required);
        if (flag == null || flag.category() != ClaimFlag.Category.PLAYER) {
            return;
        }
        final Claim claim = event.getClaim();
        if (flags().get(claim, flag) && !plugin.entry().isBanned(claim, player.getUniqueId())) {
            event.setDenialReason(null);
        }
    }

    // PvP: an owner can allow it in their claim, where GriefPrevention would otherwise prevent it
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreventPvP(@NotNull PreventPvPEvent event) {
        if (flags().get(event.getClaim(), ClaimFlag.PLAYER_DAMAGE_PLAYER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        final Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        final Entity victim = event.getEntity();
        final Claim claim = Gp.claimAt(victim.getLocation(), null);
        if (claim == null) {
            return;
        }
        if (victim instanceof Player && !victim.equals(attacker)) {
            // PvP turned off here by the owner (otherwise GriefPrevention's PvP rules decide)
            if (flags().isSet(claim, ClaimFlag.PLAYER_DAMAGE_PLAYER) && !flags().get(claim, ClaimFlag.PLAYER_DAMAGE_PLAYER)) {
                event.setCancelled(true);
            }
        } else if (victim instanceof Enemy && !flags().get(claim, ClaimFlag.PLAYER_DAMAGE_MONSTER)
                && !Gp.isTrusted(claim, attacker) && !Gp.isAdmin(attacker)) {
            event.setCancelled(true);
        }
    }

    @Nullable
    private static Player attacker(@NotNull Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // Explosions hurting players, mobs and armor stands
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(@NotNull EntityDamageEvent event) {
        final String cause = event.getCause().name();
        if (!cause.equals("BLOCK_EXPLOSION") && !cause.equals("ENTITY_EXPLOSION")) {
            return;
        }
        final Claim claim = Gp.claimAt(event.getEntity().getLocation(), null);
        if (claim != null && !flags().get(claim, ClaimFlag.EXPLOSION_DAMAGE_ENTITY)) {
            event.setCancelled(true);
        }
    }

    // Explosions breaking blocks, split into TNT-like explosions and mob explosions
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        final Entity entity = event.getEntity();
        final boolean tnt = entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart;
        filterExplosion(event.blockList(), tnt ? ClaimFlag.EXPLOSION_DAMAGE_TERRAIN : ClaimFlag.MONSTER_DAMAGE_TERRAIN);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        filterExplosion(event.blockList(), ClaimFlag.EXPLOSION_DAMAGE_TERRAIN);
    }

    private void filterExplosion(@NotNull List<Block> blocks, @NotNull ClaimFlag flag) {
        Claim cached = null;
        final Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            final Claim claim = Gp.claimAt(iterator.next().getLocation(), cached);
            if (claim == null) {
                continue;
            }
            cached = claim;
            if (flags().controlsExplosions(claim) && !flags().get(claim, flag)) {
                iterator.remove();
            }
        }
    }

    // Mobs spawning on their own
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        if (!SPAWN_REASONS.contains(event.getSpawnReason().name())) {
            return;
        }
        final Claim claim = Gp.claimAt(event.getLocation(), null);
        if (claim == null) {
            return;
        }
        final ClaimFlag flag = event.getEntity() instanceof Enemy ? ClaimFlag.MONSTER_SPAWN : ClaimFlag.PASSIVE_MOB_SPAWN;
        if (!flags().get(claim, flag)) {
            event.setCancelled(true);
        }
    }

    // Fire: only claims whose owner set the flag; otherwise GriefPrevention's fire settings apply
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireSpread(@NotNull BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE || event.getNewState().getType() == Material.FIRE) {
            fire(event, event.getBlock(), ClaimFlag.FIRE_SPREAD);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(@NotNull BlockIgniteEvent event) {
        if (FIRE_IGNITE_CAUSES.contains(event.getCause().name())) {
            fire(event, event.getBlock(), ClaimFlag.FIRE_SPREAD);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(@NotNull BlockBurnEvent event) {
        fire(event, event.getBlock(), ClaimFlag.FIRE_BURN);
    }

    private void fire(@NotNull Cancellable event, @NotNull Block block, @NotNull ClaimFlag flag) {
        final Claim claim = Gp.claimAt(block.getLocation(), null);
        if (claim != null && flags().isSet(claim, flag)) {
            event.setCancelled(!flags().get(claim, flag));
        }
    }

    // Redstone machines: pistons and dispensers
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        redstone(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        redstone(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(@NotNull BlockDispenseEvent event) {
        redstone(event, event.getBlock());
    }

    private void redstone(@NotNull Cancellable event, @NotNull Block block) {
        final Claim claim = Gp.claimAt(block.getLocation(), null);
        if (claim != null && !flags().get(claim, ClaimFlag.REDSTONE_ACTUATE)) {
            event.setCancelled(true);
        }
    }

}
