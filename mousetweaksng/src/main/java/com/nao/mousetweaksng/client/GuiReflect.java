package com.nao.mousetweaksng.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/**
 * Reads {@code GuiContainer.theSlot} — the slot currently under the cursor, which the GUI sets
 * every frame in {@code drawScreen}. The field is private, so we reflect it once and cache it.
 *
 * The field name differs by environment: it is {@code theSlot} in a deobfuscated/dev build and the
 * obfuscated {@code p} in the shipped 1.4.7 client. We resolve it directly on {@link GuiContainer}
 * (which voldeloom remaps to the obfuscated class), trying both names — the same obf-plus-clean
 * approach the pack's coremods use.
 */
final class GuiReflect {

    private GuiReflect() {}

    private static final String[] CANDIDATES = { "theSlot", "p" };

    private static Field theSlot;
    private static boolean resolved;

    static Slot getHoveredSlot(GuiContainer gui) {
        if (!resolved) {
            resolve();
        }
        if (theSlot == null) {
            return null;
        }
        try {
            Object o = theSlot.get(gui);
            return (o instanceof Slot) ? (Slot) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void resolve() {
        resolved = true;
        for (int i = 0; i < CANDIDATES.length; i++) {
            try {
                Field f = GuiContainer.class.getDeclaredField(CANDIDATES[i]);
                if (Slot.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    theSlot = f;
                    return;
                }
            } catch (NoSuchFieldException ignored) {
                // try the next candidate name
            }
        }
        System.err.println("[MouseTweaksNG] Could not resolve GuiContainer.theSlot; tweaks disabled.");
    }
}
