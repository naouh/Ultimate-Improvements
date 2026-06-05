package com.nao.voicechat.server;

import com.nao.voicechat.network.HandshakePacketHandler;

import cpw.mods.fml.common.IPlayerTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Pushes the voicechat handshake to every player as soon as FML signals login. Nothing to tear
 * down on logout — there are no server-side sessions any more; remote clients reap the talker's
 * playback stream on their own idle timeout.
 */
public class PlayerJoinTracker implements IPlayerTracker {

    @Override
    public void onPlayerLogin(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        HandshakePacketHandler.sendHandshake((EntityPlayerMP) player);
    }

    @Override public void onPlayerLogout(EntityPlayer player) {}
    @Override public void onPlayerChangedDimension(EntityPlayer player) {}
    @Override public void onPlayerRespawn(EntityPlayer player) {}
}
