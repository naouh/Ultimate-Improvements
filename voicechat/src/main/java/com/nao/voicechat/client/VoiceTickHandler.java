package com.nao.voicechat.client;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;

/**
 * Drives the PTT fallback poll and the per-tick housekeeping for {@link AudioPlayback}. Also
 * detects "the player just disconnected" and tears down the UDP client so we don't keep
 * spamming the dead server with keepalives.
 */
public class VoiceTickHandler implements ITickHandler {

    private boolean hadPlayer;

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean hasPlayer = mc != null && mc.thePlayer != null;

        if (hasPlayer) {
            VoiceKeyHandler.pollFallback();
            AudioPlayback.tick();
            hadPlayer = true;
        } else if (hadPlayer) {
            // Just left the world / disconnected.
            VoiceClient.disconnect();
            hadPlayer = false;
        }
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override
    public String getLabel() { return "VoiceChatTick"; }
}
