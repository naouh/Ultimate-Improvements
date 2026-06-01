package com.nao.serverguide.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.nao.serverguide.ServerGuideMod;
import com.nao.serverguide.config.GuidePage;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Packet layer. Two server -> client messages:
 *   {@link #PKT_OPEN}    - tell the client to open the guide GUI (used by {@code /guide}).
 *   {@link #PKT_CONTENT} - push the authoritative page content from the server's config files.
 * There are no client -> server messages.
 */
public final class GuidePacketHandler implements IPacketHandler {

    public static final byte PKT_OPEN    = 1; // S->C
    public static final byte PKT_CONTENT = 2; // S->C

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        if (packet == null || packet.data == null || packet.data.length == 0) return;
        // This channel is client-bound only; ignore anything that arrives server-side.
        if (player instanceof EntityPlayerMP) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte id = in.readByte();
            if (id == PKT_OPEN) {
                // Packet250 payloads are processed on the client main thread, so opening a GUI here is safe.
                ServerGuideMod.proxy.openGuide();
            } else if (id == PKT_CONTENT) {
                int n = in.readInt();
                List<GuidePage> pages = new ArrayList<GuidePage>();
                for (int i = 0; i < n; i++) {
                    String title = in.readUTF();
                    String file  = in.readUTF();
                    String text  = in.readUTF();
                    pages.add(new GuidePage(title, file, text));
                }
                com.nao.serverguide.client.ClientGuideState.set(pages);
            }
        } catch (Throwable t) {
            System.err.println("[ServerGuide] packet decode failed: " + t.getMessage());
        }
    }

    /** Build the "open the guide" packet to send to a player. */
    public static Packet250CustomPayload openPacket() {
        return raw(new byte[] { PKT_OPEN });
    }

    /** Build the "here is the guide content" packet from the server's loaded pages. */
    public static Packet250CustomPayload contentPacket(List<GuidePage> pages) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(PKT_CONTENT);
            out.writeInt(pages.size());
            for (int i = 0; i < pages.size(); i++) {
                GuidePage p = pages.get(i);
                out.writeUTF(p.title);
                out.writeUTF(p.fileName);
                out.writeUTF(p.text);
            }
            out.flush();
            return raw(baos.toByteArray());
        } catch (Throwable t) {
            System.err.println("[ServerGuide] contentPacket build failed: " + t.getMessage());
            return null;
        }
    }

    private static Packet250CustomPayload raw(byte[] data) {
        Packet250CustomPayload p = new Packet250CustomPayload();
        p.channel = ServerGuideMod.CHANNEL;
        p.data = data;
        p.length = data.length;
        return p;
    }
}
