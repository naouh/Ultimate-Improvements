package com.nao.voicechat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;

/**
 * Swapped in as {@code Minecraft.ingameGUI} at world-load time so we can paint our HUD on top of
 * the vanilla overlay without an ASM transformer. We forward every constructor field by going
 * through the standard {@link GuiIngame#GuiIngame(Minecraft)} ctor.
 */
public class VoiceGuiIngame extends GuiIngame {

    public VoiceGuiIngame(Minecraft mc) { super(mc); }

    @Override
    public void renderGameOverlay(float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        super.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
        VoiceHud.renderOverlay(partialTicks);
    }
}
