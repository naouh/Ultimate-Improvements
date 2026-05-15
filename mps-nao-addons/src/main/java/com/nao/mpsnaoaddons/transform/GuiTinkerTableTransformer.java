package com.nao.mpsnaoaddons.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Enlarges MPS' Tinker Table GUI so module rows and tooltips have room to
 * breathe. With multiple Tool-category modules installed (OmniWrench,
 * EU Reader, TE Multimeter), the stock 256x200 window crowds icons against
 * the tweak frame and pushes long hover descriptions over the icon grid.
 * Bumping to 320x220 keeps every frame at its existing relative offsets
 * but gives ~25% more horizontal pixels per row.
 *
 * <p>Strategy: walk {@code GuiTinkerTable.<init>}, find the {@code PUTFIELD}
 * instructions for {@code xSize}/{@code ySize}, and rewrite the integer push
 * that precedes each one. Field-name matching means we don't have to know
 * the owner (the fields are inherited from {@code MuseGui}).
 */
public class GuiTinkerTableTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "net.machinemuse.powersuits.block.GuiTinkerTable";
    private static final String TARGET_SLASH = "net/machinemuse/powersuits/block/GuiTinkerTable";

    private static final int NEW_X = 384;
    private static final int NEW_Y = 232;

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;
        System.out.println("[MpsNaoAddons] GuiTinkerTableTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!"<init>".equals(m.name)) continue;
                patched += rewriteSizeFields(m);
            }
            if (patched == 0) {
                System.err.println("[MpsNaoAddons] GuiTinkerTable: xSize/ySize PUTFIELDs not found");
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsNaoAddons] Resized Tinker Table GUI to " + NEW_X + "x" + NEW_Y
                    + " (" + patched + " field write(s) patched)");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsNaoAddons] GuiTinkerTableTransformer failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** Replace the integer push directly before each PUTFIELD xSize/ySize. */
    private static int rewriteSizeFields(MethodNode m) {
        int n = 0;
        AbstractInsnNode insn = m.instructions.getFirst();
        while (insn != null) {
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) insn;
                if (f.getOpcode() == Opcodes.PUTFIELD && "I".equals(f.desc)) {
                    if ("xSize".equals(f.name) && replacePrecedingIntPush(m, insn, NEW_X)) n++;
                    else if ("ySize".equals(f.name) && replacePrecedingIntPush(m, insn, NEW_Y)) n++;
                }
            }
            insn = insn.getNext();
        }
        return n;
    }

    /**
     * Walk backwards from {@code putfield} past metadata nodes to the real
     * instruction that pushed the constant; if it's a recognised int-push
     * (BIPUSH/SIPUSH/LDC/ICONST_*), swap it for a {@code SIPUSH newVal}.
     */
    private static boolean replacePrecedingIntPush(MethodNode m, AbstractInsnNode putfield, int newVal) {
        AbstractInsnNode prev = putfield.getPrevious();
        while (prev != null && (prev.getType() == AbstractInsnNode.LINE
                              || prev.getType() == AbstractInsnNode.LABEL
                              || prev.getType() == AbstractInsnNode.FRAME)) {
            prev = prev.getPrevious();
        }
        if (prev == null) return false;
        int op = prev.getOpcode();
        boolean isIntPush = op == Opcodes.BIPUSH || op == Opcodes.SIPUSH
                || op == Opcodes.LDC
                || (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5);
        if (!isIntPush) return false;
        m.instructions.set(prev, new IntInsnNode(Opcodes.SIPUSH, newVal));
        return true;
    }
}
