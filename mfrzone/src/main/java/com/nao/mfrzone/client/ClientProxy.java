package com.nao.mfrzone.client;

import java.util.EnumSet;

import com.nao.mfrzone.CommonProxy;
import com.nao.mfrzone.Config;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        super.init();
        // Drives the preview's auto-expire countdown.
        TickRegistry.registerTickHandler(new ClientTick(), Side.CLIENT);
    }

    @Override
    public void showBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        ClientPreview.show(new int[] { x1, y1, z1, x2, y2, z2 }, Config.showSeconds * 20);
    }

    /** Ticks down the preview so it clears itself after the configured duration. */
    private static class ClientTick implements ITickHandler {
        @Override public void tickStart(EnumSet<TickType> type, Object... data) {}
        @Override public void tickEnd(EnumSet<TickType> type, Object... data) {
            if (type.contains(TickType.CLIENT)) ClientPreview.tick();
        }
        @Override public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }
        @Override public String getLabel() { return "MFRZone:Preview"; }
    }
}
