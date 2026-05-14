package com.cagecontrol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class CageRegistry extends WorldSavedData {

    private static final String DATA_NAME = "CageControl";

    private final Map<String, CageData> byKey = new HashMap<String, CageData>();
    private final Map<Long, CageData>   byPos = new HashMap<Long, CageData>();

    public CageRegistry(String name) {
        super(name);
    }

    public static CageRegistry get(World world) {
        CageRegistry data = (CageRegistry) world.mapStorage.loadData(CageRegistry.class, DATA_NAME);
        if (data == null) {
            data = new CageRegistry(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    private static long posKey(int x, int y, int z) {
        return (((long) x) & 0x3FFFFFFL) | ((((long) z) & 0x3FFFFFFL) << 26) | ((((long) y) & 0xFFFL) << 52);
    }

    public CageData findByPos(int x, int y, int z) {
        return byPos.get(posKey(x, y, z));
    }

    public CageData findByOwnerName(String owner, String name) {
        return byKey.get((owner.toLowerCase() + "/" + name.toLowerCase()));
    }

    public boolean nameTaken(String owner, String name) {
        return byKey.containsKey(owner.toLowerCase() + "/" + name.toLowerCase());
    }

    public void put(CageData d) {
        byKey.put(d.key(), d);
        byPos.put(posKey(d.x, d.y, d.z), d);
        markDirty();
    }

    public void remove(CageData d) {
        if (d == null) return;
        byKey.remove(d.key());
        byPos.remove(posKey(d.x, d.y, d.z));
        markDirty();
    }

    public List<CageData> listOwnedBy(String owner) {
        List<CageData> r = new ArrayList<CageData>();
        for (CageData d : byKey.values()) {
            if (d.owner != null && d.owner.equalsIgnoreCase(owner)) r.add(d);
        }
        return r;
    }

    /** Cages where the player is owner OR co-owner. */
    public List<CageData> listControllableBy(String player) {
        List<CageData> r = new ArrayList<CageData>();
        for (CageData d : byKey.values()) if (d.canControl(player)) r.add(d);
        return r;
    }

    /** Finds a cage named {@code name} the player can control. Prefers cages they own. */
    public CageData findControllable(String player, String name) {
        if (name == null) return null;
        CageData own = byKey.get(player.toLowerCase() + "/" + name.toLowerCase());
        if (own != null) return own;
        for (CageData d : byKey.values()) {
            if (d.name != null && d.name.equalsIgnoreCase(name) && d.canControl(player)) return d;
        }
        return null;
    }

    /** Snapshot of all entries — safe to iterate while removing via {@link #remove}. */
    public List<CageData> snapshot() {
        return new ArrayList<CageData>(byKey.values());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        byKey.clear();
        byPos.clear();
        NBTTagList list = nbt.getTagList("cages");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = (NBTTagCompound) list.tagAt(i);
            CageData d = new CageData();
            d.readFrom(c);
            if (d.owner == null || d.name == null) continue;
            byKey.put(d.key(), d);
            byPos.put(posKey(d.x, d.y, d.z), d);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (CageData d : byKey.values()) {
            NBTTagCompound c = new NBTTagCompound();
            d.writeTo(c);
            list.appendTag(c);
        }
        nbt.setTag("cages", list);
    }
}
