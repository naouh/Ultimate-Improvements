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
 * Patches {@code BlockFlowing.updateTick(World, int, int, int, Random)} (obf
 * {@code aky.b(Lyc;IIILjava/util/Random;)V}). Injects:
 *
 * <pre>
 *     if (ClaimAsmHelper.fluidShouldBlock(world, x, y, z)) return;
 * </pre>
 *
 * Params: 0=this, 1=world, 2=x, 3=y, 4=z, 5=random.
 */
public class FluidTransformer implements IClassTransformer {

    private static final String TARGET_CLEAN       = "net.minecraft.block.BlockFlowing";
    private static final String TARGET_CLEAN_SLASH = "net/minecraft/block/BlockFlowing";
    private static final String TARGET_OBF         = "aky";

    private static final String METHOD_CLEAN      = "updateTick";
    private static final String METHOD_OBF        = "b";
    private static final String METHOD_DESC_CLEAN = "(Lnet/minecraft/world/World;IIILjava/util/Random;)V";
    private static final String METHOD_DESC_OBF   = "(Lyc;IIILjava/util/Random;)V";

    private static final String HELPER_OWNER = "com/nao/claimteam/asm/ClaimAsmHelper";
    private static final String HELPER_NAME  = "fluidShouldBlock";
    private static final String HELPER_DESC  = "(Ljava/lang/Object;III)Z";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean isObf = TARGET_OBF.equals(name);
        boolean isClean = TARGET_CLEAN.equals(name) || TARGET_CLEAN_SLASH.equals(name);
        if (!isObf && !isClean) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                boolean nameOk = METHOD_CLEAN.equals(m.name) || METHOD_OBF.equals(m.name);
                boolean descOk = METHOD_DESC_CLEAN.equals(m.desc) || METHOD_DESC_OBF.equals(m.desc);
                if (!nameOk || !descOk) continue;
                injectGuard(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[ClaimTeam] BlockFlowing.updateTick not found in " + name);
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[ClaimTeam] Patched BlockFlowing.updateTick (" + name + ")");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[ClaimTeam] Fluid transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static void injectGuard(MethodNode m) {
        LabelNode pass = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new VarInsnNode(Opcodes.ILOAD, 2));
        li.add(new VarInsnNode(Opcodes.ILOAD, 3));
        li.add(new VarInsnNode(Opcodes.ILOAD, 4));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER_OWNER, HELPER_NAME, HELPER_DESC));
        li.add(new JumpInsnNode(Opcodes.IFEQ, pass));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(pass);
        m.instructions.insert(li);
    }
}
