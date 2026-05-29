package com.nao.hdv.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * All listings, persisted as world save data on the overworld (world/data/hdv_auctions.dat). Global
 * for the whole server. Accessed only on the main server thread, so no locking is needed.
 */
public class AuctionData extends WorldSavedData {

	private static final String DATA_NAME = "hdv_auctions";

	private final Map<Long, Listing> byId = new LinkedHashMap<Long, Listing>();
	private long nextId = 1L;

	public AuctionData(String name) {
		super(name);
	}

	public static AuctionData get() {
		World w = MinecraftServer.getServer().worldServers[0];
		AuctionData data = (AuctionData) w.mapStorage.loadData(AuctionData.class, DATA_NAME);
		if (data == null) {
			data = new AuctionData(DATA_NAME);
			w.mapStorage.setData(DATA_NAME, data);
		}
		return data;
	}

	public long nextId() {
		long id = nextId++;
		markDirty();
		return id;
	}

	public void add(Listing l) {
		byId.put(l.id, l);
		markDirty();
	}

	public Listing get(long id) {
		return byId.get(id);
	}

	/** Mark dirty after mutating a listing in place (e.g. decrementing quantity). */
	public void touch() {
		markDirty();
	}

	public Listing remove(long id) {
		Listing r = byId.remove(id);
		if (r != null) markDirty();
		return r;
	}

	public List<Listing> snapshot() {
		return new ArrayList<Listing>(byId.values());
	}

	public int countBySeller(String seller) {
		int c = 0;
		for (Listing l : byId.values()) {
			if (l.seller != null && l.seller.equalsIgnoreCase(seller)) c++;
		}
		return c;
	}

	public List<Listing> bySeller(String seller) {
		List<Listing> out = new ArrayList<Listing>();
		for (Listing l : byId.values()) {
			if (l.seller != null && l.seller.equalsIgnoreCase(seller)) out.add(l);
		}
		return out;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		byId.clear();
		nextId = nbt.getLong("nextId");
		if (nextId < 1) nextId = 1;
		NBTTagList list = nbt.getTagList("listings");
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound c = (NBTTagCompound) list.tagAt(i);
			Listing l = Listing.readFrom(c);
			if (l.unit == null || l.quantity <= 0) continue; // skip corrupt entries
			byId.put(l.id, l);
			if (l.id >= nextId) nextId = l.id + 1;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setLong("nextId", nextId);
		NBTTagList list = new NBTTagList();
		for (Listing l : byId.values()) {
			NBTTagCompound c = new NBTTagCompound();
			l.writeTo(c);
			list.appendTag(c);
		}
		nbt.setTag("listings", list);
	}
}
