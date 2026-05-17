package com.quarryplus;

import com.quarryplus.render.RenderMarker;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Client-side proxy. Tile entity renderers are bound here.
 *
 * <p>The quarry intentionally has no TESR — a per-frame renderer for every active quarry
 * eats GPU cycles, and the head position is communicated to the player through the blocks
 * disappearing from the world. Mark this a perf-conscious choice: BC's quarry with its
 * animated arm runs ~1ms/frame even idle.
 */
public class ProxyClient extends ProxyCommon {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileMarker.class, new RenderMarker());
    }
}
