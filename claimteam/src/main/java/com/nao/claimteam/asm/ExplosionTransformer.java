package com.nao.claimteam.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code Explosion.doExplosionB(boolean)} to filter the list of destroyed blocks
 * against claimed chunks. Injects at method entry:
 *
 * <pre>
 *     ClaimAsmHelper.filterExplosion(this);
 * </pre>
 *
 * The helper uses reflection to access private {@code worldObj} and {@code affectedBlockPositions}
 * so no Access Transformer is required.
 */
public class ExplosionTransformer implements IClassTransformer {

    private static final String TARGET_CLEAN       = "net.minecraft.world.Explosion";
    private static final String TARGET_CLEAN_SLASH = "net/minecraft/world/Explosion";
    private static final String TARGET_OBF         = "xx";  // 1.4.7 obf for Explosion

    // doExplosionB(boolean) — clean and obf forms
    private static final String METHOD_CLEAN = "doExplosionB";
    private static final String METHOD_OBF   = "a";
    private static final String METHOD_DESC  = "(Z)V";

    private static final String HELPER_OWNER = "com/nao/claimteam/asm/ClaimAsmHelper";
    private static final String HELPER_NAME  = "filterExplosion";
    private static final String HELPER_DESC  = "(Ljava/lang/Object;)V";

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
                if (!METHOD_DESC.equals(m.desc)) continue;
                if (!(METHOD_CLEAN.equals(m.name) || METHOD_OBF.equals(m.name))) continue;
                InsnList li = new InsnList();
                li.add(new VarInsnNode(Opcodes.ALOAD, 0));
                li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HELPER_OWNER, HELPER_NAME, HELPER_DESC));
                m.instructions.insert(li);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[ClaimTeam] Explosion.doExplosionB not found in " + name);
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[ClaimTeam] Patched Explosion.doExplosionB (" + name + ")");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[ClaimTeam] Explosion transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }
}
