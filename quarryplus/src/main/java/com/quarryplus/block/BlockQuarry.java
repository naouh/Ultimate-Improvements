package com.quarryplus.block;

import java.util.ArrayList;
import java.util.Random;

import com.quarryplus.Config;
import com.quarryplus.EnchantmentHelper;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileQuarry;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

/**
 * The QuarryPlus block. Drops with its NBT enchantments preserved so the next placement
 * inherits Efficiency / Unbreaking / Fortune / Silk Touch.
 *
 * <p>Facing meta is the {@link ForgeDirection} value the player was facing on placement —
 * the quarry "looks" the opposite way (into the work area).
 */
public class BlockQuarry extends BlockContainer {

    private final ArrayList<ItemStack> capturedDrops = new ArrayList<ItemStack>();

    public BlockQuarry() {
        super(Config.blockQuarryID, Material.iron);
        setHardness(1.5f);
        setResistance(10.0f);
        setStepSound(soundMetalFootstep);
        setCreativeTab(QuarryPlusI.creativeTab);
        setBlockName("QuarryPlus");
        setTextureFile("/mods/quarryplus/textures/blocks/terrain.png");
        this.blockIndexInTexture = 4; // default = side
    }

    /**
     * The QuarryPlus is a 3-texture block: top, front (facing meta), other sides. Terrain.png
     * cells: 2 = top, 3 = front, 4 = side. Bottom uses the side texture (same as 1.7.10).
     */
    @Override
    public int getBlockTextureFromSideAndMetadata(int side, int meta) {
        if (side == 1) return 2;           // top
        if (side == meta) return 3;        // front face matches the facing direction
        return 4;                          // bottom + other 3 sides
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileQuarry();
    }

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
        if (capturedDrops.isEmpty()) {
            return super.getBlockDropped(world, x, y, z, meta, fortune);
        }
        ArrayList<ItemStack> out = new ArrayList<ItemStack>(capturedDrops);
        capturedDrops.clear();
        return out;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLiving placer) {
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        int facing;
        switch (look) {
            case 0:  facing = ForgeDirection.SOUTH.ordinal(); break;
            case 1:  facing = ForgeDirection.WEST.ordinal();  break;
            case 2:  facing = ForgeDirection.NORTH.ordinal(); break;
            default: facing = ForgeDirection.EAST.ordinal();  break;
        }
        world.setBlockMetadataWithNotify(x, y, z, facing);

        // Enchant restoration is handled by ItemBlockQuarry.placeBlockAt() (it knows the
        // stack at that point). This callback only stamps the facing.
    }
}
