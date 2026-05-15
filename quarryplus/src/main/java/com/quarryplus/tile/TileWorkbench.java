package com.quarryplus.tile;

import java.io.DataInputStream;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * WorkbenchPlus tile. Behaves like a 27-slot chest for now (Phase 5) — Phase 6 polish adds
 * the auto-craft loop that draws MJ to consume recipe inputs and dispense results from
 * {@link com.quarryplus.WorkbenchRecipe#recipes}.
 *
 * <p>The 27 slots are conceptually split as 9 input / 9 internal / 9 output, but Phase 5
 * treats them as a uniform chest to keep the GUI plumbing trivial.
 */
public class TileWorkbench extends APowerTile implements IInventory {

    private final ItemStack[] slots = new ItemStack[27];

    @Override public int getSizeInventory()   { return slots.length; }
    @Override public ItemStack getStackInSlot(int i) { return slots[i]; }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slots[slot] == null) return null;
        if (slots[slot].stackSize <= amount) {
            ItemStack ret = slots[slot];
            slots[slot] = null;
            onInventoryChanged();
            return ret;
        }
        ItemStack ret = slots[slot].splitStack(amount);
        if (slots[slot].stackSize == 0) slots[slot] = null;
        onInventoryChanged();
        return ret;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack ret = slots[slot];
        slots[slot] = null;
        return ret;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        slots[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        onInventoryChanged();
    }

    @Override public String getInvName()                            { return "WorkbenchPlus"; }
    @Override public boolean isInvNameLocalized()                   { return false; }
    @Override public int getInventoryStackLimit()                   { return 64; }
    @Override public boolean isUseableByPlayer(EntityPlayer p)      {
        return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
                && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }
    @Override public void openChest()                                { /* no-op */ }
    @Override public void closeChest()                               { /* no-op */ }
    @Override public boolean isItemValidForSlot(int slot, ItemStack s) { return true; }

    // ===== Packet stubs =====
    @Override public void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) { }
    @Override public void C_receivePacket(byte type, DataInputStream in)                       { }

    // ===== NBT =====
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            slots[i].writeToNBT(entry);
            list.appendTag(entry);
        }
        tag.setTag("Items", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagList list = tag.getTagList("Items");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = (NBTTagCompound) list.tagAt(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < slots.length) slots[slot] = ItemStack.loadItemStackFromNBT(entry);
        }
    }
}
