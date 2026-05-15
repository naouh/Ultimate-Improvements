package com.quarryplus;

import com.quarryplus.render.RenderMarker;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Client-side proxy. Tile entity renderers (frame, marker, quarry drill animation) are bound
 * here as the corresponding tile entities come online.
 */
public class ProxyClient extends ProxyCommon {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileMarker.class, new RenderMarker());
        // Phase 3 also: bind RenderFrame  (drawn over BlockFrame instances)
        // Phase 4:      bind RenderQuarry (the drill animation)
    }
}
