package com.nao.neiae;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Server-side handler: receives the recipe packet from the client and pulls
 * each ingredient out of the ME network into the open Crafting Terminal's
 * 3x3 matrix.
 *
 * <p>Heavy use of reflection here because every AE rv9 type we touch
 * ({@code ContainerCraftingTerminal}, {@code NetworkedIMEI},
 * {@code SlotCraftingMatrix}, the {@code IMEInventory} interface) is in the
 * AE jar whose own bytecode references obfuscated MC types — Voldeloom's
 * compile classpath can't satisfy those references, so we look them up by
 * name at runtime where everything is already loaded.
 */
public class ServerHandler implements IPacketHandler {

    /** Cached reflective handles, resolved lazily on first packet. */
    private static volatile boolean ready = false;
    private static Class<?>  cCraftingTerminalCls;
    private static Class<?>  cSlotMatrixCls;
    private static Field     fImeiinv;
    private static Method    mExtractItems;
    private static Method    mAddItems;
    private static Method    mDetectChanges;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        if (packet == null || packet.data == null) return;
        if (!NeiAeMod.CHANNEL.equals(packet.channel)) return;
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP epm = (EntityPlayerMP) player;

        try {
            if (!ensureReady()) return;

            NBTTagCompound payload = CompressedStreamTools.decompress(packet.data);
            NBTTagList ingredients = payload.getTagList("ingredients");
            if (ingredients == null || ingredients.tagCount() == 0) return;

            Container container = epm.openContainer;
            if (container == null || !cCraftingTerminalCls.isInstance(container)) {
                epm.sendChatToPlayer("[NeiAe] Open the ME Crafting Terminal first.");
                return;
            }

            Object imei = fImeiinv.get(container);
            if (imei == null) {
                epm.sendChatToPlayer("[NeiAe] ME network not available.");
                return;
            }

            List<Slot> matrixSlots = collectMatrixSlots(container);
            if (matrixSlots.size() < 9) {
                epm.sendChatToPlayer("[NeiAe] Could not find 9 crafting-matrix slots.");
                return;
            }

            int filled = 0;
            int missing = 0;
            for (int i = 0; i < ingredients.tagCount(); i++) {
                NBTTagCompound tag = (NBTTagCompound) ingredients.tagAt(i);
                int slotIdx = tag.getInteger("slot");
                if (slotIdx < 0 || slotIdx >= matrixSlots.size()) continue;

                ItemStack requested = ItemStack.loadItemStackFromNBT(tag);
                if (requested == null) continue;
                requested.stackSize = 1;

                ItemStack extracted = (ItemStack) mExtractItems.invoke(imei, requested);
                if (extracted == null || extracted.stackSize <= 0) {
                    missing++;
                    continue;
                }

                Slot dst = matrixSlots.get(slotIdx);
                ItemStack existing = dst.getStack();

                // If the slot already holds something, try to push it back into
                // the ME network first. If that fails (or the network can't
                // absorb it all), return what we just extracted and skip — better
                // to leave both items intact than silently destroy the original.
                if (existing != null && existing.stackSize > 0) {
                    if (!returnToNetwork(imei, existing)) {
                        returnToNetwork(imei, extracted);
                        missing++;
                        continue;
                    }
                }

                dst.putStack(extracted);
                filled++;
            }

            if (mDetectChanges != null) {
                try { mDetectChanges.invoke(container); } catch (Throwable ignore) {}
            }

            String msg = "[NeiAe] " + filled + " ingredients placed";
            if (missing > 0) msg += ", " + missing + " missing from ME";
            epm.sendChatToPlayer(msg);
        } catch (Throwable t) {
            System.err.println("[NeiAe] Server packet handler failed:");
            t.printStackTrace();
        }
    }

    /**
     * Returns true if the stack was fully accepted by the ME network. If
     * {@code addItems} isn't available (older AE), throws, or returns a
     * non-empty leftover, the caller should treat the slot as unsafe to
     * overwrite — the matrix is left untouched.
     */
    private static boolean returnToNetwork(Object imei, ItemStack stack) {
        if (mAddItems == null) return false;
        try {
            Object leftover = mAddItems.invoke(imei, stack);
            if (leftover instanceof ItemStack && ((ItemStack) leftover).stackSize > 0) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[NeiAe] addItems failed:");
            t.printStackTrace();
            return false;
        }
    }

    private static synchronized boolean ensureReady() {
        if (ready) return true;
        try {
            cCraftingTerminalCls  = Class.forName("appeng.me.container.ContainerCraftingTerminal");
            cSlotMatrixCls        = Class.forName("appeng.slot.SlotCraftingMatrix");
            Class<?> cMEInventory = Class.forName("appeng.api.me.util.IMEInventory");
            fImeiinv              = Class.forName("appeng.me.container.ContainerTerminal").getField("imeiinv");
            mExtractItems         = cMEInventory.getMethod("extractItems", ItemStack.class);
            // addItems is optional — if AE drops it, the slot-freeing path
            // refuses to overwrite occupied slots, which is the safe default.
            try {
                mAddItems = cMEInventory.getMethod("addItems", ItemStack.class);
            } catch (NoSuchMethodException ignore) {
                mAddItems = null;
            }
            try {
                mDetectChanges = Container.class.getMethod("detectAndSendChanges");
            } catch (NoSuchMethodException ignore) {
                mDetectChanges = null;
            }
            ready = true;
            return true;
        } catch (Throwable t) {
            System.err.println("[NeiAe] Reflection setup failed:");
            t.printStackTrace();
            return false;
        }
    }

    /** Returns matrix slots in stable order by their inventory slot index (0..8). */
    @SuppressWarnings("unchecked")
    private static List<Slot> collectMatrixSlots(Container container) {
        List<Slot> matrix = new ArrayList<Slot>();
        for (Object o : container.inventorySlots) {
            if (cSlotMatrixCls.isInstance(o)) matrix.add((Slot) o);
        }
        Collections.sort(matrix, new Comparator<Slot>() {
            @Override
            public int compare(Slot a, Slot b) {
                return a.getSlotIndex() - b.getSlotIndex();
            }
        });
        return matrix;
    }
}
