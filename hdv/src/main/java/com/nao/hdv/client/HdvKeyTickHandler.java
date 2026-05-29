package com.nao.hdv.client;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;

/** Client tick: polls the open key (fallback) and honours a server-requested open (/hdv command). */
public class HdvKeyTickHandler implements ITickHandler {

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		HdvKeyHandler.pollFallback();
		if (ClientPacketHandler.openRequested) {
			ClientPacketHandler.openRequested = false;
			HdvKeyHandler.openGui();
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

	@Override
	public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

	@Override
	public String getLabel() { return "HdvKeyPoll"; }
}
