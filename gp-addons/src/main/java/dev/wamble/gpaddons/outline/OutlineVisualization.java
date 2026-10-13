package dev.wamble.gpaddons.outline;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockBoundaryVisualization;
import com.griefprevention.visualization.BlockElement;
import com.griefprevention.visualization.Boundary;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * GriefPrevention's claim outline, drawn a set height above the ground, either as fake blocks (like
 * GriefPrevention) or as glowing see-through blocks (like HuskClaims). Uses the same blocks as GriefPrevention
 * for each kind of outline.
 */
public final class OutlineVisualization extends BlockBoundaryVisualization {

    private final Plugin plugin;
    private final int heightOffset;
    private final boolean glow;
    private final boolean waterTransparent;

    public OutlineVisualization(@NotNull Plugin plugin, @NotNull World world, @NotNull IntVector visualizeFrom,
                                int height, int heightOffset, boolean glow) {
        super(world, visualizeFrom, height);
        this.plugin = plugin;
        this.heightOffset = heightOffset;
        this.glow = glow;
        this.waterTransparent = visualizeFrom.toBlock(world).getType() == Material.WATER;
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addCornerElements(@NotNull Boundary boundary) {
        final String type = boundary.type().name();
        final BlockData data = switch (type) {
            case "SUBDIVISION" -> Material.IRON_BLOCK.createBlockData();
            case "INITIALIZE_ZONE", "NATURE_RESTORATION_ZONE" -> Material.DIAMOND_BLOCK.createBlockData();
            case "CONFLICT_ZONE" -> litRedstoneOre();
            default -> Material.GLOWSTONE.createBlockData();
        };
        return addElement(data, glowColor(type));
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addSideElements(@NotNull Boundary boundary) {
        final String type = boundary.type().name();
        final BlockData data = switch (type) {
            case "ADMIN_CLAIM" -> Material.PUMPKIN.createBlockData();
            case "SUBDIVISION" -> Material.WHITE_WOOL.createBlockData();
            case "INITIALIZE_ZONE", "NATURE_RESTORATION_ZONE" -> Material.DIAMOND_BLOCK.createBlockData();
            case "CONFLICT_ZONE" -> Material.NETHERRACK.createBlockData();
            default -> Material.GOLD_BLOCK.createBlockData();
        };
        return addElement(data, glowColor(type));
    }

    @NotNull
    private static BlockData litRedstoneOre() {
        final BlockData data = Material.REDSTONE_ORE.createBlockData();
        if (data instanceof Lightable lightable) {
            lightable.setLit(true);
        }
        return data;
    }

    @NotNull
    private static Color glowColor(@NotNull String type) {
        return switch (type) {
            case "ADMIN_CLAIM" -> Color.ORANGE;
            case "SUBDIVISION" -> Color.WHITE;
            case "CONFLICT_ZONE" -> Color.RED;
            case "INITIALIZE_ZONE", "NATURE_RESTORATION_ZONE" -> Color.AQUA;
            default -> Color.YELLOW;
        };
    }

    @NotNull
    private Consumer<@NotNull IntVector> addElement(@NotNull BlockData shown, @NotNull Color color) {
        return vector -> {
            final Block ground = visibleLocation(vector);
            final Block block = raised(ground);
            final IntVector coordinate = new IntVector(block);
            elements.add(glow ? new GlowElement(plugin, coordinate, shown, color)
                    : new FakeElement(coordinate, block.getBlockData(), shown));
        };
    }

    // The block a set height above the ground, without going past the world's top
    @NotNull
    private Block raised(@NotNull Block ground) {
        Block block = ground;
        for (int i = 0; i < heightOffset && block.getY() < world.getMaxHeight() - 1; i++) {
            block = block.getRelative(BlockFace.UP);
        }
        return block;
    }

    // The ground at this column, the same way GriefPrevention finds it
    @NotNull
    private Block visibleLocation(@NotNull IntVector vector) {
        Block block = vector.toBlock(world);
        final BlockFace direction = isTransparent(block) ? BlockFace.DOWN : BlockFace.UP;
        while (block.getY() >= world.getMinHeight() && block.getY() < world.getMaxHeight() - 1
                && (!isTransparent(block.getRelative(BlockFace.UP)) || isTransparent(block))) {
            block = block.getRelative(direction);
        }
        return block;
    }

    private boolean isTransparent(@NotNull Block block) {
        final Material material = block.getType();
        if (material == Material.WATER) {
            return waterTransparent;
        }
        if (material == Material.SNOW) {
            return false;
        }
        return material.isAir() || Tag.FENCES.isTagged(material) || Tag.FENCE_GATES.isTagged(material)
                || Tag.SIGNS.isTagged(material) || Tag.WALLS.isTagged(material) || Tag.WALL_SIGNS.isTagged(material)
                || material.isTransparent();
    }

    /** A block only the viewing player sees */
    private static final class FakeElement extends BlockElement {

        private final BlockData real;
        private final BlockData shown;

        FakeElement(@NotNull IntVector coordinate, @NotNull BlockData real, @NotNull BlockData shown) {
            super(coordinate);
            this.real = real;
            this.shown = shown;
        }

        @Override
        protected void draw(@NotNull Player player, @NotNull World world) {
            if (getCoordinate().isChunkLoaded(world)) {
                player.sendBlockChange(getCoordinate().toLocation(world), shown);
            }
        }

        @Override
        protected void erase(@NotNull Player player, @NotNull World world) {
            player.sendBlockChange(getCoordinate().toLocation(world), real);
        }

    }

    /** A glowing, slightly oversized block display only the viewing player sees */
    private static final class GlowElement extends BlockElement {

        private static final float SCALE = 1.002F;
        private static final Transformation TRANSFORMATION = new Transformation(
                new Vector3f(-0.001F, -0.001F, -0.001F), new AxisAngle4f(),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f());

        private final Plugin plugin;
        private final BlockData shown;
        private final Color color;
        @Nullable
        private BlockDisplay display;

        GlowElement(@NotNull Plugin plugin, @NotNull IntVector coordinate, @NotNull BlockData shown, @NotNull Color color) {
            super(coordinate);
            this.plugin = plugin;
            this.shown = shown;
            this.color = color;
        }

        @Override
        protected void draw(@NotNull Player player, @NotNull World world) {
            if (!getCoordinate().isChunkLoaded(world) || display != null) {
                return;
            }
            display = world.spawn(getCoordinate().toLocation(world), BlockDisplay.class, entity -> {
                entity.setBlock(shown);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
                entity.setGlowing(true);
                entity.setGlowColorOverride(color);
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setTransformation(TRANSFORMATION);
            });
            player.showEntity(plugin, display);
        }

        @Override
        protected void erase(@NotNull Player player, @NotNull World world) {
            if (display != null) {
                display.remove();
                display = null;
            }
        }

    }

}
