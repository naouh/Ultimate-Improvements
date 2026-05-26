package com.nao.claimteam.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class ClaimRegistry extends WorldSavedData {

    private static final String DATA_NAME = "claimteam_claims";

    /** key = packed (dim, chunkX, chunkZ). */
    private final Map<Long, Claim> byChunk = new HashMap<Long, Claim>();
    /** key = teamName lowercased -> claims belonging to that team (across all dims). */
    private final Map<String, List<Claim>> byTeam = new HashMap<String, List<Claim>>();

    public ClaimRegistry(String name) {
        super(name);
    }

    public static ClaimRegistry get(World world) {
        ClaimRegistry data = (ClaimRegistry) world.mapStorage.loadData(ClaimRegistry.class, DATA_NAME);
        if (data == null) {
            data = new ClaimRegistry(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** Pack (dim, cx, cz) into a single long. dim is 8 bits (signed), cx/cz are 28 bits each. */
    public static long key(int dim, int cx, int cz) {
        long d = ((long) dim) & 0xFFL;
        long x = ((long) cx) & 0xFFFFFFFL;
        long z = ((long) cz) & 0xFFFFFFFL;
        return (d << 56) | (x << 28) | z;
    }

    public Claim find(int dim, int cx, int cz) {
        return byChunk.get(key(dim, cx, cz));
    }

    public void put(Claim c) {
        long k = key(c.dim, c.chunkX, c.chunkZ);
        Claim prev = byChunk.get(k);
        if (prev != null) removeFromTeamIndex(prev);
        byChunk.put(k, c);
        addToTeamIndex(c);
        markDirty();
    }

    public Claim remove(int dim, int cx, int cz) {
        Claim r = byChunk.remove(key(dim, cx, cz));
        if (r != null) {
            removeFromTeamIndex(r);
            markDirty();
        }
        return r;
    }

    private void addToTeamIndex(Claim c) {
        if (c.teamName == null) return;
        String k = c.teamName.toLowerCase();
        List<Claim> list = byTeam.get(k);
        if (list == null) {
            list = new ArrayList<Claim>();
            byTeam.put(k, list);
        }
        list.add(c);
    }

    private void removeFromTeamIndex(Claim c) {
        if (c.teamName == null) return;
        List<Claim> list = byTeam.get(c.teamName.toLowerCase());
        if (list != null) {
            list.remove(c);
            if (list.isEmpty()) byTeam.remove(c.teamName.toLowerCase());
        }
    }

    public List<Claim> listByTeam(String teamName) {
        if (teamName == null) return new ArrayList<Claim>();
        List<Claim> list = byTeam.get(teamName.toLowerCase());
        if (list == null) return new ArrayList<Claim>();
        return new ArrayList<Claim>(list);
    }

    public int countByTeam(String teamName) {
        if (teamName == null) return 0;
        List<Claim> list = byTeam.get(teamName.toLowerCase());
        return list == null ? 0 : list.size();
    }

    public int countChunkloadByTeam(String teamName) {
        if (teamName == null) return 0;
        List<Claim> list = byTeam.get(teamName.toLowerCase());
        if (list == null) return 0;
        int n = 0;
        for (Claim c : list) if (c.chunkload) n++;
        return n;
    }

    /** Snapshot of all claims across dims — safe to iterate while mutating registry. */
    public List<Claim> snapshot() {
        return new ArrayList<Claim>(byChunk.values());
    }

    /** Reassign all claims of {@code oldTeam} to {@code newTeam} (e.g. on rename). */
    public void renameTeam(String oldTeam, String newTeam) {
        if (oldTeam == null || newTeam == null) return;
        List<Claim> list = byTeam.remove(oldTeam.toLowerCase());
        if (list == null) return;
        for (Claim c : list) c.teamName = newTeam;
        byTeam.put(newTeam.toLowerCase(), list);
        markDirty();
    }

    /** Drop all claims of a team. Returns the removed claims so the caller can release tickets. */
    public List<Claim> dropTeam(String teamName) {
        if (teamName == null) return new ArrayList<Claim>();
        List<Claim> list = byTeam.remove(teamName.toLowerCase());
        if (list == null) return new ArrayList<Claim>();
        for (Claim c : list) byChunk.remove(key(c.dim, c.chunkX, c.chunkZ));
        markDirty();
        return list;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        byChunk.clear();
        byTeam.clear();
        NBTTagList list = nbt.getTagList("claims");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = (NBTTagCompound) list.tagAt(i);
            Claim cl = new Claim();
            cl.readFrom(c);
            byChunk.put(key(cl.dim, cl.chunkX, cl.chunkZ), cl);
            addToTeamIndex(cl);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Claim c : byChunk.values()) {
            NBTTagCompound t = new NBTTagCompound();
            c.writeTo(t);
            list.appendTag(t);
        }
        nbt.setTag("claims", list);
    }
}
