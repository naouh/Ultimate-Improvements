package com.nao.claimteam;

import com.nao.claimteam.chunkload.IdleChunkloadHandler;

import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {
    public void preInit() {}

    public void init() {
        // Server-side sweep that auto-disables idle teams' chunk-loads. Registered for Side.SERVER so it
        // runs on a dedicated server and on the integrated server of a single-player/LAN host.
        TickRegistry.registerTickHandler(new IdleChunkloadHandler(), Side.SERVER);
    }

    public void postInit() {}
}
