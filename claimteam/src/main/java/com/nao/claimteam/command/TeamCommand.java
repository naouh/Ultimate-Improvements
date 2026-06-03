package com.nao.claimteam.command;

import java.util.ArrayList;
import java.util.List;

import com.nao.claimteam.action.TeamActions;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class TeamCommand extends CommandBase {

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

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) { reply(sender, "In-game only."); return; }
        EntityPlayerMP epm = (EntityPlayerMP) sender;
        World w = epm.worldObj;
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        String sub = args[0].toLowerCase();
        TeamRegistry tr = TeamRegistry.get(w);
        String arg1 = args.length > 1 ? args[1] : null;

        // Read-only views stay in the command; everything mutating delegates to TeamActions
        // so the map GUI's packet path runs the exact same logic (and the same ownership checks).
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

        if ("create".equals(sub))                              { TeamActions.create(epm, arg1);  return; }
        if ("leave".equals(sub))                               { TeamActions.leave(epm);          return; }
        if ("disband".equals(sub))                             { TeamActions.disband(epm);        return; }
        if ("invite".equals(sub) || "add".equals(sub))         { TeamActions.invite(epm, arg1);   return; }
        if ("promote".equals(sub))                             { TeamActions.promote(epm, arg1);  return; }
        if ("kick".equals(sub) || "remove".equals(sub))        { TeamActions.kick(epm, arg1);     return; }
        if ("ally".equals(sub))                                { TeamActions.ally(epm, arg1);     return; }
        if ("unally".equals(sub))                              { TeamActions.unally(epm, arg1);   return; }

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
