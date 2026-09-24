package com.nao.hdv.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.nao.hdv.Config;
import com.nao.hdv.HdvMod;
import com.nao.hdv.client.ClientPacketHandler;
import com.nao.hdv.data.AuctionData;
import com.nao.hdv.data.Listing;
import com.nao.hdv.eco.EssentialsEco;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Single network handler, routed by side. The SERVER is authoritative for every money/item change;
 * the client only sends intent. The client side just stashes the listing page for the GUI.
 */
public class PacketHandler implements IPacketHandler {

	// client -> server
	public static final byte PKT_REQUEST_LIST = 1; // int page, UTF search, byte kind
	public static final byte PKT_BUY          = 2; // long id, int qty
	public static final byte PKT_SELL         = 3; // int slot, int qty, double price
	public static final byte PKT_CANCEL       = 4; // long id

	// server -> client
	public static final byte PKT_LIST = 10;        // byte kind, int page, totalPages, total, count; count x row
	public static final byte PKT_OPEN = 11;        // (no body) open the GUI
	public static final byte PKT_NOTIFY = 12;      // UTF before, ItemStack unit, UTF after - client resolves the item name

	// list kind
	public static final byte KIND_BROWSE = 0;      // all listings (search applies)
	public static final byte KIND_MINE   = 1;      // only the requesting player's listings

	public static final int PAGE_SIZE = 10;

	/** Anti-spam: minimum gap between list requests of the same kind, per player (DoS guard). */
	private static final long MIN_LIST_INTERVAL_MS = 100L;
	private static final java.util.Map<String, long[]> lastListReq = new java.util.HashMap<String, long[]>();

	private static boolean allowListReq(String name, byte kind) {
		long now = System.currentTimeMillis();
		long[] ts = lastListReq.get(name);
		if (ts == null) { ts = new long[2]; lastListReq.put(name, ts); }
		int i = (kind == KIND_MINE) ? 1 : 0;
		if (now - ts[i] < MIN_LIST_INTERVAL_MS) return false;
		ts[i] = now;
		return true;
	}

	@Override
	public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
		if (packet == null || packet.data == null) return;
		if (!HdvMod.CHANNEL.equals(packet.channel)) return;
		if (p instanceof EntityPlayerMP) {
			handleServer((EntityPlayerMP) p, packet);
		} else if (FMLCommonHandler.instance().getSide().isClient()) {
			ClientPacketHandler.handle(packet);
		}
	}

	private void handleServer(EntityPlayerMP epm, Packet250CustomPayload packet) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
			byte type = in.readByte();
			if (type == PKT_REQUEST_LIST) {
				int page = in.readInt();
				String search = in.readUTF();
				byte kind = in.readByte();
				if (allowListReq(epm.username, kind)) sendList(epm, page, search, kind);
			} else if (type == PKT_BUY) {
				long id = in.readLong();
				int qty = in.readInt();
				handleBuy(epm, id, qty);
			} else if (type == PKT_SELL) {
				int slot = in.readInt();
				int qty = in.readInt();
				double price = in.readDouble();
				handleSell(epm, slot, qty, price);
			} else if (type == PKT_CANCEL) {
				long id = in.readLong();
				handleCancel(epm, id);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	// ---- server logic ----

	private static void msg(EntityPlayerMP epm, String s) {
		epm.sendChatToPlayer("§6[HDV]§r " + s);
	}

	/**
	 * Sends a chat line whose middle is an item name the CLIENT must resolve. Modded item names live in
	 * client-only .lang files, so the dedicated server can't translate them ("tile.machineBlock" / "?").
	 * We ship the item (NBT preserved) plus the surrounding text and let the receiver format the name.
	 */
	private static void msgItem(EntityPlayerMP epm, String before, ItemStack unit, String after) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_NOTIFY);
			dos.writeUTF(before == null ? "" : before);
			ItemCodec.write(dos, unit);
			dos.writeUTF(after == null ? "" : after);
		} catch (IOException e) {
			return;
		}
		sendTo(epm, bos.toByteArray());
	}

	private static boolean matches(Listing l, String search) {
		if (search == null || search.length() == 0) return true;
		String q = search.toLowerCase();
		if (l.seller != null && l.seller.toLowerCase().contains(q)) return true;
		if (l.unit != null) {
			try {
				// Display names of MODDED items don't translate on the dedicated server (client-only
				// .lang files), so also match the unlocalized name: "item.ingotCopper" still finds
				// "copper" even though the server only sees the key.
				String name = l.unit.getDisplayName();
				if (name != null && name.toLowerCase().contains(q)) return true;
				String key = l.unit.getItemName();
				if (key != null && key.toLowerCase().contains(q)) return true;
			} catch (Throwable ignored) {}
		}
		return false;
	}

	private void handleBuy(EntityPlayerMP epm, long id, int qty) {
		AuctionData data = AuctionData.get();
		Listing l = data.get(id);
		if (l == null) { msg(epm, "§cThat listing no longer exists."); return; }
		if (l.seller != null && l.seller.equalsIgnoreCase(epm.username)) {
			msg(epm, "§cYou cannot buy your own listing - use the Sell tab to cancel it.");
			return;
		}
		if (qty < 1) return;
		if (qty > l.quantity) qty = l.quantity;
		double total = qty * l.unitPrice;
		if (Double.isNaN(total) || Double.isInfinite(total) || total < 0) {
			msg(epm, "§cInvalid price on that listing.");
			return;
		}

		if (!EssentialsEco.has(epm.username, total)) {
			msg(epm, "§cNot enough money. You need " + EssentialsEco.format(total) + ".");
			return;
		}
		if (!InvHelper.canFit(epm, l.unit, qty)) {
			msg(epm, "§cNot enough inventory space - free up some slots and try again.");
			return;
		}
		if (!EssentialsEco.withdraw(epm.username, total)) {
			msg(epm, "§cPayment failed.");
			return;
		}
		double tax = total * (Config.saleTaxPercent / 100.0);
		double sellerGets = total - tax;
		EssentialsEco.deposit(l.seller, sellerGets);

		InvHelper.giveAll(epm, l.unit, qty);

		l.quantity -= qty;
		if (l.quantity <= 0) data.remove(l.id);
		else data.touch();

		msgItem(epm, "§aBought " + qty + "x ", l.unit, " for " + EssentialsEco.format(total) + ".");
		EntityPlayerMP seller = playerByName(l.seller);
		if (seller != null) {
			msgItem(seller, "§a" + epm.username + " bought " + qty + "x ", l.unit,
					" of your listing for " + EssentialsEco.format(sellerGets) + " (after tax).");
		}
	}

	private void handleSell(EntityPlayerMP epm, int slot, int qty, double price) {
		if (slot < 0 || slot >= 36) { msg(epm, "§cInvalid slot."); return; }
		ItemStack inSlot = epm.inventory.getStackInSlot(slot);
		if (inSlot == null) { msg(epm, "§cThat inventory slot is empty."); return; }
		if (qty < 1) { msg(epm, "§cQuantity must be at least 1."); return; }
		if (qty > inSlot.stackSize) qty = inSlot.stackSize;

		if (Double.isNaN(price) || Double.isInfinite(price) || price < Config.minPrice || price > Config.maxPrice) {
			msg(epm, "§cPrice must be between " + EssentialsEco.format(Config.minPrice)
					+ " and " + EssentialsEco.format(Config.maxPrice) + ".");
			return;
		}

		AuctionData data = AuctionData.get();
		if (data.countBySeller(epm.username) >= Config.maxActiveListings) {
			msg(epm, "§cYou have reached the maximum of " + Config.maxActiveListings + " active listings.");
			return;
		}
		double fee = Config.listingFee;
		if (fee > 0 && !EssentialsEco.has(epm.username, fee)) {
			msg(epm, "§cYou cannot afford the listing fee of " + EssentialsEco.format(fee) + ".");
			return;
		}

		// Take the item from the real server-side inventory FIRST, then charge the fee; refund the
		// item if the fee charge fails, so a failed payment can never consume the player's items.
		ItemStack removed = epm.inventory.decrStackSize(slot, qty);
		if (removed == null) { msg(epm, "§cCould not take the item."); return; }
		epm.inventoryContainer.detectAndSendChanges();

		if (fee > 0 && !EssentialsEco.withdraw(epm.username, fee)) {
			epm.inventory.addItemStackToInventory(removed);
			epm.inventoryContainer.detectAndSendChanges();
			msg(epm, "§cFailed to charge the listing fee.");
			return;
		}

		ItemStack unit = removed.copy();
		unit.stackSize = 1;

		long id = data.nextId();
		data.add(new Listing(id, epm.username, unit, qty, price, System.currentTimeMillis()));
		msgItem(epm, "§aListed " + qty + "x ", unit, " at " + EssentialsEco.format(price) + " each.");
	}

	private void handleCancel(EntityPlayerMP epm, long id) {
		AuctionData data = AuctionData.get();
		Listing l = data.get(id);
		if (l == null) { msg(epm, "§cThat listing no longer exists."); return; }
		if (l.seller == null || !l.seller.equalsIgnoreCase(epm.username)) {
			msg(epm, "§cThat is not your listing.");
			return;
		}
		if (!InvHelper.canFit(epm, l.unit, l.quantity)) {
			msg(epm, "§cNot enough inventory space to reclaim " + l.quantity + " items - free up slots first.");
			return;
		}
		InvHelper.giveAll(epm, l.unit, l.quantity);
		data.remove(id);
		msg(epm, "§aListing cancelled - " + l.quantity + " item(s) returned.");
	}

	private static EntityPlayerMP playerByName(String name) {
		if (name == null) return null;
		try {
			return net.minecraft.server.MinecraftServer.getServer()
					.getConfigurationManager().getPlayerForUsername(name);
		} catch (Throwable t) {
			return null;
		}
	}

	// ---- list paging (server -> client) ----

	public static void sendList(EntityPlayerMP epm, int page, String search, byte kind) {
		List<Listing> all = AuctionData.get().snapshot();
		List<Listing> filtered = new ArrayList<Listing>();
		for (Listing l : all) {
			if (l.unit == null || l.quantity <= 0) continue;
			if (kind == KIND_MINE) {
				if (l.seller != null && l.seller.equalsIgnoreCase(epm.username)) filtered.add(l);
			} else if (matches(l, search)) {
				filtered.add(l);
			}
		}
		Collections.sort(filtered, new Comparator<Listing>() {
			@Override
			public int compare(Listing a, Listing b) {
				return (a.id < b.id) ? -1 : (a.id > b.id ? 1 : 0);
			}
		});

		int total = filtered.size();
		int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
		if (page < 0) page = 0;
		if (page >= totalPages) page = totalPages - 1;
		int from = page * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, total);

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_LIST);
			dos.writeByte(kind);
			dos.writeInt(page);
			dos.writeInt(totalPages);
			dos.writeInt(total);
			dos.writeInt(to - from);
			for (int i = from; i < to; i++) {
				Listing l = filtered.get(i);
				dos.writeLong(l.id);
				dos.writeUTF(l.seller == null ? "" : l.seller);
				dos.writeInt(l.quantity);
				dos.writeDouble(l.unitPrice);
				ItemCodec.write(dos, l.unit);
			}
		} catch (IOException e) {
			return;
		}
		sendTo(epm, bos.toByteArray());
	}

	public static void sendOpen(EntityPlayerMP epm) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_OPEN);
		} catch (IOException e) {
			return;
		}
		sendTo(epm, bos.toByteArray());
	}

	private static void sendTo(EntityPlayerMP epm, byte[] data) {
		Packet250CustomPayload pkt = new Packet250CustomPayload();
		pkt.channel = HdvMod.CHANNEL;
		pkt.data = data;
		pkt.length = data.length;
		PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);
	}

	// ---- builders used by the client GUI ----

	public static byte[] buildRequestList(int page, String search, byte kind) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_REQUEST_LIST);
			dos.writeInt(page);
			dos.writeUTF(search == null ? "" : search);
			dos.writeByte(kind);
		} catch (IOException ignored) {}
		return bos.toByteArray();
	}

	public static byte[] buildBuy(long id, int qty) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_BUY);
			dos.writeLong(id);
			dos.writeInt(qty);
		} catch (IOException ignored) {}
		return bos.toByteArray();
	}

	public static byte[] buildSell(int slot, int qty, double price) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_SELL);
			dos.writeInt(slot);
			dos.writeInt(qty);
			dos.writeDouble(price);
		} catch (IOException ignored) {}
		return bos.toByteArray();
	}

	public static byte[] buildCancel(long id) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeByte(PKT_CANCEL);
			dos.writeLong(id);
		} catch (IOException ignored) {}
		return bos.toByteArray();
	}
}
