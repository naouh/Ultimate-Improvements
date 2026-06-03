package com.nao.claimteam.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import com.nao.claimteam.network.PacketHandler;

import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Decodes server -> client packets and stashes the latest grid + team info into static fields
 * for {@link ClaimMapGui} to read on the next frame. Called by {@link PacketHandler} when a
 * packet arrives on the client side (no separate IPacketHandler registration).
 */
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static volatile int gridCenterCx;
    public static volatile int gridCenterCz;
    public static volatile int gridRadius;
    public static volatile byte[] gridCells;
    public static volatile String[] gridTeams;
    public static volatile int usedClaims, maxClaims;
    public static volatile int usedChunkloads, maxChunkloads;
    public static volatile String myTeam = "";
    public static volatile String myTeamOwner = "";
    public static volatile String[] myTeamMembers = new String[0];
    public static volatile String[] myTeamAllies = new String[0];
    public static volatile long lastUpdateMs;

    public static void handle(Packet250CustomPayload packet) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte type = in.readByte();
            if (type == PacketHandler.PKT_GRID_RESPONSE) {
                int cx = in.readInt();
                int cz = in.readInt();
                int r  = in.readByte();
                int size = (r * 2 + 1) * (r * 2 + 1);
                byte[] cells = new byte[size];
                String[] teams = new String[size];
                for (int i = 0; i < size; i++) {
                    byte cell = in.readByte();
                    cells[i] = cell;
                    if (cell != PacketHandler.CELL_UNCLAIMED) teams[i] = in.readUTF();
                }
                int used = in.readInt();
                int maxC = in.readInt();
                int usedCL = in.readInt();
                int maxCL = in.readInt();
                String mt = in.readUTF();
                String mtOwner = in.readUTF();
                int memCount = in.readInt();
                String[] members = new String[memCount];
                for (int i = 0; i < memCount; i++) members[i] = in.readUTF();
                int allyCount = in.readInt();
                String[] allies = new String[allyCount];
                for (int i = 0; i < allyCount; i++) allies[i] = in.readUTF();
                gridCenterCx = cx;
                gridCenterCz = cz;
                gridRadius = r;
                gridCells = cells;
                gridTeams = teams;
                usedClaims = used;
                maxClaims = maxC;
                usedChunkloads = usedCL;
                maxChunkloads = maxCL;
                myTeam = mt;
                myTeamOwner = mtOwner;
                myTeamMembers = members;
                myTeamAllies = allies;
                lastUpdateMs = System.currentTimeMillis();
            } else if (type == PacketHandler.PKT_TEAM_INFO) {
                String name = in.readUTF();
                String ownerStr = in.readUTF();
                int memCount = in.readInt();
                int used = in.readInt();
                int maxC = in.readInt();
                int usedCL = in.readInt();
                int maxCL = in.readInt();
                String[] members = new String[memCount];
                for (int i = 0; i < memCount; i++) members[i] = in.readUTF();
                int allyCount = in.readInt();
                String[] allies = new String[allyCount];
                for (int i = 0; i < allyCount; i++) allies[i] = in.readUTF();
                myTeam = name;
                myTeamOwner = ownerStr;
                myTeamMembers = members;
                myTeamAllies = allies;
                usedClaims = used;
                maxClaims = maxC;
                usedChunkloads = usedCL;
                maxChunkloads = maxCL;
                lastUpdateMs = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
