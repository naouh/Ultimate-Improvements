package com.nao.tfcfixes.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code Container.putStacksInSlots(ItemStack[])} to bail out when
 * {@code inventorySlots} is empty.
 *
 * Without this fix, Packet104WindowItems arriving for a Container whose
 * slots haven't been populated yet (e.g. GregTech ghost-block window opened
 * during the TileEntity sync race after teleport/login) calls
 * {@code getSlot(0)} on an empty ArrayList and crashes the client with
 * {@code IndexOutOfBoundsException: Index: 0, Size: 0}.
 *
 * Injected at method entry:
 * <pre>
 *     if (this.inventorySlots.size() == 0) return;
 * </pre>
 */
public class ContainerTransformer implements IClassTransformer {

    // 1.4.7 obfuscation
    private static final String CONTAINER_OBF       = "rq";
    private static final String METHOD_OBF          = "a";
    private static final String METHOD_DESC_OBF     = "([Lur;)V"; // putStacksInSlots(ItemStack[])
    private static final String FIELD_OBF           = "c";        // inventorySlots
    private static final String FIELD_DESC          = "Ljava/util/List;";

    // Deobf names (for MCP / dev environments)
    private static final String CONTAINER_CLEAN     = "net.minecraft.inventory.Container";
    private static final String CONTAINER_CLEAN_SLASH = "net/minecraft/inventory/Container";
    private static final String METHOD_CLEAN        = "putStacksInSlots";
    private static final String FIELD_CLEAN         = "inventorySlots";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean isObf = CONTAINER_OBF.equals(name);
        boolean isClean = CONTAINER_CLEAN.equals(name) || CONTAINER_CLEAN_SLASH.equals(name);
        if (!isObf && !isClean) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            String ownerInternal = isObf ? CONTAINER_OBF : CONTAINER_CLEAN_SLASH;
            String fieldName     = isObf ? FIELD_OBF : FIELD_CLEAN;

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!matchesPutStacksInSlots(m)) continue;
                injectEmptyGuard(m, ownerInternal, fieldName);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[TFCFixes] Container: putStacksInSlots not found");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] Patched Container.putStacksInSlots");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] Container transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /**
     * Accept putStacksInSlots regardless of how the runtime names the method. MCPC+/Cauldron runs
     * FML-deobfuscated: classes get MCP names ({@code net.minecraft.inventory.Container}) but methods
     * keep SRG names ({@code func_75131_a}), which matched neither the obf ({@code a}) nor the MCP
     * ({@code putStacksInSlots}) name - so the patch silently no-op'd on the server
     * ("putStacksInSlots not found"). putStacksInSlots is the only Container method taking a single
     * array-of-objects and returning void, so we match purely by descriptor. This is naming-agnostic
     * across obf {@code ([Lur;)V}, MCP/SRG {@code ([Lnet/minecraft/item/ItemStack;)V}, etc.
     */
    private static boolean matchesPutStacksInSlots(MethodNode m) {
        return m.desc != null && m.desc.startsWith("([L") && m.desc.endsWith(";)V");
    }

    /**
     * Prepend:
     * <pre>
     *     ALOAD 0
     *     GETFIELD owner.field : Ljava/util/List;
     *     INVOKEINTERFACE java/util/List.size ()I
     *     IFNE skip
     *     RETURN
     *   skip:
     * </pre>
     */
    private static void injectEmptyGuard(MethodNode m, String ownerInternal, String fieldName) {
        LabelNode skip = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, ownerInternal, fieldName, FIELD_DESC));
        li.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "size", "()I"));
        li.add(new JumpInsnNode(Opcodes.IFNE, skip));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(skip);
        m.instructions.insert(li);
    }
}
