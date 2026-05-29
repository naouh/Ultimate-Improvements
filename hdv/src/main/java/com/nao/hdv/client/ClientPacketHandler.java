package com.nao.hdv.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import com.nao.hdv.network.ItemCodec;
import com.nao.hdv.network.PacketHandler;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Client-side stash of the latest listing pages, read by {@link HdvGui} each frame. Two buffers:
 * the browse page (all listings) and the "mine" page (the player's own listings).
 */
public final class ClientPacketHandler {

	private ClientPacketHandler() {}

	public static final class Row {
		public long id;
		public String seller;
		public ItemStack stack;
		public int qty;
		public double price;
	}

	public static volatile Row[] browseRows = new Row[0];
	public static volatile int browsePage = 0, browseTotalPages = 1, browseTotal = 0;

	public static volatile Row[] mineRows = new Row[0];
	public static volatile int minePage = 0, mineTotalPages = 1, mineTotal = 0;

	public static volatile boolean openRequested = false;
	public static volatile long lastUpdateMs = 0;

	public static void handle(Packet250CustomPayload packet) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
			byte type = in.readByte();
			if (type == PacketHandler.PKT_OPEN) {
				openRequested = true;
				return;
			}
			if (type == PacketHandler.PKT_LIST) {
				byte kind = in.readByte();
				int page = in.readInt();
				int totalPages = in.readInt();
				int total = in.readInt();
				int count = in.readInt();
				Row[] rows = new Row[count];
				for (int i = 0; i < count; i++) {
					Row r = new Row();
					r.id = in.readLong();
					r.seller = in.readUTF();
					r.qty = in.readInt();
					r.price = in.readDouble();
					r.stack = ItemCodec.read(in);
					rows[i] = r;
				}
				if (kind == PacketHandler.KIND_MINE) {
					mineRows = rows;
					minePage = page;
					mineTotalPages = totalPages;
					mineTotal = total;
				} else {
					browseRows = rows;
					browsePage = page;
					browseTotalPages = totalPages;
					browseTotal = total;
				}
				lastUpdateMs = System.currentTimeMillis();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
