package com.nao.discordrpc.client;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;

/**
 * Client tick (~20/s). Roughly once per second it decides whether the player is on a multiplayer
 * server and forwards that to {@link RpcManager}, which debounces and talks to Discord off-thread.
 */
public class RpcTickHandler implements ITickHandler {

	private int counter;

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (++counter < 20) return;
		counter = 0;

		boolean onServer = false;
		try {
			Minecraft mc = Minecraft.getMinecraft();
			onServer = mc != null && mc.thePlayer != null && !mc.isSingleplayer();
		} catch (Throwable t) {
			onServer = false;
		}
		RpcManager.INSTANCE.update(onServer);
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

	@Override
	public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

	@Override
	public String getLabel() { return "DiscordRPC"; }
}
