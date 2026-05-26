package com.nao.claimteam.client;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;

/**
 * Belt-and-braces poll for the open-map key. {@link ClaimKeyHandler}'s {@code keyDown} hook
 * should fire correctly, but 1.4.7's KeyBindingRegistry has been known to skip some bindings
 * in heavily-modded packs, so we also poll the key state ourselves every client tick.
 */
public class ClaimKeyTickHandler implements ITickHandler {

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        ClaimKeyHandler.pollFallback();
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override
    public String getLabel() { return "ClaimTeamKeyPoll"; }
}
