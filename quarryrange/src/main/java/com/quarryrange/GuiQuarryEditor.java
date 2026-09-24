package com.quarryrange;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * Live quarry-area editor. Every change pushes a preview packet so BuildCraft's own laser box
 * updates in the world; the quarry stays held (won't start) until the screen is closed.
 *
 * Closing with Confirm or Escape applies the shown size; Cancel reverts to the default (max).
 * The quarry is always released on close, so it can never get stuck idle.
 */
public class GuiQuarryEditor extends GuiScreen {

    private final int qx, qy, qz, meta, min, max;
    private int size, anchor;
    private boolean committed = false;

    private GuiTextField sizeField;

    private static final int F_SIZE   = 50;
    private static final int B_MIN    = 1;
    private static final int B_MAX    = 2;
    private static final int B_MINUS  = 3;
    private static final int B_PLUS   = 4;
    private static final int B_ANCHOR = 5;
    private static final int B_OK     = 6;
    private static final int B_CANCEL = 7;

    public GuiQuarryEditor(int x, int y, int z, int meta, int size, int anchor, int min, int max) {
        this.qx = x; this.qy = y; this.qz = z; this.meta = meta;
        this.min = min; this.max = max;
        this.size = clamp(size);
        this.anchor = (anchor >= QuarryArea.ANCHOR_FRONT && anchor <= QuarryArea.ANCHOR_CORNER_RIGHT)
                ? anchor : QuarryArea.ANCHOR_FRONT;
    }

    private int clamp(int s) { return s < min ? min : (s > max ? max : s); }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        FontRenderer fr = mc.fontRenderer;
        int cx = width / 2;
        int cy = height / 2;

        sizeField = new GuiTextField(fr, cx - 30, cy - 34, 60, 18);
        sizeField.setMaxStringLength(3);
        sizeField.setText(Integer.toString(size));
        sizeField.setFocused(true);

        controlList.clear();
        controlList.add(new GuiButton(B_MINUS, cx - 60, cy - 35, 24, 18, "-"));
        controlList.add(new GuiButton(B_PLUS,  cx + 36, cy - 35, 24, 18, "+"));
        controlList.add(new GuiButton(B_MIN,   cx - 100, cy - 8, 60, 20, "Min (" + min + ")"));
        controlList.add(new GuiButton(B_MAX,   cx + 40,  cy - 8, 60, 20, "Max (" + max + ")"));
        controlList.add(new GuiButton(B_ANCHOR, cx - 100, cy + 16, 200, 20, anchorLabel()));
        controlList.add(new GuiButton(B_OK,     cx - 100, cy + 44, 95, 20, "Confirm"));
        controlList.add(new GuiButton(B_CANCEL, cx + 5,   cy + 44, 95, 20, "Cancel"));

        preview(); // draw the initial box immediately
    }

    private String anchorLabel() {
        switch (anchor) {
            case QuarryArea.ANCHOR_CORNER_LEFT:  return "Position: Corner (left)";
            case QuarryArea.ANCHOR_CORNER_RIGHT: return "Position: Corner (right)";
            default:                             return "Position: In front";
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case B_MINUS:  setSize(size - 1); break;
            case B_PLUS:   setSize(size + 1); break;
            case B_MIN:    setSize(min); break;
            case B_MAX:    setSize(max); break;
            case B_ANCHOR:
                anchor = (anchor + 1) % 3; // In front -> Corner left -> Corner right
                b.displayString = anchorLabel();
                preview();
                break;
            case B_OK:
                committed = true;
                PacketHandler.sendToServer(PacketHandler.PKT_APPLY, qx, qy, qz, size, anchor);
                mc.displayGuiScreen(null);
                break;
            case B_CANCEL:
                committed = true;
                PacketHandler.sendToServer(PacketHandler.PKT_CANCEL, qx, qy, qz, 0, 0);
                mc.displayGuiScreen(null);
                break;
        }
    }

    private void setSize(int s) {
        size = clamp(s);
        sizeField.setText(Integer.toString(size));
        preview();
    }

    /** Live, client-side preview: draw the chosen box with our own laser entities. */
    private void preview() {
        int[] box = QuarryArea.compute(qx, qy, qz, meta, size, anchor);
        ClientPreview.show(box);                          // our blue box
        ClientPreview.sweepNative(mc.theWorld, qx, qy, qz); // remove the quarry's native/orphan box
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            committed = true;
            PacketHandler.sendToServer(PacketHandler.PKT_APPLY, qx, qy, qz, size, anchor);
            mc.displayGuiScreen(null);
            return;
        }
        if (sizeField.textboxKeyTyped(c, code)) {
            String t = sizeField.getText().trim();
            if (!t.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(t);
                    if (parsed > max) {                       // never let the field exceed the cap
                        parsed = max;
                        sizeField.setText(Integer.toString(max));
                    } else if (parsed < min && parsed * 10 > max) {
                        // Below min and can't grow into range by typing another digit -> snap to min.
                        parsed = min;
                        sizeField.setText(Integer.toString(min));
                    }
                    int newSize = clamp(parsed);
                    if (newSize != size) { size = newSize; preview(); }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int btn) {
        super.mouseClicked(x, y, btn);
        sizeField.mouseClicked(x, y, btn);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ClientPreview.clear(); // remove the preview lasers
        // Always release the quarry on close so it can never stay stuck idle.
        if (!committed) {
            PacketHandler.sendToServer(PacketHandler.PKT_APPLY, qx, qy, qz, size, anchor);
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        // Belt-and-braces: keep BuildCraft's native (client-side, untracked) box deleted every frame
        // while editing, in case a tick re-created it before the server's hold flags synced.
        net.minecraft.world.World world = mc.theWorld;
        if (world != null) ReflectQuarry.clearLasers(world.getBlockTileEntity(qx, qy, qz));

        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;
        drawCenteredString(mc.fontRenderer, "Quarry area", cx, cy - 60, 0xFFFFFF);
        int mined = Math.max(1, size - 2);
        drawCenteredString(mc.fontRenderer,
                size + " x " + size + "  (mines " + mined + " x " + mined + ")", cx, cy - 50, 0xA0A0A0);
        sizeField.drawTextBox();
        super.drawScreen(mx, my, pt);
        drawCenteredString(mc.fontRenderer,
                "Preview shows live in the world. Won't start until you close this.",
                cx, cy + 70, 0x808080);
    }
}
