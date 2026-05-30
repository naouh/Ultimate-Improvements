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
        return "/shard <list|register <name>|<name> start|stop|rename <new>|owner add|remove|list <player>>";
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
        boolean isAdmin = MinecraftServer.getServer().getConfigurationManager()
                .areCommandsAllowed(epm.username);

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
        CageData d = resolveCage(reg, epm.username, isAdmin, name);
        if (d == null) {
            if (name.indexOf(':') >= 0) {
                reply(epm, "No cage matches '" + name + "'.");
            } else if (isAdmin) {
                reply(epm, "No cage named '" + name + "' found. Use <owner>:<name> to disambiguate.");
            } else {
                reply(epm, "No cage named '" + name + "' you can control.");
            }
            return;
        }
        if (!isAdmin && !d.canControl(epm.username)) {
            reply(epm, "You are not an owner or co-owner of '" + d.name + "'.");
            return;
        }

        if ("owner".equals(action)) {
            handleOwner(epm, reg, d, args, isAdmin);
            return;
        }

        if ("rename".equals(action)) {
            if (args.length < 3) { reply(epm, "Usage: /shard <name> rename <new>"); return; }
            if (!d.isOwner(epm.username) && !isAdmin) { reply(epm, "Only the owner can rename."); return; }
            String newName = args[2].trim();
            if (!newName.matches("[A-Za-z0-9_\\-]{1,24}")) {
                reply(epm, "Invalid name (A-Z 0-9 _ -, max 24 chars)."); return;
            }
            String old = d.name;
            if (!reg.rename(d, newName)) {
                reply(epm, "You already have a cage named '" + newName + "'.");
            } else {
                reply(epm, "Cage '" + AQUA + old + RESET + "' renamed to '" + AQUA + newName + RESET + "'.");
                PacketHandler.sendCageList(epm);
            }
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
            PacketHandler.sendCageList(epm);
        } else if ("stop".equals(action)) {
            // keep mobType so the spawner visual stays; just block spawning via delay = MAX
            ReflectSS.disableSpawn(te);
            ReflectSS.resetCount(te);
            ws.markBlockForUpdate(d.x, d.y, d.z);
            d.active = false;
            reg.markDirty();
            reply(epm, "Cage '" + AQUA + d.name + RESET + "' " + RED + "stopped" + RESET + ".");
            PacketHandler.sendCageList(epm);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static void handleOwner(EntityPlayerMP epm, CageRegistry reg, CageData d,
                                    String[] args, boolean isAdmin) {
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

        if (!d.isOwner(epm.username) && !isAdmin) {
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
                PacketHandler.sendCageList(epm);
            } else {
                reply(epm, target + " is already a co-owner.");
            }
        } else if ("remove".equals(sub) || "rm".equals(sub)) {
            if (d.removeCoOwner(target)) {
                reg.markDirty();
                reply(epm, RED + "Removed " + target + " from co-owners of '" + d.name + "'." + RESET);
                PacketHandler.sendCageList(epm);
            } else {
                reply(epm, target + " is not a co-owner.");
            }
        } else {
            reply(epm, "Unknown subcommand: " + sub);
        }
    }

    /**
     * Resolve a cage from the user-facing name argument. Three accepted forms:
     * <ul>
     *   <li>{@code name} — looked up as one of the caller's controllable cages first; for admins,
     *       falls back to a global name search if none of theirs match.</li>
     *   <li>{@code owner:name} — direct {@code byOwnerName} lookup. Anyone can use this form to
     *       disambiguate; permission is still re-checked by the caller.</li>
     *   <li>{@code @x,y,z} — for admins only: positional lookup. Useful when the name is gone
     *       or ambiguous and you have coordinates from the GUI.</li>
     * </ul>
     * The caller is responsible for re-checking that {@code canControl(player)} holds when
     * {@code isAdmin} is false.
     */
    private static CageData resolveCage(CageRegistry reg, String player, boolean isAdmin, String arg) {
        if (arg == null || arg.isEmpty()) return null;

        if (arg.charAt(0) == '@' && isAdmin) {
            String[] parts = arg.substring(1).split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    int z = Integer.parseInt(parts[2].trim());
                    return reg.findByPos(x, y, z);
                } catch (NumberFormatException ignored) { /* fall through */ }
            }
            return null;
        }

        int colon = arg.indexOf(':');
        if (colon > 0 && colon < arg.length() - 1) {
            String owner = arg.substring(0, colon);
            String name  = arg.substring(colon + 1);
            return reg.findByOwnerName(owner, name);
        }

        CageData own = reg.findControllable(player, arg);
        if (own != null) return own;
        if (!isAdmin) return null;

        // Admin global fallback: pick the first cage that matches the name. If multiple share
        // the name across owners, the caller will need to use owner:name to disambiguate; we
        // return null in that case so the error message asks for that.
        CageData match = null;
        for (CageData d : reg.snapshot()) {
            if (d.name != null && d.name.equalsIgnoreCase(arg)) {
                if (match != null) return null; // ambiguous
                match = d;
            }
        }
        return match;
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
            for (String s : new String[] { "start", "stop", "owner", "rename" })
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
