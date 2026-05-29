package com.nao.hdv.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Serializes an ItemStack to/from a data stream via its full NBT compound, so modded item NBT is
 * preserved exactly (over the network and in save data). amount is normalised to 1 on write.
 */
public final class ItemCodec {
	private ItemCodec() {}

	public static void write(DataOutputStream out, ItemStack stack) throws IOException {
		NBTTagCompound tag = new NBTTagCompound();
		ItemStack one = stack.copy();
		one.stackSize = 1;
		one.writeToNBT(tag);
		CompressedStreamTools.write(tag, out);
	}

	public static ItemStack read(DataInputStream in) throws IOException {
		NBTTagCompound tag = CompressedStreamTools.read(in);
		return ItemStack.loadItemStackFromNBT(tag);
	}
}
