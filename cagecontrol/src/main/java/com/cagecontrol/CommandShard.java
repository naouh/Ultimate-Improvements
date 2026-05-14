package com.cagecontrol;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class CommandShard extends CommandBase {

    // Voldeloom's mappings declare ICommand as Comparable (raw) rather than
    // Comparable<ICommand>, so under -source 1.6 javac demands compareTo(Object).
    public int compareTo(Object other) {
        if (other instanceof net.minecraft.command.ICommand) {
            return this.getCommandName().compareTo(((net.minecraft.command.ICommand) other).getCommandName());
        }
        return 0;
    }

    // MC chat color codes
    private static final String GREEN  = "§a";
    private static final String RED    = "§c";
    private static final String YELLOW = "§e";
    private static final String GRAY   = "§7";
    private static final String AQUA   = "§b";
    private static final String RESET  = "§r";

    @Override
    public String getCommandName() { return "shard"; }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/shard <list|register <name>|<name> start|stop|<name> owner add|remove|list <player>>";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override
    public List getCommandAliases() {
        List<String> a = new ArrayList<String>();
        a.add("cage");
        return a;
    }

    private static void reply(ICommandSender s, String msg) {
        s.sendChatToPlayer("[CageControl] " + msg);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            reply(sender, "This command can only be used in-game."); return;
        }
        EntityPlayerMP epm = (EntityPlayerMP) sender;

        if (args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            CageCleanupTick.runScan();
            CageRegistry reg = CageRegistry.get(epm.worldObj);
            List<CageData> mine = reg.listControllableBy(epm.username);
            if (mine.isEmpty()) { reply(epm, "You have no registered cages."); return; }
            reply(epm, "Your cages:");
            for (CageData d : mine) {
                String status = d.active ? (GREEN + "STARTED" + RESET) : (RED + "STOPPED" + RESET);
                String role   = d.isOwner(epm.username) ? "" : (GRAY + " (co-owner of " + d.owner + ")" + RESET);
                reply(epm, " - " + AQUA + d.name + RESET + " [" + status + "]"
                        + " (" + d.mobType + ") tier" + d.tier
                        + " @ " + d.x + "," + d.y + "," + d.z + " dim" + d.dim + role);
            }
            return;
        }

        if (args.length == 2 && "register".equalsIgnoreCase(args[0])) {
            registerLookedAt(epm, args[1]);
            return;
        }

        if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));

        String name   = args[0];
        String action = args[1].toLowerCase();

        CageRegistry reg = CageRegistry.get(epm.worldObj);
        CageData d = reg.findControllable(epm.username, name);
        if (d == null) { reply(epm, "No cage named '" + name + "' you can control."); return; }

        if ("owner".equals(action)) {
            handleOwner(epm, reg, d, args);
            return;
        }

        if (args.length != 2) throw new WrongUsageException(getCommandUsage(sender));

        WorldServer ws = findWorldByDim(d.dim);
        if (ws == null) { reply(epm, "Dimension " + d.dim + " is not loaded."); return; }

        TileEntity te = ws.getBlockTileEntity(d.x, d.y, d.z);
        if (!ReflectSS.isSoulCage(te)) { reply(epm, "Cage no longer exists at " + d.x + "," + d.y + "," + d.z + "."); reg.remove(d); return; }

        if ("start".equals(action)) {
            ReflectSS.setSignal(te, ws.isBlockGettingPowered(d.x, d.y, d.z) || ws.isBlockIndirectlyGettingPowered(d.x, d.y, d.z));
            ReflectSS.setMobType(te, d.mobType, d.special);
            ReflectSS.setTier(te, d.tier);          // restores delay
            ReflectSS.rCount(te);
            ws.markBlockForUpdate(d.x, d.y, d.z);
            d.active = true;
            reg.markDirty();
            reply(epm, "Cage '" + AQUA + d.name + RESET + "' " + GREEN + "started" + RESET + ".");
        } else if ("stop".equals(action)) {
            // keep mobType so the spawner visual stays; just block spawning via delay = MAX
            ReflectSS.disableSpawn(te);
            ReflectSS.resetCount(te);
            ws.markBlockForUpdate(d.x, d.y, d.z);
            d.active = false;
            reg.markDirty();
            reply(epm, "Cage '" + AQUA + d.name + RESET + "' " + RED + "stopped" + RESET + ".");
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static void handleOwner(EntityPlayerMP epm, CageRegistry reg, CageData d, String[] args) {
        // args = [<cageName>, "owner", <sub>, <player?>]
        if (args.length < 3) {
            reply(epm, "Usage: /shard <name> owner <add|remove|list> [player]"); return;
        }
        String sub = args[2].toLowerCase();

        if ("list".equals(sub)) {
            reply(epm, "Owner of '" + AQUA + d.name + RESET + "': " + YELLOW + d.owner + RESET);
            if (d.coOwners.isEmpty()) reply(epm, "No co-owners.");
            else {
                StringBuilder sb = new StringBuilder("Co-owners: ");
                boolean first = true;
                for (String co : d.coOwners) {
                    if (!first) sb.append(", ");
                    sb.append(YELLOW).append(co).append(RESET);
                    first = false;
                }
                reply(epm, sb.toString());
            }
            return;
        }

        if (!d.isOwner(epm.username)) {
            reply(epm, "Only the primary owner (" + d.owner + ") can manage co-owners.");
            return;
        }
        if (args.length < 4) { reply(epm, "Player name required."); return; }
        String target = args[3];

        if ("add".equals(sub)) {
            if (d.isOwner(target)) { reply(epm, target + " is already the primary owner."); return; }
            if (d.addCoOwner(target)) {
                reg.markDirty();
                reply(epm, GREEN + "Added " + target + " as co-owner of '" + d.name + "'." + RESET);
            } else {
                reply(epm, target + " is already a co-owner.");
            }
        } else if ("remove".equals(sub) || "rm".equals(sub)) {
            if (d.removeCoOwner(target)) {
                reg.markDirty();
                reply(epm, RED + "Removed " + target + " from co-owners of '" + d.name + "'." + RESET);
            } else {
                reply(epm, target + " is not a co-owner.");
            }
        } else {
            reply(epm, "Unknown subcommand: " + sub);
        }
    }

    private static WorldServer findWorldByDim(int dim) {
        WorldServer[] worlds = MinecraftServer.getServer().worldServers;
        for (WorldServer w : worlds) if (w.provider.dimensionId == dim) return w;
        return null;
    }

    private static void registerLookedAt(EntityPlayerMP epm, String name) {
        if (name == null) { reply(epm, "Name required."); return; }
        name = name.trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_\\-]{1,24}")) {
            reply(epm, "Invalid name (A-Z 0-9 _ -, max 24 chars)."); return;
        }

        World w = epm.worldObj;
        MovingObjectPosition mop = rayTraceBlock(epm, 6.0);
        if (mop == null || mop.typeOfHit != net.minecraft.util.EnumMovingObjectType.TILE) {
            reply(epm, "Look at a Soul Cage and try again (range 6 blocks)."); return;
        }
        int x = mop.blockX, y = mop.blockY, z = mop.blockZ;

        int cageId = ReflectSS.getCageBlockId();
        if (cageId <= 0 || w.getBlockId(x, y, z) != cageId) {
            reply(epm, "The targeted block is not a Soul Cage."); return;
        }

        TileEntity te = w.getBlockTileEntity(x, y, z);
        if (!ReflectSS.isSoulCage(te)) { reply(epm, "Soul Cage tile entity not found."); return; }

        CageRegistry reg = CageRegistry.get(w);
        if (reg.findByPos(x, y, z) != null) {
            reply(epm, "This cage is already registered."); return;
        }
        if (reg.nameTaken(epm.username, name)) {
            reply(epm, "You already have a cage named '" + name + "'."); return;
        }

        // Prefer the cage's own mob type if active; else use held shard.
        String mobType = ReflectSS.getMobType(te);
        boolean special = false;
        int tier = ReflectSS.getTier(te);
        boolean fromCage = mobType != null && !mobType.isEmpty();

        if (!fromCage) {
            ItemStack held = epm.getCurrentEquippedItem();
            Object shardItem = ReflectSS.soulShardsItem();
            if (held == null || shardItem == null || held.getItem() != shardItem) {
                reply(epm, "Cage is inactive: hold a charged Soul Shard to prime it.");
                return;
            }
            mobType = ReflectSS.getShardType(held);
            special = ReflectSS.getShardSpecial(held);
            if (mobType == null || mobType.isEmpty()) { reply(epm, "Empty shard."); return; }
            int charges = held.getMaxDamage() - held.getItemDamage();
            tier = (int) (Math.log(charges) / Math.log(2.0)) - 5;
            if (tier <= 0) { reply(epm, "Shard not charged enough (tier 1+ required)."); return; }
            held.stackSize = 0;
        }

        CageData d = new CageData(x, y, z, w.provider.dimensionId,
                                  epm.username, name, mobType, special, tier);
        d.active = false;  // always start stopped, even if cage was previously active
        reg.put(d);

        ReflectSS.setSignal(te, w.isBlockGettingPowered(x, y, z) || w.isBlockIndirectlyGettingPowered(x, y, z));
        // setTier first (it overwrites delay), THEN disableSpawn pins delay to MAX.
        ReflectSS.setTier(te, tier);
        ReflectSS.setMobType(te, mobType, special);
        ReflectSS.disableSpawn(te);
        ReflectSS.resetCount(te);
        w.markBlockForUpdate(x, y, z);

        reply(epm, "Cage '" + name + "' registered. Use /shard " + name + " start to activate.");
    }

    private static MovingObjectPosition rayTraceBlock(EntityPlayerMP epm, double dist) {
        World w = epm.worldObj;
        Vec3 eye  = w.getWorldVec3Pool().getVecFromPool(
                epm.posX, epm.posY + 1.62 - epm.yOffset, epm.posZ);
        float yaw   = (float) Math.toRadians(epm.rotationYaw);
        float pitch = (float) Math.toRadians(epm.rotationPitch);
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz =  Math.cos(yaw) * Math.cos(pitch);
        Vec3 end = w.getWorldVec3Pool().getVecFromPool(
                eye.xCoord + dx * dist, eye.yCoord + dy * dist, eye.zCoord + dz * dist);
        return w.rayTraceBlocks(eye, end);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> r = new ArrayList<String>();
            for (String s : new String[] { "list", "register" })
                if (s.startsWith(args[0].toLowerCase())) r.add(s);
            return r;
        }
        if (args.length == 2 && !"register".equalsIgnoreCase(args[0])) {
            List<String> r = new ArrayList<String>();
            for (String s : new String[] { "start", "stop", "owner" })
                if (s.startsWith(args[1].toLowerCase())) r.add(s);
            return r;
        }
        if (args.length == 3 && "owner".equalsIgnoreCase(args[1])) {
            List<String> r = new ArrayList<String>();
            for (String s : new String[] { "add", "remove", "list" })
                if (s.startsWith(args[2].toLowerCase())) r.add(s);
            return r;
        }
        return null;
    }
}
