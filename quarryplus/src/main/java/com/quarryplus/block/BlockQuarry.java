package com.quarryplus.block;

import java.util.ArrayList;
import java.util.Random;

import com.quarryplus.Config;
import com.quarryplus.EnchantmentHelper;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileQuarry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

/**
 * The QuarryPlus block. Drops with its NBT enchantments preserved so the next placement
 * inherits Efficiency / Unbreaking / Fortune / Silk Touch.
 *
 * <p>Facing meta is the {@link ForgeDirection} value of the side the player was facing when
 * placed (the quarry "looks" the opposite way — into the work area). The TESR uses this to
 * orient the drill animation; the block face textures read it for the front-of-machine art.
 */
public class BlockQuarry extends BlockContainer {

    private final ArrayList<ItemStack> capturedDrops = new ArrayList<ItemStack>();
    private Icon iconTop, iconFront, iconSide;

    public BlockQuarry() {
        super(Config.blockQuarryID, Material.iron);
        setHardness(1.5f);
        setResistance(10.0f);
        setStepSound(soundMetalFootstep);
        setCreativeTab(QuarryPlusI.creativeTab);
        setBlockName("QuarryPlus");
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileQuarry();
    }

    // ----- Drop with NBT enchant -----

    @Override
    public void breakBlock(World world, int x, int y, int z, int oldBlockId, int oldMeta) {
        capturedDrops.clear();
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileQuarry) {
            ItemStack stack = new ItemStack(this, 1, damageDropped(oldMeta));
            EnchantmentHelper.enchantmentToIS((TileQuarry) te, stack);
            capturedDrops.add(stack);
        }
        super.breakBlock(world, x, y, z, oldBlockId, oldMeta);
    }

    @Override
    public ArrayList<ItemStack> getBlockDropped(World world, int x, int y, int z, int meta, int fortune) {
        return capturedDrops.isEmpty() ? super.getBlockDropped(world, x, y, z, meta, fortune)
                                       : capturedDrops;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0; // drops come from breakBlock so they carry NBT
    }

    // ----- Facing -----

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        // 0 = south, 1 = west, 2 = north, 3 = east — matches the vanilla furnace facing trick.
        int facing;
        switch (look) {
            case 0:  facing = ForgeDirection.SOUTH.ordinal(); break;
            case 1:  facing = ForgeDirection.WEST.ordinal();  break;
            case 2:  facing = ForgeDirection.NORTH.ordinal(); break;
            default: facing = ForgeDirection.EAST.ordinal();  break;
        }
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);

        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof TileQuarry) {
            EnchantmentHelper.enchantmentFromIS((TileQuarry) te, stack);
        }
    }

    // ----- Textures (single texture set — animation states are Phase 6 polish) -----

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IconRegister reg) {
        iconTop   = reg.registerIcon("furnace_top");    // placeholder
        iconFront = reg.registerIcon("furnace_front");  // placeholder
        iconSide  = reg.registerIcon("furnace_side");   // placeholder
        this.blockIcon = iconSide;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Icon getIcon(int side, int meta) {
        if (side == 1) return iconTop;
        if (side == meta) return iconFront;
        return iconSide;
    }
}
