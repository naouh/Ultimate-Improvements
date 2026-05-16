package com.quarryplus.block;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileMarker;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

/**
 * Torch-shaped block that pairs with up to two other markers on the same axis to define a
 * bounding box. Mounted on the face of an adjacent solid block — placement side is stored as
 * metadata so the bounding-box renderer can orient the visible torch shape.
 */
public class BlockMarker extends BlockContainer {

    public BlockMarker() {
        super(Config.blockMarkerID, Material.circuits);
        setLightValue(0.5f);
        setHardness(0.0f);
        setCreativeTab(QuarryPlusI.creativeTab);
        setBlockName("MarkerPlus");
        setTextureFile("/mods/quarryplus/textures/blocks/terrain.png");
        this.blockIndexInTexture = 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileMarker();
    }

    @Override
    public int getRenderType() {
        // 2 = vanilla torch renderer. Reads block metadata (1–5) to orient itself.
        return 2;
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
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        // Match vanilla torch bounds, indexed by the same meta values 1–5 (1=east, 2=west,
        // 3=south, 4=north, 5=standing). The torch renderer reads the same meta.
        int meta = world.getBlockMetadata(x, y, z);
        switch (meta) {
            case 1: setBlockBounds(0.00f, 0.20f, 0.35f, 0.30f, 0.80f, 0.65f); break; // east wall
            case 2: setBlockBounds(0.70f, 0.20f, 0.35f, 1.00f, 0.80f, 0.65f); break; // west wall
            case 3: setBlockBounds(0.35f, 0.20f, 0.00f, 0.65f, 0.80f, 0.30f); break; // south wall
            case 4: setBlockBounds(0.35f, 0.20f, 0.70f, 0.65f, 0.80f, 1.00f); break; // north wall
            default: setBlockBounds(0.40f, 0.00f, 0.40f, 0.60f, 0.60f, 0.60f);       // standing
        }
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        return world.isBlockSolidOnSide(
                x - dir.offsetX, y - dir.offsetY, z - dir.offsetZ, dir.getOpposite());
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ, int meta) {
        // Convert clicked face to torch-style meta so render type 2 renders correctly.
        switch (side) {
            case 1: return 5; // top  → standing
            case 2: return 4; // north face  → torch pointing north
            case 3: return 3; // south face  → torch pointing south
            case 4: return 2; // west face   → torch pointing west
            case 5: return 1; // east face   → torch pointing east
            default: return 5;
        }
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        dropIfCantStay(world, x, y, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int neighborId) {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof TileMarker) {
            ((TileMarker) te).onRedstoneChanged();
        }
        dropIfCantStay(world, x, y, z);
    }

    private void dropIfCantStay(World world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);
        if (!canPlaceBlockOnSide(world, x, y, z, meta)) {
            dropBlockAsItem(world, x, y, z, meta, 0);
            world.setBlockWithNotify(x, y, z, 0);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z,
                                    EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof TileMarker) {
            ((TileMarker) te).tryConnection();
        }
        return true;
    }
}
