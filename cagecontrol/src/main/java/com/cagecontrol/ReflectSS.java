package com.cagecontrol;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Reflective access to com.shadwdrgn.soulshards.TESoulCage and SoulShards.itemShard.
 * We avoid a compile-time dependency on SoulShards (which uses obfuscated MC types)
 * by going through reflection.
 */
public class ReflectSS {

    private static Class<?> teCageClass;
    private static Field    fMobType;     // private String mobType
    private static Field    fSpecial;     // boolean special
    private static Field    fTier;        // public int tier
    private static Field    fSignal;      // protected boolean signal
    private static Field    fCount;       // private int count
    private static Field    fDelay;       // private int a (delay)
    private static Field    fMobCount;    // private int mobCount
    private static Method   mSetMobType;  // setMobType(String, boolean)
    private static Method   mSetTier;     // setTier(int)
    private static Method   mGetMobType;  // getMobType()
    private static Method   mRCount;      // rCount()

    private static Class<?> ssClass;
    private static Field    fItemShard;   // public static Item itemShard
    private static Field    fBlockCage;   // public static Block blockCage

    private static Class<?> itemShardClass;
    private static Method   mGetType;     // static String getType(ItemStack)
    private static Method   mGetSpecial;  // static boolean getSpecial(ItemStack)

    private static boolean inited = false;
    private static boolean ok     = false;

    public static synchronized boolean init() {
        if (inited) return ok;
        inited = true;
        try {
            ssClass        = Class.forName("com.shadwdrgn.soulshards.SoulShards");
            teCageClass    = Class.forName("com.shadwdrgn.soulshards.TESoulCage");
            itemShardClass = Class.forName("com.shadwdrgn.soulshards.ItemShard");

            fItemShard = ssClass.getField("itemShard");
            fBlockCage = ssClass.getField("blockCage");

            fMobType  = teCageClass.getDeclaredField("mobType");
            fSpecial  = teCageClass.getDeclaredField("special");
            fTier     = teCageClass.getDeclaredField("tier");
            fSignal   = teCageClass.getDeclaredField("signal");
            fCount    = teCageClass.getDeclaredField("count");
            fMobCount = teCageClass.getDeclaredField("mobCount");
            // private int a (the spawn delay)
            fDelay    = teCageClass.getDeclaredField("a");

            fMobType.setAccessible(true);
            fSpecial.setAccessible(true);
            fTier.setAccessible(true);
            fSignal.setAccessible(true);
            fCount.setAccessible(true);
            fMobCount.setAccessible(true);
            fDelay.setAccessible(true);

            mSetMobType = teCageClass.getMethod("setMobType", String.class, boolean.class);
            mSetTier    = teCageClass.getMethod("setTier", int.class);
            mGetMobType = teCageClass.getMethod("getMobType");
            mRCount     = teCageClass.getMethod("rCount");

            // Use class literals — Voldeloom's Tiny Remapper rewrites these to the
            // obfuscated MC class, whereas Class.forName(String) is left as-is and
            // would throw ClassNotFoundException at runtime.
            mGetType    = itemShardClass.getMethod("getType",    net.minecraft.item.ItemStack.class);
            mGetSpecial = itemShardClass.getMethod("getSpecial", net.minecraft.item.ItemStack.class);

            ok = true;
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        return ok;
    }

    public static boolean isSoulCage(TileEntity te) {
        if (te == null) return false;
        init();
        return teCageClass != null && teCageClass.isInstance(te);
    }

    public static Class<?> teCageClass() { init(); return teCageClass; }

    public static Object soulShardsItem() {
        init();
        try { return fItemShard.get(null); } catch (Throwable t) { return null; }
    }

    public static Object soulShardsCageBlock() {
        init();
        try { return fBlockCage.get(null); } catch (Throwable t) { return null; }
    }

    public static int getCageBlockId() {
        Object block = soulShardsCageBlock();
        if (block == null) return -1;
        if (block instanceof net.minecraft.block.Block) {
            return ((net.minecraft.block.Block) block).blockID;
        }
        return -1;
    }

    public static String getShardType(net.minecraft.item.ItemStack is) {
        init();
        if (is == null) return "";
        try { return (String) mGetType.invoke(null, is); } catch (Throwable t) { return ""; }
    }

    public static boolean getShardSpecial(net.minecraft.item.ItemStack is) {
        init();
        if (is == null) return false;
        try { return (Boolean) mGetSpecial.invoke(null, is); } catch (Throwable t) { return false; }
    }

    public static String getMobType(TileEntity te) {
        try { return (String) mGetMobType.invoke(te); } catch (Throwable t) { return ""; }
    }

    public static void setMobType(TileEntity te, String type, boolean special) {
        try { mSetMobType.invoke(te, type, special); } catch (Throwable t) { t.printStackTrace(); }
    }

    public static void setTier(TileEntity te, int tier) {
        try { mSetTier.invoke(te, tier); } catch (Throwable t) { t.printStackTrace(); }
    }

    public static void rCount(TileEntity te) {
        try { mRCount.invoke(te); } catch (Throwable t) { t.printStackTrace(); }
    }

    public static int getTier(TileEntity te) {
        try { return fTier.getInt(te); } catch (Throwable t) { return 0; }
    }

    public static void setSignal(TileEntity te, boolean signal) {
        try { fSignal.setBoolean(te, signal); } catch (Throwable t) {}
    }

    /** Sets the spawn delay (private int a) to a huge value to disable spawning. */
    public static void disableSpawn(TileEntity te) {
        try {
            int before = fDelay.getInt(te);
            fDelay.setInt(te, Integer.MAX_VALUE);
            int after = fDelay.getInt(te);
            System.out.println("[CageControl] disableSpawn: delay " + before + " -> " + after);
        } catch (Throwable t) {
            System.out.println("[CageControl] disableSpawn FAILED: " + t);
            t.printStackTrace();
        }
    }

    /** Resets the spawn counter to 0 so a fresh full delay is required to next spawn. */
    public static void resetCount(TileEntity te) {
        try { fCount.setInt(te, 0); } catch (Throwable t) {}
    }

    public static int getDelay(TileEntity te) {
        try { return fDelay.getInt(te); } catch (Throwable t) { return -1; }
    }

    public static int getCount(TileEntity te) {
        try { return fCount.getInt(te); } catch (Throwable t) { return -1; }
    }
}
