package com.quarryplus;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * Creative-mode tab for the QuarryPlus blocks and items. Uses the QuarryPlus block as its
 * tab icon — resolved lazily because the tab object is constructed before {@link QuarryPlusI}
 * has populated its block references.
 */
public class CreativeTabQuarryPlus extends CreativeTabs {

    public CreativeTabQuarryPlus() {
        super("QuarryPlus");
    }

    @Override
    public String getTranslatedTabLabel() {
        return "QuarryPlus";
    }

    @Override
    public Item getTabIconItem() {
        // QuarryPlusI.blockQuarry isn't set yet at static-init time — fall back to a vanilla
        // icon until preInit runs. After that the call resolves to the real block.
        if (QuarryPlusI.blockQuarry != null) {
            return Item.itemsList[QuarryPlusI.blockQuarry.blockID];
        }
        return Item.diamond;
    }
}
