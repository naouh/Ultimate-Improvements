package com.nao.crackauthcore;

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
 * Injects a guard at the very start of {@code NetServerHandler.handleCustomPayload(Packet250CustomPayload)}:
 *
 * <pre>
 *     if (CrackAuthGate.shouldDrop(this, packet)) return;
 * </pre>
 *
 * <p>{@code handleCustomPayload} is the single server-side entry point for every inbound mod packet
 * (FML routes channel traffic through it), so dropping it for not-yet-logged-in players neutralises
 * any mod keybind whose action is sent as a custom payload - the whole class of thing a Bukkit plugin
 * can't see. The packet is passed along so the gate can drop only mod channels and leave Minecraft's
 * and FML's own channels alone (see {@link CrackAuthGate#shouldDrop}).
 *
 * <p>Matching is deliberately loose so it survives whatever naming MCPC+ presents at runtime: the
 * class is matched by simple name ({@code *.NetServerHandler}) or 1.4.7 obf ({@code iv}), and the
 * method by descriptor shape (one {@code Packet250CustomPayload} arg returning void, in any package)
 * or the obf form {@code a(di)V}. If nothing matches it dumps the class's methods to the log so the
 * real names are visible, and returns the class untouched (fail-safe).
 */
public class CustomPayloadGateTransformer implements IClassTransformer {

	private static final String GATE = "com/nao/crackauthcore/CrackAuthGate";

	@Override
	public byte[] transform(String name, byte[] bytes) {
		if (bytes == null) return null;
		if (!isNetServerHandler(name)) return bytes;

		try {
			ClassReader cr = new ClassReader(bytes);
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);

			int patched = 0;
			for (Object o : cn.methods) {
				MethodNode m = (MethodNode) o;
				if (!isHandleCustomPayload(m)) continue;
				injectGuard(m);
				patched++;
			}

			if (patched == 0) {
				System.err.println("[CrackAuthCore] matched class " + name
						+ " but no handleCustomPayload(Packet250CustomPayload) method - methods present:");
				for (Object o : cn.methods) {
					MethodNode m = (MethodNode) o;
					System.err.println("[CrackAuthCore]   " + m.name + " " + m.desc);
				}
				return bytes;
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			CrackAuthGate.markInstalled(); // signal success to the plugin (same class copy)
			System.out.println("[CrackAuthCore] gated NetServerHandler.handleCustomPayload in " + name
					+ " (" + patched + " method(s))");
			return cw.toByteArray();
		} catch (Throwable t) {
			System.err.println("[CrackAuthCore] NetServerHandler transform failed for " + name + ":");
			t.printStackTrace();
			return bytes;
		}
	}

	private static boolean isNetServerHandler(String name) {
		if (name == null) return false;
		String dotted = name.replace('/', '.');
		return dotted.endsWith(".NetServerHandler") // any package: net.minecraft.network / .server / .src
				|| dotted.equals("NetServerHandler")
				|| dotted.equals("iv");             // 1.4.7 obf
	}

	private static boolean isHandleCustomPayload(MethodNode m) {
		if (m.desc == null) return false;
		// deobf / SRG / MCPC: exactly one Packet250CustomPayload arg, returns void (package-agnostic).
		if (m.desc.startsWith("(L") && m.desc.endsWith("Packet250CustomPayload;)V")) return true;
		// 1.4.7 obf: a(di)V
		if ("a".equals(m.name) && "(Ldi;)V".equals(m.desc)) return true;
		return false;
	}

	/** Inserts {@code if (CrackAuthGate.shouldDrop(this, packet)) return;} at method entry. */
	private static void injectGuard(MethodNode m) {
		LabelNode cont = new LabelNode();
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (NetServerHandler)
		li.add(new VarInsnNode(Opcodes.ALOAD, 1)); // the Packet250CustomPayload argument
		li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "shouldDrop",
				"(Ljava/lang/Object;Ljava/lang/Object;)Z"));
		li.add(new JumpInsnNode(Opcodes.IFEQ, cont));
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(cont);
		m.instructions.insert(li);
	}
}
