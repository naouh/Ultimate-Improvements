package codechicken.translocator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import codechicken.core.raytracer.RayTracer;
import codechicken.core.raytracer.RayTracer.IndexedCuboid6;
import codechicken.core.raytracer.SelectionBox;
import codechicken.core.vec.BlockCoord;
import codechicken.core.vec.Cuboid6;
import codechicken.core.vec.Vector3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.event.ForgeSubscribe;

/**
 * The crafting-grid block: a flat 1x1 plate of items dropped on the ground by
 * pressing the "g" key. Solidifies into 9 separate item drops on break, or
 * crafts into a result with the player's "g" key (handled by
 * {@link CraftingGridKeyHandler} -&gt; TileCraftingGrid.craft).
 *
 * <p>Has no inventory slot of its own; clicking each slot interacts with the
 * tile's {@code items} array directly via the raytraced subHit index.
 */
public class BlockCraftingGrid extends Block {

    private RayTracer rayTracer = new RayTracer();

    ThreadLocal<BlockCoord> replaceCheck = new ThreadLocal<BlockCoord>();

    public BlockCraftingGrid(int id) {
        super(id, Material.circuits);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta == 0;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileCraftingGrid();
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World par1World, int par2, int par3, int par4) {
        // Effectively passable — only the per-slot raytrace cuboids "exist"
        // for clicks; player movement should not hit the grid.
        return null;
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
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean removeBlockByPlayer(World world, EntityPlayer player, int x, int y, int z) {
        if (!player.capabilities.isCreativeMode && !world.isRemote) {
            this.dropBlockAsItemWithChance(world, x, y, z, world.getBlockMetadata(x, y, z), 0, 0);
        }
        world.setBlockWithNotify(x, y, z, 0);
        return true;
    }

    @Override
    public ArrayList<ItemStack> getBlockDropped(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> ai = new ArrayList<ItemStack>();
        if (world.isRemote) {
            return ai;
        }
        TileCraftingGrid tcraft = (TileCraftingGrid) world.getBlockTileEntity(x, y, z);
        if (tcraft != null) {
            for (ItemStack item : tcraft.items) {
                if (item != null) {
                    ai.add(item.copy());
                }
            }
        }
        return ai;
    }

    @Override
    public int idDropped(int i, Random random, int j) {
        return 0;
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
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        return this.rayTracer.rayTraceCuboids(
                new Vector3(start), new Vector3(end),
                getParts(world, x, y, z), new BlockCoord(x, y, z), this);
    }

    public List<IndexedCuboid6> getParts(World world, int x, int y, int z) {
        LinkedList<IndexedCuboid6> parts = new LinkedList<IndexedCuboid6>();
        // SubHit 0 is the base plate; subHits 1-9 are the nine item slots
        // laid out around the centre.
        parts.add(new IndexedCuboid6(0,
                new Cuboid6(0.0, 0.0, 0.0, 1.0, 0.005, 1.0).add(new Vector3(x, y, z))));
        TileCraftingGrid tcraft = (TileCraftingGrid) world.getBlockTileEntity(x, y, z);
        for (int i = 0; i < 9; i++) {
            // CCC 0.8.1.6 SelectionBox.translate is the equivalent of the
            // newer .add (the SelectionBox->Cuboid6 chain only adds at .bound).
            SelectionBox box = new SelectionBox(new Cuboid6(0.0625, 0.0, 0.0625, 0.3125, 0.01, 0.3125))
                    .translate(new Vector3((double) (i % 3 * 5) / 16.0, 0.0, (double) (i / 3 * 5) / 16.0))
                    .rotateH(tcraft.rotation);
            parts.add(new IndexedCuboid6(i + 1, box.bound().add(new Vector3(x, y, z))));
        }
        return parts;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        MovingObjectPosition hit = RayTracer.retraceBlock(world, player, x, y, z);
        TileCraftingGrid tcraft = (TileCraftingGrid) world.getBlockTileEntity(x, y, z);
        if (hit != null) {
            if (hit.subHit > 0) {
                tcraft.activate(hit.subHit - 1, player);
            }
            return true;
        }
        return false;
    }

    public boolean placeBlock(World world, EntityPlayer player, int x, int y, int z, int side) {
        int id = world.getBlockId(x, y, z);
        if (id == Block.snow.blockID) {
            side = 1;
        } else if (!(id == Block.vine.blockID
                || id == Block.tallGrass.blockID
                || id == Block.deadBush.blockID
                || (Block.blocksList[id] != null && Block.blocksList[id].isBlockReplaceable(world, x, y, z)))) {
            if (side == 0) y--;
            if (side == 1) y++;
            if (side == 2) z--;
            if (side == 3) z++;
            if (side == 4) x--;
            if (side == 5) x++;
        }
        if (side != 1) {
            return false;
        }
        BlockCoord beneath = new BlockCoord(x, y, z).offset(0);
        if (!world.isBlockSolidOnSide(beneath.x, beneath.y, beneath.z, ForgeDirection.UP)) {
            return false;
        }
        if (!world.canPlaceEntityOnSide(this.blockID, x, y, z, false, side, null)) {
            return false;
        }
        player.swingItem();
        world.setBlock(x, y, z, this.blockID);
        // 1.4.7 onBlockPlacedBy is 5-arg (no ItemStack); the player is the
        // EntityLiving placer.
        this.onBlockPlacedBy(world, x, y, z, player);
        return true;
    }

    @Override
    public boolean isBlockReplaceable(World world, int x, int y, int z) {
        this.replaceCheck.set(new BlockCoord(x, y, z));
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBlockTextureFromSideAndMetadata(int side, int meta) {
        return 0;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int blockID) {
        BlockCoord beneath = new BlockCoord(x, y, z).offset(0);
        if (!world.isBlockSolidOnSide(beneath.x, beneath.y, beneath.z, ForgeDirection.UP)) {
            this.dropBlockAsItemWithChance(world, x, y, z, 0, 0, 0);
            world.setBlockWithNotify(x, y, z, 0);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLiving entity) {
        ((TileCraftingGrid) world.getBlockTileEntity(x, y, z)).onPlaced(entity);
    }
}
