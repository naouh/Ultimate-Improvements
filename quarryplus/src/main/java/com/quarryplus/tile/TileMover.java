package com.quarryplus.tile;

import java.io.DataInputStream;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * EnchantMover tile. Three slots: source (slot 0), target (slot 1), buffer (slot 2). The
 * actual "transfer enchantment from source → target" logic is exposed through
 * {@link #tryMove(int)} — Phase 5 ships the inventory plumbing only; the GUI buttons that
 * call {@code tryMove} arrive in Phase 6 polish.
 */
public class TileMover extends APowerTile implements IInventory {

    private final ItemStack[] slots = new ItemStack[3];

    @Override public int getSizeInventory()                       { return 3; }
    @Override public ItemStack getStackInSlot(int i)              { return slots[i]; }

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
        onInventoryChanged();
    }

    @Override public String getInvName()                            { return "EnchantMover"; }
    @Override public int getInventoryStackLimit()                   { return 1; }
    @Override public boolean isUseableByPlayer(EntityPlayer p)      {
        return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
                && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }
    @Override public void openChest()                                { }
    @Override public void closeChest()                               { }

    /** Move one enchantment from source (slot 0) to target (slot 1). Wired to GUI in Phase 6. */
    public boolean tryMove(int enchantId) {
        ItemStack src = slots[0], tgt = slots[1];
        if (src == null || tgt == null) return false;
        if (!src.hasTagCompound() || !src.getTagCompound().hasKey("ench")) return false;

        NBTTagList srcList = src.getTagCompound().getTagList("ench");
        NBTTagCompound moved = null;
        for (int i = 0; i < srcList.tagCount(); i++) {
            NBTTagCompound e = (NBTTagCompound) srcList.tagAt(i);
            if (e.getShort("id") == enchantId) { moved = e; srcList.removeTag(i); break; }
        }
        if (moved == null) return false;
        if (!tgt.hasTagCompound()) tgt.setTagCompound(new NBTTagCompound());
        NBTTagCompound tgtTag = tgt.getTagCompound();
        NBTTagList tgtList = tgtTag.hasKey("ench") ? tgtTag.getTagList("ench") : new NBTTagList();
        tgtList.appendTag(moved);
        tgtTag.setTag("ench", tgtList);

        if (srcList.tagCount() == 0) src.getTagCompound().removeTag("ench");
        onInventoryChanged();
        return true;
    }

    @Override public void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) { }
    @Override public void C_receivePacket(byte type, DataInputStream in) { }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound e = new NBTTagCompound();
            e.setByte("Slot", (byte) i);
            slots[i].writeToNBT(e);
            list.appendTag(e);
        }
        tag.setTag("Items", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagList list = tag.getTagList("Items");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = (NBTTagCompound) list.tagAt(i);
            int slot = e.getByte("Slot") & 0xFF;
            if (slot < slots.length) slots[slot] = ItemStack.loadItemStackFromNBT(e);
        }
    }
}
