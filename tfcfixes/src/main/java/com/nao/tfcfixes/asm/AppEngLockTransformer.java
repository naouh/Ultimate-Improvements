package com.nao.tfcfixes.asm;

import java.util.ArrayList;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Makes Applied Energistics (rv9) safe under TickThreading by serializing its network locking and
 * fixing a level-emitter visibility race. Background and rationale live on {@link com.nao.tfcfixes.AppEngLock}.
 *
 * <h3>{@code TileController} — deadlock fix</h3>
 * AE guards the controller tick ({@code g()}), item injection ({@code signalInput}) and
 * {@code resetWaitingQueue} with {@code synchronized} on the instance. A controller tick traverses
 * the whole network and reaches into neighbouring controllers' synchronized methods, so two
 * controllers ticked on different TickThreading threads acquire each other's monitors in opposite
 * order → AB-BA deadlock. Verified against the rv9 jar: these are the <em>only</em> places a
 * controller monitor is taken (no {@code synchronized} blocks anywhere, nothing locks a controller
 * from outside), so it is safe to move all of them off the per-instance monitor.
 *
 * <p>For every non-static {@code synchronized} method on the controller this transformer strips
 * {@code ACC_SYNCHRONIZED} and wraps the body in {@code synchronized (AppEngLock.LOCK)} — a single
 * shared monitor. One lock for all controllers makes opposite-order acquisition impossible, and the
 * cross-controller traversal re-enters the reentrant monitor on the same thread.
 *
 * <h3>{@code TileLevelEmitter} — never-toggling fix</h3>
 * The emitter tick ({@code g()}) reads the network ({@code getGrid().getCellArray()}) without any
 * lock and writes its {@code currentState} field, which is plain (non-volatile). The redstone system
 * reads {@code currentState} from the main server thread, so writes made on a TickThreading worker
 * may never become visible → the emitter "never activates/deactivates"; and the unlocked network read
 * can iterate a cell list while a controller mutates it. Fix: mark {@code currentState} {@code volatile}
 * for cross-thread visibility, and wrap {@code g()} in the same global lock so the network read can't
 * run concurrently with a controller tick / item injection.
 *
 * <p>Fail-safe: if the target methods/field aren't found (different AE build, deobf names, mod absent)
 * the original bytes are returned and the pack keeps running.
 */
public class AppEngLockTransformer implements IClassTransformer {

    private static final String TC_DOT   = "appeng.me.tile.TileController";
    private static final String TC_SLASH = "appeng/me/tile/TileController";
    private static final String LE_DOT   = "appeng.me.tile.TileLevelEmitter";
    private static final String LE_SLASH = "appeng/me/tile/TileLevelEmitter";

    private static final String LOCK_OWNER = "com/nao/tfcfixes/AppEngLock";
    private static final String LOCK_NAME  = "LOCK";
    private static final String LOCK_DESC  = "Ljava/lang/Object;";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean isController = TC_DOT.equals(name) || TC_SLASH.equals(name);
        boolean isEmitter    = LE_DOT.equals(name) || LE_SLASH.equals(name);
        if (!isController && !isEmitter) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int changed = isController ? patchController(cn) : patchEmitter(cn);
            if (changed == 0) {
                System.err.println("[TFCFixes] AppEngLock: nothing patched in " + name
                        + " (unexpected AE build?) — leaving it untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] AppEngLock: serialized " + changed + " method(s)/field(s) in "
                    + name + " onto the global AE lock");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] AppEngLock transform failed for " + name + ":");
            t.printStackTrace();
            return bytes;
        }
    }

    /** Move every non-static {@code synchronized} controller method off the per-instance monitor. */
    private static int patchController(ClassNode cn) {
        int n = 0;
        for (Object o : cn.methods) {
            MethodNode m = (MethodNode) o;
            boolean sync   = (m.access & Opcodes.ACC_SYNCHRONIZED) != 0;
            boolean stat   = (m.access & Opcodes.ACC_STATIC) != 0;
            boolean special = "<init>".equals(m.name) || "<clinit>".equals(m.name);
            if (sync && !stat && !special) {
                wrapWithGlobalLock(m);
                n++;
            }
        }
        return n;
    }

    /** Make {@code currentState} volatile and run the emitter tick under the global lock. */
    private static int patchEmitter(ClassNode cn) {
        int n = 0;
        for (Object o : cn.fields) {
            FieldNode f = (FieldNode) o;
            if ("currentState".equals(f.name) && "Z".equals(f.desc)) {
                f.access |= Opcodes.ACC_VOLATILE;
                n++;
            }
        }
        for (Object o : cn.methods) {
            MethodNode m = (MethodNode) o;
            boolean tick = ("g".equals(m.name) || "updateEntity".equals(m.name)) && "()V".equals(m.desc);
            if (tick && (m.access & Opcodes.ACC_STATIC) == 0) {
                wrapWithGlobalLock(m);
                n++;
            }
        }
        return n;
    }

    /**
     * Rewrites a method so its whole body runs inside {@code synchronized (AppEngLock.LOCK)}.
     *
     * <p>Strips {@code ACC_SYNCHRONIZED} (so the instance monitor is no longer taken), enters the
     * shared monitor at entry, exits it before every {@code return}, and adds a catch-all handler
     * that exits the monitor and rethrows on any uncaught throwable. The lock is a {@code static final}
     * field, so it is re-loaded for each {@code monitorexit} rather than spilled to a local — no new
     * local slots, and {@link ClassWriter#COMPUTE_MAXS} fixes up {@code maxStack}. The catch-all is
     * appended <em>last</em> in the table so the method's own handlers keep priority.
     */
    private static void wrapWithGlobalLock(MethodNode m) {
        m.access &= ~Opcodes.ACC_SYNCHRONIZED;

        InsnList insns = m.instructions;
        LabelNode start   = new LabelNode();
        LabelNode handler = new LabelNode();

        // monitorenter at entry: GETSTATIC LOCK; MONITORENTER; start:
        InsnList pre = new InsnList();
        pre.add(new FieldInsnNode(Opcodes.GETSTATIC, LOCK_OWNER, LOCK_NAME, LOCK_DESC));
        pre.add(new InsnNode(Opcodes.MONITORENTER));
        pre.add(start);
        insns.insert(pre);

        // monitorexit before every normal return
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                InsnList rel = new InsnList();
                rel.add(new FieldInsnNode(Opcodes.GETSTATIC, LOCK_OWNER, LOCK_NAME, LOCK_DESC));
                rel.add(new InsnNode(Opcodes.MONITOREXIT));
                insns.insertBefore(insn, rel);
            }
        }

        // catch-all handler (after the body): monitorexit + rethrow
        InsnList post = new InsnList();
        post.add(handler);
        post.add(new FieldInsnNode(Opcodes.GETSTATIC, LOCK_OWNER, LOCK_NAME, LOCK_DESC));
        post.add(new InsnNode(Opcodes.MONITOREXIT));
        post.add(new InsnNode(Opcodes.ATHROW));
        insns.add(post);

        if (m.tryCatchBlocks == null) m.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
        // end == handler label: protect [start, handler), handler itself is outside the range.
        m.tryCatchBlocks.add(new TryCatchBlockNode(start, handler, handler, null));
    }
}
