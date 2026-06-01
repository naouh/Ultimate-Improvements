package com.nao.serverguide.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the {@code config/serverguide/} directory and the editable content files. On first run it
 * writes sensible defaults (the tutorial is filled in; rules/banned are placeholders for the pack
 * maintainer to edit). Content is read fresh from disk by {@link #load()} every time the GUI opens,
 * so edits show up on the next open without a restart.
 *
 * Simple markup understood by the renderer:
 *   "# Heading"   -> gold heading
 *   "## Heading"  -> yellow sub-heading
 *   "- bullet"    -> bulleted line
 *   "" (blank)    -> vertical gap
 *   anything else -> paragraph text (vanilla section-sign colour codes work)
 */
public final class GuideContent {

    private GuideContent() {}

    private static final String[] TITLES = { "Rules", "Banned Items", "Getting Started" };
    private static final String[] FILES  = { "rules.txt", "banned.txt", "tutorial.txt" };

    private static File dir;

    /** Called at preInit on both sides with the mod configuration directory. */
    public static void init(File baseConfigDir) {
        dir = new File(baseConfigDir, "serverguide");
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("[ServerGuide] failed to create " + dir);
        }
        ensureDefaults();
    }

    /** Re-create any missing files (does not overwrite existing edits). */
    public static void reload() {
        ensureDefaults();
    }

    private static void ensureDefaults() {
        if (dir == null) return;
        writeIfMissing(FILES[0], DEFAULT_RULES);
        writeIfMissing(FILES[1], DEFAULT_BANNED);
        writeIfMissing(FILES[2], DEFAULT_TUTORIAL);
    }

    /** Read every page fresh from disk. Falls back to the built-in default if a file is unreadable. */
    public static List<GuidePage> load() {
        List<GuidePage> pages = new ArrayList<GuidePage>();
        for (int i = 0; i < FILES.length; i++) {
            String text = readFile(FILES[i]);
            if (text == null) text = defaultFor(i);
            pages.add(new GuidePage(TITLES[i], FILES[i], text));
        }
        return pages;
    }

    private static String defaultFor(int i) {
        switch (i) {
            case 0: return DEFAULT_RULES;
            case 1: return DEFAULT_BANNED;
            default: return DEFAULT_TUTORIAL;
        }
    }

    private static void writeIfMissing(String name, String body) {
        File f = new File(dir, name);
        if (f.exists()) return;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(body.getBytes("UTF-8"));
            System.out.println("[ServerGuide] wrote default " + name);
        } catch (IOException e) {
            System.err.println("[ServerGuide] failed to write " + name + ": " + e.getMessage());
        } finally {
            if (fos != null) try { fos.close(); } catch (IOException ignored) {}
        }
    }

    private static String readFile(String name) {
        if (dir == null) return null;
        File f = new File(dir, name);
        if (!f.exists()) return null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = 0;
            while (read < buf.length) {
                int n = fis.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(buf, 0, read, "UTF-8");
        } catch (IOException e) {
            System.err.println("[ServerGuide] failed to read " + name + ": " + e.getMessage());
            return null;
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Default content
    // ------------------------------------------------------------------

    private static final String DEFAULT_RULES =
        "# Server Rules\n" +
        "Play fair, be decent, and don't ruin the game for others.\n" +
        "\n" +
        "## Be decent\n" +
        "- No griefing, stealing, or destroying other players' builds.\n" +
        "- No harassment, hate speech, or chat spam.\n" +
        "- Keep global chat friendly.\n" +
        "\n" +
        "## Fair play\n" +
        "- No cheating, X-ray, exploits, or duplication glitches.\n" +
        "- Report bugs to staff instead of abusing them.\n" +
        "- Automated farms are fine; machines built only to lag the server are not.\n" +
        "\n" +
        "## Land & claims\n" +
        "- Protect your base with /claim. Claiming costs money per chunk.\n" +
        "- Press J to open the claim / chunk-load map.\n" +
        "- Don't build right against someone else's base without asking.\n" +
        "\n" +
        "## Banned items\n" +
        "- Some items are blocked to keep the server stable and fair. See the\n" +
        "  Banned Items tab for the full list.\n" +
        "\n" +
        "Breaking the rules may lead to a warning, mute, or ban at staff discretion.\n";

    private static final String DEFAULT_BANNED =
        "# Banned & Restricted Items\n" +
        "These items are blocked by ItemGuard. You cannot craft or place them, and\n" +
        "trying to use one does nothing. Players with staff bypass are exempt.\n" +
        "\n" +
        "## Chunk loaders\n" +
        "- Spot Loader and World Anchor are BANNED.\n" +
        "- The vanilla Chunk Loader is limited to 2 placed per player (3 for donators).\n" +
        "- For free, reliable chunk-loading use your claim instead: press J and toggle\n" +
        "  chunk-load on a claimed chunk (or /chunkload).\n" +
        "\n" +
        "## Duplication exploits (banned)\n" +
        "- Fabricator\n" +
        "- Letter\n" +
        "- Miner's Backpack\n" +
        "- Block Breaker\n" +
        "- Item Collector\n" +
        "\n" +
        "## Grief tools (banned)\n" +
        "- Miner\n" +
        "- Arcane Bore\n" +
        "- Anti-Builder\n" +
        "- Safe Net Launcher\n" +
        "- Shoulder-Mounted Piston\n" +
        "\n" +
        "## PvP abuse (banned)\n" +
        "- R.E.P.\n" +
        "- Hunter Handgun\n" +
        "\n" +
        "If you are unsure whether something is allowed, ask a staff member first.\n";

    private static final String DEFAULT_TUTORIAL =
        "# First: mine, mine, mine!\n" +
        "Before building anything, go mining and stockpile as many ores as you can:\n" +
        "iron, copper, tin, coal, redstone, gold, diamond, lapis... the more the\n" +
        "better. Everything you make later eats raw materials.\n" +
        "- PICK UP EVERYTHING. In this pack almost every item is useful: ores, dusts,\n" +
        "  mob drops, plants, even \"junk\" blocks. Don't throw things away.\n" +
        "- A deep first mining trip (or a branch mine at y=12) sets you up for hours.\n" +
        "\n" +
        "## The golden rule: use NEI\n" +
        "The recipe browser (NEI) sits to the right of your inventory.\n" +
        "- Type an item name in the search box to find it.\n" +
        "- Hover an item and press R to see how it is CRAFTED.\n" +
        "- Press U to see what the item is USED in.\n" +
        "Most \"how do I make X\" questions are answered by NEI in seconds.\n" +
        "\n" +
        "# Step 1 - Rubber and refined iron\n" +
        "IndustrialCraft (IC2) is the backbone of early progression.\n" +
        "- Find Rubber Trees (dark spots on the trunk). Tap the spots with a\n" +
        "  Treetap, or break the tree, to get Sticky Resin.\n" +
        "- Smelt Sticky Resin into Rubber. You need a lot of it for cables.\n" +
        "- Smelt iron in any furnace to start; you will upgrade smelting soon.\n" +
        "\n" +
        "# Step 2 - Power from lava (Geothermal)\n" +
        "The cheapest early power is lava. Recommended path:\n" +
        "- Craft a basic Generator first. It can burn coal/charcoal to bootstrap.\n" +
        "- Craft a Geothermal Generator. It eats lava and makes EU with almost no\n" +
        "  running cost once you have a lava supply.\n" +
        "- One bucket of lava = a large amount of EU. A few stacks run your early\n" +
        "  machines for a very long time.\n" +
        "\n" +
        "## Endless lava from the Nether\n" +
        "Nether lava is effectively unlimited. Pipe it back home like this:\n" +
        "- In the Nether, place a BuildCraft Pump over a lava lake to pump lava up.\n" +
        "- Feed that lava into an Ender Tank set to a colour code (frequency).\n" +
        "- Place a second Ender Tank on the SAME colour code at your base. It now\n" +
        "  holds the same lava - pull from it into your Geothermal Generators.\n" +
        "- Result: a wireless, cross-dimension lava pipeline that never runs dry.\n" +
        "\n" +
        "# Step 3 - Double your ores (Macerator)\n" +
        "Never smelt raw ore once you have power.\n" +
        "- Build a Macerator and power it from your generator.\n" +
        "- Ore -> 2 crushed dust (Macerator) -> smelt each dust into an ingot.\n" +
        "- Result: 2 ingots per ore. This doubles every mining trip - now you see\n" +
        "  why the big ore stockpile from step one matters.\n" +
        "Later, GregTech's Industrial Grinder and Thermal Expansion's Pulverizer\n" +
        "give even better yields plus bonus byproducts.\n" +
        "\n" +
        "# Step 4 - Complex items have several recipes\n" +
        "Many parts can be made more than one way. Use NEI to compare:\n" +
        "- Plates: a Compressor turns ingots/dust into plates. This is cheaper\n" +
        "  than the hand recipe and is required for advanced machines.\n" +
        "- Cables: the Cutter route from plates saves a lot of rubber over the\n" +
        "  hand recipe.\n" +
        "- Circuits: Electronic Circuits need insulated copper cable, refined\n" +
        "  iron and redstone - batch-craft them, you will need hundreds.\n" +
        "Rule of thumb: when two recipes exist, the MACHINE route (Compressor,\n" +
        "Macerator, Cutter) is almost always cheaper. Check it in NEI (press R).\n" +
        "\n" +
        "# A suggested early progression\n" +
        "1. Mine hard, hoard ores, pick up everything.\n" +
        "2. Wood tools -> stone tools -> a furnace.\n" +
        "3. Rubber + Refined Iron -> basic Generator (coal-powered to start).\n" +
        "4. Macerator + Electric Furnace -> ore doubling.\n" +
        "5. Geothermal + Nether lava via Ender Tanks -> cheap, steady EU.\n" +
        "6. Add a BatBox/MFE buffer so machines never stall.\n" +
        "7. Branch into your favourite mod: Forestry bees, GregTech, BuildCraft\n" +
        "   quarries, Thermal Expansion, or Applied Energistics for storage.\n" +
        "\n" +
        "# Tips\n" +
        "- Keep an energy buffer (BatBox, then MFE) between generator and machines.\n" +
        "- No voltage limit on this server: a mod changes how EU works, so machines\n" +
        "  accept any voltage by default and will NOT explode. You don't need\n" +
        "  transformers - just wire things up.\n" +
        "- Open the Quests book (default Q). Quests pay MONEY you can spend in\n" +
        "  shops or on land claims.\n" +
        "- Claiming land costs money per chunk. Press J to open the claim /\n" +
        "  chunk-load map. A team that stays offline for 7 days loses its\n" +
        "  chunk-loading, so keep your base active!\n";
}
