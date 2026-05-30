package com.cagecontrol;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;

/**
 * Belt-and-braces poll for the menu key — see {@link CageKeyHandler#pollFallback()}.
 */
public class CageKeyTickHandler implements ITickHandler {

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        CageKeyHandler.pollFallback();
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override
    public String getLabel() { return "CageControlKeyPoll"; }
}
