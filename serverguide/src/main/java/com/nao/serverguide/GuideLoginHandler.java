package com.nao.serverguide;

import com.nao.serverguide.config.GuideContent;
import com.nao.serverguide.network.GuidePacketHandler;

import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet250CustomPayload;

/** On login, push the server's authoritative guide content so the client GUI shows it. */
public final class GuideLoginHandler implements IPlayerTracker {

    @Override
    public void onPlayerLogin(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        Packet250CustomPayload pkt = GuidePacketHandler.contentPacket(GuideContent.load());
        if (pkt != null) {
            PacketDispatcher.sendPacketToPlayer(pkt, (Player) player);
        }
    }

    @Override public void onPlayerLogout(EntityPlayer player) {}
    @Override public void onPlayerChangedDimension(EntityPlayer player) {}
    @Override public void onPlayerRespawn(EntityPlayer player) {}
}
