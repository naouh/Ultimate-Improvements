package com.quarryplus.tile;

import java.io.DataInputStream;

import com.quarryplus.WorkbenchRecipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * WorkbenchPlus tile.
 *
 * <p>27 slots split conceptually as 9 input (0–8) + 9 internal (9–17) + 9 output (18–26).
 * The crafting loop drains MJ from the power buffer each tick proportional to the cost of
 * the recipe whose inputs are currently in slots 0–8, accumulates progress on the active
 * recipe in {@link #progress}, and when the recipe completes, consumes the inputs and
 * pushes one result into output slot 18–26 (or the next free slot).
 */
public class TileWorkbench extends APowerTile implements IInventory {

    private static final int IN_START  = 0,  IN_END  = 9;
    private static final int OUT_START = 18, OUT_END = 27;

    private final ItemStack[] slots = new ItemStack[27];

    /** Cost progress for the currently-cooking recipe, in MJ. */
    public double progress;

    @Override public int getSizeInventory()                       { return slots.length; }
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
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        onInventoryChanged();
    }

    @Override public String getInvName()                            { return "WorkbenchPlus"; }
    @Override public int getInventoryStackLimit()                   { return 64; }
    @Override public boolean isUseableByPlayer(EntityPlayer p)      {
        return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
                && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }
    @Override public void openChest()                                { }
    @Override public void closeChest()                               { }

    // ===== Auto-craft loop =====

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        WorkbenchRecipe active = findMatchingRecipe();
        if (active == null) {
            progress = 0;
            return;
        }
        // Configure budget — generous receive/store for the workbench (Phase 6 polish can
        // expose this as a config knob).
        configure(500, 50000);

        // Spend whatever's available toward the active recipe.
        float spent = useEnergy(0, 500, true);
        progress += spent;
        if (progress >= active.mjCost) {
            consumeInputs(active);
            tryEmitResult(active);
            progress = 0;
        }
    }

    private WorkbenchRecipe findMatchingRecipe() {
        for (WorkbenchRecipe r : WorkbenchRecipe.recipes) {
            if (hasInputs(r) && hasRoomForResult(r)) return r;
        }
        return null;
    }

    private boolean hasInputs(WorkbenchRecipe r) {
        outer:
        for (ItemStack need : r.inputs) {
            int remaining = need.stackSize;
            for (int s = IN_START; s < IN_END; s++) {
                if (slots[s] == null) continue;
                if (slots[s].itemID != need.itemID) continue;
                if (need.getItemDamage() != -1 && slots[s].getItemDamage() != need.getItemDamage()) continue;
                remaining -= slots[s].stackSize;
                if (remaining <= 0) continue outer;
            }
            return false; // not enough of this input
        }
        return true;
    }

    private void consumeInputs(WorkbenchRecipe r) {
        for (ItemStack need : r.inputs) {
            int remaining = need.stackSize;
            for (int s = IN_START; s < IN_END && remaining > 0; s++) {
                if (slots[s] == null) continue;
                if (slots[s].itemID != need.itemID) continue;
                if (need.getItemDamage() != -1 && slots[s].getItemDamage() != need.getItemDamage()) continue;
                int take = Math.min(remaining, slots[s].stackSize);
                slots[s].stackSize -= take;
                remaining -= take;
                if (slots[s].stackSize == 0) slots[s] = null;
            }
        }
        onInventoryChanged();
    }

    private boolean hasRoomForResult(WorkbenchRecipe r) {
        ItemStack result = r.result;
        for (int s = OUT_START; s < OUT_END; s++) {
            if (slots[s] == null) return true;
            if (slots[s].itemID == result.itemID
                && slots[s].getItemDamage() == result.getItemDamage()
                && slots[s].stackSize + result.stackSize <= slots[s].getMaxStackSize()) return true;
        }
        return false;
    }

    private void tryEmitResult(WorkbenchRecipe r) {
        ItemStack result = r.result.copy();
        for (int s = OUT_START; s < OUT_END; s++) {
            if (slots[s] == null) {
                slots[s] = result;
                onInventoryChanged();
                return;
            }
            if (slots[s].itemID == result.itemID
                && slots[s].getItemDamage() == result.getItemDamage()
                && slots[s].stackSize + result.stackSize <= slots[s].getMaxStackSize()) {
                slots[s].stackSize += result.stackSize;
                onInventoryChanged();
                return;
            }
        }
    }

    // ===== Packet stubs =====
    @Override public void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) { }
    @Override public void C_receivePacket(byte type, DataInputStream in)                       { }

    // ===== NBT =====
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("progress", progress);
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
        progress = tag.getDouble("progress");
        NBTTagList list = tag.getTagList("Items");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = (NBTTagCompound) list.tagAt(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < slots.length) slots[slot] = ItemStack.loadItemStackFromNBT(entry);
        }
    }
}
