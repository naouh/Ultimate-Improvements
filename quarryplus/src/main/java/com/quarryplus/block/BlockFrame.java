package com.quarryplus.block;

import java.util.Random;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The frame block placed by a QuarryPlus around its work area. Visually a small inset cube
 * (0.25–0.75 each axis) that grows toward 0/1 on faces where another frame sits, so adjacent
 * frames merge into a continuous rail.
 *
 * <p>Meta 0 = permanent (placed by an active quarry — only the quarry that owns it can
 * remove it). Meta &gt; 0 = decaying (orphaned frames from a destroyed quarry tick down to
 * nothing on random ticks).
 *
 * <p>Drops nothing on break — the frame is conceptually a marker, not a recoverable item.
 */
public class BlockFrame extends Block {

    public BlockFrame() {
        super(Config.blockFrameID, Material.circuits);
        setHardness(0.5f);
        setTickRandomly(true);
        setBlockName("qpFrame");
        setCreativeTab(QuarryPlusI.creativeTab);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public Item idDropped(int meta, Random random, int fortune) {
        return null;
    }

    @Override
    public int idDropped(int meta, Random random, int fortune2) {
        return 0;
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        if (world.isRemote) return;
        int meta = world.getBlockMetadata(x, y, z);
        if (meta != 0 && random.nextInt(10) > 5) {
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        float xMin = 0.25f, xMax = 0.75f;
        float yMin = 0.25f, yMax = 0.75f;
        float zMin = 0.25f, zMax = 0.75f;
        if (world.getBlockId(x - 1, y, z) == this.blockID) xMin = 0f;
        if (world.getBlockId(x + 1, y, z) == this.blockID) xMax = 1f;
        if (world.getBlockId(x, y - 1, z) == this.blockID) yMin = 0f;
        if (world.getBlockId(x, y + 1, z) == this.blockID) yMax = 1f;
        if (world.getBlockId(x, y, z - 1) == this.blockID) zMin = 0f;
        if (world.getBlockId(x, y, z + 1) == this.blockID) zMax = 1f;
        return AxisAlignedBB.getAABBPool().getAABB(
                x + xMin, y + yMin, z + zMin,
                x + xMax, y + yMax, z + zMax);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float xMin = 0.25f, xMax = 0.75f;
        float yMin = 0.25f, yMax = 0.75f;
        float zMin = 0.25f, zMax = 0.75f;
        if (world.getBlockId(x - 1, y, z) == this.blockID) xMin = 0f;
        if (world.getBlockId(x + 1, y, z) == this.blockID) xMax = 1f;
        if (world.getBlockId(x, y - 1, z) == this.blockID) yMin = 0f;
        if (world.getBlockId(x, y + 1, z) == this.blockID) yMax = 1f;
        if (world.getBlockId(x, y, z - 1) == this.blockID) zMin = 0f;
        if (world.getBlockId(x, y, z + 1) == this.blockID) zMax = 1f;
        setBlockBounds(xMin, yMin, zMin, xMax, yMax, zMax);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IconRegister reg) {
        this.blockIcon = reg.registerIcon("obsidian"); // placeholder; Phase 6 polish ships a dedicated texture
    }
}
