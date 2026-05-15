package com.quarryplus;

import com.quarryplus.block.BlockMarker;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

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

    // ----- Blocks ----- (populated as the corresponding phases land)
    public static Block blockQuarry;
    public static Block blockMarker;
    public static Block blockFrame;
    public static Block blockWorkbench;
    public static Block blockMover;

    // ----- Items -----
    public static Item itemTool;

    private QuarryPlusI() {}

    public static void preInit(FMLPreInitializationEvent event) {
        blockMarker = new BlockMarker();
        // Phase 3 also adds: blockFrame = new BlockFrame();   (drawn by the quarry when it
        //                                                      builds its work-area frame)
        // Phase 4 will add: blockQuarry = new BlockQuarry();
        // Phase 5 will add: blockWorkbench, blockMover, itemTool.
    }

    public static void init() {
        GameRegistry.registerBlock(blockMarker, "MarkerPlus");
        GameRegistry.registerTileEntity(TileMarker.class, "MarkerPlus");
        registerGuiHandler();
    }

    private static void registerGuiHandler() {
        // Wired in Phase 5 when GUIs (WorkbenchPlus, EnchantMover, ListEditor) come online.
        // Keeping the seam here so the @Init body in QuarryPlus stays stable.
    }

    // ----- GUI IDs -----  (kept stable across phases so NetworkRegistry mappings don't shift)
    public static final int GUI_WORKBENCH    = 1;
    public static final int GUI_MOVER        = 2;
    public static final int GUI_FORTUNE_LIST = 3;
    public static final int GUI_SILK_LIST    = 4;
}
