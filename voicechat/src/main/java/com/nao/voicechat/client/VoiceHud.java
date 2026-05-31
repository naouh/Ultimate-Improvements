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

        int size = 16;
        int x = sw - size - 6;
        int y = sh - size - 6;

        if (!VoiceConfig.enabled) {
            drawHudIcon(mc, x, y, size, 0.90f, 0.20f, 0.20f, 1f);   // red: voicechat off
        } else if (MicCapture.isTransmitting()) {
            drawHudIcon(mc, x, y, size, 0.20f, 0.95f, 0.30f, 1f);   // green: transmitting
        } else {
            drawHudIcon(mc, x, y, size, 0.65f, 0.65f, 0.65f, 0.75f); // gray: idle
        }
    }

    private static void drawHudIcon(Minecraft mc, int x, int y, int size,
                                    float r, float g, float b, float a) {
        int tex = MicIconTexture.id();
        if (tex <= 0) return;
        mc.renderEngine.bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        // 2D ortho, Y down: tex (0,0) = top-left maps directly.
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2i(x,        y);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2i(x,        y + size);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2i(x + size, y + size);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2i(x + size, y);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
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

            // Vanilla nametag sits at posY + height + ~0.5; tuck the icon just above it.
            drawTalkingMarker(mc, px, py + p.height + 0.75, pz);
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

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);   // negative scale flips winding — show both sides
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int tex = MicIconTexture.id();
        if (tex > 0) {
            mc.renderEngine.bindTexture(tex);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(0.25f, 0.95f, 0.35f, 1f);
            // After glScalef(-s, -s, s) the world axes are flipped twice. We map texture
            // (0,0)=top-left of image to the *world* top-left of the quad, which lives at
            // local vertex (r, -r) because of the inverted scale.
            float r = 5f;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(1, 0); GL11.glVertex3f(-r, -r, 0);
            GL11.glTexCoord2f(0, 0); GL11.glVertex3f( r, -r, 0);
            GL11.glTexCoord2f(0, 1); GL11.glVertex3f( r,  r, 0);
            GL11.glTexCoord2f(1, 1); GL11.glVertex3f(-r,  r, 0);
            GL11.glEnd();
        } else {
            // Fallback if the texture failed to upload — at least show something.
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(0.2f, 0.95f, 0.25f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-3.5f, -3.5f, 0);
            GL11.glVertex3f( 3.5f, -3.5f, 0);
            GL11.glVertex3f( 3.5f,  3.5f, 0);
            GL11.glVertex3f(-3.5f,  3.5f, 0);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
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
