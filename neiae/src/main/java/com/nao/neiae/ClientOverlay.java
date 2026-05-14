package com.nao.neiae;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.List;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Runs on the client side when NEI invokes our overlay handler. Translates
 * the ingredient list (each a {@code codechicken.nei.PositionedStack}) into
 * a custom NBT packet sent to the server.
 *
 * <p>{@code PositionedStack}'s relevant public fields are {@code relx},
 * {@code rely} (NEI display position), and {@code items} (an
 * {@code ItemStack[]} of valid alternatives, of which we take the first).
 * NEI lays out a 3x3 recipe with top-left at (25, 6) and 18-pixel spacing,
 * so the matrix slot index is {@code ((rely-6)/18)*3 + (relx-25)/18}.
 */
final class ClientOverlay {

    private ClientOverlay() {}

    static void handleClick(Object gui, Object ingredientsObj, boolean shift) {
        System.out.println("[NeiAe] handleClick called, shift=" + shift
                + ", ingredients=" + (ingredientsObj == null ? "null" :
                    (ingredientsObj instanceof List ? ((List<?>) ingredientsObj).size() + " items" :
                        ingredientsObj.getClass().getName())));
        try {
            if (!(ingredientsObj instanceof List)) return;
            List<?> ingredients = (List<?>) ingredientsObj;

            // PositionedStack reflection — fields are stable in NEI 1.4.x.
            Class<?> psCls = Class.forName("codechicken.nei.PositionedStack");
            Field fRelX  = psCls.getField("relx");
            Field fRelY  = psCls.getField("rely");
            Field fItems = psCls.getField("items");

            NBTTagCompound payload = new NBTTagCompound();
            NBTTagList ingrList = new NBTTagList();

            for (Object ps : ingredients) {
                if (ps == null) continue;
                int relx = fRelX.getInt(ps);
                int rely = fRelY.getInt(ps);
                Object items = fItems.get(ps);
                if (!(items instanceof Object[])) continue;
                Object[] arr = (Object[]) items;
                if (arr.length == 0 || arr[0] == null) continue;

                int slotIdx = ((rely - 6) / 18) * 3 + ((relx - 25) / 18);
                if (slotIdx < 0 || slotIdx > 8) continue;

                // arr[0] is an ItemStack (obfuscated 'ur' at runtime, which is
                // also what 'ItemStack' compiles to after Voldeloom remap).
                ItemStack stack = (ItemStack) arr[0];
                if (stack == null) continue;

                NBTTagCompound itemTag = new NBTTagCompound();
                stack.writeToNBT(itemTag);
                itemTag.setInteger("slot", slotIdx);
                ingrList.appendTag(itemTag);
            }

            if (ingrList.tagCount() == 0) return;
            payload.setTag("ingredients", ingrList);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(payload, new DataOutputStream(bos));
            byte[] data = bos.toByteArray();

            Packet250CustomPayload pkt = new Packet250CustomPayload();
            pkt.channel = NeiAeMod.CHANNEL;
            pkt.data    = data;
            pkt.length  = data.length;
            PacketDispatcher.sendPacketToServer(pkt);

            System.out.println("[NeiAe] Sent recipe packet, " + ingrList.tagCount() + " ingredients");
        } catch (Throwable t) {
            System.err.println("[NeiAe] handleClick failed:");
            t.printStackTrace();
        }
    }
}
