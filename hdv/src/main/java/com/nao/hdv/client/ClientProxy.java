package com.nao.hdv.client;

import com.nao.hdv.CommonProxy;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
	@Override
	public void init() {
		super.init();
		KeyBindingRegistry.registerKeyBinding(new HdvKeyHandler());
		TickRegistry.registerTickHandler(new HdvKeyTickHandler(), Side.CLIENT);
	}
}
