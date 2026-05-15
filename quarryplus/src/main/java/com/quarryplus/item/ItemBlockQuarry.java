package com.quarryplus.item;

import java.util.List;

import com.quarryplus.tile.IEnchantableTile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * ItemBlock for {@link com.quarryplus.block.BlockQuarry}. Carries the quarry's enchantment
 * NBT through inventory storage so a placed quarry with Efficiency V comes back as
 * Efficiency V when broken and replaced.
 *
 * <p>Vanilla {@link ItemBlock#placeBlockAt} sets the metadata but doesn't restore NBT — we
 * override to copy the enchantment list back onto the new tile after the placement settles.
 */
public class ItemBlockQuarry extends ItemBlock {

    public ItemBlockQuarry(int id) {
        super(id);
        setMaxStackSize(1); // Each enchanted quarry is unique.
        setHasSubtypes(false);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
                                int x, int y, int z, int side,
                                float hitX, float hitY, float hitZ, int meta) {
        if (!super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta)) {
            return false;
        }
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof IEnchantableTile && stack.hasTagCompound()) {
            applyEnchants((IEnchantableTile) te, stack);
        }
        return true;
    }

    private static void applyEnchants(IEnchantableTile tile, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("ench")) return;
        NBTTagList list = tag.getTagList("ench");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = (NBTTagCompound) list.tagAt(i);
            short id = entry.getShort("id");
            short lvl = entry.getShort("lvl");
            // Enchantment.efficiency.effectId / unbreaking / fortune / silkTouch are 32/34/35/33
            if (id == 32) tile.setEfficiencyLevel((byte) lvl);
            else if (id == 34) tile.setUnbreakingLevel((byte) lvl);
            else if (id == 35) tile.setFortuneLevel((byte) lvl);
            else if (id == 33) tile.setSilkTouch(lvl > 0);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean adv) {
        super.addInformation(stack, player, tooltip, adv);
    }
}
