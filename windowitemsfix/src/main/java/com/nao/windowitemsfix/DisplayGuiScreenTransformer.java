package com.nao.windowitemsfix;

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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code Minecraft.displayGuiScreen(GuiScreen)} to refuse to open a
 * {@code GuiContainer} whose underlying {@code Container} has zero slots.
 *
 * Why: when a player interacts with a block whose TileEntity hasn't synced
 * yet (e.g. GregTech placeholder during post-teleport/login race), the
 * server still dispatches a GUI-open packet. The client constructs a
 * Container, but because the TE is missing it ends up with 0 slots. With
 * the {@link ContainerTransformer} fix the subsequent WindowItems packet
 * no longer crashes, but the empty placeholder GUI stays open forever
 * because the Container never re-syncs.
 *
 * By refusing to open the empty GuiContainer in the first place, the
 * player simply has to right-click again a couple seconds later once the
 * TileEntity has arrived — no stuck empty GUI, no manual close.
 *
 * Injected at method entry:
 * <pre>
 *     if (par1GuiScreen instanceof GuiContainer
 *             &amp;&amp; ((GuiContainer) par1GuiScreen).inventorySlots != null
 *             &amp;&amp; ((GuiContainer) par1GuiScreen).inventorySlots.inventorySlots.isEmpty()) {
 *         return;
 *     }
 * </pre>
 */
public class DisplayGuiScreenTransformer implements IClassTransformer {

    // Minecraft class itself is NOT obfuscated in 1.4.7 (it's the entry point).
    private static final String MC_CLEAN_DOT   = "net.minecraft.client.Minecraft";
    private static final String MC_CLEAN_SLASH = "net/minecraft/client/Minecraft";

    // Method displayGuiScreen — obfuscated only the method name and parameter type.
    private static final String METHOD_OBF       = "a";
    private static final String METHOD_DESC_OBF  = "(Laul;)V";
    private static final String METHOD_CLEAN     = "displayGuiScreen";

    // GuiContainer + its inventorySlots field (the Container reference).
    private static final String GUICONTAINER_OBF = "avf";
    private static final String GC_FIELD_OBF     = "d";   // GuiContainer.inventorySlots
    private static final String GC_FIELD_DESC    = "Lrq;"; // Container

    // Container.inventorySlots field (the List of Slots).
    private static final String CONTAINER_OBF    = "rq";
    private static final String C_FIELD_OBF      = "c";   // Container.inventorySlots
    private static final String C_FIELD_DESC     = "Ljava/util/List;";

    // Minecraft.thePlayer (Minecraft is unobfuscated)
    private static final String MC_THEPLAYER_OBF     = "g";
    private static final String MC_THEPLAYER_DESC    = "Lays;"; // EntityClientPlayerMP

    // EntityPlayer fields (declared on qx, inherited by ays via bag).
    private static final String ENTITYPLAYER_OBF     = "qx";
    private static final String EP_INV_CONTAINER_OBF = "bK"; // inventoryContainer
    private static final String EP_OPEN_CONTAINER_OBF = "bL"; // openContainer
    private static final String EP_CONTAINER_DESC    = "Lrq;"; // Container

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!MC_CLEAN_DOT.equals(name) && !MC_CLEAN_SLASH.equals(name)) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!matchesDisplayGuiScreen(m)) continue;
                injectGuard(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[WindowItemsFix] Minecraft.displayGuiScreen not found");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[WindowItemsFix] Patched Minecraft.displayGuiScreen");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[WindowItemsFix] Minecraft transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static boolean matchesDisplayGuiScreen(MethodNode m) {
        if (m.desc == null) return false;
        if (METHOD_CLEAN.equals(m.name) && m.desc.startsWith("(L") && m.desc.endsWith(";)V")) return true;
        if (METHOD_OBF.equals(m.name) && METHOD_DESC_OBF.equals(m.desc)) return true;
        return false;
    }

    private static void injectGuard(MethodNode m) {
        LabelNode skip = new LabelNode();
        LabelNode skipReset = new LabelNode();

        InsnList li = new InsnList();
        // if (!(par1 instanceof GuiContainer)) goto skip;
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new TypeInsnNode(Opcodes.INSTANCEOF, GUICONTAINER_OBF));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));

        // Container c = ((GuiContainer) par1).inventorySlots;
        // if (c == null) goto skip;
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new TypeInsnNode(Opcodes.CHECKCAST, GUICONTAINER_OBF));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, GUICONTAINER_OBF, GC_FIELD_OBF, GC_FIELD_DESC));
        li.add(new JumpInsnNode(Opcodes.IFNULL, skip));

        // if (c.inventorySlots.size() != 0) goto skip;  -- proceed normally
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new TypeInsnNode(Opcodes.CHECKCAST, GUICONTAINER_OBF));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, GUICONTAINER_OBF, GC_FIELD_OBF, GC_FIELD_DESC));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, CONTAINER_OBF, C_FIELD_OBF, C_FIELD_DESC));
        li.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "size", "()I"));
        li.add(new JumpInsnNode(Opcodes.IFNE, skip));

        // Empty container: also reset player.openContainer to player.inventoryContainer
        // so subsequent SetSlot packets for the buggy windowId are ignored (their
        // windowId no longer matches player.openContainer.windowId), avoiding
        // visual pollution of the player's real inventory / hotbar.
        // Equivalent of:
        //     mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        li.add(new VarInsnNode(Opcodes.ALOAD, 0));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, MC_CLEAN_SLASH, MC_THEPLAYER_OBF, MC_THEPLAYER_DESC));
        li.add(new JumpInsnNode(Opcodes.IFNULL, skipReset));
        li.add(new VarInsnNode(Opcodes.ALOAD, 0));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, MC_CLEAN_SLASH, MC_THEPLAYER_OBF, MC_THEPLAYER_DESC));
        li.add(new InsnNode(Opcodes.DUP));
        li.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITYPLAYER_OBF, EP_INV_CONTAINER_OBF, EP_CONTAINER_DESC));
        li.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITYPLAYER_OBF, EP_OPEN_CONTAINER_OBF, EP_CONTAINER_DESC));
        li.add(skipReset);
        li.add(new InsnNode(Opcodes.RETURN));

        li.add(skip);
        m.instructions.insert(li);
    }
}
