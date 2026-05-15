package com.quarryplus.item;

import java.util.List;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.IEnchantableTile;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Two-mode utility item.
 *
 * <p>Meta 0 = <b>StatusChecker</b> — right-click a machine to print its enchant levels.
 * Right-click a MarkerPlus to print the bounding box.
 *
 * <p>Meta 1 = <b>ListEditor</b> — opens the fortune/silktouch block list editor on a
 * QuarryPlus. The GUI is deferred to Phase 6 polish; for now it prints a placeholder.
 */
public class ItemTool extends Item {

    public ItemTool() {
        super(Config.itemToolID - 256);
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setItemName("qpTool");
        setCreativeTab(QuarryPlusI.creativeTab);
        setTextureFile("/mods/quarryplus/textures/items/items.png");
    }

    @Override
    public int getIconFromDamage(int damage) {
        // items.png layout: 0 = StatusChecker (x=0,y=0), 1 = ListEditor (x=1,y=0).
        return damage;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hx, float hy, float hz) {
        if (world.isRemote) return true;
        TileEntity te = world.getBlockTileEntity(x, y, z);

        if (stack.getItemDamage() == 0) {
            if (te instanceof TileMarker) {
                TileMarker tm = (TileMarker) te;
                if (!tm.linked) {
                    player.sendChatToPlayer("[MarkerPlus] not linked");
                } else {
                    player.sendChatToPlayer(String.format("[MarkerPlus] %d,%d,%d -> %d,%d,%d",
                            tm.xMin, tm.yMin, tm.zMin, tm.xMax, tm.yMax, tm.zMax));
                }
                return true;
            }
            if (te instanceof IEnchantableTile) {
                IEnchantableTile et = (IEnchantableTile) te;
                player.sendChatToPlayer(String.format(
                        "[QuarryPlus] Eff %d / Unb %d / Fort %d / Silk %s",
                        et.getEfficiencyLevel(), et.getUnbreakingLevel(),
                        et.getFortuneLevel(), et.getSilkTouch() ? "yes" : "no"));
                return true;
            }
        } else if (stack.getItemDamage() == 1) {
            if (te instanceof IEnchantableTile) {
                player.sendChatToPlayer("[ListEditor] GUI lands in Phase 6 polish");
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    public void getSubItems(int id, CreativeTabs tab, List list) {
        list.add(new ItemStack(this, 1, 0));
        list.add(new ItemStack(this, 1, 1));
    }

    @Override
    public String getItemNameIS(ItemStack stack) {
        return stack.getItemDamage() == 0 ? "item.statusChecker" : "item.listEditor";
    }
}
