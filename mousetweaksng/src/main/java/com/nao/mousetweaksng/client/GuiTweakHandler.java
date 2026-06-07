package com.nao.mousetweaksng.client;

import java.util.EnumSet;

import com.nao.mousetweaksng.Config;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The whole mod. Runs once per render frame while a container GUI is open and, when the player
 * holds a mouse button, re-issues the appropriate window-click on the slot under the cursor.
 *
 * It acts at most once per <em>newly entered</em> slot ({@link #lastSlot}) — exactly like the
 * original, so holding the button on one slot doesn't spam it — and additionally paces those
 * automated clicks by {@link Config#minClickGapMs} so 1.4.7's one-transaction-at-a-time
 * window-click protocol confirms each click before the next. That pacing is what stops the visual
 * inventory desync the old jar caused (worse under TickThreading).
 */
public class GuiTweakHandler implements ITickHandler {

    // LWJGL mouse buttons.
    private static final int MOUSE_LEFT  = 0;
    private static final int MOUSE_RIGHT = 1;

    // PlayerControllerMP.windowClick modes.
    private static final int MODE_PICKUP    = 0; // normal left/right click
    private static final int MODE_QUICKMOVE = 1; // shift quick-move

    /** The slot we last considered, so we only act once per fresh hover. */
    private Slot lastSlot;
    /** Wall-clock time (ms) of the last automated click, for the pacing throttle. */
    private long lastClickMs;

    public void tickStart(EnumSet<TickType> type, Object... data) {}

    public void tickEnd(EnumSet<TickType> type, Object... data) {
        if (type.contains(TickType.RENDER)) {
            onRenderTick();
        }
    }

    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.RENDER);
    }

    public String getLabel() {
        return "MouseTweaksNG";
    }

    private void onRenderTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            lastSlot = null;
            return;
        }

        GuiScreen screen = mc.currentScreen;
        // Only normal container GUIs. The creative inventory has destructive special slots, so
        // (like the original) we leave it alone.
        if (!(screen instanceof GuiContainer) || screen instanceof GuiContainerCreative) {
            lastSlot = null;
            return;
        }
        GuiContainer gui = (GuiContainer) screen;

        // The GUI itself computes the hovered slot every frame in drawScreen; reusing it means we
        // inherit each (modded) GUI's real slot hit-testing instead of re-deriving geometry.
        Slot hovered = GuiReflect.getHoveredSlot(gui);
        if (hovered == null) {        // cursor is over no slot -> allow re-entering the same slot later
            lastSlot = null;
            return;
        }
        if (hovered == lastSlot) {    // already handled this hover
            return;
        }

        boolean rightDown = Mouse.isButtonDown(MOUSE_RIGHT);
        boolean leftDown  = Mouse.isButtonDown(MOUSE_LEFT);
        if (!rightDown && !leftDown) {
            lastSlot = null;
            return;
        }

        // Pace our automated clicks. We deliberately do NOT advance lastSlot here, so the moment
        // the gap elapses we still act on whatever slot is under the cursor.
        if (Config.minClickGapMs > 0
                && System.currentTimeMillis() - lastClickMs < Config.minClickGapMs) {
            return;
        }

        Container container = gui.inventorySlots;
        EntityPlayer player = mc.thePlayer;
        ItemStack cursorStack = player.inventory.getItemStack();
        ItemStack slotStack   = hovered.getStack();

        boolean clicked;
        if (rightDown) {
            clicked = handleRight(container, hovered, cursorStack, slotStack, player);
        } else {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                         || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            clicked = handleLeft(container, hovered, cursorStack, slotStack, shift, player);
        }

        lastSlot = hovered;
        if (clicked) {
            lastClickMs = System.currentTimeMillis();
        }
    }

    /**
     * Right-mouse tweak: while holding a stack on the cursor and dragging over slots, drop one
     * item into each (empty slot, or a slot already holding the same item).
     */
    private boolean handleRight(Container c, Slot slot, ItemStack cursorStack,
                                ItemStack slotStack, EntityPlayer player) {
        if (!Config.rmbTweak || cursorStack == null) {
            return false;
        }
        if (slotStack != null && !sameItem(slotStack, cursorStack)) {
            return false;
        }
        windowClick(c, slot, MOUSE_RIGHT, MODE_PICKUP, player);
        return true;
    }

    /**
     * Left-mouse tweaks:
     *  - holding a stack (with-item tweak): shift held -> quick-move matching stacks out; otherwise
     *    pull a matching stack onto the cursor and drop it back, merging the two, when they fit;
     *  - empty cursor (without-item tweak): shift held -> quick-move swept slots out.
     */
    private boolean handleLeft(Container c, Slot slot, ItemStack cursorStack,
                               ItemStack slotStack, boolean shift, EntityPlayer player) {
        if (cursorStack != null && Config.lmbTweakWithItem) {
            if (slotStack == null || !sameItem(slotStack, cursorStack)) {
                return false;
            }
            if (shift) {
                windowClick(c, slot, MOUSE_LEFT, MODE_QUICKMOVE, player);
                return true;
            }
            // Merge only when both stacks fit together (matches the original's guard).
            if (cursorStack.stackSize + slotStack.stackSize <= cursorStack.getMaxStackSize()) {
                windowClick(c, slot, MOUSE_LEFT, MODE_PICKUP, player); // pick the slot stack up
                windowClick(c, slot, MOUSE_LEFT, MODE_PICKUP, player); // put it back, merged
                return true;
            }
            return false;
        }

        if (Config.lmbTweakWithoutItem && slotStack != null && shift) {
            windowClick(c, slot, MOUSE_LEFT, MODE_QUICKMOVE, player);
            return true;
        }
        return false;
    }

    /** Same item id, and same damage when the item uses damage as a subtype (tools/dyes/etc.). */
    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.itemID != b.itemID) {
            return false;
        }
        return !a.getHasSubtypes() || a.getItemDamage() == b.getItemDamage();
    }

    private static void windowClick(Container c, Slot slot, int button, int mode, EntityPlayer player) {
        Minecraft.getMinecraft().playerController.windowClick(
                c.windowId, slot.slotNumber, button, mode, player);
    }
}
