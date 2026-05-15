package com.quarryplus.item;

import java.util.List;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.IEnchantableTile;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;

/**
 * Two-mode utility item.
 *
 * <p>Meta 0 = <b>StatusChecker</b> — right-click a machine to print its enchant levels and
 * current state to chat. Right-click a MarkerPlus to print the bounding box.
 *
 * <p>Meta 1 = <b>ListEditor</b> — opens the fortune/silktouch block list editor when used
 * on a QuarryPlus. Phase 6 polish wires the GUI; for Phase 5 the item just exists and
 * prints a placeholder message.
 */
public class ItemTool extends Item {

    private Icon iconStatusChecker;
    private Icon iconListEditor;

    public ItemTool() {
        super(Config.itemToolID - 256); // 1.4.7 item IDs are offset by 256 from block IDs
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName("qpTool");
        setCreativeTab(QuarryPlusI.creativeTab);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Default click — nothing to do; the per-block interaction lives in onItemUse.
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hx, float hy, float hz) {
        if (world.isRemote) return true;

        TileEntity te = world.getBlockTileEntity(x, y, z);

        if (stack.getItemDamage() == 0) {
            // StatusChecker
            if (te instanceof TileMarker) {
                TileMarker tm = (TileMarker) te;
                if (!tm.linked) {
                    player.addChatMessage("[MarkerPlus] not linked");
                } else {
                    player.addChatMessage(String.format("[MarkerPlus] %d,%d,%d -> %d,%d,%d",
                            tm.xMin, tm.yMin, tm.zMin, tm.xMax, tm.yMax, tm.zMax));
                }
                return true;
            }
            if (te instanceof IEnchantableTile) {
                IEnchantableTile et = (IEnchantableTile) te;
                player.addChatMessage(String.format(
                        "[QuarryPlus] Eff %d / Unb %d / Fort %d / Silk %s",
                        et.getEfficiencyLevel(), et.getUnbreakingLevel(),
                        et.getFortuneLevel(), et.getSilkTouch() ? "yes" : "no"));
                return true;
            }
        } else if (stack.getItemDamage() == 1) {
            // ListEditor — Phase 6 polish opens the GUI here.
            if (te instanceof IEnchantableTile) {
                player.addChatMessage("[ListEditor] GUI lands in Phase 6 polish");
                return true;
            }
        }
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Icon getIconFromDamage(int damage) {
        return damage == 0 ? iconStatusChecker : iconListEditor;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IconRegister reg) {
        iconStatusChecker = reg.registerIcon("compass");   // placeholder
        iconListEditor    = reg.registerIcon("book_normal"); // placeholder
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    public void getSubItems(int id, CreativeTabs tab, List list) {
        list.add(new ItemStack(this, 1, 0));
        list.add(new ItemStack(this, 1, 1));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return stack.getItemDamage() == 0 ? "item.statusChecker" : "item.listEditor";
    }
}
