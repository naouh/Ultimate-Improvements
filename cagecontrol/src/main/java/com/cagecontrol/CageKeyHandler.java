package com.cagecontrol;

import java.util.EnumSet;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Opens the CageControl GUI directly client-side, bypassing the {@code /cagecontrol} chat
 * command (which fails on MCPC+/Cauldron setups where Bukkit's permission layer denies the
 * underlying command node to non-OPs even though the Forge command allows everyone).
 *
 * The GUI itself only needs read-only data from the server, sent over our custom-payload
 * channel. {@link GuiCageControl#initGui} immediately issues a {@code PKT_LIST_REQUEST}, so
 * by the time the user can click anything, the list is populated.
 */
public class CageKeyHandler extends KeyHandler {

    // No colon in the description — 1.4.7's GameSettings.loadOptions splits on ':' so any colon
    // breaks the round-trip and reverts the key on relaunch.
    public static final KeyBinding OPEN_CAGES = new KeyBinding("CageControl Menu", Keyboard.KEY_K);
    private static boolean wasPressed;

    public CageKeyHandler() {
        super(new KeyBinding[] { OPEN_CAGES }, new boolean[] { false });
    }

    @Override
    public String getLabel() { return "CageControlKeys"; }

    @Override
    public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
        if (tickEnd || isRepeat) return;
        if (kb != OPEN_CAGES) return;
        openGui();
    }

    @Override
    public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    /** Belt-and-braces poll, mirroring {@code ClaimKeyHandler.pollFallback}. */
    public static void pollFallback() {
        try {
            int code = OPEN_CAGES.keyCode;
            if (code <= 0) return;
            boolean down = Keyboard.isKeyDown(code);
            if (down && !wasPressed) openGui();
            wasPressed = down;
        } catch (Throwable ignored) {}
    }

    private static void openGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        mc.displayGuiScreen(new GuiCageControl());
    }
}
