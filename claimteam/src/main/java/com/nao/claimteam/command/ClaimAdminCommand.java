package com.nao.claimteam.command;

import java.util.ArrayList;
import java.util.List;

import com.nao.claimteam.Config;
import com.nao.claimteam.chunkload.ChunkLoadManager;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;
import com.nao.claimteam.perm.GroupManagerBridge;
import com.nao.claimteam.perm.PermissionResolver;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class ClaimAdminCommand extends CommandBase {

    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }

    @Override
    public String getCommandName() { return "claimadmin"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/claimadmin <reload|setgroup <player> <group>|wipe <team>|info|gm>";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        if (sender instanceof EntityPlayer) return PermissionResolver.isOp((EntityPlayer) sender);
        return true; // console is allowed
    }

    @Override
    public List getCommandAliases() { return new ArrayList<String>(); }

    private static void reply(ICommandSender s, String msg) {
        s.sendChatToPlayer("§e[ClaimTeam admin]§r " + msg);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        String sub = args[0].toLowerCase();

        if ("reload".equals(sub)) {
            Config.reload();
            reply(sender, "Config reloaded. Groups: " + Config.knownGroupNames());
            return;
        }
        if ("setgroup".equals(sub)) {
            if (args.length < 3) { reply(sender, "Usage: /claimadmin setgroup <player> <group>"); return; }
            String player = args[1];
            String group = args[2];
            Config.playerGroups.put(player.toLowerCase(), group);
            reply(sender, "Set " + player + " -> " + group + " (memory only; edit claimteam.cfg to persist).");
            return;
        }
        if ("wipe".equals(sub)) {
            if (args.length < 2) { reply(sender, "Usage: /claimadmin wipe <team>"); return; }
            if (!(sender instanceof EntityPlayerMP)) { reply(sender, "Run in-game."); return; }
            EntityPlayerMP epm = (EntityPlayerMP) sender;
            World w = epm.worldObj;
            String name = args[1];
            ClaimTeam t = TeamRegistry.get(w).getByName(name);
            if (t == null) { reply(sender, "No team named " + name + "."); return; }
            List<Claim> dropped = ClaimRegistry.get(w).dropTeam(name);
            for (Claim c : dropped) if (c.chunkload) ChunkLoadManager.release(c.dim, c.chunkX, c.chunkZ);
            TeamRegistry.get(w).disband(name);
            reply(sender, "Wiped team " + name + " — released " + dropped.size() + " claims.");
            return;
        }
        if ("info".equals(sub)) {
            reply(sender, "useGroupManager=" + Config.useGroupManager
                  + "  GM bridge enabled=" + GroupManagerBridge.isEnabled());
            reply(sender, "groups: " + Config.knownGroupNames());
            reply(sender, "transformers: explosion=" + Config.enableExplosionTransformer
                  + " piston=" + Config.enablePistonTransformer
                  + " fluid=" + Config.enableFluidTransformer
                  + " pvp=" + Config.enablePvpTransformer);
            return;
        }
        if ("gm".equals(sub)) {
            if (args.length < 2) { reply(sender, "Usage: /claimadmin gm <player>"); return; }
            if (!(sender instanceof EntityPlayerMP)) { reply(sender, "Run in-game."); return; }
            EntityPlayerMP epm = (EntityPlayerMP) sender;
            String target = args[1];
            // Best-effort: resolve via the sender's world name. If user is offline, world inference fails.
            String world;
            try { world = epm.worldObj.getWorldInfo().getWorldName(); }
            catch (Throwable t) { world = "world"; }
            String g = GroupManagerBridge.getGroupName(target, world);
            reply(sender, target + " -> " + (g == null ? "(unknown / GM not active)" : g));
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> r = new ArrayList<String>();
            for (String s : new String[] { "reload", "setgroup", "wipe", "info", "gm" }) {
                if (s.startsWith(args[0].toLowerCase())) r.add(s);
            }
            return r;
        }
        return null;
    }
}
