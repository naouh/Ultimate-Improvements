package com.nao.claimteam;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.common.ConfigCategory;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

/**
 * Layout in claimteam.cfg:
 * <pre>
 * [general]
 *   useGroupManager=true
 *
 * [groupLimits]
 *   Default.maxClaims=25
 *   Default.maxChunkloads=5
 *   Builder.maxClaims=50
 *   ...
 *
 * [playerGroups]
 *   naouh=Admin
 *
 * [transformers]
 *   explosion=true
 *   piston=true
 *   fluid=true
 *   pvp=true
 *
 * [gui]
 *   gridRadius=6
 * </pre>
 */
public final class Config {

    private Config() {}

    public static final class GroupLimits {
        public final int maxClaims;
        public final int maxChunkloads;
        public GroupLimits(int c, int cl) { this.maxClaims = c; this.maxChunkloads = cl; }
    }

    public static boolean useGroupManager;
    public static int gridRadius;

    public static boolean enableExplosionTransformer;
    public static boolean enablePistonTransformer;
    public static boolean enableFluidTransformer;
    public static boolean enablePvpTransformer;

    /** group name (case-preserved) -> limits. Lookup is case-insensitive via {@link #limitsFor}. */
    public static final Map<String, GroupLimits> groupLimits = new HashMap<String, GroupLimits>();
    /** player username lowercased -> group name (case-preserved as written). */
    public static final Map<String, String> playerGroups = new HashMap<String, String>();

    private static File configFile;

    public static void load(File file) {
        configFile = file;
        reload();
    }

    public static void reload() {
        if (configFile == null) return;
        groupLimits.clear();
        playerGroups.clear();

        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();

            cfg.addCustomCategoryComment("general",
                    "useGroupManager: if true and Essentials GroupManager is on the classpath, " +
                    "player groups come from GM. Otherwise the [playerGroups] section is used.");
            useGroupManager = cfg.get("general", "useGroupManager", true).getBoolean(true);

            cfg.addCustomCategoryComment("grouplimits",
                    "Per-group max claims and max chunkloads. Format: <group>.maxClaims, <group>.maxChunkloads. " +
                    "Use -1 for unlimited. Group names should match what your permission system uses.");
            ensureDefaultGroup(cfg, "Default", 25, 5);
            ensureDefaultGroup(cfg, "Builder", 50, 15);
            ensureDefaultGroup(cfg, "Moderator", 200, 50);
            ensureDefaultGroup(cfg, "Admin", -1, -1);
            ensureDefaultGroup(cfg, "Owner", -1, -1);
            ConfigCategory glCat = cfg.getCategory("grouplimits");
            Map<String, Integer[]> agg = new HashMap<String, Integer[]>();
            for (Map.Entry<String, Property> e : glCat.getValues().entrySet()) {
                String k = e.getKey();
                int dot = k.indexOf('.');
                if (dot <= 0) continue;
                String group = k.substring(0, dot);
                String field = k.substring(dot + 1);
                Integer[] pair = agg.get(group);
                if (pair == null) { pair = new Integer[]{ 25, 5 }; agg.put(group, pair); }
                int v;
                try { v = Integer.parseInt(e.getValue().value); }
                catch (NumberFormatException ex) { v = 0; }
                if ("maxClaims".equals(field)) pair[0] = v;
                else if ("maxChunkloads".equals(field)) pair[1] = v;
            }
            for (Map.Entry<String, Integer[]> e : agg.entrySet()) {
                groupLimits.put(e.getKey(), new GroupLimits(e.getValue()[0], e.getValue()[1]));
            }

            cfg.addCustomCategoryComment("playergroups",
                    "Fallback mapping when GroupManager is absent or disabled. <username>=<groupName>");
            ConfigCategory pgCat = cfg.getCategory("playergroups");
            // ensure section exists (touch a default)
            if (pgCat.getValues().isEmpty()) {
                cfg.get("playergroups", "exampleAdmin", "Admin");
                pgCat = cfg.getCategory("playergroups");
            }
            for (Map.Entry<String, Property> e : pgCat.getValues().entrySet()) {
                String user = e.getKey();
                if ("exampleAdmin".equals(user)) continue;
                playerGroups.put(user.toLowerCase(), e.getValue().value);
            }

            cfg.addCustomCategoryComment("transformers",
                    "Kill switches per ASM transformer. Set false to disable. Requires restart.");
            enableExplosionTransformer = cfg.get("transformers", "explosion", true).getBoolean(true);
            enablePistonTransformer    = cfg.get("transformers", "piston",    true).getBoolean(true);
            enableFluidTransformer     = cfg.get("transformers", "fluid",     true).getBoolean(true);
            enablePvpTransformer       = cfg.get("transformers", "pvp",       true).getBoolean(true);

            cfg.addCustomCategoryComment("gui",
                    "gridRadius = number of chunks shown around the player in each direction. " +
                    "Grid size = 2*gridRadius+1.");
            gridRadius = cfg.get("gui", "gridRadius", 6).getInt();
            if (gridRadius < 2) gridRadius = 2;
            if (gridRadius > 20) gridRadius = 20;
        } finally {
            cfg.save();
        }
    }

    private static void ensureDefaultGroup(Configuration cfg, String group, int maxClaims, int maxCL) {
        cfg.get("grouplimits", group + ".maxClaims", maxClaims);
        cfg.get("grouplimits", group + ".maxChunkloads", maxCL);
    }

    /** Case-insensitive lookup; falls back to Default; if Default missing returns 25/5. */
    public static GroupLimits limitsFor(String groupName) {
        if (groupName != null) {
            for (Map.Entry<String, GroupLimits> e : groupLimits.entrySet()) {
                if (e.getKey().equalsIgnoreCase(groupName)) return e.getValue();
            }
        }
        for (Map.Entry<String, GroupLimits> e : groupLimits.entrySet()) {
            if ("Default".equalsIgnoreCase(e.getKey())) return e.getValue();
        }
        return new GroupLimits(25, 5);
    }

    /** Case-insensitive username lookup in [playerGroups]. */
    public static String groupForPlayer(String username) {
        if (username == null) return null;
        return playerGroups.get(username.toLowerCase());
    }

    /** For debugging. */
    public static Set<String> knownGroupNames() {
        return groupLimits.keySet();
    }
}
