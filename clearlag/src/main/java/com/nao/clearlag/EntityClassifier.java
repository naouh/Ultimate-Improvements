package com.nao.clearlag;

import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Decides whether an entity is a hostile mob, a passive animal, or a boss.
 *
 * <p>The hard part on a modpack: many modded mobs do NOT implement Bukkit's {@link Monster} /
 * {@link Animals} interfaces, so a pure-Bukkit check would miss them. The Minecraft classes do model
 * this correctly though - every hostile mob (vanilla and modded) implements {@code IMob}, animals
 * implement {@code IAnimals}, and bosses implement {@code IBossDisplayData}. On a Forge server those
 * classes exist at runtime under their deobfuscated names, so we resolve them reflectively and test
 * the entity's underlying handle against them.
 *
 * <p>If the NMS classes can't be found (or reflection fails for an entity), we fall back to the
 * Bukkit interfaces. The plugin's policy is "only remove what we can positively identify as hostile",
 * so a classification miss always errs on the side of NOT deleting something - farms stay safe.
 */
final class EntityClassifier {

	private final Class<?> iMob;
	private final Class<?> iAnimals;
	private final Class<?> iBoss;
	private final boolean nmsActive;

	/** Cached CraftEntity#getHandle (declared on the shared CraftEntity superclass). */
	private volatile Method getHandle;
	private volatile boolean getHandleBroken;

	EntityClassifier(Logger log) {
		this.iMob = tryClass("net.minecraft.entity.monster.IMob");
		this.iAnimals = tryClass("net.minecraft.entity.passive.IAnimals");
		this.iBoss = tryClass("net.minecraft.entity.boss.IBossDisplayData");
		this.nmsActive = iMob != null;
		if (nmsActive) {
			log.info("ClearLag: NMS entity classification active (modded mobs supported).");
		} else {
			log.info("ClearLag: NMS classes not found - using Bukkit-only classification "
					+ "(modded mobs may be skipped, which is safe).");
		}
	}

	private static Class<?> tryClass(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable t) {
			return null;
		}
	}

	/** The net.minecraft handle behind a CraftEntity, or null if it can't be obtained. */
	private Object handle(Entity e) {
		if (getHandleBroken) return null;
		try {
			Method m = getHandle;
			if (m == null) {
				m = e.getClass().getMethod("getHandle");
				getHandle = m;
			}
			return m.invoke(e);
		} catch (Throwable t) {
			getHandleBroken = true; // don't keep paying for reflection that won't work
			return null;
		}
	}

	/** Hostile mob (zombie, skeleton, creeper, slime, ghast, blaze, and modded equivalents). */
	boolean isHostile(Entity e) {
		if (nmsActive) {
			Object h = handle(e);
			if (h != null) return iMob.isInstance(h);
		}
		return (e instanceof Monster) || (e instanceof Slime) || (e instanceof Ghast) || (e instanceof Flying);
	}

	/** Passive animal (cow, pig, sheep, chicken, and modded passives) - things players keep/farm. */
	boolean isAnimal(Entity e) {
		if (e instanceof Animals) return true;
		if (nmsActive && iAnimals != null) {
			Object h = handle(e);
			if (h != null) return iAnimals.isInstance(h) && !iMob.isInstance(h);
		}
		return false;
	}

	/** Boss mob (Ender Dragon, Wither, and modded bosses with a boss health bar). */
	boolean isBoss(Entity e) {
		if (iBoss == null) return false;
		Object h = handle(e);
		return h != null && iBoss.isInstance(h);
	}

	boolean isNmsActive() {
		return nmsActive;
	}
}
