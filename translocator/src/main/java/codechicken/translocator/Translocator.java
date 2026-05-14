package codechicken.translocator;

import java.io.File;

import codechicken.core.CommonUtils;
import codechicken.core.config.ConfigFile;
import codechicken.core.packet.PacketCustom;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import net.minecraft.item.Item;

/**
 * 1.4.7 backport of ChickenBones' Translocator 1.1.0.2.
 *
 * <p>This is the entry point: it wires up the FML lifecycle to the sided proxy
 * (which performs the actual block/item registration and binds renderers on
 * the client). The CodeChickenCore {@code PacketCustom} pipeline is reused for
 * client&lt;-&gt;server traffic so we don't have to reimplement the framed-NBT
 * networking that NEI-style "tiny" packets give us for free.
 */
@Mod(modid = "Translocator",
     name = "Translocator",
     version = "1.1.0.3-1.4.7",
     useMetadata = false,
     dependencies = "required-after:CodeChickenCore@[0.8.1,)",
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            tinyPacketHandler = PacketCustom.CustomTinyPacketHandler.class)
public class Translocator {

    @SidedProxy(clientSide = "codechicken.translocator.TranslocatorClientProxy",
                serverSide = "codechicken.translocator.TranslocatorProxy")
    public static TranslocatorProxy proxy;

    @Instance("Translocator")
    public static Translocator instance;

    public static ConfigFile config;
    public static BlockTranslocator blockTranslocator;
    public static BlockCraftingGrid blockCraftingGrid;
    public static Item itemDiamondNugget;

    @PreInit
    public void preInit(FMLPreInitializationEvent event) {
        config = new ConfigFile(new File(CommonUtils.getMinecraftDir() + "/config", "Translocator.cfg"))
                .setComment("Translocator Configuration File\n"
                          + "Deleting any element will restore it to it's default value\n"
                          + "Block ID's will be automatically generated the first time it's run");
        proxy.preInit();
    }

    @Init
    public void initialize(FMLInitializationEvent event) {
        proxy.load();
    }
}
