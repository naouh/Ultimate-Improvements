package com.nao.claimteam.action;

import java.util.ArrayList;
import java.util.List;

import com.nao.claimteam.Config;
import com.nao.claimteam.chunkload.ChunkLoadManager;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;
import com.nao.claimteam.network.PacketHandler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Team-management operations shared by {@link com.nao.claimteam.command.TeamCommand} and the
 * map GUI's network path ({@link PacketHandler}).
 *
 * <p>Routing the GUI through these methods — instead of having the client dispatch a
 * {@code /team ...} chat command — means players don't need permission to run the command.
 * On Bukkit/MCPC+ servers, Forge {@code registerServerCommand} commands are wrapped behind a
 * permission node that regular (non-op) players lack, so the chat-command path silently fails
 * for them. The mod authorises by team ownership directly here, on its own packet channel.
 */
public final class TeamActions {

    private TeamActions() {}

    private static final String GREEN = "§a";
    private static final String RED   = "§c";
    private static final String YEL   = "§e";
    private static final String AQUA  = "§b";
    private static final String R     = "§r";

    public static void reply(EntityPlayerMP epm, String msg) {
        epm.sendChatToPlayer("§e[ClaimTeam]§r " + msg);
    }

    public static void create(EntityPlayerMP epm, String name) {
        TeamRegistry tr = TeamRegistry.get(epm.worldObj);
        if (name == null || name.length() == 0) { reply(epm, "Usage: /team create <name>"); return; }
        if (!name.matches("[A-Za-z0-9_\\-]{3,16}")) {
            reply(epm, "Invalid name (A-Z 0-9 _ -, 3-16 chars)."); return;
        }
        if (tr.getByPlayer(epm.username) != null) { reply(epm, "You are already in a team."); return; }
        if (tr.nameTaken(name)) { reply(epm, "Name taken."); return; }
        ClaimTeam t = tr.create(name, epm.username);
        if (t == null) { reply(epm, "Could not create team."); return; }
        reply(epm, GREEN + "Team '" + name + "' created. You are the owner." + R);
        pushUpdate(epm);
    }

    public static void invite(EntityPlayerMP epm, String target) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team. Use /team create <name>."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can invite."); return; }
        if (target == null || target.length() == 0) { reply(epm, "Usage: /team invite <player>"); return; }
        if (tr.getByPlayer(target) != null) { reply(epm, target + " is already in a team."); return; }
        if (tr.addMember(t, target)) {
            reply(epm, GREEN + "Added " + target + " to " + t.name + "." + R);
            pushUpdateTeam(w, t, null);
        } else {
            reply(epm, "Could not add " + target + ".");
        }
    }

    public static void kick(EntityPlayerMP epm, String target) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can kick."); return; }
        if (target == null || target.length() == 0) { reply(epm, "Usage: /team kick <player>"); return; }
        if (tr.removeMember(t, target)) {
            reply(epm, RED + "Removed " + target + "." + R);
            pushUpdateTeam(w, t, target);
        } else {
            reply(epm, target + " is not a member.");
        }
    }

    public static void promote(EntityPlayerMP epm, String target) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can promote."); return; }
        if (target == null || target.length() == 0) { reply(epm, "Usage: /team promote <member>"); return; }
        if (!t.members.contains(target.toLowerCase())) {
            reply(epm, target + " is not a member of your team."); return;
        }
        if (tr.transferOwnership(t, target)) {
            reply(epm, YEL + "Ownership of " + t.name + " transferred to " + target + ". You are now a regular member." + R);
            pushUpdateTeam(w, t, null);
        } else {
            reply(epm, "Could not promote " + target + ".");
        }
    }

    public static void leave(EntityPlayerMP epm) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (t.isOwner(epm.username)) {
            reply(epm, "Owners can't leave. Use /team disband to remove the team."); return;
        }
        if (tr.removeMember(t, epm.username)) {
            reply(epm, "You left " + AQUA + t.name + R + ".");
            pushUpdate(epm);
            pushUpdateTeam(w, t, null);
        } else {
            reply(epm, "You are not a member.");
        }
    }

    public static void disband(EntityPlayerMP epm) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can disband."); return; }
        ClaimRegistry cr = ClaimRegistry.get(w);
        // Snapshot members BEFORE disband (which clears the team).
        List<String> formerMembers = new ArrayList<String>(t.members);
        String formerOwner = t.owner;
        List<Claim> claims = cr.dropTeam(t.name);
        for (Claim c : claims) if (c.chunkload) ChunkLoadManager.release(c.dim, c.chunkX, c.chunkZ);
        tr.disband(t.name);
        reply(epm, RED + "Team " + t.name + " disbanded. " + claims.size() + " claims released." + R);
        // Refresh all affected players.
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv != null) {
            if (formerOwner != null) {
                EntityPlayerMP p = srv.getConfigurationManager().getPlayerForUsername(formerOwner);
                if (p != null) pushUpdate(p);
            }
            for (String m : formerMembers) {
                EntityPlayerMP p = srv.getConfigurationManager().getPlayerForUsername(m);
                if (p != null) pushUpdate(p);
            }
        }
    }

    public static void ally(EntityPlayerMP epm, String target) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can manage allies."); return; }
        if (target == null || target.length() == 0) { reply(epm, "Usage: /team ally <playerOrTeamOwner>"); return; }
        if (t.addAlly(target)) {
            tr.markDirty();
            reply(epm, GREEN + "Allied with " + target + "." + R);
            pushUpdateTeam(w, t, target);
        } else {
            reply(epm, target + " is already an ally.");
        }
    }

    public static void unally(EntityPlayerMP epm, String target) {
        World w = epm.worldObj;
        TeamRegistry tr = TeamRegistry.get(w);
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team."); return; }
        if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can manage allies."); return; }
        if (target == null || target.length() == 0) { reply(epm, "Usage: /team unally <playerOrTeamOwner>"); return; }
        if (t.removeAlly(target)) {
            tr.markDirty();
            reply(epm, RED + "Unallied " + target + "." + R);
            pushUpdateTeam(w, t, target);
        } else {
            reply(epm, target + " was not an ally.");
        }
    }

    // ---- Update-push helpers (shared with TeamCommand's former private copies) ----

    /** Re-push the grid + team info to one player (used after we mutate their team). */
    public static void pushUpdate(EntityPlayerMP epm) {
        if (epm == null || epm.worldObj == null) return;
        int cx = ((int) Math.floor(epm.posX)) >> 4;
        int cz = ((int) Math.floor(epm.posZ)) >> 4;
        PacketHandler.sendGrid(epm, cx, cz, Config.gridRadius);
        PacketHandler.sendTeamInfo(epm);
    }

    /** Re-push to every player currently in {@code team} (and an extra just-removed/affected user). */
    public static void pushUpdateTeam(World w, ClaimTeam team, String extraUser) {
        if (team == null) return;
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) return;
        EntityPlayerMP p = srv.getConfigurationManager().getPlayerForUsername(team.owner);
        if (p != null) pushUpdate(p);
        for (String m : team.members) {
            p = srv.getConfigurationManager().getPlayerForUsername(m);
            if (p != null) pushUpdate(p);
        }
        if (extraUser != null) {
            p = srv.getConfigurationManager().getPlayerForUsername(extraUser);
            if (p != null) pushUpdate(p);
        }
    }
}
