package com.paintbrush;

import java.util.ArrayList;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The 16 paint colours. Index order matches vanilla dye damage and the IC2 painter index, so
 * a colour value is passed straight to {@code IPaintableBlock.colorBlock(...)} unchanged.
 */
public final class PaintColors {

    private PaintColors() {}

    /** Ore-dictionary dye names, indexed 0..15. */
    public static final String[] DYE_ORE = {
        "dyeBlack", "dyeRed", "dyeGreen", "dyeBrown",
        "dyeBlue", "dyePurple", "dyeCyan", "dyeLightGray",
        "dyeGray", "dyePink", "dyeLime", "dyeYellow",
        "dyeLightBlue", "dyeMagenta", "dyeOrange", "dyeWhite",
    };

    /** Display names, same index order as {@link #DYE_ORE}. */
    public static final String[] NAME = {
        "Black", "Red", "Green", "Brown",
        "Blue", "Purple", "Cyan", "Light Gray",
        "Gray", "Pink", "Lime", "Yellow",
        "Light Blue", "Magenta", "Orange", "White",
    };

    /**
     * Resolves the dye colour of a stack to an index 0..15, or -1 if it isn't a dye. The ore
     * dictionary is checked first (so modded dyes work) with a vanilla dye fallback.
     */
    public static int getDyeColor(ItemStack stack) {
        if (stack == null) return -1;
        for (int c = 0; c < 16; c++) {
            ArrayList ores = OreDictionary.getOres(DYE_ORE[c]);
            for (int i = 0; i < ores.size(); i++) {
                if (oreMatches((ItemStack) ores.get(i), stack)) {
                    return c;
                }
            }
        }
        if (stack.itemID == Item.dyePowder.itemID) {
            int d = stack.getItemDamage();
            if (d >= 0 && d < 16) return d;
        }
        return -1;
    }

    private static boolean oreMatches(ItemStack ore, ItemStack in) {
        if (ore == null || ore.itemID != in.itemID) return false;
        int od = ore.getItemDamage();
        return od == 32767 || od == in.getItemDamage(); // 32767 = OreDictionary wildcard
    }
}
