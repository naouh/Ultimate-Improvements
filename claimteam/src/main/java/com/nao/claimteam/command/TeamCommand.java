package com.nao.claimteam.command;

import java.util.ArrayList;
import java.util.List;

import com.nao.claimteam.Config;
import com.nao.claimteam.chunkload.ChunkLoadManager;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;
import com.nao.claimteam.network.PacketHandler;
import net.minecraft.server.MinecraftServer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class TeamCommand extends CommandBase {

    private static final String GREEN = "§a";
    private static final String RED   = "§c";
    private static final String YEL   = "§e";
    private static final String AQUA  = "§b";
    private static final String GRAY  = "§7";
    private static final String R     = "§r";

    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }

    @Override
    public String getCommandName() { return "team"; }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/team <create|invite|kick|promote|ally|unally|leave|disband|info|list> [...]";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override
    public List getCommandAliases() {
        List<String> a = new ArrayList<String>();
        a.add("ctteam");
        return a;
    }

    private static void reply(ICommandSender s, String msg) {
        s.sendChatToPlayer("§e[ClaimTeam]§r " + msg);
    }

    /** Re-push the grid + team info to one player (used after we mutate their team). */
    private static void pushUpdate(EntityPlayerMP epm) {
        if (epm == null || epm.worldObj == null) return;
        int cx = ((int) Math.floor(epm.posX)) >> 4;
        int cz = ((int) Math.floor(epm.posZ)) >> 4;
        PacketHandler.sendGrid(epm, cx, cz, Config.gridRadius);
        PacketHandler.sendTeamInfo(epm);
    }

    /** Re-push to every player currently in {@code team} (and the just-removed member when needed). */
    private static void pushUpdateTeam(World w, ClaimTeam team, String extraUser) {
        if (team == null) return;
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) return;
        // Owner
        EntityPlayerMP p = srv.getConfigurationManager().getPlayerForUsername(team.owner);
        if (p != null) pushUpdate(p);
        // Members
        for (String m : team.members) {
            p = srv.getConfigurationManager().getPlayerForUsername(m);
            if (p != null) pushUpdate(p);
        }
        // Extra (e.g. just-kicked player, no longer in members)
        if (extraUser != null) {
            p = srv.getConfigurationManager().getPlayerForUsername(extraUser);
            if (p != null) pushUpdate(p);
        }
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) { reply(sender, "In-game only."); return; }
        EntityPlayerMP epm = (EntityPlayerMP) sender;
        World w = epm.worldObj;
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        String sub = args[0].toLowerCase();
        TeamRegistry tr = TeamRegistry.get(w);

        if ("create".equals(sub)) {
            if (args.length < 2) { reply(epm, "Usage: /team create <name>"); return; }
            String name = args[1];
            if (!name.matches("[A-Za-z0-9_\\-]{3,16}")) {
                reply(epm, "Invalid name (A-Z 0-9 _ -, 3-16 chars)."); return;
            }
            if (tr.getByPlayer(epm.username) != null) {
                reply(epm, "You are already in a team."); return;
            }
            if (tr.nameTaken(name)) { reply(epm, "Name taken."); return; }
            ClaimTeam t = tr.create(name, epm.username);
            if (t == null) { reply(epm, "Could not create team."); return; }
            reply(epm, GREEN + "Team '" + name + "' created. You are the owner." + R);
            pushUpdate(epm);
            return;
        }

        if ("info".equals(sub) || "list".equals(sub) && args.length == 1) {
            ClaimTeam t = tr.getByPlayer(epm.username);
            if (t == null) { reply(epm, "You are not in a team."); return; }
            ClaimRegistry cr = ClaimRegistry.get(w);
            reply(epm, "Team: " + AQUA + t.name + R + "  Owner: " + YEL + t.owner + R);
            reply(epm, "Members (" + t.members.size() + "): " + joinSet(t.members));
            reply(epm, "Allies ("  + t.allies.size()  + "): " + joinSet(t.allies));
            reply(epm, "Claims: " + cr.countByTeam(t.name)
                    + "   Chunk-loaded: " + cr.countChunkloadByTeam(t.name));
            return;
        }

        if ("list".equals(sub)) {
            List<ClaimTeam> teams = tr.all();
            reply(epm, "Teams (" + teams.size() + "):");
            for (ClaimTeam t : teams) {
                reply(epm, " - " + AQUA + t.name + R + "  owner: " + YEL + t.owner + R
                        + GRAY + "  (" + (1 + t.members.size()) + " players)" + R);
            }
            return;
        }

        // Everything below requires an owned or joined team
        ClaimTeam t = tr.getByPlayer(epm.username);
        if (t == null) { reply(epm, "You are not in a team. Use /team create <name>."); return; }

        if ("leave".equals(sub)) {
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
            return;
        }

        if ("disband".equals(sub)) {
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
            return;
        }

        if ("invite".equals(sub) || "add".equals(sub)) {
            if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can invite."); return; }
            if (args.length < 2) { reply(epm, "Usage: /team invite <player>"); return; }
            String target = args[1];
            if (tr.getByPlayer(target) != null) { reply(epm, target + " is already in a team."); return; }
            if (tr.addMember(t, target)) {
                reply(epm, GREEN + "Added " + target + " to " + t.name + "." + R);
                pushUpdateTeam(w, t, null);
            } else {
                reply(epm, "Could not add " + target + ".");
            }
            return;
        }

        if ("promote".equals(sub)) {
            if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can promote."); return; }
            if (args.length < 2) { reply(epm, "Usage: /team promote <member>"); return; }
            String target = args[1];
            if (!t.members.contains(target.toLowerCase())) {
                reply(epm, target + " is not a member of your team."); return;
            }
            if (tr.transferOwnership(t, target)) {
                reply(epm, YEL + "Ownership of " + t.name + " transferred to " + target + ". You are now a regular member." + R);
                pushUpdateTeam(w, t, null);
            } else {
                reply(epm, "Could not promote " + target + ".");
            }
            return;
        }

        if ("kick".equals(sub) || "remove".equals(sub)) {
            if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can kick."); return; }
            if (args.length < 2) { reply(epm, "Usage: /team kick <player>"); return; }
            String target = args[1];
            if (tr.removeMember(t, target)) {
                reply(epm, RED + "Removed " + target + "." + R);
                pushUpdateTeam(w, t, target);
            } else {
                reply(epm, target + " is not a member.");
            }
            return;
        }

        if ("ally".equals(sub)) {
            if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can manage allies."); return; }
            if (args.length < 2) { reply(epm, "Usage: /team ally <playerOrTeamOwner>"); return; }
            String target = args[1];
            if (t.addAlly(target)) { tr.markDirty(); reply(epm, GREEN + "Allied with " + target + "." + R); }
            else reply(epm, target + " is already an ally.");
            return;
        }

        if ("unally".equals(sub)) {
            if (!t.isOwner(epm.username)) { reply(epm, "Only the owner can manage allies."); return; }
            if (args.length < 2) { reply(epm, "Usage: /team unally <playerOrTeamOwner>"); return; }
            String target = args[1];
            if (t.removeAlly(target)) { tr.markDirty(); reply(epm, RED + "Unallied " + target + "." + R); }
            else reply(epm, target + " was not an ally.");
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static String joinSet(java.util.Set<String> s) {
        if (s.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String x : s) {
            if (!first) sb.append(", ");
            sb.append(x);
            first = false;
        }
        return sb.toString();
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> r = new ArrayList<String>();
            for (String s : new String[] { "create", "invite", "kick", "promote", "ally", "unally",
                                            "leave", "disband", "info", "list" }) {
                if (s.startsWith(args[0].toLowerCase())) r.add(s);
            }
            return r;
        }
        return null;
    }
}
