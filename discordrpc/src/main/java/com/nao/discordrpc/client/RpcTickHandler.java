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
		int playerCount = 0;
		try {
			Minecraft mc = Minecraft.getMinecraft();
			onServer = mc != null && mc.thePlayer != null && !mc.isSingleplayer();
			if (onServer) {
				// playerInfoList is the client's copy of the tab list (one GuiPlayerInfo per online
				// player, incl. ourselves). Read it here, on the game thread, where it is safe to touch.
				playerCount = mc.thePlayer.sendQueue.playerInfoList.size();
			}
		} catch (Throwable t) {
			onServer = false;
			playerCount = 0;
		}
		RpcManager.INSTANCE.update(onServer, playerCount);
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

	@Override
	public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

	@Override
	public String getLabel() { return "DiscordRPC"; }
}
