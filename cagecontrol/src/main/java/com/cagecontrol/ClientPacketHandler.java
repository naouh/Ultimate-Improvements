package com.cagecontrol;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * Client-only state cache for the management GUI.
 *
 * The server pushes the cage list on /cagecontrol, and again whenever it
 * mutates state for this player. The GUI reads from {@link #lastList} and
 * decides what to render.
 */
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static volatile List<CageData> lastList = new ArrayList<CageData>();
    public static volatile boolean lastIsAdmin = false;
    public static volatile long lastUpdateMs;

    public static void handle(byte type, DataInputStream in) throws Exception {
        if (type == PacketHandler.PKT_OPEN_GUI) {
            // Defer to next client tick — calling displayGuiScreen from the network
            // thread can race with the render loop.
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.displayGuiScreen(new GuiCageControl());
            }
            return;
        }
        if (type == PacketHandler.PKT_LIST_RESPONSE) {
            boolean isAdmin = in.readBoolean();
            int count = in.readInt();
            List<CageData> list = new ArrayList<CageData>(count);
            for (int i = 0; i < count; i++) {
                CageData d = new CageData();
                d.owner = in.readUTF();
                d.name = in.readUTF();
                d.mobType = in.readUTF();
                d.special = in.readBoolean();
                d.tier = in.readInt();
                d.active = in.readBoolean();
                d.dim = in.readInt();
                d.x = in.readInt();
                d.y = in.readInt();
                d.z = in.readInt();
                in.readBoolean();          // isOwner — re-derived by GUI via username comparison
                int co = in.readInt();
                for (int j = 0; j < co; j++) d.coOwners.add(in.readUTF());
                list.add(d);
            }
            lastList = list;
            lastIsAdmin = isAdmin;
            lastUpdateMs = System.currentTimeMillis();
        }
    }
}
