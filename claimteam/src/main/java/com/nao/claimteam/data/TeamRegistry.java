package com.nao.claimteam.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class TeamRegistry extends WorldSavedData {

    private static final String DATA_NAME = "claimteam_teams";

    private final Map<String, ClaimTeam> byName = new HashMap<String, ClaimTeam>();
    /** player username (lowercased) -> team they belong to as owner or member */
    private final Map<String, ClaimTeam> byPlayer = new HashMap<String, ClaimTeam>();

    public TeamRegistry(String name) {
        super(name);
    }

    public static TeamRegistry get(World world) {
        TeamRegistry data = (TeamRegistry) world.mapStorage.loadData(TeamRegistry.class, DATA_NAME);
        if (data == null) {
            data = new TeamRegistry(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    public ClaimTeam getByName(String name) {
        if (name == null) return null;
        return byName.get(name.toLowerCase());
    }

    public ClaimTeam getByPlayer(String player) {
        if (player == null) return null;
        return byPlayer.get(player.toLowerCase());
    }

    public boolean nameTaken(String name) {
        return name != null && byName.containsKey(name.toLowerCase());
    }

    public ClaimTeam create(String name, String owner) {
        if (name == null || owner == null) return null;
        if (nameTaken(name)) return null;
        if (getByPlayer(owner) != null) return null;
        ClaimTeam t = new ClaimTeam(name, owner);
        byName.put(name.toLowerCase(), t);
        byPlayer.put(owner.toLowerCase(), t);
        markDirty();
        return t;
    }

    /** Add a member. Returns true on success. */
    public boolean addMember(ClaimTeam team, String player) {
        if (team == null || player == null) return false;
        if (getByPlayer(player) != null) return false;
        if (team.addMember(player)) {
            byPlayer.put(player.toLowerCase(), team);
            markDirty();
            return true;
        }
        return false;
    }

    public boolean removeMember(ClaimTeam team, String player) {
        if (team == null || player == null) return false;
        if (team.removeMember(player)) {
            byPlayer.remove(player.toLowerCase());
            markDirty();
            return true;
        }
        return false;
    }

    /** Remove the team and unregister all members. Returns the removed team. */
    public ClaimTeam disband(String name) {
        if (name == null) return null;
        ClaimTeam t = byName.remove(name.toLowerCase());
        if (t == null) return null;
        if (t.owner != null) byPlayer.remove(t.owner.toLowerCase());
        for (String m : t.members) byPlayer.remove(m);
        markDirty();
        return t;
    }

    public List<ClaimTeam> all() {
        return new ArrayList<ClaimTeam>(byName.values());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        byName.clear();
        byPlayer.clear();
        NBTTagList list = nbt.getTagList("teams");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = (NBTTagCompound) list.tagAt(i);
            ClaimTeam t = new ClaimTeam();
            t.readFrom(c);
            if (t.name == null || t.owner == null) continue;
            byName.put(t.name.toLowerCase(), t);
            byPlayer.put(t.owner.toLowerCase(), t);
            for (String m : t.members) byPlayer.put(m, t);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (ClaimTeam t : byName.values()) {
            NBTTagCompound c = new NBTTagCompound();
            t.writeTo(c);
            list.appendTag(c);
        }
        nbt.setTag("teams", list);
    }
}
