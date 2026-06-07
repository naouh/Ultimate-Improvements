package com.nao.mousetweaksng;

import java.io.File;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

/** Per-tweak toggles plus the anti-desync pacing throttle. */
public final class Config {

    /** Right-click drag: distribute one item per slot. */
    public static boolean rmbTweak = true;
    /** Left-click drag while holding a stack: merge / quick-move matching stacks. */
    public static boolean lmbTweakWithItem = true;
    /** Left-click drag with an empty hand + sneak: quick-move swept slots out. */
    public static boolean lmbTweakWithoutItem = true;

    /**
     * Minimum milliseconds between two automated clicks. Paces the mod's own clicks so 1.4.7's
     * one-at-a-time window-click transactions confirm before the next is sent, which prevents the
     * visual slot desync (grabbed/crafted items not showing until you click), made worse by
     * TickThreading. 0 disables the throttle.
     */
    public static int minClickGapMs = 50;

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();

            rmbTweak = cfg.get("tweaks", "rmbTweak", true,
                    "Right-click drag: drop one item per slot.").getBoolean(true);

            lmbTweakWithItem = cfg.get("tweaks", "lmbTweakWithItem", true,
                    "Left-click drag while holding a stack: merge / shift-move matching stacks.")
                    .getBoolean(true);

            lmbTweakWithoutItem = cfg.get("tweaks", "lmbTweakWithoutItem", true,
                    "Left-click drag with an empty hand + sneak: shift-move swept slots out.")
                    .getBoolean(true);

            Property pGap = cfg.get("tweaks", "minClickGapMs", 50,
                    "Minimum milliseconds between automated clicks. Paces the mod so 1.4.7's "
                  + "window-click transactions keep up and items don't desync visually (worse "
                  + "under TickThreading). 0 = no throttle. Raise toward 100-150 if you still see "
                  + "items not appearing until you click.");
            minClickGapMs = pGap.getInt(50);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            cfg.save();
        }

        if (minClickGapMs < 0)    minClickGapMs = 0;
        if (minClickGapMs > 1000) minClickGapMs = 1000;
    }
}
