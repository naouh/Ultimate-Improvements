package com.nao.mystutils.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

/**
 * Runtime half of the Writing-Desk search bar. Every method here is invoked from bytecode injected
 * by {@link com.nao.mystutils.asm.GuiPageSurfaceTransformer} and
 * {@link com.nao.mystutils.asm.GuiWritingDeskTransformer}.
 *
 * <p>Only one writing desk is ever open at a time, so the search state is a handful of statics.
 * Everything Mystcraft-specific (the {@code PositionableItem} page wrappers, {@code Page.getSymbol},
 * {@code SymbolManager.getAgeSymbol}, the page dimensions) is reached by reflection so this mod
 * needs no compile-time dependency on Mystcraft and tolerates it being absent — if any lookup
 * fails the search simply disables itself and the desk behaves exactly as vanilla Mystcraft.
 *
 * <p>The hook method <em>signatures</em> deliberately use only {@code Object}, primitives and
 * {@code java.util.List}: the ASM call descriptors must stay stable under the deobf→notch remap.
 */
public final class SearchHook {

    private SearchHook() {}

    // ---- search state -------------------------------------------------------------------------

    private static final int MAX_LEN = 32;
    private static final int COLS = 5;        // matches Mystcraft's own 5-column notebook layout
    private static final int BOX_HEIGHT = 11;

    private static String filter = "";
    private static boolean focused = false;
    private static int lastCount = 0;

    /** Identity of the surface element we last drew for, so reopening the desk starts fresh. */
    private static Object lastSurface = null;

    // Cached on-screen rectangle of the box (set while drawing, read for hit-testing).
    private static int boxX, boxY, boxW, boxH;
    private static boolean boxValid = false;

    // ---- page-list filtering (GuiElementPageSurface.update) -----------------------------------

    /**
     * Replaces the surface page list with a filtered, re-laid-out copy while a query is active.
     * Returns the input unchanged when there is no query (or anything goes wrong).
     */
    public static List applyFilter(List in) {
        if (in == null) return null;
        String q = filter;
        if (q == null || q.length() == 0) return in;
        if (!Reflect.ready()) return in;

        String needle = q.toLowerCase();
        try {
            List<Object> out = new ArrayList<Object>();
            int idx = 0;
            for (Object pi : in) {
                Object stack = Reflect.f_itemstack.get(pi);
                if (stack == null) continue;
                String hay = haystack(stack);
                if (hay == null || hay.indexOf(needle) < 0) continue;
                int slotId = Reflect.f_slotId.getInt(pi);
                Object npi = Reflect.ctor.newInstance(stack, Integer.valueOf(slotId));
                Reflect.f_x.setFloat(npi, (idx % COLS) * (Reflect.pageWidth + 1.0f));
                Reflect.f_y.setFloat(npi, (idx / COLS) * (Reflect.pageHeight + 1.0f));
                out.add(npi);
                ++idx;
            }
            lastCount = out.size();
            return out;
        } catch (Throwable t) {
            return in;
        }
    }

    /** Lower-cased "displayName identifier" for one page item, or null if the page has no symbol. */
    private static String haystack(Object stack) {
        try {
            Object idObj = Reflect.m_getSymbol.invoke(null, stack);
            String id = (idObj == null) ? null : idObj.toString();
            if (id == null) return null;
            String display = id;
            Object sym = Reflect.m_getAgeSymbol.invoke(null, id);
            if (sym != null) {
                Object dn = Reflect.m_displayName.invoke(sym);
                if (dn != null) display = dn.toString();
            }
            return (display + " " + id).toLowerCase();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- drawing (GuiElementPageSurface.render) -----------------------------------------------

    public static void drawBox(Object surface, int mouseX, int mouseY) {
        if (surface != lastSurface) { // desk (re)opened -> fresh search
            lastSurface = surface;
            filter = "";
            focused = false;
        }
        boxValid = false;
        if (!locate(surface)) return;

        // Draw on top of everything in the left panel regardless of Mystcraft's depth juggling.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF000000);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, focused ? 0xFF3A3A48 : 0xFF1C1C24);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, 0xFF55617E); // top highlight line

        FontRenderer fr = font();
        if (fr != null) {
            boolean empty = (filter == null || filter.length() == 0);
            if (empty && !focused) {
                fr.drawString("Search pages...", boxX + 3, boxY + 2, 0xFF9AA0B0);
            } else {
                fr.drawString((filter == null ? "" : filter) + (focused ? "_" : ""),
                        boxX + 3, boxY + 2, 0xFFFFFFFF);
                if (!empty) {
                    String tag = "(" + lastCount + ")";
                    fr.drawString(tag, boxX + boxW - 2 - fr.getStringWidth(tag), boxY + 2, 0xFFB0B0B0);
                }
            }
        }

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        boxValid = true;
    }

    /**
     * Reads guiLeft/guiTop/xSize from the surface and places the box in the empty strip just
     * <em>above</em> the page grid, so it never overlaps the pages.
     */
    private static boolean locate(Object surface) {
        if (!Reflect.ready()) return false;
        try {
            int guiLeft = Reflect.f_guiLeft.getInt(surface);
            int guiTop = Reflect.f_guiTop.getInt(surface);
            int xSize = Reflect.f_xSize.getInt(surface);
            boxX = guiLeft;
            boxW = xSize - 1;
            boxH = BOX_HEIGHT;
            boxY = guiTop - BOX_HEIGHT - 2; // sit above the grid
            if (boxY < 1) boxY = 1;          // clamp on-screen if the desk hugs the top
            if (boxW < 24) boxW = 24;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static FontRenderer font() {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc == null) ? null : mc.fontRenderer;
    }

    // ---- input --------------------------------------------------------------------------------

    /** Click inside the page surface: focus the box (and swallow) if the box was hit. */
    public static boolean surfaceClicked(Object surface, int x, int y, int button) {
        if (boxValid && inBox(x, y)) {
            focused = true;
            return true;
        }
        return false;
    }

    /** Any click on the desk: defocus when it lands outside the box. */
    public static void deskClicked(int x, int y, int button) {
        if (!boxValid || !inBox(x, y)) {
            focused = false;
        }
    }

    private static boolean inBox(int x, int y) {
        return x >= boxX && x < boxX + boxW && y >= boxY && y < boxY + boxH;
    }

    /** @return true if the keystroke was consumed by the focused search box. */
    public static boolean keyTyped(char c, int key) {
        if (!focused) return false;
        switch (key) {
            case 1:   // ESC -> just close the search, keep the desk open
            case 28:  // RETURN
            case 156: // NUMPAD ENTER
                focused = false;
                return true;
            case 14:  // BACKSPACE
                if (filter.length() > 0) filter = filter.substring(0, filter.length() - 1);
                return true;
            default:
                if (c >= 32 && c != 127 && filter.length() < MAX_LEN) filter += c;
                return true; // consume everything else (arrows, inventory key) while typing
        }
    }

    // ---- reflection cache ---------------------------------------------------------------------

    private static final class Reflect {
        static boolean attempted = false;
        static boolean ok = false;

        static Field f_itemstack, f_x, f_y, f_slotId;
        static Field f_guiLeft, f_guiTop, f_xSize;
        static Constructor<?> ctor;
        static Method m_getSymbol, m_getAgeSymbol, m_displayName;
        static float pageWidth = 30.0f, pageHeight = 40.0f;

        static synchronized boolean ready() {
            if (attempted) return ok;
            attempted = true;
            try {
                Class<?> cPos = Class.forName("com.xcompwiz.mystcraft.utility.PositionableItem");
                f_itemstack = cPos.getField("itemstack");
                f_x = cPos.getField("x");
                f_y = cPos.getField("y");
                f_slotId = cPos.getField("slotId");
                ctor = pick2ArgCtor(cPos);

                Class<?> cSurf = Class.forName("com.xcompwiz.mystcraft.client.gui.GuiElementPageSurface");
                f_guiLeft = field(cSurf, "guiLeft");
                f_guiTop = field(cSurf, "guiTop");
                f_xSize = field(cSurf, "xSize");

                Class<?> cPage = Class.forName("com.xcompwiz.mystcraft.item.Page");
                m_getSymbol = method1(cPage, "getSymbol");

                Class<?> cSym = Class.forName("com.xcompwiz.mystcraft.generation.symbols.SymbolManager");
                m_getAgeSymbol = method1(cSym, "getAgeSymbol");

                Class<?> cAge = Class.forName("com.xcompwiz.mystcraft.api.symbol.IAgeSymbol");
                m_displayName = cAge.getMethod("displayName");

                Class<?> cNb = Class.forName("com.xcompwiz.mystcraft.inventory.InventoryNotebook");
                pageWidth = field(cNb, "pagewidth").getFloat(null);
                pageHeight = field(cNb, "pageheight").getFloat(null);

                ok = true;
            } catch (Throwable t) {
                System.err.println("[MystUtils] Search disabled (Mystcraft layout not found): " + t);
                ok = false;
            }
            return ok;
        }

        private static Field field(Class<?> c, String name) throws NoSuchFieldException {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }

        private static Constructor<?> pick2ArgCtor(Class<?> c) {
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (k.getParameterTypes().length == 2) { k.setAccessible(true); return k; }
            }
            throw new IllegalStateException("PositionableItem has no 2-arg constructor");
        }

        private static Method method1(Class<?> c, String name) {
            for (Method mm : c.getDeclaredMethods()) {
                if (mm.getName().equals(name) && mm.getParameterTypes().length == 1) {
                    mm.setAccessible(true);
                    return mm;
                }
            }
            throw new IllegalStateException("Method not found: " + c.getName() + "." + name);
        }
    }
}
