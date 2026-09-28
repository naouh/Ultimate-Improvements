package com.nao.tfcfixes.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Stops IC2 electric tools (the GraviSuite Vajra in particular) from mining Twilight Forest
 * mazestone, which would otherwise destroy them. See {@link com.nao.tfcfixes.MazestoneHook}.
 *
 * <p>Patches {@code Block.getPlayerRelativeBlockHardness(EntityPlayer, World, int, int, int)}
 * (obf {@code amq.a(Lqx;Lyc;III)F}, SRG {@code func_71908_a}). {@code BlockTFMazestone} does not
 * override it, so patching the base method covers it. Injected at method entry:
 * <pre>
 *     if (MazestoneHook.blockMazestoneMining(this, player)) return 0.0F;
 * </pre>
 *
 * <p>Ported from the UpsilonFixes NilLoader patch of the same name. As a plain FML transformer it
 * matches the method by name (obf / SRG / MCP) plus shape, so it works both on the obfuscated client
 * and on MCPC+, where the class arrives under its clean name.
 */
public class MazestoneVajraTransformer implements IClassTransformer {

    private static final String BLOCK_OBF         = "amq";
    private static final String BLOCK_CLEAN       = "net.minecraft.block.Block";
    private static final String BLOCK_CLEAN_SLASH = "net/minecraft/block/Block";

    private static final String HOOK = "com/nao/tfcfixes/MazestoneHook";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!BLOCK_OBF.equals(name) && !BLOCK_CLEAN.equals(name) && !BLOCK_CLEAN_SLASH.equals(name)) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!isGetPlayerRelativeBlockHardness(m)) continue;
                injectGuard(m);
                patched++;
            }

            if (patched != 1) {
                // 0 = not found; >1 = ambiguous shape, refuse rather than patch the wrong method.
                System.err.println("[TFCFixes] MazestoneVajra: getPlayerRelativeBlockHardness matched "
                        + patched + " method(s) in " + name + " - leaving Block untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] MazestoneVajra: IC2 electric tools can no longer mine Twilight Forest mazestone");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] MazestoneVajra transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** {@code float x(EntityPlayer, World, int, int, int)} under its obf, SRG or MCP name. */
    private static boolean isGetPlayerRelativeBlockHardness(MethodNode m) {
        if (!"a".equals(m.name) && !"func_71908_a".equals(m.name)
                && !"getPlayerRelativeBlockHardness".equals(m.name)) return false;
        if ((m.access & Opcodes.ACC_STATIC) != 0 || m.desc == null) return false;
        if (Type.getReturnType(m.desc).getSort() != Type.FLOAT) return false;
        Type[] a = Type.getArgumentTypes(m.desc);
        return a.length == 5
                && a[0].getSort() == Type.OBJECT && a[1].getSort() == Type.OBJECT
                && a[2].getSort() == Type.INT && a[3].getSort() == Type.INT && a[4].getSort() == Type.INT;
    }

    private static void injectGuard(MethodNode m) {
        LabelNode allow = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (Block)
        li.add(new VarInsnNode(Opcodes.ALOAD, 1)); // player
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "blockMazestoneMining", "(Ljava/lang/Object;Ljava/lang/Object;)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, allow));
        li.add(new InsnNode(Opcodes.FCONST_0));
        li.add(new InsnNode(Opcodes.FRETURN));
        li.add(allow);
        m.instructions.insert(li);
    }
}
