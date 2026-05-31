package com.nao.voicechat;

import java.io.File;

import net.minecraftforge.common.Configuration;

public final class VoiceConfig {

    private VoiceConfig() {}

    public static int     udpPort;
    public static String  publicHost;
    public static int     maxRangeBlocks;
    public static int     micGainPercent;
    public static int     spkGainPercent;
    public static boolean selfEcho;
    public static boolean verboseLogging;
    /** Client-side master switch. Toggleable at runtime via the mute GUI; persisted here. */
    public static boolean enabled = true;
    private static java.io.File cfgFile;

    public static void load(File file) {
        cfgFile = file;
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();

            cfg.addCustomCategoryComment("general",
                    "enabled: master switch (client). When false, mic capture is off and " +
                    "incoming audio is dropped. Toggled at runtime via the mute GUI (B).");
            enabled        = cfg.get("general", "enabled", true).getBoolean(true);

            cfg.addCustomCategoryComment("network",
                    "udpPort: UDP port the voice server listens on (server side). " +
                    "publicHost: hostname/IP the client should connect to; leave empty to reuse " +
                    "the host of the Minecraft connection.");
            udpPort        = cfg.get("network", "udpPort", 25566).getInt();
            publicHost     = cfg.get("network", "publicHost", "").value;

            cfg.addCustomCategoryComment("audio",
                    "maxRangeBlocks: distance (blocks) at which a talker is fully attenuated. " +
                    "gain values are 0..400, applied as a multiplier on PCM samples (clamped). " +
                    "selfEcho: if true, the server also sends your own voice back to you — for " +
                    "solo testing. Leave false in normal play.");
            maxRangeBlocks = cfg.get("audio", "maxRangeBlocks", 32).getInt();
            micGainPercent = cfg.get("audio", "micGainPercent", 100).getInt();
            spkGainPercent = cfg.get("audio", "spkGainPercent", 100).getInt();
            selfEcho       = cfg.get("audio", "selfEcho", false).getBoolean(false);

            cfg.addCustomCategoryComment("debug", "Spam the log with UDP packet counts.");
            verboseLogging = cfg.get("debug", "verboseLogging", false).getBoolean(false);
        } finally {
            cfg.save();
        }

        if (maxRangeBlocks < 4) maxRangeBlocks = 4;
        if (maxRangeBlocks > 256) maxRangeBlocks = 256;
        if (micGainPercent < 0) micGainPercent = 0;
        if (micGainPercent > 400) micGainPercent = 400;
        if (spkGainPercent < 0) spkGainPercent = 0;
        if (spkGainPercent > 400) spkGainPercent = 400;
    }

    /** Persist a flipped {@link #enabled} flag back to disk without rewriting the rest. */
    public static void saveEnabled() {
        if (cfgFile == null) return;
        Configuration cfg = new Configuration(cfgFile);
        try {
            cfg.load();
            cfg.get("general", "enabled", true).value = Boolean.toString(enabled);
        } finally {
            cfg.save();
        }
    }
}
