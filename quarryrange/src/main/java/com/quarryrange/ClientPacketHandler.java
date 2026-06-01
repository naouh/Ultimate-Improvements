package com.quarryrange;

import java.io.DataInputStream;

public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static void handle(byte type, DataInputStream in) throws Exception {
        if (type == PacketHandler.PKT_OPEN) {
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            int meta = in.readInt();
            int size = in.readInt();
            int anchor = in.readByte();
            int min = in.readInt(), max = in.readInt();
            QuarryRange.proxy.openEditor(x, y, z, meta, size, anchor, min, max);
        }
    }
}
