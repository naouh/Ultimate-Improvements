package com.quarryplus;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

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
        // Placeholder until BlockQuarry exists (Phase 4). Returning a vanilla item keeps the
        // tab visible/clickable in the creative inventory before our blocks land.
        return Item.diamond;
    }
}
