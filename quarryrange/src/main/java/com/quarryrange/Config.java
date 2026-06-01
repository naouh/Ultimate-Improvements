package com.quarryrange;

import java.io.File;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

/** Server-authoritative tuning. Sizes are the TOTAL quarry box edge (frame included). */
public final class Config {

    /** Smallest selectable box edge. Defaults to 11 (BuildCraft's own default 9x9 mined area). */
    public static int minSize = 11;
    /** Largest selectable box edge. 64 is the classic BuildCraft landmark maximum. */
    public static int maxSize = 64;
    /** Edge used when a quarry is placed / when the editor is cancelled. Defaults to the max. */
    public static int defaultSize = 64;

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            Property pMin = cfg.get("quarry", "minSize", 11);
            pMin.comment = "Smallest selectable quarry box edge (>= 3). Default 11 = BuildCraft's 9x9.";
            minSize = pMin.getInt(11);

            Property pMax = cfg.get("quarry", "maxSize", 64);
            pMax.comment = "Largest selectable quarry box edge (<= 64 for chunkloading sanity).";
            maxSize = pMax.getInt(64);

            Property pDef = cfg.get("quarry", "defaultSize", 64);
            pDef.comment = "Box edge applied on placement / on cancel. Defaults to max.";
            defaultSize = pDef.getInt(64);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            cfg.save();
        }
        sanitize();
    }

    private static void sanitize() {
        if (minSize < 3) minSize = 3;
        if (maxSize > 64) maxSize = 64;
        if (maxSize < minSize) maxSize = minSize;
        if (defaultSize < minSize) defaultSize = minSize;
        if (defaultSize > maxSize) defaultSize = maxSize;
    }

    public static int clamp(int size) {
        if (size < minSize) return minSize;
        if (size > maxSize) return maxSize;
        return size;
    }
}
