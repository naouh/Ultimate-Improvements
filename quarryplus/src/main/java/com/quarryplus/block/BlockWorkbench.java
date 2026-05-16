package com.quarryplus.block;

import com.quarryplus.Config;
import com.quarryplus.InvUtils;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileWorkbench;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockWorkbench extends BlockContainer {

    public BlockWorkbench() {
        super(Config.blockWorkbenchID, Material.iron);
        setHardness(1.5f);
        setResistance(10.0f);
        setBlockName("WorkbenchPlus");
        setCreativeTab(QuarryPlusI.creativeTab);
        setTextureFile("/mods/quarryplus/textures/blocks/terrain.png");
        this.blockIndexInTexture = 6; // side
    }

    @Override
    public int getBlockTextureFromSideAndMetadata(int side, int meta) {
        return (side == 0 || side == 1) ? 5 : 6; // 5 = top/bottom, 6 = sides
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileWorkbench();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hx, float hy, float hz) {
        if (world.isRemote) return true;
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof IInventory) {
            player.displayGUIChest((IInventory) te);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, int oldId, int oldMeta) {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                InvUtils.dropAt(world, x, y, z, inv.getStackInSlot(i));
            }
        }
        super.breakBlock(world, x, y, z, oldId, oldMeta);
    }
}
