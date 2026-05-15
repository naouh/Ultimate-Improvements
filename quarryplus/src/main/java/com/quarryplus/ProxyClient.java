package com.quarryplus;

import com.quarryplus.render.RenderMarker;
import com.quarryplus.render.RenderQuarry;
import com.quarryplus.tile.TileMarker;
import com.quarryplus.tile.TileQuarry;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Client-side proxy. Tile entity renderers are bound here.
 */
public class ProxyClient extends ProxyCommon {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileMarker.class, new RenderMarker());
        ClientRegistry.bindTileEntitySpecialRenderer(TileQuarry.class, new RenderQuarry());
    }
}
