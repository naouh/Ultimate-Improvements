package com.nao.questcycle.client;

import com.nao.questcycle.CommonProxy;
import com.nao.questcycle.client.hud.CustomTabOverlay;
import com.nao.questcycle.client.hud.ToastOverlay;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
	@Override
	public void preInit() {
		super.preInit();
	}

	@Override
	public void init() {
		super.init();
		KeyBindingRegistry.registerKeyBinding(new QuestKeyHandler());
		TickRegistry.registerTickHandler(new ToastOverlay(), Side.CLIENT);
		TickRegistry.registerTickHandler(new CustomTabOverlay(), Side.CLIENT);
	}

	@Override
	public void postInit() {
		super.postInit();
	}
}
