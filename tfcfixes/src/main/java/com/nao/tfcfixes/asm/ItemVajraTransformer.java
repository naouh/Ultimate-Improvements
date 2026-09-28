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
 * Companion to {@link MazestoneVajraTransformer}. That one stops left-click mining of mazestone with
 * an IC2 electric tool; this one refuses the GraviSuite Vajra's "accurate" right-click mode
 * ({@code onItemUse}), which breaks the targeted block directly without {@code harvestBlock}. It does
 * not destroy the Vajra, but it would still bypass the mazestone restriction.
 *
 * <p>Target: {@code gravisuite.ItemVajra.onItemUse(ItemStack, EntityPlayer, World, int x, int y,
 * int z, int side, float, float, float)Z} — obf {@code a(Lur;Lqx;Lyc;IIIIFFF)Z}. It is a mod class,
 * so the class name is never remapped; the method is matched by name (obf / SRG / MCP) plus shape.
 * Injected at method entry:
 * <pre>
 *     if (MazestoneHook.isMazestoneAt(world, x, y, z)) return false;
 * </pre>
 *
 * <p>Ported from the UpsilonFixes NilLoader patch of the same name.
 */
public class ItemVajraTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "gravisuite.ItemVajra";
    private static final String TARGET_SLASH = "gravisuite/ItemVajra";

    private static final String HOOK = "com/nao/tfcfixes/MazestoneHook";

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
                if (!isOnItemUse(m)) continue;
                injectGuard(m);
                patched++;
            }

            if (patched != 1) {
                System.err.println("[TFCFixes] ItemVajra: onItemUse matched " + patched
                        + " method(s) - leaving the Vajra untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] ItemVajra: accurate mode refused on Twilight Forest mazestone");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] ItemVajra transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** {@code boolean x(ItemStack, EntityPlayer, World, int, int, int, int, float, float, float)}. */
    private static boolean isOnItemUse(MethodNode m) {
        if (!"a".equals(m.name) && !"func_77648_a".equals(m.name) && !"onItemUse".equals(m.name)) return false;
        if ((m.access & Opcodes.ACC_STATIC) != 0 || m.desc == null) return false;
        if (Type.getReturnType(m.desc).getSort() != Type.BOOLEAN) return false;
        Type[] a = Type.getArgumentTypes(m.desc);
        if (a.length != 10) return false;
        for (int i = 0; i < 3; i++) if (a[i].getSort() != Type.OBJECT) return false;
        for (int i = 3; i < 7; i++) if (a[i].getSort() != Type.INT) return false;
        for (int i = 7; i < 10; i++) if (a[i].getSort() != Type.FLOAT) return false;
        return true;
    }

    /** Locals: 0 this, 1 stack, 2 player, 3 world, 4 x, 5 y, 6 z, 7 side, 8-10 hit. */
    private static void injectGuard(MethodNode m) {
        LabelNode allow = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 3));
        li.add(new VarInsnNode(Opcodes.ILOAD, 4));
        li.add(new VarInsnNode(Opcodes.ILOAD, 5));
        li.add(new VarInsnNode(Opcodes.ILOAD, 6));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "isMazestoneAt", "(Ljava/lang/Object;III)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, allow));
        li.add(new InsnNode(Opcodes.ICONST_0));
        li.add(new InsnNode(Opcodes.IRETURN));
        li.add(allow);
        m.instructions.insert(li);
    }
}
