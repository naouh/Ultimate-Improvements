package codechicken.translocator;

import codechicken.core.vec.BlockCoord;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.liquids.ITankContainer;

/**
 * The ItemBlock for {@link BlockTranslocator}. We override the placement
 * logic so that right-clicking onto an inventory/tank attaches a translocator
 * mouth on that face — placing on a fresh block makes a new translocator,
 * placing on an existing one of the same metadata just adds another mouth.
 */
public class ItemTranslocator extends ItemBlock {

    public ItemTranslocator(int id) {
        super(id);
        this.setHasSubtypes(true);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
                                int x, int y, int z, int side,
                                float hitX, float hitY, float hitZ, int metadata) {
        int blockID = world.getBlockId(x, y, z);
        if (blockID != this.getBlockID()) {
            // 1.4.7's canPlaceEntityOnSide is 7-args (no stack); 1.5+ added stack.
            if (!world.canPlaceEntityOnSide(this.getBlockID(), x, y, z, false, side, null)) {
                return false;
            }
            if (!super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
                return false;
            }
        }
        TileTranslocator ttrans = (TileTranslocator) world.getBlockTileEntity(x, y, z);
        ttrans.createAttachment(side ^ 1);
        world.notifyBlocksOfNeighborChange(x, y, z, blockID);
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
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
        if (stack.stackSize == 0 || !player.canPlayerEdit(x, y, z, side, stack)) {
            return false;
        }
        if (this.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, stack.getItemDamage())) {
            Block block = Block.blocksList[this.getBlockID()];
            world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5,
                    block.stepSound.getPlaceSound(),
                    (block.stepSound.getVolume() + 1.0f) / 2.0f,
                    block.stepSound.getPitch() * 0.8f);
            stack.stackSize--;
            return true;
        }
        return false;
    }

    @Override
    public boolean canPlaceItemBlockOnSide(World world, int x, int y, int z, int side,
                                           EntityPlayer player, ItemStack stack) {
        BlockCoord pos = new BlockCoord(x, y, z).offset(side);
        if (world.getBlockId(pos.x, pos.y, pos.z) == this.getBlockID()
                && world.getBlockMetadata(pos.x, pos.y, pos.z) != stack.getItemDamage()) {
            return false;
        }
        switch (stack.getItemDamage()) {
            case 0: return world.getBlockTileEntity(x, y, z) instanceof IInventory;
            case 1: return world.getBlockTileEntity(x, y, z) instanceof ITankContainer;
        }
        return false;
    }

    @Override
    public String getItemNameIS(ItemStack stack) {
        // 1.4.7 equivalent of 1.5+ getUnlocalizedName(ItemStack).
        return super.getItemName() + "|" + stack.getItemDamage();
    }
}
