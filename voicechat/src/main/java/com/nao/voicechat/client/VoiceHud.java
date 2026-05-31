package com.nao.voicechat.client;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.nao.voicechat.VoiceConfig;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.ForgeSubscribe;

/**
 * All voicechat HUD rendering:
 *
 * <ul>
 *   <li>Bottom-right of the screen: a small "MIC" badge while you're transmitting (green) or
 *       a "VC OFF" badge when the master switch is off (gray).</li>
 *   <li>Above remote players' heads: a small filled circle when they're talking in our
 *       playback ring (and not muted).</li>
 * </ul>
 *
 * The overlay path is called by {@link VoiceGuiIngame} after vanilla finishes drawing the HUD,
 * so the GL state is already in 2D ortho mode. The world-icon path is driven by
 * {@link RenderWorldLastEvent} which fires after entities are drawn but before the HUD overlay.
 */
public class VoiceHud implements ITickHandler {

    /** Called by {@link VoiceGuiIngame} after vanilla HUD draw. GL state is 2D ortho. */
    public static void renderOverlay(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.gameSettings.showDebugInfo) return; // don't overlap F3 screen

        ScaledResolution sr = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        if (!VoiceConfig.enabled) {
            drawBadge(mc, "VC off", 0x808080, sw - 50, sh - 12);
            return;
        }
        if (MicCapture.isTransmitting()) {
            drawBadge(mc, "MIC", 0x30D050, sw - 32, sh - 12);
        }
    }

    private static void drawBadge(Minecraft mc, String label, int rgb, int x, int y) {
        int w = mc.fontRenderer.getStringWidth(label) + 6;
        int h = 11;
        drawRect(x - 2, y - 2, x + w - 2, y + h - 2, 0x80000000);
        mc.fontRenderer.drawStringWithShadow(label, x + 1, y, 0xFF000000 | rgb);
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ((argb)       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x1, y1);
        GL11.glVertex2i(x1, y2);
        GL11.glVertex2i(x2, y2);
        GL11.glVertex2i(x2, y1);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /* ---------------- above-head icons ---------------- */

    @ForgeSubscribe
    @SuppressWarnings("unchecked")
    public void onRenderWorldLast(RenderWorldLastEvent ev) {
        if (!VoiceConfig.enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.renderViewEntity == null) return;
        float partial = ev.partialTicks;

        double camX = mc.renderViewEntity.lastTickPosX
                    + (mc.renderViewEntity.posX - mc.renderViewEntity.lastTickPosX) * partial;
        double camY = mc.renderViewEntity.lastTickPosY
                    + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * partial;
        double camZ = mc.renderViewEntity.lastTickPosZ
                    + (mc.renderViewEntity.posZ - mc.renderViewEntity.lastTickPosZ) * partial;

        List<EntityPlayer> players = mc.theWorld.playerEntities;
        for (int i = 0, n = players.size(); i < n; i++) {
            EntityPlayer p = players.get(i);
            if (p == mc.renderViewEntity) continue;
            if (!AudioPlayback.isTalking(p.entityId)) continue;
            if (MuteList.isMuted(p.username)) continue;

            double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial - camX;
            double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * partial - camY;
            double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial - camZ;

            // Vanilla nametag renders at posY + height + ~0.5 with its own scale; offset above
            // that so our marker clears the text instead of overlapping it.
            drawTalkingMarker(mc, px, py + p.height + 1.0, pz);
        }
    }

    private static void drawTalkingMarker(Minecraft mc, double dx, double dy, double dz) {
        GL11.glPushMatrix();
        GL11.glTranslated(dx, dy, dz);
        // billboard: cancel yaw + pitch of the camera so the quad always faces us
        GL11.glRotatef(-mc.renderViewEntity.rotationYaw, 0, 1, 0);
        GL11.glRotatef(mc.renderViewEntity.rotationPitch, 1, 0, 0);
        float s = 0.025f;
        GL11.glScalef(-s, -s, s);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // dark rounded backplate (approximated by stacked rects so corners look softer)
        fillRect(-5.5f, -4.5f,  5.5f,  4.5f, 0f, 0f, 0f, 0.55f);
        fillRect(-4.5f, -5.5f,  4.5f,  5.5f, 0f, 0f, 0f, 0.55f);

        drawMicGlyph(0.20f, 0.95f, 0.25f, 1f);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    /**
     * Draw a stylised handheld-microphone glyph centred on (0,0), occupying roughly
     * 8×9 units in the local 2D plane. Composed of axis-aligned rectangles only — the
     * "rounded" appearance comes from stacking a slightly narrower rect above/below the
     * head capsule. Color is the talking-state highlight.
     */
    private static void drawMicGlyph(float r, float g, float b, float a) {
        // mic capsule (top pill)
        fillRect(-2.5f,  0.0f,  2.5f, 3.0f, r, g, b, a);   // main body
        fillRect(-1.8f,  3.0f,  1.8f, 3.6f, r, g, b, a);   // soft top
        fillRect(-1.8f, -0.6f,  1.8f, 0.0f, r, g, b, a);   // soft bottom
        // U-shape mount under the capsule
        fillRect(-3.0f, -1.6f,  3.0f, -1.2f, r, g, b, a);  // mount cradle bottom
        fillRect(-3.0f, -1.2f, -2.4f, -0.6f, r, g, b, a);  // left arm
        fillRect( 2.4f, -1.2f,  3.0f, -0.6f, r, g, b, a);  // right arm
        // short stem
        fillRect(-0.6f, -3.0f,  0.6f, -1.6f, r, g, b, a);
        // foot/base
        fillRect(-2.6f, -3.8f,  2.6f, -3.0f, r, g, b, a);
    }

    private static void fillRect(float x1, float y1, float x2, float y2,
                                 float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(x1, y1, 0);
        GL11.glVertex3f(x1, y2, 0);
        GL11.glVertex3f(x2, y2, 0);
        GL11.glVertex3f(x2, y1, 0);
        GL11.glEnd();
    }

    /* ITickHandler: install our GuiIngame override once the world loads. */

    @Override
    public void tickStart(java.util.EnumSet<TickType> type, Object... tickData) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null) return;
        if (!(mc.ingameGUI instanceof VoiceGuiIngame)) {
            mc.ingameGUI = new VoiceGuiIngame(mc);
        }
    }

    @Override public void tickEnd(java.util.EnumSet<TickType> type, Object... tickData) {}
    @Override public java.util.EnumSet<TickType> ticks() { return java.util.EnumSet.of(TickType.CLIENT); }
    @Override public String getLabel() { return "VoiceChatHudInstaller"; }
}
