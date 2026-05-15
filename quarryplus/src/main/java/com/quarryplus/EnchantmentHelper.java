package com.quarryplus;

import com.quarryplus.tile.IEnchantableTile;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Bridges between a placed enchantable tile and the {@link ItemStack} that represents it in
 * an inventory.
 *
 * <p>The four supported enchantments are Efficiency, Unbreaking, Fortune and Silk Touch.
 * Their levels are stored in the tile via {@link IEnchantableTile}; when the block is broken
 * we copy the levels back into the dropped item's NBT so the next placement restores them.
 *
 * <p>1.7.10 used real vanilla enchantment tags ({@code ench:[{id:..,lvl:..}]}). We keep that
 * exact layout — that means an enchanted QuarryPlus item glints in the inventory and reads
 * properly in any anvil that handles ItemStacks generically.
 */
public final class EnchantmentHelper {

    private EnchantmentHelper() {}

    public static void enchantmentToIS(IEnchantableTile tile, ItemStack stack) {
        if (stack == null || tile == null) return;
        addEnchant(stack, Enchantment.efficiency.effectId,    tile.getEfficiencyLevel());
        addEnchant(stack, Enchantment.unbreaking.effectId,    tile.getUnbreakingLevel());
        addEnchant(stack, Enchantment.fortune.effectId,       tile.getFortuneLevel());
        if (tile.getSilkTouch()) {
            addEnchant(stack, Enchantment.silkTouch.effectId, (short) 1);
        }
    }

    public static void enchantmentFromIS(IEnchantableTile tile, ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("ench")) return;
        NBTTagList list = tag.getTagList("ench");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = (NBTTagCompound) list.tagAt(i);
            short id  = e.getShort("id");
            short lvl = e.getShort("lvl");
            if (id == Enchantment.efficiency.effectId) tile.setEfficiencyLevel((byte) lvl);
            else if (id == Enchantment.unbreaking.effectId) tile.setUnbreakingLevel((byte) lvl);
            else if (id == Enchantment.fortune.effectId)    tile.setFortuneLevel((byte) lvl);
            else if (id == Enchantment.silkTouch.effectId)  tile.setSilkTouch(lvl > 0);
        }
    }

    private static void addEnchant(ItemStack stack, int id, short lvl) {
        if (lvl <= 0) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagList list = tag.hasKey("ench") ? tag.getTagList("ench") : new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setShort("id", (short) id);
        entry.setShort("lvl", lvl);
        list.appendTag(entry);
        tag.setTag("ench", list);
    }
}
