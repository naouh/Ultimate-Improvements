package com.nao.mfrzone;

import java.io.File;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

/** Lightweight tuning for the laser preview. */
public final class Config {

    /** Max reach (blocks) for triggering a machine's preview — anti-cheat sanity. */
    public static double maxDistance = 8.0;
    /** Safety cap on the radius the preview will draw (a machine's radius normally tops out ~12). */
    public static int maxRadius = 24;
    /** How long the laser box stays up, in seconds. */
    public static int showSeconds = 6;
    /** BuildCraft LaserKind name: typically Red, Blue or Stripes. */
    public static String laserColor = "Red";

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            Property pDist = cfg.get("general", "maxDistance", 8.0);
            pDist.comment = "Max distance (blocks) a player may be from a machine to preview its zone.";
            maxDistance = pDist.getDouble(8.0);

            Property pRad = cfg.get("general", "maxRadius", 24);
            pRad.comment = "Safety cap on the previewed radius (half-extent).";
            maxRadius = pRad.getInt(24);

            Property pSec = cfg.get("general", "showSeconds", 6);
            pSec.comment = "How long the laser box stays visible, in seconds.";
            showSeconds = pSec.getInt(6);

            Property pColor = cfg.get("general", "laserColor", "Red");
            pColor.comment = "BuildCraft laser colour: Red, Blue or Stripes.";
            laserColor = pColor.value;
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            cfg.save();
        }
        if (maxDistance < 2.0) maxDistance = 2.0;
        if (maxRadius < 1) maxRadius = 1;
        if (maxRadius > 64) maxRadius = 64;
        if (showSeconds < 1) showSeconds = 1;
        if (showSeconds > 60) showSeconds = 60;
        if (laserColor == null || laserColor.length() == 0) laserColor = "Red";
    }
}
