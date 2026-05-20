package com.paintbrush;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/**
 * Re-colour recipe: an existing Paint Brush + any one dye -> the brush re-coloured to that
 * dye. A custom {@link IRecipe} rather than 16x16 explicit recipes, since the input brush can
 * be any of the 16 colours. New brushes use the {@code ShapelessOreRecipe}s in
 * {@link PaintBrush#init}.
 */
public class RecipePaintBrush implements IRecipe {

    public boolean matches(InventoryCrafting inv, World world) {
        return craft(inv) != null;
    }

    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return craft(inv);
    }

    /** Returns the re-coloured brush, or null if the grid isn't exactly {brush, dye}. */
    private ItemStack craft(InventoryCrafting inv) {
        ItemStack brush = null;
        int dyeColor = -1;
        int count = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            count++;
            if (s.getItem() instanceof ItemPaintBrush) {
                if (brush != null) return null; // two brushes -> not our recipe
                brush = s;
                continue;
            }
            int c = PaintColors.getDyeColor(s);
            if (c < 0 || dyeColor != -1) return null; // non-dye, or a second dye
            dyeColor = c;
        }
        if (count != 2 || brush == null || dyeColor < 0) return null;
        return new ItemStack(PaintBrush.itemPaintBrush, 1, dyeColor);
    }

    public int getRecipeSize() {
        return 2;
    }

    public ItemStack getRecipeOutput() {
        return new ItemStack(PaintBrush.itemPaintBrush, 1, 0);
    }
}
