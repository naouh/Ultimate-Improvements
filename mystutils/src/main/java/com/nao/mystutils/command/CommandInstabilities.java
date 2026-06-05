package com.nao.mystutils.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * {@code /instabilities} (alias {@code /instab}) — reports the active instability effects of the
 * Mystcraft Age the player is currently standing in. Runs server-side, where the Age's saved data
 * lives. Read-only; usable by everyone.
 */
public class CommandInstabilities extends CommandBase {

    /** Pretty labels for Mystcraft's internal effect identifiers; unknowns are prettified. */
    private static final Map<String, String> NAMES = new HashMap<String, String>();
    static {
        NAMES.put("decay", "Decay");
        NAMES.put("decayblack", "Black Decay");
        NAMES.put("decayblue", "Blue Decay");
        NAMES.put("decaypurple", "Purple Decay");
        NAMES.put("decayred", "Red Decay");
        NAMES.put("decaywhite", "White Decay");
        NAMES.put("crumble", "Crumbling (cave-in)");
        NAMES.put("erosion", "Erosion");
        NAMES.put("scorched", "Scorched Terrain");
        NAMES.put("poisonedSurface", "Poisoned Surface");
        NAMES.put("volcano", "Volcanoes");
        NAMES.put("meteors", "Meteor Showers");
        NAMES.put("lightning", "Lightning Storms");
        NAMES.put("explosion", "Random Explosions");
        NAMES.put("blindness", "Blindness");
        NAMES.put("confusion", "Confusion (nausea)");
        NAMES.put("fatigue", "Mining Fatigue");
        NAMES.put("hunger", "Hunger");
        NAMES.put("slow", "Slowness");
        NAMES.put("weakness", "Weakness");
        NAMES.put("poison", "Poison");
        NAMES.put("enemyregeneration", "Mob Regeneration");
    }

    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }

    @Override public String getCommandName() { return "instabilities"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }

    @Override
    public String getCommandUsage(ICommandSender s) { return "/instabilities"; }

    @Override
    public List getCommandAliases() {
        List<String> a = new ArrayList<String>();
        a.add("instab");
        return a;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // A command must never take the server down. If anything throws — a version mismatch where a
        // helper class is missing (NoClassDefFoundError), Mystcraft reflection blowing up, etc. —
        // report it to the player and the log instead of letting it propagate into the net handler.
        try {
            run(sender, args);
        } catch (Throwable t) {
            try {
                sender.sendChatToPlayer("§c[MystUtils] Command error (see server log): " + t);
            } catch (Throwable ignored) {}
            System.err.println("[MystUtils] /instabilities failed:");
            t.printStackTrace();
        }
    }

    private void run(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendChatToPlayer("[MystUtils] This command can only be used by a player.");
            return;
        }
        if (!MystAgeInfo.available()) {
            sender.sendChatToPlayer("§c[MystUtils] Mystcraft was not detected on this server.");
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        int dimId = player.worldObj.provider.dimensionId;
        MystAgeInfo.Report report = MystAgeInfo.lookup(player.worldObj, dimId);

        if (report == null) {
            sender.sendChatToPlayer("§eYou are not standing in a Mystcraft Age.");
            return;
        }
        if (!report.instabilityEnabled) {
            sender.sendChatToPlayer("§a[Age] Instability is disabled here — this Age is stable.");
            return;
        }

        // Dedupe while preserving order (a provider may be listed per level).
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        for (String id : report.effects) {
            if (id != null && id.length() > 0) unique.add(id);
        }

        if (unique.isEmpty()) {
            sender.sendChatToPlayer("§a[Age] No active instabilities — this Age is stable.");
            return;
        }

        sender.sendChatToPlayer("§b[Age] Active instabilities (§f" + unique.size() + "§b):");
        for (String id : unique) {
            sender.sendChatToPlayer("§7 - §f" + friendly(id));
        }
    }

    private static String friendly(String id) {
        String name = NAMES.get(id);
        if (name != null) return name;
        // Unknown identifier: turn "someEffectName" into "Some Effect Name".
        StringBuilder sb = new StringBuilder();
        boolean newWord = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == ' ') { sb.append(' '); newWord = true; continue; }
            if (Character.isUpperCase(c) && sb.length() > 0) sb.append(' ');
            sb.append(newWord ? Character.toUpperCase(c) : c);
            newWord = false;
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
