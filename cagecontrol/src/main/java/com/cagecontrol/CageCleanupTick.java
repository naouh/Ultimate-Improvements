package com.cagecontrol;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * Server-side tick handler: every {@link #INTERVAL_TICKS} ticks scans the registry
 * for each loaded WorldServer and removes entries whose Soul Cage block is gone
 * (broken, mined, replaced, etc.).
 */
public class CageCleanupTick implements ITickHandler {

    private static final int INTERVAL_TICKS = 20; // ~1 s
    private int counter = 0;

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        if (!type.contains(TickType.SERVER)) return;
        if (++counter < INTERVAL_TICKS) return;
        counter = 0;
        runScan();
    }

    public static void runScan() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        WorldServer[] worlds = server.worldServers;
        if (worlds == null) return;

        int cageId = ReflectSS.getCageBlockId();
        if (cageId <= 0) return;

        int removed = 0;
        for (WorldServer w : worlds) {
            if (w == null) continue;
            CageRegistry reg = CageRegistry.get(w);
            List<CageData> stale = new ArrayList<CageData>();
            for (CageData d : reg.snapshot()) {
                if (d.dim != w.provider.dimensionId) continue;
                if (!w.blockExists(d.x, d.y, d.z)) continue;
                if (w.getBlockId(d.x, d.y, d.z) != cageId) {
                    stale.add(d);
                }
            }
            for (CageData d : stale) {
                reg.remove(d);
                removed++;
                System.out.println("[CageControl] Removed stale cage '" + d.name
                        + "' owned by " + d.owner + " @ " + d.x + "," + d.y + "," + d.z);
            }
        }
        if (removed > 0) {
            System.out.println("[CageControl] Cleanup scan removed " + removed + " stale entries");
        }
    }

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.SERVER); }

    @Override
    public String getLabel() { return CageControl.MODID + ":CleanupTick"; }
}
