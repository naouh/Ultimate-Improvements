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
 * Left side: scrollable list of cages. Click one to select.
 * Right side: detail panel with rename field, co-owner list + add box,
 * a Start/Stop button, and (admin only on the All view) a Delete button.
 *
 * All write actions are dispatched through the existing /shard chat commands -
 * the server handles permissions there, so this GUI is "just a UI".
 */
public class GuiCageControl extends GuiScreen {

    private static final int LIST_X = 10;
    private static final int LIST_TOP = 50;
    private static final int LIST_WIDTH = 250;
    private static final int ROW_H = 22;

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

    // Detail-panel anchor (computed from screen size in initGui).
    private int detailX, detailY;
    private static final int DETAIL_PAD = 8;
    private static final int RENAME_FIELD_OFFSET_Y = 100;
    private static final int ADDCO_FIELD_OFFSET_Y  = 130;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        detailX = LIST_X + LIST_WIDTH + 14;
        detailY = LIST_TOP;
        renameField = new GuiTextField(fontRenderer,
                detailX + 60, detailY + RENAME_FIELD_OFFSET_Y, 160, 18);
        renameField.setMaxStringLength(24);
        coOwnerField = new GuiTextField(fontRenderer,
                detailX + 90, detailY + ADDCO_FIELD_OFFSET_Y, 130, 18);
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
        // The server-side command pipeline isn't routed to the chat hook here, so
        // re-request the list shortly after so the UI catches up. The request goes
        // over the same packet channel and is processed after the chat command on
        // the server tick.
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
        int listH = height - LIST_TOP - 20;
        for (int i = 0; i < v.size(); i++) {
            int rowY = LIST_TOP + i * ROW_H - scroll;
            if (rowY < LIST_TOP) continue;
            if (rowY + ROW_H > LIST_TOP + listH) continue;
            if (mx >= LIST_X && mx < LIST_X + LIST_WIDTH && my >= rowY && my < rowY + ROW_H) {
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

    /**
     * Build the cage identifier sent over chat. If the caller doesn't own the cage (i.e. an
     * admin acting on someone else's), prefix with {@code owner:} so the server can resolve it
     * unambiguously even if multiple players have a cage of the same name.
     */
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
        drawCenteredString(fontRenderer, "§lCageControl", width / 2, 12, 0xFFFFFF);
        int total = ClientPacketHandler.lastList == null ? 0 : ClientPacketHandler.lastList.size();
        drawCenteredString(fontRenderer,
                "§7" + total + " cage" + (total == 1 ? "" : "s") + " known   "
                        + (ClientPacketHandler.lastIsAdmin ? "§a(admin)" : "§7(player view)"),
                width / 2, 26, 0xCCCCCC);

        btns.clear();

        // View tabs (admin sees both; non-admin sees only "Mine" - just for clarity).
        addBtn(LIST_X, 30, 80, 16, "Mine", !adminView ? 0xFF2E8B57 : 0xFF333333, new Runnable() {
            @Override public void run() { adminView = false; selectedIdx = -1; scroll = 0; }
        });
        if (ClientPacketHandler.lastIsAdmin) {
            addBtn(LIST_X + 84, 30, 80, 16, "All cages", adminView ? 0xFFB22222 : 0xFF333333, new Runnable() {
                @Override public void run() { adminView = true; selectedIdx = -1; scroll = 0; }
            });
        }
        addBtn(LIST_X + LIST_WIDTH - 60, 30, 60, 16, "Refresh", 0xFF445566, new Runnable() {
            @Override public void run() { sendListRequest(); setStatus("Refreshing..."); }
        });

        drawCageList(mx, my);
        drawDetailPanel(mx, my);

        // Status flash.
        if (statusMsg != null && System.currentTimeMillis() < statusUntilMs) {
            drawCenteredString(fontRenderer, "§e" + statusMsg, width / 2, height - 14, 0xFFFFAA00);
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
        int listH = height - LIST_TOP - 20;
        drawRect(LIST_X - 2, LIST_TOP - 2, LIST_X + LIST_WIDTH + 2, LIST_TOP + listH + 2, 0xFF202020);
        drawRect(LIST_X, LIST_TOP, LIST_X + LIST_WIDTH, LIST_TOP + listH, 0xFF101418);

        List<CageData> v = visibleCages();
        if (v.isEmpty()) {
            drawCenteredString(fontRenderer, "§7(no cages to show)",
                    LIST_X + LIST_WIDTH / 2, LIST_TOP + listH / 2 - 4, 0xFFAAAAAA);
            return;
        }
        String me = myUser();
        for (int i = 0; i < v.size(); i++) {
            int rowY = LIST_TOP + i * ROW_H - scroll;
            if (rowY + ROW_H < LIST_TOP) continue;
            if (rowY > LIST_TOP + listH) break;
            CageData d = v.get(i);
            int bg = (i == selectedIdx) ? 0xFF335577 : 0xFF1A1F26;
            if (i != selectedIdx && mx >= LIST_X && mx < LIST_X + LIST_WIDTH && my >= rowY && my < rowY + ROW_H) {
                bg = 0xFF273040; // hover
            }
            drawRect(LIST_X + 2, rowY + 1, LIST_X + LIST_WIDTH - 2, rowY + ROW_H - 1, bg);

            // Status dot.
            int dotColor = d.active ? 0xFF40DD60 : 0xFFAA3333;
            drawRect(LIST_X + 6, rowY + 4, LIST_X + 12, rowY + 10, dotColor);

            // Name + mob.
            boolean mine = d.owner != null && d.owner.equalsIgnoreCase(me);
            String prefix = mine ? "§b" : (d.coOwners.contains(me.toLowerCase()) ? "§d" : "§e");
            drawString(fontRenderer, prefix + d.name + "§r §7- " + d.mobType,
                    LIST_X + 16, rowY + 3, 0xFFFFFF);
            // Sub-line: owner + pos.
            drawString(fontRenderer,
                    "§8t" + d.tier + " §7dim" + d.dim + " §7@ " + d.x + "," + d.y + "," + d.z
                            + "  §8(" + (d.owner == null ? "?" : d.owner) + ")",
                    LIST_X + 16, rowY + 12, 0xFFAAAAAA);
        }
    }

    private void drawDetailPanel(int mx, int my) {
        int px = detailX;
        int py = detailY;
        int pw = width - px - 10;
        int ph = height - py - 20;
        drawRect(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF202020);
        drawRect(px, py, px + pw, py + ph, 0xFF101418);

        CageData d = selected();
        if (d == null) {
            drawCenteredString(fontRenderer, "§7Select a cage on the left.",
                    px + pw / 2, py + ph / 2 - 4, 0xFFAAAAAA);
            return;
        }

        // --- Header (fixed offsets) ---
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

        // --- Rename row (uses fixed-position renameField) ---
        drawString(fontRenderer, "§7Rename:", px + DETAIL_PAD, py + RENAME_FIELD_OFFSET_Y + 5, 0xFFAAAAAA);
        renameField.drawTextBox();
        addBtn(px + DETAIL_PAD + 52 + 162 + 4, py + RENAME_FIELD_OFFSET_Y, 50, 18, "Apply", 0xFF445566,
                new Runnable() { @Override public void run() { actionRename(); } });

        // --- Add co-owner row (uses fixed-position coOwnerField) ---
        String me = myUser();
        boolean canAdd = (d.owner != null && d.owner.equalsIgnoreCase(me)) || ClientPacketHandler.lastIsAdmin;
        if (canAdd) {
            drawString(fontRenderer, "§7Add co-owner:", px + DETAIL_PAD, py + ADDCO_FIELD_OFFSET_Y + 5, 0xFFAAAAAA);
            coOwnerField.drawTextBox();
            addBtn(px + DETAIL_PAD + 82 + 132 + 4, py + ADDCO_FIELD_OFFSET_Y, 40, 18, "Add", 0xFF2E8B57,
                    new Runnable() { @Override public void run() { actionAddCoOwner(); } });
        } else {
            drawString(fontRenderer, "§8(only the owner can manage co-owners)",
                    px + DETAIL_PAD, py + ADDCO_FIELD_OFFSET_Y + 5, 0xFF888888);
        }

        // --- Co-owners list (below the input rows) ---
        int cy = py + 160;
        drawString(fontRenderer, "§7Co-owners:", px + DETAIL_PAD, cy, 0xFFAAAAAA); cy += 11;
        if (d.coOwners.isEmpty()) {
            drawString(fontRenderer, "§8(none)", px + DETAIL_PAD + 6, cy, 0xFF888888);
        } else {
            int n = 0;
            for (final String co : d.coOwners) {
                if (n >= 6) { drawString(fontRenderer, "§8...", px + DETAIL_PAD + 6, cy, 0xFF888888); break; }
                drawString(fontRenderer, "§f" + co, px + DETAIL_PAD + 6, cy + 5, 0xFFFFFFFF);
                if (canAdd) {
                    addBtn(px + DETAIL_PAD + 110, cy + 2, 60, 14, "Remove", 0xFFAA5555,
                            new Runnable() { @Override public void run() { actionRemoveCoOwner(co); } });
                }
                cy += 16;
                n++;
            }
        }
    }
}
