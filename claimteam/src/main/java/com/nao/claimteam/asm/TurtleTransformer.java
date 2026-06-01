package com.nao.claimteam.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
 * Keeps a ComputerCraft turtle out of other teams' claims. Patches the (private) methods
 * {@code dan200.turtle.shared.TileEntityTurtle.move(I)Z} and {@code dig(I)Z}, injecting at entry:
 *
 * <pre>
 *     if (!ClaimAsmHelper.turtleCanAct(this, dir)) return false;
 * </pre>
 *
 * A turtle therefore can't move across a team boundary (so it can never get inside a foreign claim)
 * nor dig across one — but it works freely in its own claim and in the wild. Expanded / RedPower
 * turtle subclasses run the same parent methods, so one patch covers them all. {@code TileEntityTurtle}
 * is a ComputerCraft class (never obfuscated) and the methods take only an int, so the clean
 * name/descriptor matches in both dev and production.
 */
public class TurtleTransformer implements IClassTransformer {

    private static final String TARGET = "dan200.turtle.shared.TileEntityTurtle";
    private static final String DESC   = "(I)Z";

    private static final String HELPER_OWNER = "com/nao/claimteam/asm/ClaimAsmHelper";
    private static final String HELPER_NAME  = "turtleCanAct";
    private static final String HELPER_DESC  = "(Ljava/lang/Object;I)Z";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null || !TARGET.equals(name)) return bytes;
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!DESC.equals(m.desc)) continue;
                if (!("move".equals(m.name) || "dig".equals(m.name))) continue;

                LabelNode cont = new LabelNode();
                InsnList li = new InsnList();
                li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (the turtle tile)
                li.add(new VarInsnNode(Opcodes.ILOAD, 1)); // dir
                li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER_OWNER, HELPER_NAME, HELPER_DESC));
                li.add(new JumpInsnNode(Opcodes.IFNE, cont)); // allowed -> fall through
                li.add(new InsnNode(Opcodes.ICONST_0));
                li.add(new InsnNode(Opcodes.IRETURN));        // blocked -> return false
                li.add(cont);
                m.instructions.insert(li);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[ClaimTeam] TileEntityTurtle move/dig not found in " + name);
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[ClaimTeam] Patched TileEntityTurtle move/dig (" + patched + " method(s))");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[ClaimTeam] Turtle transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }
}
