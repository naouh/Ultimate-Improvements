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
     * cells: 2 = top, 3 = front, 4 = side. Bottom uses the side texture.
     *
     * <p>For inventory rendering (meta=0) we coerce the "facing" to SOUTH (3) so the iso
     * view actually shows the control-panel front instead of plain side textures all
     * around. Same convention as vanilla furnace's inventory icon.
     */
    @Override
    public int getBlockTextureFromSideAndMetadata(int side, int meta) {
        if (side == 1) return 2;                                 // top
        int facing = (meta >= 2 && meta <= 5) ? meta : 3;        // default to SOUTH for inventory
        if (side == facing) return 3;                            // front face
        return 4;                                                // bottom + other sides
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
            TileQuarry tq = (TileQuarry) te;
            // Drop the quarry as an itemblock carrying its enchant NBT…
            ItemStack stack = new ItemStack(this, 1, damageDropped(oldMeta));
            EnchantmentHelper.enchantmentToIS(tq, stack);
            capturedDrops.add(stack);
            // …and tear down the 12 edge frames the quarry placed in the world. Otherwise
            // breaking the quarry leaves a wireframe box floating around with no way to
            // remove it without manually breaking each frame.
            tq.removeAllFrames();
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
        // Match vanilla furnace placement: the block's "front" face stamps on the metadata
        // value of the side the player can see — i.e., the face opposite to the player's
        // look direction. That way the control panel ends up facing the player.
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        int facing;
        switch (look) {
            case 0:  facing = ForgeDirection.NORTH.ordinal(); break; // player looks south → front faces north (toward player)
            case 1:  facing = ForgeDirection.EAST.ordinal();  break; // looks west  → front faces east
            case 2:  facing = ForgeDirection.SOUTH.ordinal(); break; // looks north → front faces south
            default: facing = ForgeDirection.WEST.ordinal();  break; // looks east  → front faces west
        }
        world.setBlockMetadataWithNotify(x, y, z, facing);

        // Enchant restoration is handled by ItemBlockQuarry.placeBlockAt() (it knows the
        // stack at that point). This callback only stamps the facing.
    }
}
