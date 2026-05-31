package com.nao.voicechat.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

/**
 * Provides the GL texture id of the mic glyph rendered above talking players.
 *
 * Order of resolution:
 * <ol>
 *   <li>{@code config/voicechat-mic.png} — user override. Drop any PNG (square, RGBA, ≥16 px)
 *       and it'll be picked up on the next mod load.</li>
 *   <li>If missing, a 64×64 mic glyph is drawn into a {@link BufferedImage} with antialiasing
 *       and uploaded as a GL texture once.</li>
 * </ol>
 * Texture id is cached for the lifetime of the JVM; we never bother deleting it.
 */
public final class MicIconTexture {

    private MicIconTexture() {}

    private static int textureId = -1;
    private static File overridePath;

    /** Called once at preInit so we know where to look for a user override. */
    public static void setConfigDir(File configDir) {
        overridePath = new File(configDir, "voicechat-mic.png");
    }

    /** Allocates the texture on first call. {@code -1} only if the GL upload fails. */
    public static int id() {
        if (textureId != -1) return textureId;
        BufferedImage img = loadOverride();
        if (img == null) img = generate();
        try {
            textureId = Minecraft.getMinecraft().renderEngine.allocateAndSetupTexture(img);
        } catch (Throwable t) {
            System.err.println("[VoiceChat] could not upload mic texture: " + t);
            textureId = 0;
        }
        return textureId;
    }

    private static BufferedImage loadOverride() {
        if (overridePath == null || !overridePath.isFile()) return null;
        try {
            return ImageIO.read(overridePath);
        } catch (IOException e) {
            System.err.println("[VoiceChat] failed to read " + overridePath + ": " + e);
            return null;
        }
    }

    /**
     * Draw a stylised mic. Coordinates are in a 64×64 frame so we can tweak the proportions
     * here without touching the call site. Antialiasing on, fully transparent background,
     * white fill so the renderer can recolour via {@code glColor4f}.
     */
    private static BufferedImage generate() {
        int sz = 64;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, sz, sz);
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Soft dark backplate, slightly rounded.
        g.setColor(new Color(0, 0, 0, 140));
        g.fill(new RoundRectangle2D.Float(2, 2, sz - 4, sz - 4, 14, 14));

        g.setColor(Color.WHITE);

        // Mic capsule (top pill).
        int capX = 22, capW = 20;
        int capY = 8,  capH = 30;
        g.fill(new RoundRectangle2D.Float(capX, capY, capW, capH, capW, capW));

        // U-shaped cradle: outer arc, then erase the inside to leave a 4-px stroke.
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(16, 28, 32, 22, 0, -180);

        // Vertical stem from cradle base down to the foot.
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawLine(32, 44, 32, 52);

        // Horizontal foot.
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(22, 54, 42, 54);

        g.dispose();
        return img;
    }
}
