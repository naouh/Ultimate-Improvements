package com.nao.mfrzone.client;

import java.io.DataInputStream;

import com.nao.mfrzone.MFRZoneMod;
import com.nao.mfrzone.network.PacketHandler;

public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static void handle(byte type, DataInputStream in) throws Exception {
        if (type == PacketHandler.PKT_SHOW) {
            int x1 = in.readInt(), y1 = in.readInt(), z1 = in.readInt();
            int x2 = in.readInt(), y2 = in.readInt(), z2 = in.readInt();
            MFRZoneMod.proxy.showBox(x1, y1, z1, x2, y2, z2);
        }
    }
}
