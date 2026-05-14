package com.cagecontrol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.network.packet.Packet250CustomPayload;
import org.lwjgl.input.Keyboard;

public class GuiCageName extends GuiScreen {

    private GuiTextField nameField;
    private final int cx, cy, cz;
    private static final int FIELD_ID  = 100;
    private static final int BTN_OK    = 0;
    private static final int BTN_CANCEL = 1;

    public GuiCageName(int x, int y, int z) {
        this.cx = x; this.cy = y; this.cz = z;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        FontRenderer fr = mc.fontRenderer;
        int fx = width / 2 - 100;
        int fy = height / 2 - 10;
        nameField = new GuiTextField(fr, fx, fy, 200, 20);
        nameField.setMaxStringLength(24);
        nameField.setFocused(true);

        controlList.clear();
        controlList.add(new GuiButton(BTN_OK,     width / 2 - 100, height / 2 + 20, 95, 20, "Confirm"));
        controlList.add(new GuiButton(BTN_CANCEL, width / 2 + 5,   height / 2 + 20, 95, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == BTN_OK)     submit();
        else if (b.id == BTN_CANCEL) mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            submit();
            return;
        }
        nameField.textboxKeyTyped(c, code);
    }

    @Override
    protected void mouseClicked(int x, int y, int btn) {
        super.mouseClicked(x, y, btn);
        nameField.mouseClicked(x, y, btn);
    }

    private void submit() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) return;
        sendNamePacket(cx, cy, cz, name);
        mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(mc.fontRenderer, "Name the cage", width / 2, height / 2 - 40, 0xFFFFFF);
        drawCenteredString(mc.fontRenderer, "(A-Z 0-9 _ -)", width / 2, height / 2 - 25, 0xAAAAAA);
        nameField.drawTextBox();
        super.drawScreen(mx, my, pt);
    }

    private static void sendNamePacket(int x, int y, int z, String name) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PacketHandler.PKT_SET_NAME);
            dos.writeInt(x); dos.writeInt(y); dos.writeInt(z);
            dos.writeUTF(name);
        } catch (IOException e) {
            return;
        }
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = CageControl.CHANNEL;
        pkt.data    = bos.toByteArray();
        pkt.length  = pkt.data.length;
        PacketDispatcher.sendPacketToServer(pkt);
    }
}
