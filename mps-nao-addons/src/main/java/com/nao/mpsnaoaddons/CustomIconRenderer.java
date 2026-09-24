package com.nao.mpsnaoaddons;

import net.machinemuse.general.gui.MuseIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

/**
 * Renders the actual item textures (OmniWrench, IC2 EU-Reader, TE Multimeter,
 * AE Wireless Terminal) as the module icons everywhere MPS would otherwise
 * draw the generic MuseIcon sprite — both the Tinker Table grid AND the
 * mode-switcher HUD above the hotbar.
 *
 * <p>Two mechanisms feed into the same item-render path:
 * <ul>
 *   <li>Our own modules carry an {@link ItemMuseIcon} (MuseIcon subclass +
 *       ItemStack reference), set at registration via
 *       {@code PowerModule.setIcon}.</li>
 *   <li>A static {@code MuseIcon → ItemStack} override map lets us also
 *       remap MPS' built-in icons we want to upgrade — currently the three
 *       generic Tool icons (axe / pickaxe / shovel) get rendered as the
 *       corresponding diamond tools.</li>
 * </ul>
 *
 * <p>{@link com.nao.mpsnaoaddons.transform.MuseRendererTransformer} patches
 * both {@code MuseRenderer.drawIconAt} and {@code MuseRenderer.drawIconPartial}
 * to consult this class. Match → render the item here, skip the MuseIcon
 * sprite. No match → original sprite render runs.
 *
 * <p>Rendering is client-side only. The class itself does get loaded on a
 * dedicated server (module registration calls the {@code build*Icon} helpers
 * on both sides) — that is fine because only {@code ItemMuseIcon} construction
 * runs there; the GL / RenderItem code is resolved lazily and never executed.
 */
public final class CustomIconRenderer {

    private static RenderItem renderItem;
    /** MuseIcon instance → ItemStack to render in its place. Populated lazily
     *  on first {@link #resolveStack} call so we don't NPE if MPS' static
     *  MuseIcon fields aren't initialised yet at class-load time. */
    private static java.util.Map<net.machinemuse.general.gui.MuseIcon, ItemStack> ICON_OVERRIDES;
    private static boolean inited = false;

    private CustomIconRenderer() {}

    /** Resolve the AE wireless terminal / OmniWrench item stacks here so the
     *  module registration code can just call {@link #buildIcon} per module
     *  without re-doing the cross-mod lookups. */
    public static ItemMuseIcon buildOmniWrenchIcon() { return buildIcon(lookupOmniWrenchStack()); }
    public static ItemMuseIcon buildEUReaderIcon()   { return buildIcon(EUReaderHelper.getEcMeterStack()); }
    public static ItemMuseIcon buildTEMultimeterIcon() { return buildIcon(TEMultimeterHelper.getMultimeterStack()); }
    public static ItemMuseIcon buildMEWirelessIcon() { return buildIcon(MEWirelessHelper.getWirelessTerminalStack()); }

    private static ItemMuseIcon buildIcon(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        return new ItemMuseIcon(stack);
    }

    /** OmniTools' wrench item — same lookup as in {@link MpsNaoAddonsMod}. */
    private static ItemStack lookupOmniWrenchStack() {
        try {
            Class<?> cOmniTools = Class.forName("omnitools.OmniTools");
            Object wrench = cOmniTools.getField("wrench").get(null);
            return new ItemStack((Item) wrench, 1);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Called from ASM-injected bytecode prepended to
     * {@code MuseRenderer.drawIconAt}. If the icon is one of ours OR maps to
     * a known override (diamond tools etc.), render the underlying item and
     * return true. Otherwise return false so the original draw runs.
     */
    public static boolean tryDraw(MuseIcon icon, double x, double y) {
        ItemStack stack = resolveStack(icon);
        if (stack == null) return false;
        ensureRenderer();
        renderItemAt(stack, (int) x, (int) y);
        return true;
    }

    /**
     * Mode-switcher HUD calls this with the visible sub-rect of the 16x16
     * icon as {@code (l, t, r, b)}. Prev/next mode icons pass a clipped
     * bottom so they peek out from behind the hotbar — without clipping
     * the item rendering, the icons overlap the hotbar entirely. We set
     * up a GL scissor matching the requested sub-rect in screen pixels.
     */
    public static boolean tryDrawPartial(MuseIcon icon, double x, double y,
                                         double l, double t, double r, double b) {
        ItemStack stack = resolveStack(icon);
        if (stack == null) return false;
        ensureRenderer();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return false;

        boolean clipped = (l > 0.0) || (t > 0.0) || (r < 16.0) || (b < 16.0);
        if (clipped) {
            ScaledResolution sr = new ScaledResolution(mc.gameSettings,
                    mc.displayWidth, mc.displayHeight);
            int scale = sr.getScaleFactor();
            int gx1 = (int) Math.floor(x + l);
            int gy1 = (int) Math.floor(y + t);
            int gx2 = (int) Math.ceil(x + r);
            int gy2 = (int) Math.ceil(y + b);
            // glScissor is in pixel coords with origin at bottom-left.
            int sx = gx1 * scale;
            int sy = (sr.getScaledHeight() - gy2) * scale;
            int sw = (gx2 - gx1) * scale;
            int sh = (gy2 - gy1) * scale;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(sx, sy, sw, sh);
        }
        try {
            renderItemAt(stack, (int) x, (int) y);
        } finally {
            if (clipped) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        return true;
    }

    /** Returns the ItemStack to render for this icon, or null if MPS'
     *  sprite render should run instead. */
    private static ItemStack resolveStack(MuseIcon icon) {
        if (icon == null) return null;
        if (icon instanceof ItemMuseIcon) {
            return ((ItemMuseIcon) icon).getStack();
        }
        if (ICON_OVERRIDES == null) buildOverrideMap();
        return ICON_OVERRIDES.get(icon);
    }

    /** Populate the override map. Done lazily because MPS' static MuseIcon
     *  fields are initialised eagerly but Item.diamondPickaxe etc. need
     *  vanilla MC class init — safer to defer. */
    private static synchronized void buildOverrideMap() {
        if (ICON_OVERRIDES != null) return;
        java.util.HashMap<net.machinemuse.general.gui.MuseIcon, ItemStack> map =
                new java.util.HashMap<net.machinemuse.general.gui.MuseIcon, ItemStack>();
        try {
            putIfBoth(map, net.machinemuse.general.gui.MuseIcon.TOOL_AXE,    new ItemStack(Item.axeDiamond, 1));
            putIfBoth(map, net.machinemuse.general.gui.MuseIcon.TOOL_PICK,   new ItemStack(Item.pickaxeDiamond, 1));
            putIfBoth(map, net.machinemuse.general.gui.MuseIcon.TOOL_SHOVEL, new ItemStack(Item.shovelDiamond, 1));
        } catch (Throwable t) {
            System.err.println("[CustomIconRenderer] Diamond tool icon overrides failed: " + t);
        }
        ICON_OVERRIDES = map;
    }

    private static void putIfBoth(java.util.Map<net.machinemuse.general.gui.MuseIcon, ItemStack> map,
                                  net.machinemuse.general.gui.MuseIcon key, ItemStack stack) {
        if (key != null && stack != null && stack.getItem() != null) map.put(key, stack);
    }

    private static synchronized void ensureRenderer() {
        if (inited) return;
        inited = true;
        renderItem = new RenderItem();
        // Sit at the same z plane as MPS' MuseIcon sprite would — the
        // Tinker Table tooltip is drawn afterwards without depth test, so
        // leaving zLevel at 0 lets the tooltip text appear on top correctly.
        renderItem.zLevel = 0.0F;
    }

    /** Render {@code stack} at top-left ({@code x},{@code y}) on the GUI
     *  plane. Mirrors MC's slot rendering setup, then leaves the GL state
     *  the way MPS expects (depth test off) so the GUI's tooltip render
     *  later in the frame draws on top of our icons. */
    private static void renderItemAt(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y);
        RenderHelper.disableStandardItemLighting();
        // RenderItem.renderItemAndEffectIntoGUI enables GL_DEPTH_TEST and
        // leaves it on — that breaks MPS' tooltip render (which draws at
        // z=0 without depth handling). Restore the disabled-depth-test
        // baseline that the GUI stack started with.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }
}
