package com.quarryplus;

import java.io.File;

import net.minecraftforge.common.Configuration;

/**
 * Configuration loader. Block / item IDs live here so a server admin can move them out of the
 * way of conflicting mods via {@code config/QuarryPlus.cfg}.
 *
 * <p>Power-cost knobs (BasePower, EfficiencyCoefficient, etc.) are exposed by
 * {@code PowerManager} when that class lands in Phase 4 — keeping them out of here avoids a
 * giant config file before the machines that read them exist.
 */
public final class Config {

    private Config() {}

    // ----- Block IDs -----
    public static int blockQuarryID;
    public static int blockMarkerID;
    public static int blockFrameID;
    public static int blockWorkbenchID;
    public static int blockMoverID;

    // ----- Item IDs -----
    public static int itemToolID;

    // ----- Behaviour knobs -----
    public static int markerMaxRange;
    public static int quarryMaxSize;
    public static double workbenchRecipeDifficulty;

    public static Configuration cfg;

    public static void load(File configFile) {
        cfg = new Configuration(configFile);
        try {
            cfg.load();

            blockQuarryID     = cfg.getBlock("blocks", "QuarryPlus",     1910).getInt();
            blockMarkerID     = cfg.getBlock("blocks", "MarkerPlus",     1911).getInt();
            blockFrameID      = cfg.getBlock("blocks", "FramePlus",      1912).getInt();
            blockWorkbenchID  = cfg.getBlock("blocks", "WorkbenchPlus",  1913).getInt();
            blockMoverID      = cfg.getBlock("blocks", "EnchantMover",   1914).getInt();

            itemToolID = cfg.getItem("items", "QuarryPlusTool", 6100).getInt();

            markerMaxRange = cfg.get("limits", "MarkerMaxRange", 256,
                    "Maximum block span between two paired MarkerPlus along any axis.").getInt();
            quarryMaxSize = cfg.get("limits", "QuarryMaxSize", 256,
                    "Maximum side length of the rectangle a QuarryPlus will mine.").getInt();
            workbenchRecipeDifficulty = cfg.get("workbench", "RecipeDifficulty", 2.0,
                    "Multiplier on the MJ cost of WorkbenchPlus recipes (1.0 = vanilla cost).")
                    .getDouble(2.0);
        } finally {
            cfg.save();
        }
    }
}
