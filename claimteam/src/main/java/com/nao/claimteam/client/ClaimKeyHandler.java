package com.nao.claimteam.client;

import java.util.EnumSet;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class ClaimKeyHandler extends KeyHandler {

    // No colon in the description — 1.4.7's GameSettings.loadOptions splits saved lines on ':'
    // so any colon in the name breaks the round-trip and the key reverts to default on relaunch.
    public static final KeyBinding OPEN_MAP = new KeyBinding("ClaimTeam Map", Keyboard.KEY_C);
    private static boolean wasPressed = false;

    public ClaimKeyHandler() {
        super(new KeyBinding[] { OPEN_MAP }, new boolean[] { false });
        System.out.println("[ClaimTeam] KeyHandler constructed, default key=" + OPEN_MAP.keyCode);
    }

    @Override
    public String getLabel() { return "ClaimTeamKeys"; }

    @Override
    public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
        // tickEnd fires twice per tick (start+end); only handle the first.
        if (tickEnd) return;
        if (isRepeat) return;
        if (kb != OPEN_MAP) return;
        openMap();
    }

    @Override
    public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    /**
     * Belt-and-braces fallback: every client tick, poll the key. If FML's keyTick somehow
     * doesn't fire for our binding (some 1.4.7 mod conflicts have been reported with custom
     * KeyHandlers), this still works.
     */
    public static void pollFallback() {
        try {
            int code = OPEN_MAP.keyCode;
            if (code <= 0) return;
            boolean down = Keyboard.isKeyDown(code);
            if (down && !wasPressed) openMap();
            wasPressed = down;
        } catch (Throwable t) {
            // Keyboard polling may fail when display isn't ready yet
        }
    }

    private static void openMap() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        System.out.println("[ClaimTeam] opening map GUI (key=" + OPEN_MAP.keyCode + ")");
        mc.displayGuiScreen(new ClaimMapGui());
    }
}
