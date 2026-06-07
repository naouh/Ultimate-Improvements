package com.nao.tfcfixes.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code NetServerHandler.handleWindowClick} so the server re-sends the player's open
 * container in full after <em>every</em> click, not only when the client's prediction mismatched.
 *
 * Why: in 1.4.7 a window click is client-predicted and the server confirms by transaction id; when
 * the prediction matched, the server sends no correcting slot packets and the client keeps its
 * predicted view. Under packet jitter — much worse with TickThreading — that view can render stale,
 * so crafted/grabbed items don't appear until the next click forces a re-sync. The mismatch branch
 * of handleWindowClick already does a full {@code sendContainerAndContentsToPlayer}; this makes the
 * match path equally authoritative. No items are lost either way — purely a display fix.
 *
 * The injected bytecode only calls our own {@link com.nao.tfcfixes.WindowClickSyncHook}, which does
 * the field/method access reflectively, so we don't bake any MC member name (obf vs SRG vs MCP)
 * into the patch and a resolution miss can only no-op.
 */
public class WindowClickSyncTransformer implements IClassTransformer {

    private static final String NSH_OBF         = "iv";
    private static final String NSH_CLEAN       = "net.minecraft.network.NetServerHandler";
    private static final String NSH_CLEAN_SLASH = "net/minecraft/network/NetServerHandler";

    private static final String HOOK_OWNER = "com/nao/tfcfixes/WindowClickSyncHook";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean isObf = NSH_OBF.equals(name);
        boolean isClean = NSH_CLEAN.equals(name) || NSH_CLEAN_SLASH.equals(name);
        if (!isObf && !isClean) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!isHandleWindowClick(m)) continue;
                injectResyncBeforeReturns(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[TFCFixes] NetServerHandler.handleWindowClick not found");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] Patched NetServerHandler.handleWindowClick (window-items resync)");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] NetServerHandler transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /**
     * handleWindowClick takes one {@code Packet102WindowClick} and returns void. Match by name
     * across runtime mapping flavours — {@code handleWindowClick} (deobf), {@code func_72523_a}
     * (MCPC+/SRG) or {@code a} (obf) — guarded by the one-object-arg / void shape, and for the
     * ambiguous obf {@code a} additionally by the parameter type (clean {@code Packet102WindowClick}
     * or obf {@code dc}).
     */
    private static boolean isHandleWindowClick(MethodNode m) {
        if (m.name == null || m.desc == null || !m.desc.endsWith(")V")) return false;
        Type[] args = Type.getArgumentTypes(m.desc);
        if (args.length != 1 || args[0].getSort() != Type.OBJECT) return false;

        if ("handleWindowClick".equals(m.name) || "func_72523_a".equals(m.name)) return true;
        if ("a".equals(m.name)) {
            String param = args[0].getInternalName();
            return param.endsWith("Packet102WindowClick") || "dc".equals(param);
        }
        return false;
    }

    /**
     * Insert before every RETURN (handleWindowClick has a single one):
     * <pre>
     *     WindowClickSyncHook.afterWindowClick(this);
     * </pre>
     */
    private static void injectResyncBeforeReturns(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList li = new InsnList();
                li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (NetServerHandler)
                li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "afterWindowClick", "(Ljava/lang/Object;)V"));
                m.instructions.insertBefore(insn, li);
            }
        }
    }
}
