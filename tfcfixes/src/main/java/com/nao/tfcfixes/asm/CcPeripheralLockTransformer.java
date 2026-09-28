package com.nao.tfcfixes.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Fixes a ComputerCraft 1.5 deadlock under TickThreading (seen on the Expert server, a computer in a
 * base at x4150 z4150):
 *
 * <pre>
 *   Server thread  : Computer.setPeripheral            -> synchronized(m_peripherals)           [IPeripheral[]]
 *                    -> PeripheralAPI.onPeripheralChanged -> synchronized(m_peripherals)         [PeripheralWrapper[]]
 *                    -> Computer.queueLuaEvent           -> synchronized(this)  BLOCKED          [Computer]
 *   TT tick thread : Computer.advance                   -> synchronized(this)                    [Computer]
 *                    -> PeripheralAPI.advance            -> synchronized(m_peripherals) BLOCKED  [PeripheralWrapper[]]
 * </pre>
 *
 * In vanilla both paths run on the main thread, so the opposite lock orders never meet. With
 * TickThreading the computer's tick ({@code advance}) runs on a region worker while a neighbour
 * block change (here a Vajra breaking a block next to the computer, handled on the server thread)
 * calls {@code setPeripheral}: AB-BA deadlock, and the computer's own Lua thread piles up behind it.
 *
 * <p>Fix: wrap {@code Computer.setPeripheral(int, IPeripheral)} in {@code synchronized (this)}. The
 * neighbour-change path then takes the {@code Computer} monitor <em>first</em> — the same first lock
 * as {@code advance} — before the two array monitors, and the later {@code queueLuaEvent} simply
 * re-enters it. Both paths now lock in the order Computer → arrays, so the cycle cannot form. The
 * server thread at most waits for one (short) computer tick to finish.
 *
 * <p>Fail-safe: if the class/method shape differs (other CC build), the original bytes are returned.
 */
public class CcPeripheralLockTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "dan200.computer.core.Computer";
    private static final String TARGET_SLASH = "dan200/computer/core/Computer";

    private static final String METHOD_NAME = "setPeripheral";
    private static final String METHOD_DESC = "(ILdan200/computer/api/IPeripheral;)V";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!METHOD_NAME.equals(m.name) || !METHOD_DESC.equals(m.desc)) continue;
                if ((m.access & Opcodes.ACC_STATIC) != 0) continue;
                MonitorWrap.withThis(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[TFCFixes] CcPeripheralLock: Computer.setPeripheral not found (other CC build?) — leaving it untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] CcPeripheralLock: Computer.setPeripheral now takes the Computer monitor first (TickThreading deadlock fix)");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] CcPeripheralLock transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }
}
