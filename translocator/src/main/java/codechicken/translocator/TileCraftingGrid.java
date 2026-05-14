package codechicken.translocator;

import codechicken.core.inventory.InventoryUtils;
import codechicken.core.packet.ICustomPacketTile;
import codechicken.core.packet.PacketCustom;
import codechicken.core.vec.Vector3;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;

/**
 * Mini crafting bench — 3x3 grid stored as 9 items, rotated by viewing
 * direction. The crafting recipe is checked at every rotation (4 of them) so
 * a recipe placed off-angle still works.
 */
public class TileCraftingGrid extends TileEntity implements ICustomPacketTile {

    public ItemStack[] items = new ItemStack[9];
    public ItemStack result = null;
    public int rotation = 0;
    public int timeout = 400;

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("items", InventoryUtils.writeItemStacksToTag(this.items));
        tag.setInteger("timeout", this.timeout);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        InventoryUtils.readItemStacksFromTag(this.items, tag.getTagList("items"));
        this.timeout = tag.getInteger("timeout");
    }

    @Override
    public void updateEntity() {
        if (!this.worldObj.isRemote) {
            this.timeout--;
            if (this.timeout == 0) {
                dropItems();
                this.worldObj.setBlockWithNotify(this.xCoord, this.yCoord, this.zCoord, 0);
            }
        }
    }

    public void dropItems() {
        Vector3 drop = Vector3.fromTileEntityCenter(this);
        for (ItemStack item : this.items) {
            if (item != null) {
                InventoryUtils.dropItem(item, this.worldObj, drop);
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 3);
        packet.setChunkDataPacket();
        packet.writeCoord(this.xCoord, this.yCoord, this.zCoord);
        packet.writeByte(this.rotation);
        for (ItemStack item : this.items) {
            packet.writeItemStack(item);
        }
        return packet.toPacket();
    }

    @Override
    public void handleDescriptionPacket(PacketCustom packet) {
        this.rotation = packet.readUnsignedByte();
        for (int i = 0; i < 9; i++) {
            this.items[i] = packet.readItemStack();
        }
        updateResult();
    }

    public void activate(int subHit, EntityPlayer player) {
        ItemStack held = player.inventory.getCurrentItem();
        if (held == null) {
            if (this.items[subHit] != null) {
                giveOrDropItem(this.items[subHit], player);
            }
            this.items[subHit] = null;
        } else if (!InventoryUtils.areStacksIdentical(held, this.items[subHit])) {
            ItemStack old = this.items[subHit];
            this.items[subHit] = InventoryUtils.copyStack(held, 1);
            player.inventory.decrStackSize(player.inventory.currentItem, 1);
            if (old != null) {
                giveOrDropItem(old, player);
            }
        }
        this.timeout = 2400;
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        this.onInventoryChanged();
    }

    private void updateResult() {
        InventoryCrafting craftMatrix = getCraftMatrix();
        // We rotate up to 3 times trying to match a recipe — this is what
        // makes crafting work regardless of how the grid is oriented.
        for (int i = 0; i < 4; i++) {
            ItemStack mresult = CraftingManager.getInstance().findMatchingRecipe(craftMatrix, this.worldObj);
            if (mresult != null) {
                this.result = mresult;
                return;
            }
            rotateItems(craftMatrix);
        }
        this.result = null;
    }

    private void giveOrDropItem(ItemStack stack, EntityPlayer player) {
        if (player.inventory.addItemStackToInventory(stack)) {
            player.inventoryContainer.detectAndSendChanges();
        } else {
            InventoryUtils.dropItem(stack, this.worldObj, Vector3.fromTileEntityCenter(this));
        }
    }

    public void craft(EntityPlayer player) {
        InventoryCrafting craftMatrix = getCraftMatrix();
        for (int i = 0; i < 4; i++) {
            ItemStack mresult = CraftingManager.getInstance().findMatchingRecipe(craftMatrix, this.worldObj);
            if (mresult != null) {
                doCraft(mresult, craftMatrix, player);
                break;
            }
            rotateItems(craftMatrix);
        }
        player.closeScreen();
        dropItems();
        this.worldObj.setBlockWithNotify(this.xCoord, this.yCoord, this.zCoord, 0);
    }

    private InventoryCrafting getCraftMatrix() {
        InventoryCrafting craftMatrix = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer entityplayer) {
                return true;
            }
        }, 3, 3);
        for (int i = 0; i < 9; i++) {
            craftMatrix.setInventorySlotContents(i, this.items[i]);
        }
        return craftMatrix;
    }

    private void doCraft(ItemStack mresult, InventoryCrafting craftMatrix, EntityPlayer player) {
        giveOrDropItem(mresult, player);
        GameRegistry.onItemCrafted(player, mresult, craftMatrix);
        mresult.onCrafting(this.worldObj, player, mresult.stackSize);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = craftMatrix.getStackInSlot(slot);
            if (stack != null) {
                craftMatrix.decrStackSize(slot, 1);
                if (stack.getItem().hasContainerItem()) {
                    ItemStack container = stack.getItem().getContainerItemStack(stack);
                    if (container != null) {
                        if (container.isItemStackDamageable()
                                && container.getItemDamage() > container.getMaxDamage()) {
                            container = null;
                        }
                        craftMatrix.setInventorySlotContents(slot, container);
                    }
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            this.items[i] = craftMatrix.getStackInSlot(i);
        }
    }

    private void rotateItems(InventoryCrafting inv) {
        // Walk the 8 perimeter slots clockwise (0,1,2,5,8,7,6,3); rotating
        // by two positions in this sequence is a 90deg rotation of the grid.
        int[] slots = { 0, 1, 2, 5, 8, 7, 6, 3 };
        ItemStack[] arrangement = new ItemStack[9];
        arrangement[4] = inv.getStackInSlot(4);
        for (int i = 0; i < 8; i++) {
            arrangement[slots[(i + 2) % 8]] = inv.getStackInSlot(slots[i]);
        }
        for (int i = 0; i < 9; i++) {
            inv.setInventorySlotContents(i, arrangement[i]);
        }
    }

    public void onPlaced(EntityLiving entity) {
        this.rotation = (int) ((double) (entity.rotationYaw * 4.0f / 360.0f) + 0.5) & 3;
    }
}
