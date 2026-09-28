package com.nao.tfcfixes;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry for TFCFixes — a bundle of small ASM bug fixes for the Ultimate Remastered
 * (1.4.7) modpack.
 *
 * Each transformer listed here is independently fail-safe: if its target class/method isn't
 * found at load time (different mod version, different obfuscation, mod absent) it logs and
 * returns the original bytes, so the pack keeps running. To add a new fix, write a transformer
 * under {@code com.nao.tfcfixes.asm} and append its fully-qualified name to the array below.
 */
public class TFCFixesCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            // Mystcraft: silence writing-desk worldgen tile-entity spam.
            "com.nao.tfcfixes.asm.WritingDeskTransformer",
            // IC2 / AdvancedMachines / GregTech: ClassCastException fixes (merged from ic2netfix).
            "com.nao.tfcfixes.asm.NetworkManagerTransformer",
            "com.nao.tfcfixes.asm.BlockMultiIDTransformer",
            "com.nao.tfcfixes.asm.GtMetaMachineItemTransformer",
            // Post-teleport/login TileEntity sync race: window-items crash + ghost interaction
            // (merged from windowitemsfix).
            "com.nao.tfcfixes.asm.RightClickGuardTransformer",
            "com.nao.tfcfixes.asm.ContainerTransformer",
            // Applied Energistics (rv9) under TickThreading: serialize controller network locking
            // onto a single global monitor (kills the cross-region AB-BA deadlock + subnet
            // corruption) and fix the level-emitter visibility race.
            "com.nao.tfcfixes.asm.AppEngLockTransformer",
            // Server-side: re-send the open container in full after every window click so the
            // client's slot view can't stay stale (crafted/grabbed items not showing until you
            // click again) — the 1.4.7 transaction race, worse under TickThreading.
            "com.nao.tfcfixes.asm.WindowClickSyncTransformer",
            // ComputerCraft 1.5 under TickThreading: Computer.setPeripheral (neighbour change on the
            // server thread) and Computer.advance (region worker) took the Computer / peripheral-array
            // monitors in opposite order -> AB-BA deadlock. setPeripheral now takes Computer first.
            "com.nao.tfcfixes.asm.CcPeripheralLockTransformer",
            // Twilight Forest mazestone destroys IC2 electric tools (GraviSuite Vajra): make it
            // unminable with them, and refuse the Vajra's accurate right-click mode on it.
            // (Ported from UpsilonFixes.)
            "com.nao.tfcfixes.asm.MazestoneVajraTransformer",
            "com.nao.tfcfixes.asm.ItemVajraTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}
