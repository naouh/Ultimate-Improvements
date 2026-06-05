package com.nao.mystutils.command;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Reflective bridge to Mystcraft's {@code AgeData} so the command needs no compile-time dependency
 * on Mystcraft (and degrades gracefully if Mystcraft is absent or a different version).
 *
 * <p>{@code AgeData.getAge(World, int)} returns {@code null} for any non-Mystcraft dimension, which
 * is exactly the "are you in an Age?" test we need. {@code getEffects()} is the list of active
 * instability effect identifiers; {@code isInstabilityEnabled()} reports whether the Age can become
 * unstable at all.
 */
public final class MystAgeInfo {

    private MystAgeInfo() {}

    private static boolean attempted = false;
    private static boolean ok = false;
    private static Method m_getAge;          // static (World, int) -> AgeData
    private static Method m_getEffects;       // AgeData -> List<String>
    private static Method m_isEnabled;        // AgeData -> boolean

    /** Result of a lookup: whether instability is enabled and the active effect identifiers. */
    public static final class Report {
        public final boolean instabilityEnabled;
        public final List<String> effects;
        Report(boolean enabled, List<String> effects) {
            this.instabilityEnabled = enabled;
            this.effects = effects;
        }
    }

    private static synchronized boolean ready() {
        if (attempted) return ok;
        attempted = true;
        try {
            Class<?> cAge = Class.forName("com.xcompwiz.mystcraft.generation.AgeData");
            for (Method mm : cAge.getDeclaredMethods()) {
                if (m_getAge == null && mm.getName().equals("getAge")
                        && mm.getParameterTypes().length == 2
                        && mm.getParameterTypes()[1] == int.class) {
                    mm.setAccessible(true);
                    m_getAge = mm;
                }
            }
            m_getEffects = cAge.getMethod("getEffects");
            m_isEnabled = cAge.getMethod("isInstabilityEnabled");
            ok = (m_getAge != null);
        } catch (Throwable t) {
            ok = false;
        }
        return ok;
    }

    /**
     * @return a {@link Report} for the Age in {@code dimId} (storage read from {@code world}), or
     *         {@code null} if that dimension is not a Mystcraft Age (or Mystcraft is unavailable).
     */
    @SuppressWarnings("unchecked")
    public static Report lookup(Object world, int dimId) {
        if (!ready()) return null;
        try {
            Object age = m_getAge.invoke(null, world, Integer.valueOf(dimId));
            if (age == null) return null;
            boolean enabled = ((Boolean) m_isEnabled.invoke(age)).booleanValue();
            List<String> effects = (List<String>) m_getEffects.invoke(age);
            if (effects == null) effects = Collections.emptyList();
            return new Report(enabled, effects);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when Mystcraft was found and the reflection bridge is usable. */
    public static boolean available() {
        return ready();
    }
}
