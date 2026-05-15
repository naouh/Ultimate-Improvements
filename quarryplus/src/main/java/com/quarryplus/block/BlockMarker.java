package com.quarryplus.block;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

/**
 * Torch-shaped block that pairs with up to two other markers on the same axis to define a
 * bounding box (used by the QuarryPlus to set its mining area).
 *
 * <p>Mounted on the face of an adjacent solid block — the placement side is stored in the
 * tile's metadata to drive {@link #setBlockBoundsBasedOnState}. Drops itself if the mount
 * goes away (same behaviour as a vanilla torch).
 */
public class BlockMarker extends BlockContainer {

    public BlockMarker() {
        super(Config.blockMarkerID, Material.circuits);
        setLightValue(0.5f);
        setHardness(0.0f);
        setCreativeTab(QuarryPlusI.creativeTab);
        setBlockName("MarkerPlus");
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileMarker();
    }

    @Override
    public int getRenderType() {
        // Default cube renderer — the block draws as a small textured stub thanks to the
        // bounds set in setBlockBoundsBasedOnState. The TESR ({@link
        // com.quarryplus.render.RenderMarker}) overlays the box-edge wireframe on top when
        // the marker is linked. A proper torch-shape ISimpleBlockRenderingHandler is a
        // Phase 6 polish item.
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
            default:    setBlockBounds(0.35f, 0.35f, 0.35f, 1.00f, 0.65f, 0.65f); // WEST + UNKNOWN
        }
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        return world.isBlockSolidOnSide(
                x - dir.offsetX, y - dir.offsetY, z - dir.offsetZ, dir.getOpposite());
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        // Store the placement side as metadata for setBlockBoundsBasedOnState.
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
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z,
                                    EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!(te instanceof TileMarker)) return true;
        TileMarker tm = (TileMarker) te;

        // ItemTool meta 0 = StatusChecker — print the marker's bounding box.
        // The status-checker class lands in Phase 5; until then we just route every click
        // to the connection logic (which is the common case).
        tm.tryConnection();
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IconRegister reg) {
        // Texture lookup defers to vanilla until Phase 6 polish adds dedicated artwork.
        this.blockIcon = reg.registerIcon("torch");
    }
}
