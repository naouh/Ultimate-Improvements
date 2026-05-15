package com.quarryplus;

import com.quarryplus.block.BlockFrame;
import com.quarryplus.block.BlockMarker;
import com.quarryplus.block.BlockMover;
import com.quarryplus.block.BlockQuarry;
import com.quarryplus.block.BlockWorkbench;
import com.quarryplus.item.ItemBlockQuarry;
import com.quarryplus.item.ItemTool;
import com.quarryplus.tile.TileMarker;
import com.quarryplus.tile.TileMover;
import com.quarryplus.tile.TileQuarry;
import com.quarryplus.tile.TileWorkbench;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Central registry — holds the static {@link Block} / {@link Item} references and runs the
 * registration calls that {@link QuarryPlus#preInit} / {@link QuarryPlus#init} delegate to.
 *
 * <p>The class is named after the 1.7.10 original ({@code QuarryPlusI}) for grep parity; the
 * leading {@code I} doesn't stand for "interface", it's a holdover from yogpstop's pattern
 * of using a singleton object as a registration hub.
 */
public final class QuarryPlusI {

    public static final CreativeTabs creativeTab = new CreativeTabQuarryPlus();

    // ----- Blocks -----
    public static Block blockQuarry;
    public static Block blockMarker;
    public static Block blockFrame;
    public static Block blockWorkbench;
    public static Block blockMover;

    // ----- Items -----
    public static Item itemTool;

    private QuarryPlusI() {}

    public static void preInit(FMLPreInitializationEvent event) {
        PowerManager.loadConfiguration(Config.cfg);
        Config.cfg.save();

        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(
                QuarryPlus.instance, new ChunkLoadingHandler());

        blockMarker    = new BlockMarker();
        blockFrame     = new BlockFrame();
        blockQuarry    = new BlockQuarry();
        blockWorkbench = new BlockWorkbench();
        blockMover     = new BlockMover();

        itemTool = new ItemTool();
    }

    public static void init() {
        GameRegistry.registerBlock(blockMarker,    "MarkerPlus");
        GameRegistry.registerBlock(blockFrame,     "FramePlus");
        GameRegistry.registerBlock(blockQuarry,    ItemBlockQuarry.class, "QuarryPlus");
        GameRegistry.registerBlock(blockWorkbench, "WorkbenchPlus");
        GameRegistry.registerBlock(blockMover,     "EnchantMover");

        GameRegistry.registerItem(itemTool, "qpTool");

        GameRegistry.registerTileEntity(TileMarker.class,    "MarkerPlus");
        GameRegistry.registerTileEntity(TileQuarry.class,    "QuarryPlus");
        GameRegistry.registerTileEntity(TileWorkbench.class, "WorkbenchPlus");
        GameRegistry.registerTileEntity(TileMover.class,     "EnchantMover");

        registerRecipes();
    }

    private static void registerRecipes() {
        // WorkbenchPlus recipe — Phase 5 ships a vanilla 3x3 fallback so the workbench is
        // craftable without needing the actual workbench. Phase 6 polish adds the rest of
        // the cross-machine recipes (mover, quarry, markers via workbench cost).
        GameRegistry.addRecipe(new ItemStack(blockWorkbench),
                "III", "GDG", "RRR",
                'I', Block.blockSteel,
                'G', Block.blockGold,
                'D', Item.diamond,
                'R', Item.redstone);

        // The remaining recipes (Marker, Quarry, Mover, ItemTool subtypes) are intended to
        // go through the WorkbenchPlus MJ-craft loop. WorkbenchRecipe entries live here:
        WorkbenchRecipe.addRecipe(new ItemStack(blockMarker), 20000,
                new ItemStack(Item.redstone, 300),
                new ItemStack(Item.dyePowder, 300, 4),
                new ItemStack(Item.goldNugget, 175),
                new ItemStack(Item.ingotIron, 150),
                new ItemStack(Item.lightStoneDust, 50),
                new ItemStack(Item.enderPearl, 10));
        WorkbenchRecipe.addRecipe(new ItemStack(blockQuarry), 320000,
                new ItemStack(Item.diamond, 800),
                new ItemStack(Item.ingotGold, 800),
                new ItemStack(Item.ingotIron, 1600),
                new ItemStack(Item.redstone, 400),
                new ItemStack(Item.enderPearl, 50));
        WorkbenchRecipe.addRecipe(new ItemStack(blockMover), 320000,
                new ItemStack(Block.obsidian, 1600),
                new ItemStack(Item.diamond, 800),
                new ItemStack(Item.redstone, 1200),
                new ItemStack(Item.enderPearl, 25));
        WorkbenchRecipe.addRecipe(new ItemStack(itemTool, 1, 0), 80000,
                new ItemStack(Item.ingotGold, 400),
                new ItemStack(Item.ingotIron, 600),
                new ItemStack(Item.diamond, 100),
                new ItemStack(Item.redstone, 400));
        WorkbenchRecipe.addRecipe(new ItemStack(itemTool, 1, 1), 160000,
                new ItemStack(Item.ingotIron, 400),
                new ItemStack(Item.paper, 1600),
                new ItemStack(Item.feather, 50));
    }

    // ----- GUI IDs ----- (kept stable; Phase 6 polish wires them to a real GuiHandler)
    public static final int GUI_WORKBENCH    = 1;
    public static final int GUI_MOVER        = 2;
    public static final int GUI_FORTUNE_LIST = 3;
    public static final int GUI_SILK_LIST    = 4;
}
