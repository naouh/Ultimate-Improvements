package com.nao.voicechat.client;

import java.util.EnumSet;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.settings.KeyBinding;

public class VoiceKeyHandler extends KeyHandler {

    public static final KeyBinding PUSH_TO_TALK = new KeyBinding("VoiceChat PTT", Keyboard.KEY_V);
    public static final KeyBinding OPEN_MENU    = new KeyBinding("VoiceChat Menu", Keyboard.KEY_B);

    private static boolean menuWasPressed;

    public VoiceKeyHandler() {
        super(new KeyBinding[] { PUSH_TO_TALK, OPEN_MENU }, new boolean[] { true, false });
    }

    @Override
    public String getLabel() { return "VoiceChatKeys"; }

    @Override
    public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
        if (tickEnd) return;
        if (kb == PUSH_TO_TALK) {
            MicCapture.setTransmitting(true);
        } else if (kb == OPEN_MENU && !isRepeat) {
            openMenu();
        }
    }

    @Override
    public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {
        if (kb == PUSH_TO_TALK) MicCapture.setTransmitting(false);
    }

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    /**
     * Belt-and-braces poll: in some heavily-modded 1.4.7 packs FML's KeyBindingRegistry skips
     * fast-toggling bindings, so we also read the key state every tick.
     */
    public static void pollFallback() {
        try {
            int code = PUSH_TO_TALK.keyCode;
            if (code > 0) MicCapture.setTransmitting(Keyboard.isKeyDown(code));

            int mcode = OPEN_MENU.keyCode;
            if (mcode > 0) {
                boolean pressed = Keyboard.isKeyDown(mcode);
                if (pressed && !menuWasPressed) openMenu();
                menuWasPressed = pressed;
            }
        } catch (Throwable ignored) {}
    }

    private static void openMenu() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        mc.displayGuiScreen(new VoiceMuteGui());
    }
}
