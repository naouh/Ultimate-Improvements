package com.favouredcraft.serverlist;

import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
	@Override
	public void init() {
		// There is no GuiOpenEvent in Forge 1.4.7, so we watch the render tick and swap
		// the vanilla GuiMultiplayer for our custom screen as soon as it opens.
		TickRegistry.registerTickHandler(new GuiSwapTickHandler(), Side.CLIENT);
	}
}
