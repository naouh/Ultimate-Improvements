package com.nao.voicechat.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.nao.voicechat.VoiceConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Voicechat settings screen — opens on the configured key. Master enable/disable at the top,
 * scrolling list of usernames below (own name + every other player on the local client world +
 * any name we've muted in the past), each with a toggle.
 */
public class VoiceMuteGui extends GuiScreen {

    private static final int ID_TOGGLE_ENABLED = 1;
    private static final int ID_DONE           = 2;
    private static final int ID_PLAYER_BASE    = 100;

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP   = 50;
    private static final int LIST_PAD   = 60;

    private List<String> rows;
    private int scrollOffset;

    @Override
    public void initGui() {
        controlList.clear();
        controlList.add(new GuiButton(ID_TOGGLE_ENABLED, width / 2 - 100, 18,
                                      enabledLabel()));
        controlList.add(new GuiButton(ID_DONE, width / 2 - 100, height - 28, "Done"));

        rebuildRows();
        rebuildPlayerButtons();
    }

    private String enabledLabel() {
        return VoiceConfig.enabled ? "Voicechat: ON" : "Voicechat: OFF";
    }

    private void rebuildRows() {
        Set<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (mc != null && mc.theWorld != null) {
            for (Object o : mc.theWorld.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (p == mc.thePlayer) continue;
                if (p.username != null) set.add(p.username);
            }
        }
        // Also keep already-muted names so the user can unmute someone who's offline.
        set.addAll(MuteList.snapshot());
        rows = new ArrayList<String>(set);
        Collections.sort(rows, String.CASE_INSENSITIVE_ORDER);
    }

    private void rebuildPlayerButtons() {
        // Remove any existing player buttons (IDs >= base) before re-adding.
        for (int i = controlList.size() - 1; i >= 0; i--) {
            GuiButton b = (GuiButton) controlList.get(i);
            if (b.id >= ID_PLAYER_BASE) controlList.remove(i);
        }
        int visible = Math.max(1, (height - LIST_TOP - LIST_PAD) / ROW_HEIGHT);
        for (int i = 0; i < visible && (i + scrollOffset) < rows.size(); i++) {
            String name = rows.get(i + scrollOffset);
            boolean muted = MuteList.isMuted(name);
            String lbl = (muted ? "[Muted] " : "[Hear ]  ") + name;
            controlList.add(new GuiButton(ID_PLAYER_BASE + i,
                                          width / 2 - 100, LIST_TOP + i * ROW_HEIGHT,
                                          200, 20, lbl));
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == ID_TOGGLE_ENABLED) {
            VoiceConfig.enabled = !VoiceConfig.enabled;
            VoiceConfig.saveEnabled();
            ((GuiButton) controlList.get(0)).displayString = enabledLabel();
            if (VoiceConfig.enabled) {
                MicCapture.tryReopen();
            } else {
                MicCapture.stop();
                AudioPlayback.shutdown();
            }
            return;
        }
        if (b.id == ID_DONE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (b.id >= ID_PLAYER_BASE) {
            int idx = scrollOffset + (b.id - ID_PLAYER_BASE);
            if (idx >= 0 && idx < rows.size()) {
                MuteList.toggle(rows.get(idx));
                rebuildPlayerButtons();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) return;
        int delta = wheel > 0 ? -1 : 1;
        int max = Math.max(0, rows.size() -
                  Math.max(1, (height - LIST_TOP - LIST_PAD) / ROW_HEIGHT));
        scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
        rebuildPlayerButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Voicechat", width / 2, 6, 0xFFFFFF);
        if (rows.isEmpty()) {
            drawCenteredString(fontRenderer,
                    "No other players online.", width / 2, LIST_TOP + 4, 0xA0A0A0);
        }
        drawString(fontRenderer,
                   "Click a name to toggle mute. Scroll for more.",
                   width / 2 - 100, height - 42, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
