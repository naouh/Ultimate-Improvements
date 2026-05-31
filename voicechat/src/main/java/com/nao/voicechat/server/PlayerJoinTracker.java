package com.nao.voicechat.server;

import com.nao.voicechat.network.HandshakePacketHandler;

import cpw.mods.fml.common.IPlayerTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Pushes the voicechat handshake to every player as soon as FML signals login, and tears down
 * their session on logout. Dimension changes / respawns don't touch the session — the player's
 * entity ID is what matters and that's preserved.
 */
public class PlayerJoinTracker implements IPlayerTracker {

    @Override
    public void onPlayerLogin(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        HandshakePacketHandler.sendHandshake((EntityPlayerMP) player);
    }

    @Override
    public void onPlayerLogout(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        VoiceServer.removeSession(player.entityId);
    }

    @Override public void onPlayerChangedDimension(EntityPlayer player) {}
    @Override public void onPlayerRespawn(EntityPlayer player) {}
}
