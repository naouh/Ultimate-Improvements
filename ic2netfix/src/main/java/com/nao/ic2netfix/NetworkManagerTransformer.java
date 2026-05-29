package com.nao.ic2netfix;

import java.util.HashSet;
import java.util.Set;

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

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Guards IC2's {@code ic2.core.network.NetworkManager} server->client send methods so they
 * early-return on the logical client instead of running {@code sendUpdatePacket}, which casts
 * every player in the list to {@code EntityPlayerMP} and throws a {@code ClassCastException}
 * against the client's {@code EntityClientPlayerMP}.
 *
 * {@code NetworkManager} is a mod class, so its name and method names are not obfuscated by
 * Forge - we match them as-is. Each guarded method is {@code void}, so the injected guard is
 * simply:
 * <pre>
 *     if (Ic2NetFixHook.skipClientSend()) return;
 * </pre>
 * inserted at method entry. The client->server methods ({@code requestInitialData},
 * {@code initiateClientTileEntityEvent}, ...) are intentionally left alone.
 */
public class NetworkManagerTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "ic2.core.network.NetworkManager";
    private static final String TARGET_SLASH = "ic2/core/network/NetworkManager";

    /** Server->client send methods that funnel into sendUpdatePacket and crash client-side. */
    private static final Set<String> GUARDED = new HashSet<String>();
    static {
        GUARDED.add("updateTileEntityField");
        GUARDED.add("initiateTileEntityEvent");
        GUARDED.add("initiateItemEvent");
        GUARDED.add("announceBlockUpdate");
    }

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
                if (!GUARDED.contains(m.name)) continue;
                if (m.desc == null || !m.desc.endsWith(")V")) continue; // void only
                injectClientGuard(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[IC2NetFix] no NetworkManager send methods matched - IC2 layout changed?");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[IC2NetFix] guarded " + patched + " NetworkManager send method(s) against client-side execution");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[IC2NetFix] NetworkManager transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** Inserts {@code if (Ic2NetFixHook.skipClientSend()) return;} at method entry. */
    private static void injectClientGuard(MethodNode m) {
        LabelNode cont = new LabelNode();
        InsnList li = new InsnList();
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/nao/ic2netfix/Ic2NetFixHook", "skipClientSend", "()Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(cont);
        m.instructions.insert(li);
    }
}
