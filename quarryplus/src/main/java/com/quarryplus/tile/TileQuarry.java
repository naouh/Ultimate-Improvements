package com.quarryplus.tile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.quarryplus.Config;
import com.quarryplus.PacketHandler;
import com.quarryplus.PowerManager;
import com.quarryplus.QuarryPlusI;

import buildcraft.api.core.IAreaProvider;
import buildcraft.core.utils.Utils;
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
public class TileQuarry extends APowerTile implements IEnchantableTile, IInventory {

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
    /** Client-only: previous broadcast head position. RenderQuarry lerps between
     *  prev and current using partialTick for smoother visuals. */
    public double prevHeadPosX, prevHeadPosY, prevHeadPosZ;

    // ----- State -----
    private byte now = NONE;
    private boolean initialized = false;

    // ----- Output buffer -----
    // 27-slot fixed array so the quarry advertises itself to BC pipes / wooden pipes /
    // chests as a regular IInventory. Mining drops land here; flushCacheToOutputs pushes
    // them out to adjacent inventories and transport pipes each tick.
    public static final int INV_SIZE = 27;
    private final ItemStack[] slots = new ItemStack[INV_SIZE];

    // ===== Tick =====

    private int diagTickCounter = 0;

    /**
     * Tear down every frame block sitting on an edge of the work area. Called from the
     * BlockQuarry break hook so the quarry doesn't leave a 12-edge wireframe floating in
     * mid-air after the player picks it up.
     */
    public void removeAllFrames() {
        if (worldObj == null || worldObj.isRemote) return;
        if (xMin == xMax || yMin == yMax || zMin == zMax) return;
        int frameId = QuarryPlusI.blockFrame.blockID;
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    int flag = 0;
                    if (x == xMin || x == xMax) flag++;
                    if (y == yMin || y == yMax) flag++;
                    if (z == zMin || z == zMax) flag++;
                    if (flag < 2) continue; // not on an edge
                    if (worldObj.getBlockId(x, y, z) == frameId) {
                        worldObj.setBlockWithNotify(x, y, z, 0);
                    }
                }
            }
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        // Heartbeat once per second so we can see whether the tile is actually ticking and
        // whether MJ is flowing in.
        if (++diagTickCounter % 20 == 0) {
            System.out.println("[QuarryPlus] tick now=" + now + " stored=" + getStoredEnergy()
                    + " target=(" + targetX + "," + targetY + "," + targetZ + ")"
                    + " area=(" + xMin + "," + yMin + "," + zMin + ")-(" + xMax + "," + yMax + "," + zMax + ")");
        }

        // Self-heal: if a previously-saved tile has a degenerate work area (which an
        // unlinked-marker IAreaProvider used to produce before the degenerate-skip fix),
        // force re-initialisation. Costs one tick before mining can resume.
        if (initialized && (xMin == xMax || zMin == zMax || yMin == yMax)) {
            initialized = false;
            yMax = Integer.MIN_VALUE; // force resolveWorkArea to rerun
            return;
        }

        if (!initialized) {
            initialized = true;
            // Force a fresh work-area resolve any time the box is degenerate.
            if (xMin == xMax || yMin == yMax || zMin == zMax) {
                resolveWorkArea();
            }
            now = MAKE_FRAME;
            PowerManager.configureF(this, efficiency, unbreaking);
            targetX = xMin; targetY = yMax; targetZ = zMin;
            digged = true; addX = true; addZ = true; changeZ = false;
            // Initialise the drill head ABOVE the work area so the eventual MOVE_HEAD has a
            // realistic starting point. Without this the head defaults to (0,0,0) and the
            // first MOVE_HEAD ticks try to traverse a few hundred blocks at <1 unit per
            // tick — quarry never actually drills.
            headPosX = (xMin + xMax) / 2.0 + 0.5;
            headPosY = yMax + 2.0;
            headPosZ = (zMin + zMax) / 2.0 + 0.5;
            sendStateUpdate();
            // Force the client to refresh its NBT view of this tile — the area fields
            // (xMin/xMax/...) are only synced via getDescriptionPacket, and without this
            // the RenderQuarry sees x{Min,Max}==0 and skips drawing the bridge.
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            System.out.println("[QuarryPlus] init at " + xCoord + "," + yCoord + "," + zCoord
                    + " area=(" + xMin + "," + yMin + "," + zMin + ")-(" + xMax + "," + yMax + "," + zMax + ")");
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
        // Reject providers whose reported area is degenerate on either horizontal axis —
        // a single unlinked marker reports {xCoord..xCoord, zCoord..zCoord} which would
        // give us a 1×N×1 column. Better to fall back to the default 11×4×11 box.
        int[][] offs = { {-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}, {0,-1,0}, {0,1,0} };
        for (int[] o : offs) {
            TileEntity te = worldObj.getBlockTileEntity(xCoord + o[0], yCoord + o[1], zCoord + o[2]);
            if (!(te instanceof IAreaProvider)) continue;
            IAreaProvider iap = (IAreaProvider) te;
            int axMin = iap.xMin(), axMax = iap.xMax();
            int azMin = iap.zMin(), azMax = iap.zMax();
            if (axMin == axMax || azMin == azMax) continue;   // degenerate — skip
            xMin = axMin; xMax = axMax;
            yMin = iap.yMin();
            zMin = azMin; zMax = azMax;
            int sizeX = xMax - xMin;
            int sizeZ = zMax - zMin;
            yMax = yMin + Math.max(4, Math.max(sizeX, sizeZ) / 2);
            iap.removeFromWorld();
            clampToConfigLimits();
            return;
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
        worldObj.setBlockAndMetadataWithNotify(targetX, targetY, targetZ, QuarryPlusI.blockFrame.blockID, 0);
        System.out.println("[QuarryPlus] frame@" + targetX + "," + targetY + "," + targetZ
                + " stored=" + getStoredEnergy());
        // Advance target inside the step itself (mirrors yogpstop's S_makeFrame). The outer
        // updateEntity loop then runs stepCheckTarget on the NEW target — without this we
        // would replace the same frame block every tick and burn 25 MJ for nothing.
        stepAdvanceTarget();
        return true;
    }

    private boolean stepBreakBlock() {
        int idHere = worldObj.getBlockId(targetX, targetY, targetZ);
        // Belt-and-braces guard: never break an edge frame even if the target happens to
        // land on one (e.g. right after the MAKE_FRAME → NOT_NEED_BREAK transition where
        // the target is reset to the corner).
        if (now == NOT_NEED_BREAK
                && idHere == QuarryPlusI.blockFrame.blockID
                && worldObj.getBlockMetadata(targetX, targetY, targetZ) == 0) {
            int flag = 0;
            if (targetX == xMin || targetX == xMax) flag++;
            if (targetY == yMin || targetY == yMax) flag++;
            if (targetZ == zMin || targetZ == zMax) flag++;
            if (flag > 1) {
                stepAdvanceTarget();
                return true;
            }
        }
        digged = true;
        boolean ok = breakAt(targetX, targetY, targetZ);
        if ((diagTickCounter % 5) == 0) {
            System.out.println("[QuarryPlus] stepBreakBlock @ " + targetX + "," + targetY + "," + targetZ
                    + " id=" + idHere + " ok=" + ok + " stored=" + getStoredEnergy());
        }
        if (!ok) return false;
        suckUpDrops(targetX, targetY, targetZ);
        // Snap the drill head to the just-broken position so the client TESR follows the
        // mining visually — without this the head only animates during MOVE_HEAD and the
        // NOT_NEED_BREAK / BREAK_BLOCK sweep looks frozen even while blocks disappear.
        headPosX = targetX + 0.5;
        headPosY = targetY + 1.0;
        headPosZ = targetZ + 0.5;
        broadcastHead();
        if (now == BREAK_BLOCK) now = MOVE_HEAD;
        stepAdvanceTarget();
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
        boolean ueb = PowerManager.useEnergyB(this, hardness, fortuneArg, unbreaking);
        if ((diagTickCounter % 5) == 0) {
            System.out.println("[QuarryPlus]   breakAt id=" + id + " hardness=" + hardness
                    + " fortune=" + fortuneArg + " unb=" + unbreaking
                    + " ueb=" + ueb + " stored=" + getStoredEnergy()
                    + " maxRecv=" + powerProvider.getMaxEnergyReceived()
                    + " maxStored=" + powerProvider.getMaxEnergyStored());
        }
        if (!ueb) return false;

        int meta = worldObj.getBlockMetadata(x, y, z);
        ArrayList<ItemStack> drops;
        if (silkTouch && b.canSilkHarvest(worldObj, null, x, y, z, meta)) {
            drops = new ArrayList<ItemStack>();
            drops.add(new ItemStack(id, 1, meta));
        } else {
            drops = b.getBlockDropped(worldObj, x, y, z, meta, fortune);
        }
        if (drops != null) for (ItemStack drop : drops) addToInventory(drop);
        worldObj.setBlockWithNotify(x, y, z, 0);
        return true;
    }

    private void suckUpDrops(int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                (double)(x - 4), (double)(y - 4), (double)(z - 4),
                (double)(x + 6), (double)(y + 6), (double)(z + 6));
        @SuppressWarnings("rawtypes")
        List items = worldObj.getEntitiesWithinAABB(EntityItem.class, box);
        for (Object o : items) {
            if (!(o instanceof EntityItem)) continue;
            EntityItem e = (EntityItem) o;
            if (e.isDead) continue;
            ItemStack stack = e.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;
            addToInventory(stack);
            e.setDead();
        }
    }

    /**
     * Drop an item into the internal inventory, merging with an existing same-type stack
     * first then spilling into the next empty slot. Anything that doesn't fit gets thrown
     * back into the world as an EntityItem so we never silently void drops.
     */
    private void addToInventory(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        // First pass — stack onto existing same-item slots.
        for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
            if (slots[i] == null) continue;
            if (!slots[i].isItemEqual(stack)) continue;
            if (!ItemStack.areItemStackTagsEqual(slots[i], stack)) continue;
            int room = slots[i].getMaxStackSize() - slots[i].stackSize;
            int give = Math.min(room, stack.stackSize);
            slots[i].stackSize += give;
            stack.stackSize -= give;
        }
        // Second pass — drop into the first empty slot.
        for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
            if (slots[i] != null) continue;
            slots[i] = stack.copy();
            stack.stackSize = 0;
        }
        // Anything left over goes back into the world above the quarry.
        if (stack.stackSize > 0) {
            EntityItem ei = new EntityItem(worldObj,
                    xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, stack.copy());
            ei.delayBeforeCanPickup = 10;
            worldObj.spawnEntityInWorld((Entity) ei);
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
                if (edgeCount <= 1) return false;   // not on edge → advance
                // On edge — valid target only if the block isn't already a frame. This
                // lets the outer while-loop skip past previously-placed frames in a single
                // tick, eventually wrapping back to the start corner where the digged flag
                // toggle decrements Y. Without this gate the algorithm loops forever on the
                // top plane (digged keeps getting reset to true at every stepMakeFrame).
                return worldObj.getBlockId(targetX, targetY, targetZ)
                       != QuarryPlusI.blockFrame.blockID;
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
                int id = worldObj.getBlockId(targetX, targetY, targetZ);
                if (id == 0) return false; // air → nothing to do, advance
                // Skip our own frame blocks when they sit on a true edge of the work area.
                // Without this guard the sweep would tear back down the frame we just built.
                if (id == QuarryPlusI.blockFrame.blockID
                        && worldObj.getBlockMetadata(targetX, targetY, targetZ) == 0) {
                    int flag = 0;
                    if (targetX == xMin || targetX == xMax) flag++;
                    if (targetY == yMin || targetY == yMax) flag++;
                    if (targetZ == zMin || targetZ == zMax) flag++;
                    if (flag > 1) return false; // edge frame → preserve
                }
                return true;
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

    /**
     * Walk our 27 inventory slots and try to push their contents into adjacent inventories
     * (chests, machines) and BC transport pipes. Any slot whose stack is fully consumed
     * becomes null. Wooden pipes pull from us through the regular IInventory interface, so
     * this only needs to handle the push side.
     */
    private void flushCacheToOutputs() {
        boolean anyChanged = false;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.stackSize <= 0) {
                if (stack != null) { slots[i] = null; anyChanged = true; }
                continue;
            }
            int before = stack.stackSize;
            // Adjacent IInventory transfer via BC's own Transactor logic. Returns the stack
            // of items that were actually inserted — subtract from the source stack.
            ItemStack added = Utils.addToRandomInventory(stack, worldObj, xCoord, yCoord, zCoord,
                                                         ForgeDirection.UNKNOWN);
            if (added != null) stack.stackSize -= added.stackSize;
            // Anything still here goes into an adjacent BC transport pipe if there is one.
            if (stack.stackSize > 0 && Utils.addToRandomPipeEntry(this, ForgeDirection.UNKNOWN, stack)) {
                stack.stackSize = 0;
            }
            if (stack.stackSize != before) anyChanged = true;
            if (stack.stackSize <= 0) slots[i] = null;
        }
        if (anyChanged) onInventoryChanged();
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
            double nx = in.readDouble();
            double ny = in.readDouble();
            double nz = in.readDouble();
            // Snapshot the current pos for the renderer to lerp from. If the jump is very
            // large (>4 blocks on any axis) the renderer would otherwise show the drill
            // visibly flying across the world — just snap in that case.
            double dx = Math.abs(nx - headPosX);
            double dy = Math.abs(ny - headPosY);
            double dz = Math.abs(nz - headPosZ);
            if (dx > 4 || dy > 4 || dz > 4 || (prevHeadPosX == 0 && prevHeadPosY == 0 && prevHeadPosZ == 0)) {
                prevHeadPosX = nx; prevHeadPosY = ny; prevHeadPosZ = nz;
            } else {
                prevHeadPosX = headPosX; prevHeadPosY = headPosY; prevHeadPosZ = headPosZ;
            }
            headPosX = nx; headPosY = ny; headPosZ = nz;
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
        // Save the inventory contents.
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            slots[i].writeToNBT(entry);
            list.appendTag(entry);
        }
        tag.setTag("Items", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        // Wipe the inventory before refilling — readFromNBT can be called multiple times.
        for (int i = 0; i < slots.length; i++) slots[i] = null;
        net.minecraft.nbt.NBTTagList list = tag.getTagList("Items");
        if (list != null) {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = (NBTTagCompound) list.tagAt(i);
                int slot = entry.getByte("Slot") & 0xFF;
                if (slot >= 0 && slot < slots.length) {
                    slots[slot] = ItemStack.loadItemStackFromNBT(entry);
                }
            }
        }
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

    // ===== IInventory =====
    // Exposing the 27-slot internal buffer as an IInventory makes the quarry a valid pull
    // target for BC wooden pipes (which extract from any adjacent IInventory via engine
    // pulses) and a valid push/connect target for any BC pipe segment (which check
    // isPipeConnected against tile entities — Forge auto-accepts IInventory).

    @Override public int getSizeInventory() { return slots.length; }

    @Override public ItemStack getStackInSlot(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= slots.length || slots[slot] == null) return null;
        ItemStack s = slots[slot];
        if (s.stackSize <= amount) {
            slots[slot] = null;
            onInventoryChanged();
            return s;
        }
        ItemStack split = s.splitStack(amount);
        if (s.stackSize == 0) slots[slot] = null;
        onInventoryChanged();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= slots.length) return null;
        ItemStack s = slots[slot];
        slots[slot] = null;
        return s;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.length) return;
        slots[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        onInventoryChanged();
    }

    @Override public String getInvName()        { return "QuarryPlus"; }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUseableByPlayer(EntityPlayer p) {
        return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this
                && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }
    @Override public void openChest()  { /* no-op */ }
    @Override public void closeChest() { /* no-op */ }
}
