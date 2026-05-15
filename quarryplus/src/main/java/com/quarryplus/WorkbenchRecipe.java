package com.quarryplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Registry for WorkbenchPlus recipes.
 *
 * <p>Each recipe is { result, MJ cost, [inputs...] }. Inputs are matched by item ID + meta;
 * stack sizes specify how many the recipe consumes. The auto-craft loop in
 * {@link com.quarryplus.tile.TileWorkbench} consumes power proportional to {@link #mjCost}
 * each tick, then dispenses a result.
 *
 * <p>Phase 5 ships only the registry — the actual {@code TileWorkbench.craft()} loop is
 * Phase 6 polish. Recipe entries can be added freely without breaking anything.
 */
public final class WorkbenchRecipe {

    public static final List<WorkbenchRecipe> recipes = new ArrayList<WorkbenchRecipe>();
    public static double difficulty = 2.0;

    public final ItemStack result;
    public final double mjCost;
    public final ItemStack[] inputs;

    private WorkbenchRecipe(ItemStack result, double mjCost, ItemStack[] inputs) {
        this.result = result;
        this.mjCost = mjCost;
        this.inputs = inputs;
    }

    public static void addRecipe(ItemStack result, double mjCost, ItemStack... inputs) {
        recipes.add(new WorkbenchRecipe(result, mjCost * difficulty, inputs));
    }
}
