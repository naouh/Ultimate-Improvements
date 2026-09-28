package com.nao.tfcfixes.asm;

import java.util.ArrayList;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Rewrites a method so its whole body runs inside a {@code synchronized} block on a monitor of our
 * choosing: either a {@code static final} field (one shared lock, see {@link AppEngLockTransformer})
 * or the instance itself ({@code this}, see {@link CcPeripheralLockTransformer}).
 *
 * <p>Strips {@code ACC_SYNCHRONIZED} (so the JVM's implicit instance monitor is no longer taken by
 * the method flag), enters the monitor at entry, exits it before every {@code return}, and adds a
 * catch-all handler that exits the monitor and rethrows on any uncaught throwable. The lock is
 * re-loaded for each {@code monitorexit} rather than spilled to a local — a {@code static final}
 * field and {@code this} both never change — so no new local slots are needed and
 * {@link org.objectweb.asm.ClassWriter#COMPUTE_MAXS} fixes up {@code maxStack}. The catch-all is
 * appended <em>last</em> in the exception table so the method's own handlers keep priority.
 * Targets are Java 6 (v50) classes, so no StackMapTable frames are required.
 */
final class MonitorWrap {

    private MonitorWrap() {}

    /** Wrap {@code m} in {@code synchronized (Owner.name)} where the field is {@code static final}. */
    static void withStaticField(MethodNode m, String owner, String name, String desc) {
        wrap(m, owner, name, desc);
    }

    /** Wrap the non-static method {@code m} in {@code synchronized (this)}. */
    static void withThis(MethodNode m) {
        wrap(m, null, null, null);
    }

    private static AbstractInsnNode loadLock(String owner, String name, String desc) {
        return owner == null
                ? new VarInsnNode(Opcodes.ALOAD, 0)
                : new FieldInsnNode(Opcodes.GETSTATIC, owner, name, desc);
    }

    private static void wrap(MethodNode m, String owner, String name, String desc) {
        m.access &= ~Opcodes.ACC_SYNCHRONIZED;

        InsnList insns = m.instructions;
        LabelNode start   = new LabelNode();
        LabelNode handler = new LabelNode();

        // monitorenter at entry: <load lock>; MONITORENTER; start:
        InsnList pre = new InsnList();
        pre.add(loadLock(owner, name, desc));
        pre.add(new InsnNode(Opcodes.MONITORENTER));
        pre.add(start);
        insns.insert(pre);

        // monitorexit before every normal return
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                InsnList rel = new InsnList();
                rel.add(loadLock(owner, name, desc));
                rel.add(new InsnNode(Opcodes.MONITOREXIT));
                insns.insertBefore(insn, rel);
            }
        }

        // catch-all handler (after the body): monitorexit + rethrow
        InsnList post = new InsnList();
        post.add(handler);
        post.add(loadLock(owner, name, desc));
        post.add(new InsnNode(Opcodes.MONITOREXIT));
        post.add(new InsnNode(Opcodes.ATHROW));
        insns.add(post);

        if (m.tryCatchBlocks == null) m.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
        // end == handler label: protect [start, handler), handler itself is outside the range.
        m.tryCatchBlocks.add(new TryCatchBlockNode(start, handler, handler, null));
    }
}
