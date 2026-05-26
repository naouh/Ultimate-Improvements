package com.nao.claimteam.data;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public class ClaimTeam {
    public String name;
    public String owner;
    public final Set<String> members = new LinkedHashSet<String>();
    public final Set<String> allies = new LinkedHashSet<String>();

    public ClaimTeam() {}

    public ClaimTeam(String name, String owner) {
        this.name = name;
        this.owner = owner == null ? null : owner.toLowerCase();
    }

    public boolean isOwner(String player) {
        return owner != null && player != null && owner.equalsIgnoreCase(player);
    }

    /** Owner counts as a member for permission purposes. */
    public boolean isMember(String player) {
        if (player == null) return false;
        if (isOwner(player)) return true;
        return members.contains(player.toLowerCase());
    }

    public boolean isAlly(String player) {
        return player != null && allies.contains(player.toLowerCase());
    }

    public boolean addMember(String player) {
        if (player == null) return false;
        if (isOwner(player)) return false;
        return members.add(player.toLowerCase());
    }

    public boolean removeMember(String player) {
        if (player == null) return false;
        return members.remove(player.toLowerCase());
    }

    public boolean addAlly(String player) {
        if (player == null) return false;
        return allies.add(player.toLowerCase());
    }

    public boolean removeAlly(String player) {
        if (player == null) return false;
        return allies.remove(player.toLowerCase());
    }

    public void writeTo(NBTTagCompound nbt) {
        nbt.setString("name", name);
        nbt.setString("owner", owner);
        NBTTagList mList = new NBTTagList();
        for (String m : members) mList.appendTag(new NBTTagString("m", m));
        nbt.setTag("members", mList);
        NBTTagList aList = new NBTTagList();
        for (String a : allies) aList.appendTag(new NBTTagString("a", a));
        nbt.setTag("allies", aList);
    }

    public void readFrom(NBTTagCompound nbt) {
        name = nbt.getString("name");
        owner = nbt.getString("owner");
        members.clear();
        if (nbt.hasKey("members")) {
            NBTTagList list = nbt.getTagList("members");
            for (int i = 0; i < list.tagCount(); i++) {
                Object tag = list.tagAt(i);
                if (tag instanceof NBTTagString) members.add(((NBTTagString) tag).data);
            }
        }
        allies.clear();
        if (nbt.hasKey("allies")) {
            NBTTagList list = nbt.getTagList("allies");
            for (int i = 0; i < list.tagCount(); i++) {
                Object tag = list.tagAt(i);
                if (tag instanceof NBTTagString) allies.add(((NBTTagString) tag).data);
            }
        }
    }
}
