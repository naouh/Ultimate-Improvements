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
        setTextureFile("/mods/quarryplus/textures/blocks/marker.png");
        this.blockIndexInTexture = 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileMarker();
    }

    @Override
    public int getRenderType() {
        return 0;
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
        ForgeDirection dir = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
        switch (dir) {
            case DOWN:  setBlockBounds(0.35f, 0.35f, 0.35f, 0.65f, 1.00f, 0.65f); break;
            case UP:    setBlockBounds(0.35f, 0.00f, 0.35f, 0.65f, 0.65f, 0.65f); break;
            case SOUTH: setBlockBounds(0.35f, 0.35f, 0.00f, 0.65f, 0.65f, 0.65f); break;
            case NORTH: setBlockBounds(0.35f, 0.35f, 0.35f, 0.65f, 0.65f, 1.00f); break;
            case EAST:  setBlockBounds(0.00f, 0.35f, 0.35f, 0.65f, 0.65f, 0.65f); break;
            default:    setBlockBounds(0.35f, 0.35f, 0.35f, 1.00f, 0.65f, 0.65f);
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
        return side;
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
