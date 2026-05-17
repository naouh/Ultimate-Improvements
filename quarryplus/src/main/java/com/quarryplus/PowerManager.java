package com.quarryplus;

import com.quarryplus.tile.APowerTile;

import net.minecraftforge.common.Configuration;

/**
 * Per-machine MJ budget calculator. Holds the tuning knobs the 1.7.10 original called
 * "BasePower / EfficiencyCoefficient / UnbreakingCoefficient / FortuneCoefficient /
 * SilktouchCoefficient" plus the receive/store caps, and exposes static helpers the tiles
 * call from their mining loop.
 *
 * <p>The 1.7.10 PowerManager covers Quarry, MiningWell, Laser, Refinery and Pump — we keep
 * only the Quarry and the per-quarry Frame-building path. Everything else stays out of the
 * config to keep {@code QuarryPlus.cfg} tight.
 *
 * <p>Cost formulae (E = Efficiency level, U = Unbreaking level, SF = Fortune level, or -1
 * meaning Silk Touch):
 * <pre>
 *   block break:  BP * hardness * (SF&lt;0 ? CS : CF^SF) / (U*CU + 1)
 *   make frame:   BP                                   / (U*CU + 1)
 *   move head:    min(2 + stored/500, (dist-0.1)*BP    / (U*CU + 1))
 *   buffer/recv:  XR or MS  *  CE^E                    / (U*CU + 1)
 * </pre>
 */
public final class PowerManager {

    private PowerManager() {}

    // ----- Quarry: BreakBlock -----
    private static double B_BP, B_CE, B_CU, B_CF, B_CS, B_XR, B_MS;
    // ----- Quarry: MoveHead -----
    private static double H_BP, H_CU;
    // ----- Quarry: MakeFrame -----
    private static double F_BP, F_CE, F_CU, F_XR, F_MS;

    public static void loadConfiguration(Configuration cfg) {
        String cat = "PowerSetting.Quarry";

        B_BP = cfg.get(cat + ".BreakBlock", "BasePower",              40.0).getDouble(40.0);
        B_CE = cfg.get(cat + ".BreakBlock", "EfficiencyCoefficient",  1.3).getDouble(1.3);
        B_CU = cfg.get(cat + ".BreakBlock", "UnbreakingCoefficient",  1.0).getDouble(1.0);
        B_CF = cfg.get(cat + ".BreakBlock", "FortuneCoefficient",     1.3).getDouble(1.3);
        B_CS = cfg.get(cat + ".BreakBlock", "SilktouchCoefficient",   2.0).getDouble(2.0);
        B_XR = cfg.get(cat + ".BreakBlock", "BaseMaxReceive",         300.0).getDouble(300.0);
        B_MS = cfg.get(cat + ".BreakBlock", "BaseMaxStored",          15000.0).getDouble(15000.0);

        H_BP = cfg.get(cat + ".MoveHead",   "BasePower",              200.0).getDouble(200.0);
        H_CU = cfg.get(cat + ".MoveHead",   "UnbreakingCoefficient",  1.0).getDouble(1.0);

        F_BP = cfg.get(cat + ".MakeFrame",  "BasePower",              25.0).getDouble(25.0);
        F_CE = cfg.get(cat + ".MakeFrame",  "EfficiencyCoefficient",  1.3).getDouble(1.3);
        F_CU = cfg.get(cat + ".MakeFrame",  "UnbreakingCoefficient",  1.0).getDouble(1.0);
        F_XR = cfg.get(cat + ".MakeFrame",  "BaseMaxReceive",         100.0).getDouble(100.0);
        F_MS = cfg.get(cat + ".MakeFrame",  "BaseMaxStored",          15000.0).getDouble(15000.0);
    }

    /** Configure tile budget for "break block" mode (the main mining loop). */
    public static void configureB(APowerTile tile, byte efficiency, byte unbreaking) {
        configureCommon(tile, B_CE, efficiency, unbreaking, B_CU, B_XR, B_MS);
    }

    /** Configure tile budget for "make frame" mode (initial frame placement). */
    public static void configureF(APowerTile tile, byte efficiency, byte unbreaking) {
        configureCommon(tile, F_CE, efficiency, unbreaking, F_CU, F_XR, F_MS);
    }

    private static void configureCommon(APowerTile t, double CE, byte E, byte U,
                                        double CU, double XR, double MS) {
        double scale = Math.pow(CE, E) / ((double) U * CU + 1.0);
        t.configure((int) (XR * scale), (int) (MS * scale));
    }

    /**
     * Attempt to spend break-block energy. SF = -1 means silk touch; otherwise it's the
     * Fortune level. Returns true if the tile had enough buffer to break the block.
     *
     * <p>Calls BC's PowerProvider.useEnergy directly with real=true and checks the return,
     * matching the BC quarry's own pattern. The previous wrapper-based pre-check was
     * returning a stale "charge" value that didn't reflect what BC's PowerProvider
     * actually drained.
     */
    public static boolean useEnergyB(APowerTile t, float hardness, byte fortune, byte unbreaking) {
        double mult = fortune < 0 ? B_CS : Math.pow(B_CF, fortune);
        float cost = (float) (B_BP * hardness * mult / ((double) unbreaking * B_CU + 1.0));
        if (cost <= 0f) return true; // free break (e.g. hardness=0 paths)
        return t.getPowerProvider().useEnergy(cost, cost, true) >= cost;
    }

    /** Attempt to spend frame-build energy for one frame block. */
    public static boolean useEnergyF(APowerTile t, byte unbreaking) {
        float cost = (float) (F_BP / ((double) unbreaking * F_CU + 1.0));
        if (cost <= 0f) return true;
        return t.getPowerProvider().useEnergy(cost, cost, true) >= cost;
    }

    /**
     * Spend head-move energy. Returns the distance the quarry can travel this tick (caller
     * clamps to the remaining distance to target). The dynamic cost ramps with how much
     * energy is currently buffered, so a freshly-charged quarry moves faster than a starved
     * one.
     */
    public static double useEnergyH(APowerTile t, double dist, byte unbreaking) {
        float budget = (float) Math.min(2.0 + t.getStoredEnergy() / 500.0,
                                       (dist - 0.1) * H_BP / ((double) unbreaking * H_CU + 1.0));
        if (budget <= 0f) return 0.1;
        float charged = t.getPowerProvider().useEnergy(0.0f, budget, true);
        return charged * ((double) unbreaking * H_CU + 1.0) / H_BP + 0.1;
    }
}
