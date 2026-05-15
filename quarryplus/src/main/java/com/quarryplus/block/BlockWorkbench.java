package com.quarryplus.block;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileWorkbench;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
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
            // Phase 5: open as a vanilla chest UI. Phase 6 polish swaps in a proper
            // Container/GuiContainer pair with the input/internal/output slot split and the
            // power-progress bar.
            player.displayGUIChest((IInventory) te);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, int oldId, int oldMeta) {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof IInventory) {
            // Drop inventory contents.
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                if (inv.getStackInSlot(i) != null) {
                    dropBlockAsItem_do(world, x, y, z, inv.getStackInSlot(i));
                }
            }
        }
        super.breakBlock(world, x, y, z, oldId, oldMeta);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IconRegister reg) {
        this.blockIcon = reg.registerIcon("workbench");
    }
}
