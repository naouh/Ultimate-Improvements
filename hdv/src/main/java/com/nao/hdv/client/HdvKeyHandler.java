package com.nao.hdv.client;

import java.util.EnumSet;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** Opens the auction house GUI. Default key: H. */
public class HdvKeyHandler extends KeyHandler {

	// No colon in the label - 1.4.7 GameSettings splits saved key lines on ':'.
	public static final KeyBinding OPEN = new KeyBinding("Hotel de Vente", Keyboard.KEY_H);
	private static boolean wasPressed = false;

	public HdvKeyHandler() {
		super(new KeyBinding[] { OPEN }, new boolean[] { false });
	}

	@Override
	public String getLabel() { return "HdvKeys"; }

	@Override
	public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
		if (tickEnd || isRepeat) return;
		if (kb != OPEN) return;
		openGui();
	}

	@Override
	public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {}

	@Override
	public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

	/** Fallback poll, in case FML's keyDown is skipped in a heavy pack. */
	public static void pollFallback() {
		try {
			int code = OPEN.keyCode;
			if (code <= 0) return;
			boolean down = Keyboard.isKeyDown(code);
			if (down && !wasPressed) openGui();
			wasPressed = down;
		} catch (Throwable ignored) {}
	}

	public static void openGui() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) return;
		if (mc.currentScreen != null) return;
		mc.displayGuiScreen(new HdvGui());
	}
}
