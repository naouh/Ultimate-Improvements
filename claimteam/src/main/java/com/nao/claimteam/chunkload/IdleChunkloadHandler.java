package com.nao.claimteam.chunkload;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nao.claimteam.Config;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * Server tick handler that:
 *   1. every {@link #REFRESH_TICKS} marks each online player's team as "active now", and
 *   2. every {@link #SWEEP_TICKS} disables chunk-loading for any team that has had nobody online
 *      for {@link Config#idleDisableDays} real days. Claims themselves are kept; only the
 *      Forge chunk-loading tickets are released.
 *
 * The team/claim registries are global (a single store holds all dimensions), so we read them from
 * the overworld and act on every dimension via the claim's own {@code dim}.
 */
public class IdleChunkloadHandler implements ITickHandler {

    /** 20 ticks/sec. Refresh online teams once a minute. */
    private static final int REFRESH_TICKS = 20 * 60;
    /** Run the idle sweep every 5 minutes. */
    private static final int SWEEP_TICKS = 20 * 60 * 5;

    private int counter = 0;

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || srv.worldServers == null || srv.worldServers.length == 0) return;
        counter++;
        if (counter % REFRESH_TICKS == 0) refreshOnlineTeams(srv);
        if (counter % SWEEP_TICKS == 0) sweepIdleTeams(srv);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> onlineUsernames(MinecraftServer srv) {
        Set<String> online = new HashSet<String>();
        List<EntityPlayerMP> players = srv.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP p : players) {
            if (p != null && p.username != null) online.add(p.username.toLowerCase());
        }
        return online;
    }

    /** Stamp every team that has at least one member online with the current time. */
    private void refreshOnlineTeams(MinecraftServer srv) {
        WorldServer overworld = srv.worldServers[0];
        TeamRegistry teams = TeamRegistry.get(overworld);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String name : onlineUsernames(srv)) {
            ClaimTeam t = teams.getByPlayer(name);
            if (t != null) { t.lastActiveMillis = now; changed = true; }
        }
        if (changed) teams.markDirty();
    }

    /** Disable chunk-loads of teams idle longer than the configured threshold. */
    private void sweepIdleTeams(MinecraftServer srv) {
        int days = Config.idleDisableDays;
        if (days <= 0) return;
        long cutoff = days * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        WorldServer overworld = srv.worldServers[0];
        TeamRegistry teams = TeamRegistry.get(overworld);
        ClaimRegistry claims = ClaimRegistry.get(overworld);
        Set<String> online = onlineUsernames(srv);

        for (ClaimTeam t : teams.all()) {
            if (t.name == null) continue;
            if (teamHasOnlineMember(t, online)) continue;
            if (now - t.lastActiveMillis < cutoff) continue;

            int disabled = 0;
            for (Claim c : claims.listByTeam(t.name)) {
                if (!c.chunkload) continue;
                c.chunkload = false;
                ChunkLoadManager.release(c.dim, c.chunkX, c.chunkZ);
                disabled++;
            }
            if (disabled > 0) {
                claims.markDirty();
                System.out.println("[ClaimTeam] Auto-disabled " + disabled + " chunk-load(s) for idle team '"
                        + t.name + "' (no member online for " + days + "+ days).");
            }
        }
    }

    private static boolean teamHasOnlineMember(ClaimTeam t, Set<String> online) {
        for (String name : online) {
            if (t.isMember(name)) return true;
        }
        return false;
    }

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.SERVER); }

    @Override
    public String getLabel() { return "ClaimTeamIdleChunkload"; }
}
