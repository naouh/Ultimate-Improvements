package codechicken.translocator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import codechicken.core.alg.MathHelper;
import codechicken.core.raytracer.RayTracer;
import codechicken.core.raytracer.RayTracer.IndexedCuboid6;
import codechicken.core.vec.BlockCoord;
import codechicken.core.vec.Rotation;
import codechicken.core.vec.Vector3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.liquids.ITankContainer;

/**
 * Block for both translocator variants — metadata 0 = item, 1 = liquid. The
 * tile entity is the one that holds attachment state; this class is mostly
 * concerned with raytracing the small mouth-cuboids on each face so that the
 * player can click individual attachments rather than the whole block.
 */
public class BlockTranslocator extends Block {

    private RayTracer rayTracer = new RayTracer();

    public BlockTranslocator(int id) {
        super(id, Material.iron);
        this.setHardness(1.5f);
        this.setResistance(10.0f);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return metadata < 2;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        switch (metadata) {
            case 0: return new TileItemTranslocator();
            case 1: return new TileLiquidTranslocator();
        }
        return null;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public int idDropped(int i, Random random, int j) {
        return 0;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
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
    public boolean removeBlockByPlayer(World world, EntityPlayer player, int x, int y, int z) {
        MovingObjectPosition hit = RayTracer.retraceBlock(world, player, x, y, z);
        if (hit == null) {
            return false;
        }
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        return ttrans.harvestPart(hit.subHit % 6, !player.capabilities.isCreativeMode);
    }

    public static boolean canExistOnSide(World world, int x, int y, int z, int side, int meta) {
        BlockCoord pos = new BlockCoord(x, y, z).offset(side);
        switch (meta) {
            case 0: return world.getBlockTileEntity(pos.x, pos.y, pos.z) instanceof IInventory;
            case 1: return world.getBlockTileEntity(pos.x, pos.y, pos.z) instanceof ITankContainer;
        }
        return false;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int blockID) {
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        for (int i = 0; i < 6; i++) {
            if (ttrans.attachments[i] != null
                    && !canExistOnSide(world, x, y, z, i, meta)
                    && ttrans.harvestPart(i, true)) {
                break;
            }
        }
    }

    @Override
    public int quantityDropped(int meta, int fortune, Random random) {
        return 0;
    }

    @Override
    public ArrayList<ItemStack> getBlockDropped(World world, int x, int y, int z, int md, int fortune) {
        ArrayList<ItemStack> ai = new ArrayList<ItemStack>();
        if (world.isRemote) {
            return ai;
        }
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        if (ttrans != null) {
            for (TileTranslocator.Attachment a : ttrans.attachments) {
                if (a != null) {
                    ai.addAll(a.getDrops());
                }
            }
        }
        return ai;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBlockTextureFromSideAndMetadata(int side, int meta) {
        // Custom renderer (getRenderType == -1) means this is never queried for
        // world rendering; only edge-cases like particle textures will hit it.
        return 0;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        return this.rayTracer.rayTraceCuboids(
                new Vector3(start), new Vector3(end),
                getParts(world, x, y, z), new BlockCoord(x, y, z), this);
    }

    public List<IndexedCuboid6> getParts(World world, int x, int y, int z) {
        TileTranslocator tile = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        if (tile == null) {
            return null;
        }
        LinkedList<IndexedCuboid6> cuboids = new LinkedList<IndexedCuboid6>();
        tile.addTraceableCuboids(cuboids);
        return cuboids;
    }

    @Override
    public void addCollidingBlockToList(World world, int x, int y, int z, AxisAlignedBB ebb, List list, Entity entity) {
        List<IndexedCuboid6> cuboids = getParts(world, x, y, z);
        for (IndexedCuboid6 cb : cuboids) {
            AxisAlignedBB aabb = cb.toAABB();
            if (aabb.intersectsWith(ebb)) {
                list.add(aabb);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        MovingObjectPosition hit = RayTracer.retraceBlock(world, player, x, y, z);
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        if (hit != null) {
            if (hit.subHit < 6) {
                // The mouth has a flat outer plate and a recessed inner peg.
                // We bias hits within ±0.125 of the centre to the peg (subPart
                // 1) so right-clicking the obvious centre toggles the eject
                // direction, while the outer ring (subPart 0) opens the GUI.
                Vector3 vhit = new Vector3(hit.hitVec);
                vhit.add(-x - 0.5, -y - 0.5, -z - 0.5);
                vhit.rotate(Rotation.sideQuatsR[hit.subHit % 6]);
                if (MathHelper.between(-0.125, vhit.x, 0.125)
                        && MathHelper.between(-0.125, vhit.z, 0.125)) {
                    hit.subHit += 6;
                }
            }
            return ttrans.attachments[hit.subHit % 6].activate(player, hit.subHit / 6);
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    @ForgeSubscribe
    public void onBlockHighlight(DrawBlockHighlightEvent event) {
        if (event.target.typeOfHit == EnumMovingObjectType.TILE
                && event.player.worldObj.getBlockId(event.target.blockX, event.target.blockY, event.target.blockZ) == this.blockID) {
            RayTracer.retraceBlock(event.player.worldObj, event.player,
                    event.target.blockX, event.target.blockY, event.target.blockZ);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(int id, CreativeTabs tab, List list) {
        list.add(new ItemStack(this, 1, 0));
        list.add(new ItemStack(this, 1, 1));
    }

    @Override
    public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        return ttrans.connectRedstone();
    }

    @Override
    public boolean isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int side) {
        // 1.4.7 Block.isProvidingStrongPower returns boolean (1.5+ widened it
        // to int 0..15). For our purposes a binary on/off works fine: the
        // signal-upgraded mouth either pushes redstone or it doesn't.
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        return ttrans.isProvidingStrongPower(side);
    }
}
