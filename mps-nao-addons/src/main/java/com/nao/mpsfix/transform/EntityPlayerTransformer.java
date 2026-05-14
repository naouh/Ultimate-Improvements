package com.nao.mpsfix.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches obfuscated {@code qx.a(Lamq;)F} (the MC 1.4.7 EntityPlayer.
 * getCurrentPlayerStrVsBlock method). Anchors specifically on the
 * {@code GETFIELD onGround} that's immediately followed by {@code IFNE} and a
 * {@code LDC 5.0F + FDIV} — i.e. the airborne dig-speed penalty. Other
 * {@code GETFIELD onGround} occurrences in this method (if any) are skipped.
 *
 * <p>Injection right after the GETFIELD ORs in the Air Stride module check,
 * so the IFNE skips the {@code /5} division whenever {@code onGround} OR the
 * module is active.
 */
public class EntityPlayerTransformer implements IClassTransformer {

    private static final String TARGET_OBF        = "qx";
    private static final String METHOD_OBF        = "a";
    private static final String METHOD_DESC_OBF   = "(Lamq;)F"; // getCurrentPlayerStrVsBlock(Block)F

    private static final String TARGET_CLEAN      = "net.minecraft.entity.player.EntityPlayer";
    private static final String METHOD_CLEAN      = "getCurrentPlayerStrVsBlock";

    private static final String ON_GROUND_FIELD   = "E";   // 1.4.7 obf for Entity.onGround

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        // Accept obf and clean names, in both dot and slash form.
        if (!TARGET_OBF.equals(name)
                && !TARGET_CLEAN.equals(name)
                && !"net/minecraft/entity/player/EntityPlayer".equals(name)) {
            return bytes;
        }
        System.out.println("[MpsFlightFix] EntityPlayerTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!matchesGetStrVsBlock(m)) continue;
                if (patchAirbornePenalty(m)) patched++;
            }

            if (patched == 0) {
                System.err.println("[MpsFlightFix] EntityPlayer: airborne penalty pattern not found");
                return bytes;
            }

            // COMPUTE_MAXS only — COMPUTE_FRAMES tries to resolve referenced
            // classes which deadlocks during the class-load that we're in.
            // Java 6 / MC 1.4.7 doesn't need StackMapTable frames anyway.
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsFlightFix] Patched " + patched + " variant(s) of EntityPlayer.getCurrentPlayerStrVsBlock");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] EntityPlayer transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** Accept obf {@code a(Lamq;)F}, obf {@code a(Lamq;I)F} (Forge-patched 2-arg
     *  variant), or any clean name {@code getCurrentPlayerStrVsBlock}. */
    private static boolean matchesGetStrVsBlock(MethodNode m) {
        if (METHOD_CLEAN.equals(m.name)) return true;
        if (!METHOD_OBF.equals(m.name)) return false;
        // Match obf descriptors that return float.
        return m.desc != null
            && (m.desc.equals("(Lamq;)F") || m.desc.equals("(Lamq;I)F"));
    }

    /**
     * Find the {@code GETFIELD onGround} that's directly part of the airborne
     * penalty: followed by {@code IFNE} then {@code LDC 5.0F} then {@code FDIV}.
     * Inject {@code | hasActiveModule(player)} into the boolean on the stack.
     */
    private static boolean patchAirbornePenalty(MethodNode m) {
        AbstractInsnNode insn = m.instructions.getFirst();
        while (insn != null) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) insn;
                if (f.getOpcode() == Opcodes.GETFIELD
                        && ON_GROUND_FIELD.equals(f.name)
                        && "Z".equals(f.desc)
                        && isFollowedByAirbornePenalty(insn)) {
                    inject(m, insn);
                    return true;
                }
            }
            insn = insn.getNext();
        }
        return false;
    }

    /** Confirm GETFIELD is the airborne-penalty one by looking ahead for
     *  {@code IFNE ... LDC 5.0F ... FDIV} within a small window. */
    private static boolean isFollowedByAirbornePenalty(AbstractInsnNode getfield) {
        AbstractInsnNode n = getfield.getNext();
        // Skip line numbers / labels / frames.
        n = skipMeta(n);
        if (!(n instanceof org.objectweb.asm.tree.JumpInsnNode)) return false;
        if (n.getOpcode() != Opcodes.IFNE) return false;
        // Within next 8 real instructions, expect LDC 5.0F + FDIV.
        AbstractInsnNode p = n.getNext();
        for (int i = 0; i < 12 && p != null; i++) {
            p = skipMeta(p);
            if (p == null) break;
            if (p instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) p).cst;
                if (cst instanceof Float && ((Float) cst).floatValue() == 5.0F) {
                    AbstractInsnNode q = skipMeta(p.getNext());
                    return q != null && q.getOpcode() == Opcodes.FDIV;
                }
            }
            p = p.getNext();
        }
        return false;
    }

    private static AbstractInsnNode skipMeta(AbstractInsnNode n) {
        while (n != null && (n.getType() == AbstractInsnNode.LINE
                          || n.getType() == AbstractInsnNode.LABEL
                          || n.getType() == AbstractInsnNode.FRAME)) {
            n = n.getNext();
        }
        return n;
    }

    private static void inject(MethodNode m, AbstractInsnNode anchor) {
        // Stack after anchor: [Z onGround]
        // Inject: replace with onGround | hasActiveModule(this)
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (EntityPlayer)
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/nao/mpsfix/AirStrideHelper",
                "hasActiveModule",
                "(L" + TARGET_OBF + ";)Z"));
        li.add(new InsnNode(Opcodes.IOR));
        m.instructions.insert(anchor, li);
    }
}
