package com.paintbrush;

import java.io.File;

import net.minecraftforge.common.Configuration;

/**
 * Configuration loader. The Paint Brush item ID lives in {@code config/PaintBrush.cfg} so a
 * server admin can move it if 14785 collides with another mod in a heavily-loaded pack.
 */
public final class Config {

    private Config() {}

    public static int itemPaintBrushID;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            itemPaintBrushID = cfg.getItem("items", "PaintBrush", 14785).getInt();
        } finally {
            cfg.save();
        }
    }
}
