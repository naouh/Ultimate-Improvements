package com.nao.claimteam.asm;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Stops a BuildCraft Quarry from digging into another team's claim. Patches
 * {@code buildcraft.factory.TileQuarry.isQuarriableBlock(III)Z} so each return becomes:
 *
 * <pre>
 *     return &lt;original&gt; &amp;&amp; ClaimAsmHelper.machineCanBreak(this, bx, by, bz);
 * </pre>
 *
 * Blocks in a foreign claim are no longer considered quarriable, so the quarry simply skips them.
 * {@code TileQuarry} is a BuildCraft class (never obfuscated), so only the clean name is matched.
 */
public class QuarryTransformer implements IClassTransformer {

    private static final String TARGET = "buildcraft.factory.TileQuarry";
    private static final String METHOD = "isQuarriableBlock";
    private static final String DESC   = "(III)Z";

    private static final String HELPER_OWNER = "com/nao/claimteam/asm/ClaimAsmHelper";
    private static final String HELPER_NAME  = "machineCanBreak";
    private static final String HELPER_DESC  = "(Ljava/lang/Object;III)Z";

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
                if (!METHOD.equals(m.name) || !DESC.equals(m.desc)) continue;

                List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.IRETURN) returns.add(insn);
                }
                for (AbstractInsnNode ret : returns) {
                    InsnList li = new InsnList();
                    li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (the quarry tile)
                    li.add(new VarInsnNode(Opcodes.ILOAD, 1)); // bx
                    li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // by
                    li.add(new VarInsnNode(Opcodes.ILOAD, 3)); // bz
                    li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER_OWNER, HELPER_NAME, HELPER_DESC));
                    li.add(new InsnNode(Opcodes.IAND));        // original && machineCanBreak
                    m.instructions.insertBefore(ret, li);
                    patched++;
                }
            }

            if (patched == 0) {
                System.err.println("[ClaimTeam] TileQuarry.isQuarriableBlock not found in " + name);
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[ClaimTeam] Patched TileQuarry.isQuarriableBlock (" + patched + " return(s))");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[ClaimTeam] Quarry transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }
}
