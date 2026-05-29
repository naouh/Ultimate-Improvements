package com.nao.hdv.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** One item type offered for sale by a player. The held stack carries full (modded) NBT. */
public class Listing {

	public long id;
	public String seller;
	/** Template stack, amount == 1. */
	public ItemStack unit;
	/** Items still available. */
	public int quantity;
	public double unitPrice;
	public long createdAt;

	public Listing() {}

	public Listing(long id, String seller, ItemStack unit, int quantity, double unitPrice, long createdAt) {
		this.id = id;
		this.seller = seller;
		this.unit = unit;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.createdAt = createdAt;
	}

	public double total() {
		return quantity * unitPrice;
	}

	public void writeTo(NBTTagCompound nbt) {
		nbt.setLong("id", id);
		nbt.setString("seller", seller == null ? "" : seller);
		nbt.setInteger("qty", quantity);
		nbt.setDouble("price", unitPrice);
		nbt.setLong("ts", createdAt);
		NBTTagCompound item = new NBTTagCompound();
		if (unit != null) unit.writeToNBT(item);
		nbt.setCompoundTag("item", item);
	}

	public static Listing readFrom(NBTTagCompound nbt) {
		Listing l = new Listing();
		l.id = nbt.getLong("id");
		l.seller = nbt.getString("seller");
		l.quantity = nbt.getInteger("qty");
		l.unitPrice = nbt.getDouble("price");
		l.createdAt = nbt.getLong("ts");
		l.unit = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("item"));
		return l;
	}
}
