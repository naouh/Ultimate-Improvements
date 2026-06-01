package com.favouredcraft.serverlist;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Swaps the vanilla {@link GuiMultiplayer} for our {@link GuiFavouredServerList} the instant it
 * is displayed. We match the exact class so we never re-trigger on our own screen (which extends
 * {@link GuiScreen}, not GuiMultiplayer) or on any other mod's subclass.
 */
public class GuiSwapTickHandler implements ITickHandler {

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		Minecraft mc = Minecraft.getMinecraft();
		GuiScreen screen = mc.currentScreen;
		if (screen != null && screen.getClass() == GuiMultiplayer.class) {
			// In 1.4.7 the multiplayer screen is only ever opened from the main menu.
			mc.displayGuiScreen(new GuiFavouredServerList(new GuiMainMenu()));
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "FavouredServerList GUI swap";
	}
}
