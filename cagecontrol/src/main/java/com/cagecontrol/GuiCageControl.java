package com.cagecontrol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.network.packet.Packet250CustomPayload;
import org.lwjgl.input.Keyboard;

/**
 * Management GUI opened via {@code /cagecontrol}. Two views:
 *   - "Mine"  : cages the player owns or co-owns
 *   - "All"   : every cage in the world (visible to OPs only)
 *
 * Rendered as a centered panel (not full-screen). Left: scrollable cage list. Right: detail panel
 * with rename field, co-owner add/list, a Start/Stop button. All write actions go through the
 * existing /shard chat commands - the server enforces permissions, so this GUI is "just a UI".
 */
public class GuiCageControl extends GuiScreen {

    private static final int ROW_H = 22;

    // Centered panel geometry (computed in initGui).
    private int panelX, panelY, panelW, panelH;
    private int listX, listTop, listWidth, listH;

    private boolean adminView; // false = Mine, true = All
    private int scroll;
    private int selectedIdx = -1;

    private GuiTextField renameField;
    private GuiTextField coOwnerField;
    private String statusMsg;
    private long statusUntilMs;

    private final List<Btn> btns = new ArrayList<Btn>();

    private static final class Btn {
        int x, y, w, h;
        String label;
        int color;
        Runnable action;
    }

    private String myUser() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.thePlayer == null ? "" : mc.thePlayer.username;
    }

    // Detail-panel anchor (computed from panel size in initGui).
    private int detailX, detailY, detailW, detailH;
    private static final int DETAIL_PAD = 8;
    private static final int RENAME_FIELD_OFFSET_Y = 100;
    private static final int ADDCO_FIELD_OFFSET_Y  = 130;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panelW = 486;
        panelH = Math.min(height - 20, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        listX = panelX + 8;
        listTop = panelY + 48;
        listWidth = 200;
        listH = panelH - 56;

        detailX = listX + listWidth + 10;
        detailY = listTop;
        detailW = panelX + panelW - 8 - detailX;
        detailH = listH;

        renameField = new GuiTextField(fontRenderer,
                detailX + 55, detailY + RENAME_FIELD_OFFSET_Y, 150, 18);
        renameField.setMaxStringLength(24);
        coOwnerField = new GuiTextField(fontRenderer,
                detailX + 85, detailY + ADDCO_FIELD_OFFSET_Y, 120, 18);
        coOwnerField.setMaxStringLength(16);
        // Request a fresh list on every open.
        sendListRequest();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    /** Filter the cached list by current view + sort. */
    private List<CageData> visibleCages() {
        List<CageData> raw = ClientPacketHandler.lastList;
        if (raw == null) return new ArrayList<CageData>();
        String me = myUser();
        List<CageData> out = new ArrayList<CageData>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            CageData d = raw.get(i);
            if (adminView) out.add(d);
            else if (d.owner != null && d.owner.equalsIgnoreCase(me)) out.add(d);
            else if (d.coOwners.contains(me.toLowerCase())) out.add(d);
        }
        return out;
    }

    private CageData selected() {
        List<CageData> v = visibleCages();
        if (selectedIdx < 0 || selectedIdx >= v.size()) return null;
        return v.get(selectedIdx);
    }

    private void setStatus(String s) {
        statusMsg = s;
        statusUntilMs = System.currentTimeMillis() + 4000L;
    }

    private void sendListRequest() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PacketHandler.PKT_LIST_REQUEST);
        } catch (IOException e) { return; }
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = CageControl.CHANNEL;
        pkt.data = bos.toByteArray();
        pkt.length = pkt.data.length;
        PacketDispatcher.sendPacketToServer(pkt);
    }

    private void runShard(String cmd) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) mc.thePlayer.sendChatMessage(cmd);
        // Re-request the list shortly after; the request is processed after the chat command on the
        // server tick, so the UI catches up.
        sendListRequest();
    }

    // ----- keyboard / mouse -----

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (renameField != null && renameField.isFocused()) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) { actionRename(); return; }
            renameField.textboxKeyTyped(c, code); return;
        }
        if (coOwnerField != null && coOwnerField.isFocused()) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) { actionAddCoOwner(); return; }
            coOwnerField.textboxKeyTyped(c, code); return;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += (wheel > 0 ? -1 : 1) * ROW_H;
            if (scroll < 0) scroll = 0;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (btn != 0) return;
        if (renameField != null) renameField.mouseClicked(mx, my, btn);
        if (coOwnerField != null) coOwnerField.mouseClicked(mx, my, btn);

        // Buttons (built each frame in drawScreen).
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                if (b.action != null) b.action.run();
                return;
            }
        }

        // List row clicks.
        List<CageData> v = visibleCages();
        for (int i = 0; i < v.size(); i++) {
            int rowY = listTop + i * ROW_H - scroll;
            if (rowY < listTop) continue;
            if (rowY + ROW_H > listTop + listH) continue;
            if (mx >= listX && mx < listX + listWidth && my >= rowY && my < rowY + ROW_H) {
                selectedIdx = i;
                CageData d = v.get(i);
                renameField.setText(d.name == null ? "" : d.name);
                renameField.setFocused(false);
                coOwnerField.setText("");
                coOwnerField.setFocused(false);
                return;
            }
        }
    }

    // ----- actions -----

    private String idFor(CageData d) {
        String me = myUser();
        boolean mine = d.owner != null && d.owner.equalsIgnoreCase(me);
        if (mine || d.owner == null || d.owner.isEmpty()) return d.name;
        return d.owner + ":" + d.name;
    }

    private void actionRename() {
        CageData d = selected();
        if (d == null) return;
        String n = renameField.getText().trim();
        if (n.length() == 0) return;
        if (n.equalsIgnoreCase(d.name)) return;
        runShard("/shard " + idFor(d) + " rename " + n);
        setStatus("Rename sent: " + d.name + " -> " + n);
    }

    private void actionAddCoOwner() {
        CageData d = selected();
        if (d == null) return;
        String p = coOwnerField.getText().trim();
        if (p.length() < 2) { setStatus("Enter a player name."); return; }
        runShard("/shard " + idFor(d) + " owner add " + p);
        coOwnerField.setText("");
        setStatus("Co-owner add sent: " + p);
    }

    private void actionRemoveCoOwner(String co) {
        CageData d = selected();
        if (d == null) return;
        runShard("/shard " + idFor(d) + " owner remove " + co);
        setStatus("Co-owner remove sent: " + co);
    }

    private void actionToggle() {
        CageData d = selected();
        if (d == null) return;
        runShard("/shard " + idFor(d) + (d.active ? " stop" : " start"));
        setStatus(d.active ? "Stop sent." : "Start sent.");
    }

    // ----- drawing -----

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        // Panel background.
        drawRect(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF202020);
        drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xF00E0E0E);
        drawRect(panelX, panelY, panelX + panelW, panelY + 20, 0xFF1A1A1A);

        drawCenteredString(fontRenderer, "§lCageControl", panelX + panelW / 2, panelY + 6, 0xFFFFFF);
        int total = ClientPacketHandler.lastList == null ? 0 : ClientPacketHandler.lastList.size();
        drawCenteredString(fontRenderer,
                "§7" + total + " cage" + (total == 1 ? "" : "s") + " known   "
                        + (ClientPacketHandler.lastIsAdmin ? "§a(admin)" : "§7(player view)"),
                panelX + panelW / 2, panelY + 24, 0xCCCCCC);

        btns.clear();

        // View tabs.
        int tabY = panelY + 30;
        addBtn(listX, tabY, 80, 16, "Mine", !adminView ? 0xFF2E8B57 : 0xFF333333, new Runnable() {
            @Override public void run() { adminView = false; selectedIdx = -1; scroll = 0; }
        });
        if (ClientPacketHandler.lastIsAdmin) {
            addBtn(listX + 84, tabY, 80, 16, "All cages", adminView ? 0xFFB22222 : 0xFF333333, new Runnable() {
                @Override public void run() { adminView = true; selectedIdx = -1; scroll = 0; }
            });
        }
        addBtn(detailX + detailW - 60, tabY, 60, 16, "Refresh", 0xFF445566, new Runnable() {
            @Override public void run() { sendListRequest(); setStatus("Refreshing..."); }
        });

        drawCageList(mx, my);
        drawDetailPanel(mx, my);

        // Status flash (just below the panel).
        if (statusMsg != null && System.currentTimeMillis() < statusUntilMs) {
            int sy = Math.min(panelY + panelH + 4, height - 10);
            drawCenteredString(fontRenderer, "§e" + statusMsg, width / 2, sy, 0xFFFFAA00);
        }

        // Render buttons last so they overlay the list/detail backgrounds.
        for (int i = 0; i < btns.size(); i++) drawButton(btns.get(i));
    }

    private void addBtn(int x, int y, int w, int h, String label, int color, Runnable action) {
        Btn b = new Btn();
        b.x = x; b.y = y; b.w = w; b.h = h;
        b.label = label; b.color = color; b.action = action;
        btns.add(b);
    }

    private void drawButton(Btn b) {
        drawRect(b.x, b.y, b.x + b.w, b.y + b.h, 0xFF101418);
        drawRect(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, b.color);
        drawCenteredString(fontRenderer, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, 0xFFFFFF);
    }

    private void drawCageList(int mx, int my) {
        drawRect(listX - 2, listTop - 2, listX + listWidth + 2, listTop + listH + 2, 0xFF202020);
        drawRect(listX, listTop, listX + listWidth, listTop + listH, 0xFF101418);

        List<CageData> v = visibleCages();
        if (v.isEmpty()) {
            drawCenteredString(fontRenderer, "§7(no cages to show)",
                    listX + listWidth / 2, listTop + listH / 2 - 4, 0xFFAAAAAA);
            return;
        }
        String me = myUser();
        for (int i = 0; i < v.size(); i++) {
            int rowY = listTop + i * ROW_H - scroll;
            if (rowY + ROW_H < listTop) continue;
            if (rowY > listTop + listH) break;
            CageData d = v.get(i);
            int bg = (i == selectedIdx) ? 0xFF335577 : 0xFF1A1F26;
            if (i != selectedIdx && mx >= listX && mx < listX + listWidth && my >= rowY && my < rowY + ROW_H) {
                bg = 0xFF273040; // hover
            }
            drawRect(listX + 2, rowY + 1, listX + listWidth - 2, rowY + ROW_H - 1, bg);

            // Status dot.
            int dotColor = d.active ? 0xFF40DD60 : 0xFFAA3333;
            drawRect(listX + 6, rowY + 4, listX + 12, rowY + 10, dotColor);

            // Name + mob.
            boolean mine = d.owner != null && d.owner.equalsIgnoreCase(me);
            String prefix = mine ? "§b" : (d.coOwners.contains(me.toLowerCase()) ? "§d" : "§e");
            drawString(fontRenderer, prefix + d.name + "§r §7- " + d.mobType,
                    listX + 16, rowY + 3, 0xFFFFFF);
            // Sub-line: tier + pos + owner.
            drawString(fontRenderer,
                    "§8t" + d.tier + " §7dim" + d.dim + " §7@ " + d.x + "," + d.y + "," + d.z
                            + "  §8(" + (d.owner == null ? "?" : d.owner) + ")",
                    listX + 16, rowY + 12, 0xFFAAAAAA);
        }
    }

    private void drawDetailPanel(int mx, int my) {
        int px = detailX;
        int py = detailY;
        int pw = detailW;
        int ph = detailH;
        drawRect(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF202020);
        drawRect(px, py, px + pw, py + ph, 0xFF101418);

        CageData d = selected();
        if (d == null) {
            drawCenteredString(fontRenderer, "§7Select a cage on the left.",
                    px + pw / 2, py + ph / 2 - 4, 0xFFAAAAAA);
            return;
        }

        // --- Header ---
        drawString(fontRenderer, "§l" + d.name, px + DETAIL_PAD, py + 8, 0xFFFFFFFF);
        drawString(fontRenderer, "§7Owner: §e" + d.owner, px + DETAIL_PAD, py + 22, 0xFFAAAAAA);
        drawString(fontRenderer, "§7Mob: §f" + d.mobType + "  §7Tier: §f" + d.tier
                + (d.special ? "  §6special" : ""), px + DETAIL_PAD, py + 33, 0xFFAAAAAA);
        drawString(fontRenderer, "§7Dim §f" + d.dim + " §7at §f" + d.x + "," + d.y + "," + d.z,
                px + DETAIL_PAD, py + 44, 0xFFAAAAAA);
        drawString(fontRenderer, "§7Status: " + (d.active ? "§aSTARTED" : "§cSTOPPED"),
                px + DETAIL_PAD, py + 55, 0xFFAAAAAA);

        // --- Start/Stop button ---
        addBtn(px + DETAIL_PAD, py + 70, 100, 18,
                d.active ? "Stop" : "Start",
                d.active ? 0xFFB22222 : 0xFF2E8B57,
                new Runnable() { @Override public void run() { actionToggle(); } });

        // --- Rename row ---
        drawString(fontRenderer, "§7Rename:", px + DETAIL_PAD, py + RENAME_FIELD_OFFSET_Y + 5, 0xFFAAAAAA);
        renameField.drawTextBox();
        addBtn(detailX + 55 + 150 + 4, py + RENAME_FIELD_OFFSET_Y, 44, 18, "Apply", 0xFF445566,
                new Runnable() { @Override public void run() { actionRename(); } });

        // --- Add co-owner row ---
        String me = myUser();
        boolean canAdd = (d.owner != null && d.owner.equalsIgnoreCase(me)) || ClientPacketHandler.lastIsAdmin;
        if (canAdd) {
            drawString(fontRenderer, "§7Co-owner:", px + DETAIL_PAD, py + ADDCO_FIELD_OFFSET_Y + 5, 0xFFAAAAAA);
            coOwnerField.drawTextBox();
            addBtn(detailX + 85 + 120 + 4, py + ADDCO_FIELD_OFFSET_Y, 40, 18, "Add", 0xFF2E8B57,
                    new Runnable() { @Override public void run() { actionAddCoOwner(); } });
        } else {
            drawString(fontRenderer, "§8(only the owner can manage co-owners)",
                    px + DETAIL_PAD, py + ADDCO_FIELD_OFFSET_Y + 5, 0xFF888888);
        }

        // --- Co-owners list ---
        int cy = py + 158;
        drawString(fontRenderer, "§7Co-owners:", px + DETAIL_PAD, cy, 0xFFAAAAAA); cy += 12;
        if (d.coOwners.isEmpty()) {
            drawString(fontRenderer, "§8(none)", px + DETAIL_PAD + 6, cy, 0xFF888888);
        } else {
            int n = 0;
            for (final String co : d.coOwners) {
                if (n >= 4) { drawString(fontRenderer, "§8...", px + DETAIL_PAD + 6, cy, 0xFF888888); break; }
                drawString(fontRenderer, "§f" + co, px + DETAIL_PAD + 6, cy + 4, 0xFFFFFFFF);
                if (canAdd) {
                    addBtn(px + pw - 64, cy + 1, 56, 14, "Remove", 0xFFAA5555,
                            new Runnable() { @Override public void run() { actionRemoveCoOwner(co); } });
                }
                cy += 16;
                n++;
            }
        }
    }
}
