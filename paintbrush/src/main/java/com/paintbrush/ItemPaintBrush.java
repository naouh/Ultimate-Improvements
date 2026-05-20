package com.paintbrush;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import ic2.api.IPaintableBlock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * The Paint Brush item.
 *
 * <ul>
 *   <li><b>Right-click</b> an IC2 {@link IPaintableBlock} (glass fibre / any IC2 cable) —
 *       paints the whole connected cable run the brush's colour.</li>
 *   <li><b>Sneak + right-click</b> any block, or the air — cycles to the next of 16 colours.</li>
 * </ul>
 *
 * <p>Painting flood-fills the run on purpose. IC2 only connects cables of equal (or neutral)
 * colour — {@code TileEntityCable.canInteractWithCable} — so re-colouring a single cable in a
 * uniform run would disconnect it from its neighbours and leave a visible gap. Colouring the
 * whole run at once keeps it uniform, so it stays connected and gap-free.
 *
 * <p>The colour is stored in <b>NBT</b>, not item metadata. In creative mode
 * {@code ItemInWorldManager} restores an item's metadata after {@code onItemUse}, which
 * reverted a damage-based colour on every click. NBT is not touched by that path; the
 * metadata is kept mirrored to it by {@link #onUpdate} so the icon still follows the colour.
 */
public class ItemPaintBrush extends Item {

    /** Upper bound on cables coloured by one flood-fill, to cap the cost on huge networks. */
    private static final int MAX_FLOOD = 512;

    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    public ItemPaintBrush() {
        super(Config.itemPaintBrushID - 256);
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setItemName("paintBrush");
        setCreativeTab(CreativeTabs.tabTools);
        setTextureFile("/mods/paintbrush/textures/items/items.png");
    }

    /** Current brush colour, 0..15. NBT is authoritative; metadata is the fallback. */
    public static int getColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("color")) {
            return tag.getInteger("color") & 15;
        }
        return stack.getItemDamage() & 15;
    }

    public static void setColor(ItemStack stack, int color) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger("color", color & 15);
    }

    @Override
    public int getIconFromDamage(int damage) {
        return damage & 15;
    }

    /**
     * Mirrors the item metadata to the NBT colour every tick. The icon is resolved through
     * {@code Item.getIconIndex(ItemStack)}, which is {@code final} — it can't be overridden
     * to read NBT — and it delegates to {@link #getIconFromDamage(int)}. Unlike the
     * onItemUse path, this per-tick callback is not subject to creative mode's metadata
     * reset, so the write sticks and the brush icon keeps following its colour.
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        int color = getColor(stack);
        if (stack.getItemDamage() != color) {
            stack.setItemDamage(color);
        }
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hx, float hy, float hz) {
        // Sneak + right-click anything: cycle to the next colour.
        if (player.isSneaking()) {
            if (!world.isRemote) {
                cycleColor(stack, player);
            }
            return true;
        }
        // Plain right-click on an IC2-paintable block: flood-fill the whole cable run.
        int id = world.getBlockId(x, y, z);
        if (id > 0 && id < Block.blocksList.length && Block.blocksList[id] instanceof IPaintableBlock) {
            if (!world.isRemote) {
                floodPaint(world, x, y, z, getColor(stack));
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Sneak + right-click in the air: cycle colour (when no block is in reach).
        if (player.isSneaking() && !world.isRemote) {
            cycleColor(stack, player);
        }
        return stack;
    }

    /**
     * Colours every cable connected to the one at (x,y,z) — same block id and metadata
     * (cable type), reachable by 6-way adjacency. {@code colorBlock} handles each cable's
     * server-side change and network sync; IC2 re-renders every cable whose colour changed,
     * so the whole run refreshes. Server-side only.
     */
    private void floodPaint(World world, int x, int y, int z, int color) {
        int id = world.getBlockId(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        IPaintableBlock paintable = (IPaintableBlock) Block.blocksList[id];

        HashSet<String> seen = new HashSet<String>();
        LinkedList<int[]> queue = new LinkedList<int[]>();
        queue.add(new int[] { x, y, z });
        seen.add(x + "," + y + "," + z);
        int painted = 0;

        while (!queue.isEmpty() && painted < MAX_FLOOD) {
            int[] p = queue.removeFirst();
            paintable.colorBlock(world, p[0], p[1], p[2], color);
            painted++;
            for (int dir = 0; dir < 6; dir++) {
                int nx = p[0] + DX[dir];
                int ny = p[1] + DY[dir];
                int nz = p[2] + DZ[dir];
                String key = nx + "," + ny + "," + nz;
                if (seen.contains(key)) continue;
                if (world.getBlockId(nx, ny, nz) == id
                        && world.getBlockMetadata(nx, ny, nz) == meta) {
                    seen.add(key);
                    queue.add(new int[] { nx, ny, nz });
                }
            }
        }
    }

    /**
     * Advances the brush colour by one. Called server-side only: the change is written to
     * NBT and the server re-syncs the held stack to the client, so there is no client/server
     * race and the creative-mode metadata reset never touches it.
     */
    private void cycleColor(ItemStack stack, EntityPlayer player) {
        int next = (getColor(stack) + 1) & 15;
        setColor(stack, next);
        player.sendChatToPlayer("§6[Paintbrush] §fColor: " + PaintColors.NAME[next]);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    public void getSubItems(int id, CreativeTabs tab, List list) {
        for (int i = 0; i < 16; i++) {
            list.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7Color: §f" + PaintColors.NAME[getColor(stack)]);
        list.add("§8Right-click: paint the cable run");
        list.add("§8Sneak + right-click: change color");
    }
}
