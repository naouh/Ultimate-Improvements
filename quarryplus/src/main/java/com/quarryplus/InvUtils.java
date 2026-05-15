package com.quarryplus;

import java.util.Random;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Inventory drop helpers. Spawn an item stack as a free entity at a block position with the
 * usual scatter offset — used by every BlockContainer in this mod when broken to dump its
 * tile's inventory on the ground.
 */
public final class InvUtils {

    private static final Random RNG = new Random();

    private InvUtils() {}

    public static void dropAt(World world, int x, int y, int z, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        double dx = RNG.nextFloat() * 0.7 + 0.15;
        double dy = RNG.nextFloat() * 0.7 + 0.15;
        double dz = RNG.nextFloat() * 0.7 + 0.15;
        EntityItem ei = new EntityItem(world, x + dx, y + dy, z + dz, stack);
        ei.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(ei);
    }
}
