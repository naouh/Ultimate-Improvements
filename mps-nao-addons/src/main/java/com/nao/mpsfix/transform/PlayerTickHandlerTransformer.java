package com.nao.mpsfix.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code net.machinemuse.powersuits.tick.PlayerTickHandler.handle}
 * to treat the Flight Control module as inactive while the player is on
 * the ground.
 *
 * <p>Target source line:
 * <pre>hasFlightControl = MuseItemUtils.itemHasActiveModule(helmet, MODULE_FLIGHT_CONTROL);</pre>
 *
 * <p>Bytecode pattern:
 * <pre>
 *   ALOAD helmet
 *   LDC "Flight Control"
 *   INVOKESTATIC net/machinemuse/api/MuseItemUtils.itemHasActiveModule(...)Z
 *   ISTORE hasFlightControl
 * </pre>
 *
 * <p>We inject between INVOKESTATIC and ISTORE: if {@code player.onGround},
 * replace the boolean on the stack with {@code false}. The Forge class
 * transformer runs on obfuscated bytecode, so we match the obf names of
 * MC types and Forge-mapped types alike (only mod-internal names stay
 * un-obfuscated, which is the case for MuseItemUtils).
 */
public class PlayerTickHandlerTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "net.machinemuse.powersuits.tick.PlayerTickHandler";
    private static final String TARGET_SLASH = "net/machinemuse/powersuits/tick/PlayerTickHandler";
    private static final String FLIGHT_CONTROL = "Flight Control";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        // Forge may pass either dot- or slash-form class names depending on the
        // transformer chain stage.
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;
        System.out.println("[MpsFlightFix] PlayerTickHandlerTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!"handle".equals(m.name)) continue;
                patchHandle(m);
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsFlightFix] Patched PlayerTickHandler.handle");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] PlayerTickHandler transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static void patchHandle(MethodNode m) {
        // Find the LDC "Flight Control" followed by INVOKESTATIC itemHasActiveModule.
        AbstractInsnNode cursor = m.instructions.getFirst();
        while (cursor != null) {
            if (cursor instanceof LdcInsnNode
                    && FLIGHT_CONTROL.equals(((LdcInsnNode) cursor).cst)) {
                // Walk forward looking for the INVOKESTATIC call.
                AbstractInsnNode probe = cursor.getNext();
                while (probe != null && !(probe instanceof MethodInsnNode)) probe = probe.getNext();
                if (probe instanceof MethodInsnNode
                        && ((MethodInsnNode) probe).getOpcode() == Opcodes.INVOKESTATIC
                        && "itemHasActiveModule".equals(((MethodInsnNode) probe).name)) {
                    injectGroundCheck(m, probe);
                    return;
                }
            }
            cursor = cursor.getNext();
        }
        System.err.println("[MpsFlightFix] PlayerTickHandler.handle: anchor not found");
    }

    private static void injectGroundCheck(MethodNode m, AbstractInsnNode anchor) {
        // After the INVOKESTATIC, the boolean (hasFlightControl) is on the stack.
        // Insert:
        //   ALOAD 1                         // player parameter (handle is non-static)
        //   GETFIELD Entity.onGround : Z    // MC obfuscated owner is "sa" in 1.4.7
        //   IFEQ keep                       // if not onGround, keep original boolean
        //   POP                             // discard original
        //   ICONST_0                        // push false
        // keep:
        InsnList insns = new InsnList();
        LabelNode keep = new LabelNode();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        // The obfuscated EntityPlayer is "qx" in MC 1.4.7. Its inherited "onGround"
        // field is declared on Entity ("sa"). Forge's transformer chain handles
        // de-obfuscation of standard refs, so the SRG/deobf-style path below
        // remaps correctly at runtime.
        insns.add(new FieldInsnNode(Opcodes.GETFIELD, "qx", "E", "Z"));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, keep));
        insns.add(new InsnNode(Opcodes.POP));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(keep);
        m.instructions.insert(anchor, insns);
    }
}
