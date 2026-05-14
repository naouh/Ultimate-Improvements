package com.cagecontrol;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public class CageData {
    public int x, y, z;
    public int dim;
    /** Primary owner — the player who registered the cage. Only they can manage co-owners. */
    public String owner;
    public String name;
    public String mobType;
    public boolean special;
    public int tier;
    public boolean active;
    /** Additional players granted start/stop/list rights (lowercased). */
    public final Set<String> coOwners = new LinkedHashSet<String>();

    public CageData() {}

    public CageData(int x, int y, int z, int dim, String owner, String name,
                    String mobType, boolean special, int tier) {
        this.x = x; this.y = y; this.z = z; this.dim = dim;
        this.owner = owner; this.name = name;
        this.mobType = mobType; this.special = special; this.tier = tier;
        this.active = false;
    }

    public String key() {
        return owner.toLowerCase() + "/" + name.toLowerCase();
    }

    public boolean isOwner(String playerName) {
        return owner != null && owner.equalsIgnoreCase(playerName);
    }

    public boolean canControl(String playerName) {
        if (playerName == null) return false;
        if (isOwner(playerName)) return true;
        return coOwners.contains(playerName.toLowerCase());
    }

    public boolean addCoOwner(String playerName) {
        if (playerName == null) return false;
        if (isOwner(playerName)) return false;
        return coOwners.add(playerName.toLowerCase());
    }

    public boolean removeCoOwner(String playerName) {
        if (playerName == null) return false;
        return coOwners.remove(playerName.toLowerCase());
    }

    public void writeTo(NBTTagCompound nbt) {
        nbt.setInteger("x", x);
        nbt.setInteger("y", y);
        nbt.setInteger("z", z);
        nbt.setInteger("dim", dim);
        nbt.setString("owner", owner);
        nbt.setString("name", name);
        nbt.setString("mobType", mobType);
        nbt.setBoolean("special", special);
        nbt.setInteger("tier", tier);
        nbt.setBoolean("active", active);
        NBTTagList list = new NBTTagList();
        for (String co : coOwners) list.appendTag(new NBTTagString("co", co));
        nbt.setTag("coOwners", list);
    }

    public void readFrom(NBTTagCompound nbt) {
        x = nbt.getInteger("x");
        y = nbt.getInteger("y");
        z = nbt.getInteger("z");
        dim = nbt.getInteger("dim");
        owner = nbt.getString("owner");
        name = nbt.getString("name");
        mobType = nbt.getString("mobType");
        special = nbt.getBoolean("special");
        tier = nbt.getInteger("tier");
        active = nbt.getBoolean("active");
        coOwners.clear();
        if (nbt.hasKey("coOwners")) {
            NBTTagList list = nbt.getTagList("coOwners");
            for (int i = 0; i < list.tagCount(); i++) {
                Object tag = list.tagAt(i);
                if (tag instanceof NBTTagString) coOwners.add(((NBTTagString) tag).data);
            }
        }
    }
}
