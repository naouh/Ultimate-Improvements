package com.nao.tfcfixes;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Runtime helper for {@link com.nao.tfcfixes.asm.MazestoneVajraTransformer} and
 * {@link com.nao.tfcfixes.asm.ItemVajraTransformer}.
 *
 * <p>Twilight Forest's mazestone damages any non-Mazebreaker {@code ItemTool} by 16 durability per
 * block broken ({@code BlockTFMazestone.harvestBlock}). IC2 electric tools such as the GraviSuite
 * Vajra store their charge in the item-damage value, so that penalty corrupts the energy bar and
 * destroys a Vajra after about two blocks. These hooks keep IC2 electric tools off mazestone.
 *
 * <p>Twilight Forest and IC2 are matched by class name, so nothing breaks when either is absent.
 * Minecraft types here are remapped by Voldeloom and only touched at runtime (block mining), long
 * after Minecraft has loaded. Every hook fails open (returns false) on any error.
 */
public final class MazestoneHook {

    private static final String MAZESTONE = "twilightforest.block.BlockTFMazestone";

    private static volatile boolean resolved;
    private static Class<?> electricItemCls;

    private MazestoneHook() {}

    /**
     * Called at the top of {@code Block.getPlayerRelativeBlockHardness}. True when the block is
     * mazestone and the player holds an IC2 electric item: the caller then returns hardness 0, so
     * mining never completes and {@code harvestBlock} (with its durability penalty) never runs.
     * Normal tools keep Twilight Forest's intended behaviour.
     */
    public static boolean blockMazestoneMining(Object block, Object player) {
        try {
            if (!(player instanceof EntityPlayer) || !isMazestone(block)) return false;
            ItemStack held = ((EntityPlayer) player).getCurrentEquippedItem();
            return held != null && isElectric(held.getItem());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Called at the top of the Vajra's {@code onItemUse} ("accurate" right-click mode, which breaks
     * the targeted block directly without going through {@code harvestBlock}). Refusing it on
     * mazestone keeps the restriction consistent with left-click mining.
     */
    public static boolean isMazestoneAt(Object world, int x, int y, int z) {
        try {
            if (!(world instanceof World)) return false;
            int id = ((World) world).getBlockId(x, y, z);
            return id > 0 && id < Block.blocksList.length && isMazestone(Block.blocksList[id]);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMazestone(Object block) {
        return block != null && MAZESTONE.equals(block.getClass().getName());
    }

    private static boolean isElectric(Object item) {
        if (item == null) return false;
        if (!resolved) {
            try {
                electricItemCls = Class.forName("ic2.api.IElectricItem");
            } catch (Throwable t) {
                electricItemCls = null; // IC2 absent: nothing to protect
            }
            resolved = true;
        }
        return electricItemCls != null && electricItemCls.isInstance(item);
    }
}
