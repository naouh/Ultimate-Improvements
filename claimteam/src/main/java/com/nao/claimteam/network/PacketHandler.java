package com.nao.claimteam.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.nao.claimteam.ClaimTeamMod;
import com.nao.claimteam.Config;
import com.nao.claimteam.action.ClaimActions;
import com.nao.claimteam.action.TeamActions;
import com.nao.claimteam.client.ClientPacketHandler;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;
import com.nao.claimteam.perm.PermissionResolver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.world.World;

/**
 * Single network handler — routes by side. Server side decodes requests/actions and replies.
 * Client side delegates to {@link ClientPacketHandler} which stashes payload into static fields
 * for the GUI.
 */
public class PacketHandler implements IPacketHandler {

    public static final byte PKT_GRID_REQUEST = 1;   // C->S
    public static final byte PKT_GRID_RESPONSE = 2;  // S->C
    public static final byte PKT_ACTION       = 3;   // C->S
    public static final byte PKT_TEAM_INFO    = 4;   // S->C
    public static final byte PKT_TEAM_CMD     = 5;   // C->S  (team management from the map GUI)

    public static final byte ACTION_CLAIM      = 1;
    public static final byte ACTION_UNCLAIM    = 2;
    public static final byte ACTION_TOGGLE_CL  = 3;

    // Sub-actions carried by PKT_TEAM_CMD. These mirror the /team subcommands but run through
    // the mod's own packet channel, so players don't need permission to run the Forge command.
    public static final byte TEAM_CREATE   = 1;
    public static final byte TEAM_INVITE   = 2;
    public static final byte TEAM_KICK     = 3;
    public static final byte TEAM_PROMOTE  = 4;
    public static final byte TEAM_LEAVE    = 5;
    public static final byte TEAM_DISBAND  = 6;
    public static final byte TEAM_ALLY     = 7;
    public static final byte TEAM_UNALLY   = 8;

    public static final byte CELL_UNCLAIMED       = 0;
    public static final byte CELL_OWN             = 1;
    public static final byte CELL_ALLY            = 2;
    public static final byte CELL_FOREIGN         = 3;
    public static final byte CELL_OWN_CHUNKLOAD   = 4;
    public static final byte CELL_FOREIGN_CL      = 5;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!ClaimTeamMod.CHANNEL.equals(packet.channel)) return;
        // EntityPlayerMP only exists on the server -> reliable side detection.
        if (p instanceof EntityPlayerMP) {
            handleServer((EntityPlayerMP) p, packet);
        } else if (FMLCommonHandler.instance().getSide().isClient()) {
            ClientPacketHandler.handle(packet);
        }
    }

    private void handleServer(EntityPlayerMP epm, Packet250CustomPayload packet) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte type = in.readByte();
            if (type == PKT_GRID_REQUEST) {
                int cx = in.readInt();
                int cz = in.readInt();
                int radius = in.readByte();
                if (radius < 1) radius = 1;
                if (radius > Config.gridRadius) radius = Config.gridRadius;
                sendGrid(epm, cx, cz, radius);
            } else if (type == PKT_ACTION) {
                byte action = in.readByte();
                int cx = in.readInt();
                int cz = in.readInt();
                handleAction(epm, action, cx, cz);
            } else if (type == PKT_TEAM_CMD) {
                byte sub = in.readByte();
                String arg = in.readUTF();
                handleTeamCmd(epm, sub, arg);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void handleTeamCmd(EntityPlayerMP epm, byte sub, String arg) {
        if (arg != null) arg = arg.trim();
        switch (sub) {
            case TEAM_CREATE:  TeamActions.create(epm, arg);  break;
            case TEAM_INVITE:  TeamActions.invite(epm, arg);  break;
            case TEAM_KICK:    TeamActions.kick(epm, arg);    break;
            case TEAM_PROMOTE: TeamActions.promote(epm, arg); break;
            case TEAM_LEAVE:   TeamActions.leave(epm);        break;
            case TEAM_DISBAND: TeamActions.disband(epm);      break;
            case TEAM_ALLY:    TeamActions.ally(epm, arg);    break;
            case TEAM_UNALLY:  TeamActions.unally(epm, arg);  break;
            default: break;
        }
    }

    private static void handleAction(EntityPlayerMP epm, byte action, int cx, int cz) {
        World w = epm.worldObj;
        if (w == null) return;
        if (action == ACTION_CLAIM) {
            String msg = ClaimActions.claim(epm, w, cx, cz);
            if (msg != null) epm.sendChatToPlayer("§e[ClaimTeam] " + msg);
        } else if (action == ACTION_UNCLAIM) {
            String msg = ClaimActions.unclaim(epm, w, cx, cz);
            if (msg != null) epm.sendChatToPlayer("§e[ClaimTeam] " + msg);
        } else if (action == ACTION_TOGGLE_CL) {
            String msg = ClaimActions.toggleChunkload(epm, w, cx, cz);
            if (msg != null) epm.sendChatToPlayer("§e[ClaimTeam] " + msg);
        }
        sendGrid(epm, ((int) Math.floor(epm.posX)) >> 4, ((int) Math.floor(epm.posZ)) >> 4, Config.gridRadius);
        sendTeamInfo(epm);
    }

    public static void sendGrid(EntityPlayerMP epm, int centerCx, int centerCz, int radius) {
        World w = epm.worldObj;
        if (w == null) return;
        int dim = w.provider.dimensionId;
        ClaimRegistry creg = ClaimRegistry.get(w);
        TeamRegistry treg = TeamRegistry.get(w);
        ClaimTeam myTeam = treg.getByPlayer(epm.username);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_GRID_RESPONSE);
            dos.writeInt(centerCx);
            dos.writeInt(centerCz);
            dos.writeByte(radius);
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int cx = centerCx + dx;
                    int cz = centerCz + dz;
                    Claim c = creg.find(dim, cx, cz);
                    if (c == null) {
                        dos.writeByte(CELL_UNCLAIMED);
                    } else {
                        ClaimTeam claimTeam = treg.getByName(c.teamName);
                        boolean mine = myTeam != null && myTeam.name != null && c.teamName != null
                                       && myTeam.name.equalsIgnoreCase(c.teamName);
                        // Allies are per-player: this chunk's team must have allied THIS viewer
                        // by name (not their team), matching the per-player build permission.
                        boolean ally = !mine && claimTeam != null
                                       && claimTeam.isAlly(epm.username);
                        byte cell;
                        if (mine)        cell = c.chunkload ? CELL_OWN_CHUNKLOAD : CELL_OWN;
                        else if (ally)   cell = CELL_ALLY;
                        else             cell = c.chunkload ? CELL_FOREIGN_CL : CELL_FOREIGN;
                        dos.writeByte(cell);
                        dos.writeUTF(c.teamName == null ? "" : c.teamName);
                    }
                }
            }
            int used = (myTeam == null) ? 0 : creg.countByTeam(myTeam.name);
            int usedCL = (myTeam == null) ? 0 : creg.countChunkloadByTeam(myTeam.name);
            Config.GroupLimits limits = PermissionResolver.limitsFor(epm);
            dos.writeInt(used);
            dos.writeInt(limits.maxClaims);
            dos.writeInt(usedCL);
            dos.writeInt(limits.maxChunkloads);
            dos.writeUTF(myTeam == null ? "" : myTeam.name);
            dos.writeUTF(myTeam == null || myTeam.owner == null ? "" : myTeam.owner);
            // Member list for the team UI (excludes owner; owner is sent separately above).
            if (myTeam == null) {
                dos.writeInt(0);
            } else {
                dos.writeInt(myTeam.members.size());
                for (String m : myTeam.members) dos.writeUTF(m);
            }
            // Ally list for the team UI (owner-managed; drives the "Remove ally" picker).
            if (myTeam == null) {
                dos.writeInt(0);
            } else {
                dos.writeInt(myTeam.allies.size());
                for (String a : myTeam.allies) dos.writeUTF(a);
            }
        } catch (IOException e) {
            return;
        }
        sendTo(epm, bos.toByteArray());
    }

    public static void sendTeamInfo(EntityPlayerMP epm) {
        World w = epm.worldObj;
        if (w == null) return;
        ClaimTeam t = TeamRegistry.get(w).getByPlayer(epm.username);
        ClaimRegistry creg = ClaimRegistry.get(w);
        Config.GroupLimits limits = PermissionResolver.limitsFor(epm);
        int used = (t == null) ? 0 : creg.countByTeam(t.name);
        int usedCL = (t == null) ? 0 : creg.countChunkloadByTeam(t.name);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_TEAM_INFO);
            dos.writeUTF(t == null ? "" : t.name);
            dos.writeUTF(t == null || t.owner == null ? "" : t.owner);
            dos.writeInt(t == null ? 0 : t.members.size());
            dos.writeInt(used);
            dos.writeInt(limits.maxClaims);
            dos.writeInt(usedCL);
            dos.writeInt(limits.maxChunkloads);
            if (t == null) {
                // No member / ally lists to ship.
                dos.writeInt(0);
            } else {
                for (String m : t.members) dos.writeUTF(m);
                dos.writeInt(t.allies.size());
                for (String a : t.allies) dos.writeUTF(a);
            }
        } catch (IOException e) {
            return;
        }
        sendTo(epm, bos.toByteArray());
    }

    private static void sendTo(EntityPlayerMP epm, byte[] data) {
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = ClaimTeamMod.CHANNEL;
        pkt.data = data;
        pkt.length = data.length;
        PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);
    }

    /** Convenience for client side: build and ship a request packet. */
    public static byte[] buildGridRequest(int cx, int cz, int radius) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_GRID_REQUEST);
            dos.writeInt(cx);
            dos.writeInt(cz);
            dos.writeByte(radius);
        } catch (IOException ignored) {}
        return bos.toByteArray();
    }

    public static byte[] buildAction(byte action, int cx, int cz) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_ACTION);
            dos.writeByte(action);
            dos.writeInt(cx);
            dos.writeInt(cz);
        } catch (IOException ignored) {}
        return bos.toByteArray();
    }

    /** Convenience for client side: build a team-management command packet. */
    public static byte[] buildTeamCmd(byte sub, String arg) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_TEAM_CMD);
            dos.writeByte(sub);
            dos.writeUTF(arg == null ? "" : arg);
        } catch (IOException ignored) {}
        return bos.toByteArray();
    }
}
