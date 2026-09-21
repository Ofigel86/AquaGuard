package dev.aquaguard.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class BlockUtil {
    private BlockUtil() {}

    public static boolean isWaterLike(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN
                || type == Material.KELP || type == Material.KELP_PLANT
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
            return true;
        }
        try {
            BlockData data = block.getBlockData();
            return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLiquidLike(Block block) {
        if (block == null) return false;
        if (block.isLiquid() || block.getType() == Material.LAVA) return true;
        return isWaterLike(block);
    }

    public static boolean isClimbable(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (Tag.CLIMBABLE.isTagged(type)) return true;
        String name = type.name();
        return name.contains("VINE") || type == Material.SCAFFOLDING;
    }

    public static boolean isIce(Material type) {
        return type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }

    public static boolean isBed(Material type) {
        return Tag.BEDS.isTagged(type);
    }

    public static boolean isBounce(Material type) {
        return type == Material.SLIME_BLOCK || type == Material.HONEY_BLOCK || isBed(type);
    }

    public static double iceTickBonus(Material below) {
        if (below == Material.BLUE_ICE) return 0.18;
        if (below == Material.PACKED_ICE) return 0.12;
        if (below == Material.ICE || below == Material.FROSTED_ICE) return 0.08;
        return 0;
    }

    public static double iceBpsMultiplier(Material below) {
        if (below == Material.BLUE_ICE) return 2.1;
        if (below == Material.PACKED_ICE) return 1.55;
        if (below == Material.ICE || below == Material.FROSTED_ICE) return 1.35;
        return 1.0;
    }

    public static boolean serverOnGround(Player player) {
        BoundingBox box = player.getBoundingBox();
        BoundingBox probe = new BoundingBox(
                box.getMinX() + 0.02, box.getMinY() - 0.1, box.getMinZ() + 0.02,
                box.getMaxX() - 0.02, box.getMinY() + 0.02, box.getMaxZ() - 0.02);
        return collides(player.getWorld(), probe);
    }

    public static boolean insideSolid(Player player) {
        BoundingBox box = player.getBoundingBox().expand(-0.08, -0.08, -0.08);
        if (box.getWidthX() <= 0 || box.getHeight() <= 0 || box.getWidthZ() <= 0) return false;
        return collides(player.getWorld(), box);
    }

    public static boolean collides(World world, BoundingBox box) {
        if (world == null || box == null) return false;
        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX());
        int maxY = (int) Math.floor(box.getMaxY());
        int maxZ = (int) Math.floor(box.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (intersects(block, box)) return true;
                }
            }
        }
        return false;
    }

    public static boolean intersects(Block block, BoundingBox box) {
        if (block == null || box == null) return false;
        Material type = block.getType();
        if (type.isAir() || isLiquidLike(block) || type == Material.SCAFFOLDING) return false;
        if (Tag.CLIMBABLE.isTagged(type) || type == Material.COBWEB || type == Material.POWDER_SNOW) return false;
        try {
            var shape = block.getCollisionShape();
            if (shape == null || shape.isEmpty()) return false;
            for (BoundingBox part : shape.getBoundingBoxes()) {
                BoundingBox worldBox = part.shift(block.getX(), block.getY(), block.getZ());
                if (box.overlaps(worldBox)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return type.isSolid() && block.getBoundingBox().overlaps(box);
        }
    }

    public static boolean hasWallAdjacent(Player player) {
        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            Block side = feet.getRelative(off[0], 0, off[1]);
            if (side.getType().isSolid() && !isClimbable(side) && !Tag.FENCES.isTagged(side.getType())
                    && !Tag.WALLS.isTagged(side.getType()) && !Tag.FENCE_GATES.isTagged(side.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean rayHitsBlock(Player player, Block target) {
        if (player == null || target == null) return false;
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return false;
        Vector to = target.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        double dist = to.length();
        if (dist < 0.01) return true;
        RayTraceResult hit = world.rayTraceBlocks(eye, to.normalize(), dist + 0.4, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return false;
        Block hitBlock = hit.getHitBlock();
        return hitBlock.getX() == target.getX() && hitBlock.getY() == target.getY() && hitBlock.getZ() == target.getZ();
    }

    public static boolean hasLineOfSight(Player attacker, org.bukkit.entity.LivingEntity victim) {
        Location eye = attacker.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return attacker.hasLineOfSight(victim);
        try {
            BoundingBox box = victim.getBoundingBox();
            double[][] points = {
                    {avg(box.getMinX(), box.getMaxX()), avg(box.getMinY(), box.getMaxY()), avg(box.getMinZ(), box.getMaxZ())},
                    {avg(box.getMinX(), box.getMaxX()), box.getMaxY() - 0.1, avg(box.getMinZ(), box.getMaxZ())},
                    {avg(box.getMinX(), box.getMaxX()), box.getMinY() + 0.1, avg(box.getMinZ(), box.getMaxZ())}
            };
            for (double[] p : points) {
                Vector dir = new Vector(p[0], p[1], p[2]).subtract(eye.toVector());
                double dist = dir.length();
                if (dist < 0.2) return true;
                RayTraceResult hit = world.rayTraceBlocks(eye, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
                if (hit == null) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return attacker.hasLineOfSight(victim);
        }
    }

    public static int enchant(ItemStack item, org.bukkit.enchantments.Enchantment enchantment) {
        if (item == null || enchantment == null) return 0;
        return item.getEnchantmentLevel(enchantment);
    }

    public static String toolCategory(ItemStack item) {
        if (item == null) return "hand";
        String name = item.getType().name();
        if (name.endsWith("_PICKAXE")) return "pickaxe";
        if (name.endsWith("_AXE") && !name.contains("PICKAXE")) return "axe";
        if (name.endsWith("_SHOVEL")) return "shovel";
        if (name.endsWith("_HOE")) return "hoe";
        if (name.equals("SHEARS")) return "shears";
        if (name.endsWith("_SWORD")) return "sword";
        return "hand";
    }

    public static boolean correctTool(String category, Material block) {
        if (block == null) return false;
        return switch (category) {
            case "pickaxe" -> Tag.MINEABLE_PICKAXE.isTagged(block);
            case "axe" -> Tag.MINEABLE_AXE.isTagged(block);
            case "shovel" -> Tag.MINEABLE_SHOVEL.isTagged(block);
            case "hoe" -> Tag.MINEABLE_HOE.isTagged(block);
            case "shears" -> Tag.WOOL.isTagged(block) || block.name().contains("LEAVES") || block == Material.COBWEB;
            case "sword" -> block == Material.COBWEB || block == Material.BAMBOO;
            default -> false;
        };
    }

    private static double avg(double a, double b) {
        return (a + b) * 0.5;
    }
}
