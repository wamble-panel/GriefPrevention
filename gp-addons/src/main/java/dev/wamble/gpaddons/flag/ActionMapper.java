package dev.wamble.gpaddons.flag;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Works out which flag covers the action GriefPrevention is checking permission for, from the game event that
 * triggered the check. Returns null when it can't tell, in which case GriefPrevention's decision stands.
 */
public final class ActionMapper {

    private static final Set<String> FARM_BLOCKS = Set.of(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "MELON", "PUMPKIN", "MELON_STEM", "PUMPKIN_STEM",
            "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "COCOA", "NETHER_WART", "SWEET_BERRY_BUSH",
            "SUGAR_CANE", "CACTUS", "BAMBOO", "BAMBOO_SAPLING", "KELP", "KELP_PLANT", "CAVE_VINES",
            "CAVE_VINES_PLANT", "TORCHFLOWER_CROP", "TORCHFLOWER", "PITCHER_CROP", "PITCHER_PLANT", "FARMLAND",
            "COMPOSTER", "BEE_NEST", "BEEHIVE", "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS",
            "TORCHFLOWER_SEEDS", "PITCHER_POD", "CARROT", "POTATO", "SWEET_BERRIES", "GLOW_BERRIES", "COCOA_BEANS"
    );
    private static final Set<String> CONTAINERS_WITHOUT_INVENTORY = Set.of(
            "LECTERN", "JUKEBOX", "CHISELED_BOOKSHELF", "DECORATED_POT", "ENDER_CHEST"
    );

    private ActionMapper() {
    }

    @Nullable
    public static ClaimFlag map(@Nullable Event event, @NotNull Player player, @NotNull ClaimPermission required) {
        if (event == null) {
            return null;
        }
        if (event instanceof Claim.CompatBuildBreakEvent compat) {
            return fromMaterial(compat.getMaterial(), compat.isBreak(), player);
        }
        if (event instanceof BlockBreakEvent breakEvent) {
            return isFarm(breakEvent.getBlock().getType()) ? ClaimFlag.FARM_BLOCK_BREAK : ClaimFlag.BLOCK_BREAK;
        }
        if (event instanceof BlockPlaceEvent placeEvent) {
            return isFarm(placeEvent.getBlockPlaced().getType()) ? ClaimFlag.FARM_BLOCK_PLACE : ClaimFlag.BLOCK_PLACE;
        }
        if (event instanceof PlayerBucketEmptyEvent) {
            return ClaimFlag.EMPTY_BUCKET;
        }
        if (event instanceof PlayerBucketFillEvent) {
            return ClaimFlag.FILL_BUCKET;
        }
        if (event instanceof HangingPlaceEvent) {
            return ClaimFlag.PLACE_HANGING_ENTITY;
        }
        if (event instanceof HangingBreakEvent) {
            return ClaimFlag.BREAK_HANGING_ENTITY;
        }
        if (event instanceof VehicleDamageEvent || event instanceof VehicleDestroyEvent) {
            return ClaimFlag.BREAK_VEHICLE;
        }
        if (event instanceof PlayerTeleportEvent) {
            return ClaimFlag.ENDER_PEARL_TELEPORT;
        }
        if (event instanceof RaidTriggerEvent) {
            return ClaimFlag.START_RAID;
        }
        if (event instanceof PlayerEggThrowEvent) {
            return ClaimFlag.USE_SPAWN_EGG;
        }
        if (event instanceof PlayerTakeLecternBookEvent || event instanceof InventoryOpenEvent) {
            return ClaimFlag.CONTAINER_OPEN;
        }
        if (event instanceof PlayerArmorStandManipulateEvent || event instanceof PlayerInteractEntityEvent) {
            return ClaimFlag.ENTITY_INTERACT;
        }
        if (event instanceof EntityDamageByEntityEvent damage) {
            return fromVictim(damage.getEntity());
        }
        if (event instanceof PotionSplashEvent || event instanceof AreaEffectCloudApplyEvent) {
            return ClaimFlag.PLAYER_DAMAGE_ENTITY;
        }
        if (event instanceof ProjectileHitEvent) {
            return ClaimFlag.BLOCK_BREAK;
        }
        if (event instanceof CauldronLevelChangeEvent) {
            return ClaimFlag.BLOCK_INTERACT;
        }
        if (event instanceof PlayerSignOpenEvent || event instanceof SignChangeEvent || event instanceof EntityBlockFormEvent) {
            return ClaimFlag.BLOCK_PLACE;
        }
        if (event instanceof EntityChangeBlockEvent change) {
            return isFarm(change.getBlock().getType()) ? ClaimFlag.FARM_BLOCK_BREAK : ClaimFlag.BLOCK_BREAK;
        }
        if (event instanceof PlayerInteractEvent interact) {
            return fromInteract(interact, required);
        }
        return null;
    }

    @NotNull
    private static ClaimFlag fromMaterial(@NotNull Material material, boolean isBreak, @NotNull Player player) {
        final String name = material.name();
        if (isBreak) {
            if (isHanging(name)) {
                return ClaimFlag.BREAK_HANGING_ENTITY;
            }
            return isFarm(material) ? ClaimFlag.FARM_BLOCK_BREAK : ClaimFlag.BLOCK_BREAK;
        }
        if (name.equals("WATER") || name.equals("LAVA")) {
            return ClaimFlag.EMPTY_BUCKET;
        }
        if (material.isAir()) {
            // GriefPrevention checks both filling a bucket and breaking an item frame or painting this way
            return isHoldingEmptyBucket(player) ? ClaimFlag.FILL_BUCKET : ClaimFlag.BREAK_HANGING_ENTITY;
        }
        if (isHanging(name)) {
            return ClaimFlag.PLACE_HANGING_ENTITY;
        }
        if (isVehicle(name)) {
            return ClaimFlag.PLACE_VEHICLE;
        }
        return isFarm(material) ? ClaimFlag.FARM_BLOCK_PLACE : ClaimFlag.BLOCK_PLACE;
    }

    @Nullable
    private static ClaimFlag fromInteract(@NotNull PlayerInteractEvent event, @NotNull ClaimPermission required) {
        final Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL) {
            return block != null && block.getType().name().equals("FARMLAND") ? ClaimFlag.FARM_BLOCK_BREAK
                    : ClaimFlag.REDSTONE_INTERACT;
        }
        final ItemStack item = event.getItem();
        if (item != null) {
            final String itemName = item.getType().name();
            if (itemName.endsWith("_SPAWN_EGG")) {
                return ClaimFlag.USE_SPAWN_EGG;
            }
            if (isVehicle(itemName)) {
                return ClaimFlag.PLACE_VEHICLE;
            }
            if (isHanging(itemName) || itemName.equals("ARMOR_STAND")) {
                return ClaimFlag.PLACE_HANGING_ENTITY;
            }
            if (itemName.equals("BONE_MEAL") && block != null && isFarm(block.getType())) {
                return ClaimFlag.FARM_BLOCK_INTERACT;
            }
        }
        if (block == null) {
            return null;
        }
        if (required == ClaimPermission.Container || required == ClaimPermission.Inventory || isContainer(block)) {
            return ClaimFlag.CONTAINER_OPEN;
        }
        if (isRedstone(block.getType().name())) {
            return ClaimFlag.REDSTONE_INTERACT;
        }
        if (isFarm(block.getType())) {
            return ClaimFlag.FARM_BLOCK_INTERACT;
        }
        return required == ClaimPermission.Build ? ClaimFlag.BLOCK_PLACE : ClaimFlag.BLOCK_INTERACT;
    }

    @NotNull
    private static ClaimFlag fromVictim(@NotNull Entity victim) {
        if (victim instanceof Boat || victim instanceof Minecart) {
            return ClaimFlag.BREAK_VEHICLE;
        }
        if (victim instanceof Hanging || victim instanceof ArmorStand) {
            return ClaimFlag.BREAK_HANGING_ENTITY;
        }
        if (victim instanceof Player) {
            return ClaimFlag.PLAYER_DAMAGE_PLAYER;
        }
        if (victim instanceof Enemy) {
            return ClaimFlag.PLAYER_DAMAGE_MONSTER;
        }
        final boolean persistent = victim.customName() != null
                || victim instanceof Tameable tameable && tameable.isTamed()
                || victim instanceof LivingEntity living && !living.getRemoveWhenFarAway();
        return persistent ? ClaimFlag.PLAYER_DAMAGE_PERSISTENT_ENTITY : ClaimFlag.PLAYER_DAMAGE_ENTITY;
    }

    public static boolean isFarm(@NotNull Material material) {
        return FARM_BLOCKS.contains(material.name());
    }

    private static boolean isHanging(@NotNull String name) {
        return name.equals("PAINTING") || name.endsWith("ITEM_FRAME");
    }

    private static boolean isVehicle(@NotNull String name) {
        return name.contains("MINECART") || name.endsWith("_BOAT") || name.endsWith("_RAFT");
    }

    private static boolean isRedstone(@NotNull String name) {
        return name.endsWith("_BUTTON") || name.endsWith("PRESSURE_PLATE") || name.equals("LEVER")
                || name.equals("REPEATER") || name.equals("COMPARATOR") || name.equals("DAYLIGHT_DETECTOR")
                || name.equals("NOTE_BLOCK") || name.equals("TRIPWIRE");
    }

    private static boolean isContainer(@NotNull Block block) {
        if (CONTAINERS_WITHOUT_INVENTORY.contains(block.getType().name())) {
            return true;
        }
        final BlockState state = block.getState(false);
        return state instanceof InventoryHolder;
    }

    private static boolean isHoldingEmptyBucket(@NotNull Player player) {
        return player.getInventory().getItemInMainHand().getType().name().equals("BUCKET")
                || player.getInventory().getItemInOffHand().getType().name().equals("BUCKET");
    }

}
