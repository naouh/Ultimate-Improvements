package com.nao.discordrpc.client;

import com.nao.discordrpc.CommonProxy;
import com.nao.discordrpc.Config;

import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
	@Override
	public void init() {
		if (!Config.enabled()) {
			System.out.println("[DiscordRPC] applicationId not set in config/DiscordRPC.cfg - Rich Presence disabled.");
			return;
		}
		TickRegistry.registerTickHandler(new RpcTickHandler(), Side.CLIENT);
	}
}
