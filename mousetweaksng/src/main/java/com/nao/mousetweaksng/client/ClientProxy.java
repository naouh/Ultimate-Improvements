package com.nao.mousetweaksng.client;

import com.nao.mousetweaksng.CommonProxy;

import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * Client proxy: registers the GUI tick handler that drives the tweaks. This class (and everything
 * it references) is only ever loaded on the physical client.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        super.init();
        TickRegistry.registerTickHandler(new GuiTweakHandler(), Side.CLIENT);
    }
}
