package com.quarryplus.tile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.quarryplus.Config;
import com.quarryplus.PacketHandler;
import com.quarryplus.PowerManager;
import com.quarryplus.QuarryPlusI;

import buildcraft.api.core.IAreaProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.ForgeDirection;

/**
 * The mining brain of QuarryPlus.
 *
 * <p>Lifecycle: on first tick we resolve the work area (from any adjacent
 * {@link IAreaProvider} marker pair, or fall back to an 11×4×11 box in front of the quarry),
 * then run through:
 * <ol>
 *   <li>{@code MAKEFRAME} — walk the box edges and place {@link com.quarryplus.block.BlockFrame}s,</li>
 *   <li>{@code NOTNEEDBREAK} — sweep the top plane, clearing any block already inside the frame,</li>
 *   <li>{@code MOVEHEAD} / {@code BREAKBLOCK} — alternate moving the visual head to the next target
 *       and breaking that block, sweeping in serpentine rows top-down.</li>
 * </ol>
 *
 * <p>Cost / speed: every action goes through {@link PowerManager}. The four supported
 * enchantments (Efficiency, Unbreaking, Fortune, Silk Touch) modify those costs and what
 * gets dropped — see {@link com.quarryplus.EnchantmentHelper} for the NBT layout.
 */
public class TileQuarry extends APowerTile implements IEnchantableTile {

    public static final byte NONE         = 0;
    public static final byte NOT_NEED_BREAK = 1;
    public static final byte MAKE_FRAME   = 2;
    public static final byte MOVE_HEAD    = 4;
    public static final byte BREAK_BLOCK  = 5;

    // ----- Enchantment levels -----
    private byte efficiency;
    private byte unbreaking;
    private byte fortune;
    private boolean silkTouch;

    // ----- Work area -----
    public int xMin, xMax, yMin, yMax = Integer.MIN_VALUE, zMin, zMax;
    private int targetX, targetY, targetZ;
    private boolean addX = true, addZ = true, digged = true, changeZ = false;

    // ----- Head animation -----
    public double headPosX, headPosY, headPosZ;

    // ----- State -----
    private byte now = NONE;
    private boolean initialized = false;

    // ----- Output buffer -----
    private final LinkedList<ItemStack> cacheItems = new LinkedList<ItemStack>();

    // ===== Tick =====

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        if (!initialized) {
            initialized = true;
            if (yMax == Integer.MIN_VALUE) resolveWorkArea();
            now = MAKE_FRAME;
            PowerManager.configureF(this, efficiency, unbreaking);
            targetX = xMin; targetY = yMax; targetZ = zMin;
            digged = true; addX = true; addZ = true; changeZ = false;
            sendStateUpdate();
        }

        switch (now) {
            case MAKE_FRAME:
                if (stepMakeFrame()) while (!stepCheckTarget()) stepAdvanceTarget();
                break;
            case MOVE_HEAD:
                if (stepMoveHead()) {
                    now = BREAK_BLOCK;
                    broadcastHead();
                }
                broadcastHead();
                break;
            case BREAK_BLOCK:
            case NOT_NEED_BREAK:
                if (stepBreakBlock()) while (!stepCheckTarget()) stepAdvanceTarget();
                break;
        }

        flushCacheToOutputs();
    }

    // ===== Work area =====

    private void resolveWorkArea() {
        // Check the 6 neighbours for any IAreaProvider (typically TileMarker pairs).
        int[][] offs = { {-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}, {0,-1,0}, {0,1,0} };
        for (int[] o : offs) {
            TileEntity te = worldObj.getBlockTileEntity(xCoord + o[0], yCoord + o[1], zCoord + o[2]);
            if (te instanceof IAreaProvider) {
                IAreaProvider iap = (IAreaProvider) te;
                xMin = iap.xMin(); xMax = iap.xMax();
                yMin = iap.yMin();
                zMin = iap.zMin(); zMax = iap.zMax();
                int sizeX = xMax - xMin;
                int sizeZ = zMax - zMin;
                yMax = yMin + Math.max(4, Math.max(sizeX, sizeZ) / 2);
                iap.removeFromWorld();
                clampToConfigLimits();
                return;
            }
        }

        // Fallback: 11×4×11 box in front of the quarry, based on facing meta.
        ForgeDirection facing = ForgeDirection.values()[worldObj.getBlockMetadata(xCoord, yCoord, zCoord)]
                                   .getOpposite();
        switch (facing) {
            case EAST:  xMin = xCoord + 1;  zMin = zCoord - 5;  break;
            case WEST:  xMin = xCoord - 11; zMin = zCoord - 5;  break;
            case SOUTH: xMin = xCoord - 5;  zMin = zCoord + 1;  break;
            default:    xMin = xCoord - 5;  zMin = zCoord - 11; break; // NORTH + fallback
        }
        yMin = yCoord;
        xMax = xMin + 10;
        zMax = zMin + 10;
        yMax = yCoord + 4;
    }

    private void clampToConfigLimits() {
        int cap = Config.quarryMaxSize;
        if (xMax - xMin > cap) xMax = xMin + cap;
        if (zMax - zMin > cap) zMax = zMin + cap;
    }

    // ===== State-machine steps =====

    private boolean stepMakeFrame() {
        digged = true;
        if (!PowerManager.useEnergyF(this, unbreaking)) return false;
        worldObj.setBlock(targetX, targetY, targetZ, QuarryPlusI.blockFrame.blockID, 0, 3);
        return true;
    }

    private boolean stepBreakBlock() {
        digged = true;
        if (!breakAt(targetX, targetY, targetZ)) return false;
        suckUpDrops(targetX, targetY, targetZ);
        if (now == BREAK_BLOCK) now = MOVE_HEAD;
        return true;
    }

    /**
     * @return true if the block was broken (or was already air / non-breakable so we can move
     *         on). false if we couldn't afford the energy and should retry next tick.
     */
    private boolean breakAt(int x, int y, int z) {
        int id = worldObj.getBlockId(x, y, z);
        if (id == 0) return true;
        Block b = Block.blocksList[id];
        if (b == null) return true;
        float hardness = b.getBlockHardness(worldObj, x, y, z);
        if (hardness < 0f) return true; // unbreakable — skip

        byte fortuneArg = silkTouch ? (byte) -1 : fortune;
        if (!PowerManager.useEnergyB(this, hardness, fortuneArg, unbreaking)) return false;

        int meta = worldObj.getBlockMetadata(x, y, z);
        ArrayList<ItemStack> drops;
        if (silkTouch && b.canSilkHarvest(worldObj, null, x, y, z, meta)) {
            drops = new ArrayList<ItemStack>();
            drops.add(new ItemStack(id, 1, meta));
        } else {
            drops = b.getBlockDropped(worldObj, x, y, z, meta, fortune);
        }
        if (drops != null) cacheItems.addAll(drops);
        worldObj.setBlockToAir(x, y, z);
        return true;
    }

    private void suckUpDrops(int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.getAABBPool().getAABB(
                x - 4, y - 4, z - 4, x + 6, y + 6, z + 6);
        @SuppressWarnings("rawtypes")
        List items = worldObj.getEntitiesWithinAABB(EntityItem.class, box);
        for (Object o : items) {
            if (!(o instanceof EntityItem)) continue;
            EntityItem e = (EntityItem) o;
            if (e.isDead) continue;
            ItemStack stack = e.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;
            cacheItems.add(stack);
            e.setDead();
        }
    }

    private boolean stepMoveHead() {
        double tx = targetX + 0.5, ty = targetY + 1.0, tz = targetZ + 0.5;
        double dx = tx - headPosX, dy = ty - headPosY, dz = tz - headPosZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.05) return true;
        double move = PowerManager.useEnergyH(this, dist, unbreaking);
        double step = Math.min(move, dist);
        headPosX += dx / dist * step;
        headPosY += dy / dist * step;
        headPosZ += dz / dist * step;
        return dist - step < 0.05;
    }

    /** Returns true if the current target is valid for the current state. */
    private boolean stepCheckTarget() {
        if (targetY < 1 || targetY > yMax) {
            // Whole column exhausted — for our simplified state machine, we just stop.
            now = NONE;
            sendStateUpdate();
            return true;
        }

        switch (now) {
            case MAKE_FRAME: {
                if (targetY < yMin) {
                    now = NOT_NEED_BREAK;
                    PowerManager.configureB(this, efficiency, unbreaking);
                    targetX = xMin; targetZ = zMin; targetY = yMax;
                    digged = true; addX = true; addZ = true; changeZ = false;
                    sendStateUpdate();
                    return stepCheckTarget();
                }
                int edgeCount = 0;
                if (targetX == xMin || targetX == xMax) edgeCount++;
                if (targetY == yMin || targetY == yMax) edgeCount++;
                if (targetZ == zMin || targetZ == zMax) edgeCount++;
                return edgeCount > 1; // only true edges get frames
            }
            case NOT_NEED_BREAK: {
                if (targetY < yMin) {
                    now = MOVE_HEAD;
                    PowerManager.configureB(this, efficiency, unbreaking);
                    targetX = xMin + 1; targetZ = zMin + 1; targetY = yMin;
                    digged = true; addX = true; addZ = true; changeZ = false;
                    sendStateUpdate();
                    return stepCheckTarget();
                }
                return worldObj.getBlockId(targetX, targetY, targetZ) != 0;
            }
            case MOVE_HEAD:
            case BREAK_BLOCK: {
                if (targetY < 1) {
                    now = NONE;
                    sendStateUpdate();
                    return true;
                }
                int id = worldObj.getBlockId(targetX, targetY, targetZ);
                return id != 0 && Block.blocksList[id] != null
                        && Block.blocksList[id].getBlockHardness(worldObj, targetX, targetY, targetZ) >= 0f;
            }
        }
        return true;
    }

    /** Advance to the next target cell in the current sweep order. */
    private void stepAdvanceTarget() {
        if (now == MAKE_FRAME) {
            // Edge-walking — toggle between X-sweep and Z-sweep when we hit a corner.
            if (changeZ) targetZ += addZ ? 1 : -1; else targetX += addX ? 1 : -1;
            if (targetX < xMin || targetX > xMax) {
                addX = !addX; changeZ = true;
                targetX = Math.max(xMin, Math.min(xMax, targetX));
            }
            if (targetZ < zMin || targetZ > zMax) {
                addZ = !addZ; changeZ = false;
                targetZ = Math.max(zMin, Math.min(zMax, targetZ));
            }
            if (targetX == xMin && targetZ == zMin) {
                if (digged) digged = false; else targetY--;
            }
        } else {
            // Serpentine sweep — bottom of box up.
            int out = (now == NOT_NEED_BREAK) ? 0 : 1;
            targetX += addX ? 1 : -1;
            if (targetX < xMin + out || targetX > xMax - out) {
                addX = !addX;
                targetX = Math.max(xMin + out, Math.min(xMax - out, targetX));
                targetZ += addZ ? 1 : -1;
                if (targetZ < zMin + out || targetZ > zMax - out) {
                    addZ = !addZ;
                    targetZ = Math.max(zMin + out, Math.min(zMax - out, targetZ));
                    if (digged) digged = false; else targetY--;
                }
            }
        }
    }

    // ===== Output =====

    private void flushCacheToOutputs() {
        if (cacheItems.isEmpty()) return;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity te = worldObj.getBlockTileEntity(
                    xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (!(te instanceof IInventory)) continue;
            IInventory inv = (IInventory) te;
            for (int slot = 0; slot < inv.getSizeInventory() && !cacheItems.isEmpty(); slot++) {
                ItemStack head = cacheItems.peek();
                ItemStack here = inv.getStackInSlot(slot);
                if (here == null) {
                    inv.setInventorySlotContents(slot, head);
                    cacheItems.removeFirst();
                } else if (here.isItemEqual(head) && here.stackSize < here.getMaxStackSize()) {
                    int room = here.getMaxStackSize() - here.stackSize;
                    int give = Math.min(room, head.stackSize);
                    here.stackSize += give;
                    head.stackSize -= give;
                    if (head.stackSize <= 0) cacheItems.removeFirst();
                }
            }
            if (cacheItems.isEmpty()) return;
        }
        // No adjacent inventory accepted anything — spew on the floor above the quarry.
        ItemStack stuck;
        while ((stuck = cacheItems.poll()) != null) {
            EntityItem ei = new EntityItem(worldObj,
                    xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, stuck);
            ei.delayBeforeCanPickup = 10;
            worldObj.spawnEntityInWorld((Entity) ei);
        }
    }

    // ===== Packets =====

    private void sendStateUpdate() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(now);
            sendToAround(PacketHandler.StC_NOW, bos.toByteArray());
        } catch (IOException ignored) {}
    }

    private void broadcastHead() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeDouble(headPosX);
            out.writeDouble(headPosY);
            out.writeDouble(headPosZ);
            sendToAround(PacketHandler.StC_HEAD_POS, bos.toByteArray());
        } catch (IOException ignored) {}
    }

    @Override
    public void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) {
        // Phase 5 will add fortune-list / silk-list edit packets here.
    }

    @Override
    public void C_receivePacket(byte type, DataInputStream in) throws IOException {
        if (type == PacketHandler.StC_HEAD_POS) {
            headPosX = in.readDouble();
            headPosY = in.readDouble();
            headPosZ = in.readDouble();
        } else if (type == PacketHandler.StC_NOW) {
            now = in.readByte();
        }
    }

    // ===== NBT =====

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("efficiency", efficiency);
        tag.setByte("unbreaking", unbreaking);
        tag.setByte("fortune", fortune);
        tag.setBoolean("silkTouch", silkTouch);
        tag.setInteger("xMin", xMin); tag.setInteger("xMax", xMax);
        tag.setInteger("yMin", yMin); tag.setInteger("yMax", yMax);
        tag.setInteger("zMin", zMin); tag.setInteger("zMax", zMax);
        tag.setInteger("tX", targetX);
        tag.setInteger("tY", targetY);
        tag.setInteger("tZ", targetZ);
        tag.setDouble("hX", headPosX);
        tag.setDouble("hY", headPosY);
        tag.setDouble("hZ", headPosZ);
        tag.setByte("now", now);
        tag.setBoolean("init", initialized);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        efficiency = tag.getByte("efficiency");
        unbreaking = tag.getByte("unbreaking");
        fortune    = tag.getByte("fortune");
        silkTouch  = tag.getBoolean("silkTouch");
        xMin = tag.getInteger("xMin"); xMax = tag.getInteger("xMax");
        yMin = tag.getInteger("yMin"); yMax = tag.getInteger("yMax");
        zMin = tag.getInteger("zMin"); zMax = tag.getInteger("zMax");
        targetX = tag.getInteger("tX");
        targetY = tag.getInteger("tY");
        targetZ = tag.getInteger("tZ");
        headPosX = tag.getDouble("hX");
        headPosY = tag.getDouble("hY");
        headPosZ = tag.getDouble("hZ");
        now = tag.getByte("now");
        initialized = tag.getBoolean("init");
    }

    // ===== IEnchantableTile =====

    @Override public byte getEfficiencyLevel() { return efficiency; }
    @Override public byte getUnbreakingLevel() { return unbreaking; }
    @Override public byte getFortuneLevel()    { return fortune; }
    @Override public boolean getSilkTouch()    { return silkTouch; }

    @Override public void setEfficiencyLevel(byte l) { efficiency = l; }
    @Override public void setUnbreakingLevel(byte l) { unbreaking = l; }
    @Override public void setFortuneLevel(byte l)    { fortune = l; }
    @Override public void setSilkTouch(boolean on)   { silkTouch = on; }

    /** Used by BlockQuarry's top-face icon to pick the active-state texture in Phase 6. */
    public byte getNow() { return now; }
}
