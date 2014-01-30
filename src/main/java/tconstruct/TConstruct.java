package tconstruct;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.*;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.config.LoggerConfig;
import tconstruct.achievements.TAchievements;
import tconstruct.client.event.EventCloakRender;
import tconstruct.common.*;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.*;
import mantle.lib.TabTools;
import tconstruct.plugins.PluginController;
import tconstruct.util.*;
import tconstruct.util.config.*;
import tconstruct.util.landmine.behavior.Behavior;
import tconstruct.util.landmine.behavior.stackCombo.SpecialStackHandler;
import tconstruct.util.network.packet.PacketPipeline;
import tconstruct.util.player.TPlayerHandler;
import tconstruct.worldgen.*;
import tconstruct.worldgen.village.*;

/** TConstruct, the tool mod.
 * Craft your tools with style, then modify until the original is gone!
 * @author mDiyo
 */

@Mod(modid = "TConstruct", name = "TConstruct", version = "${version}",
        dependencies = "required-after:Forge@[9.11,);required-after:Mantle;after:ForgeMultipart;after:MineFactoryReloaded;after:NotEnoughItems;after:Waila;after:ThermalExpansion")
public class TConstruct
{
    /** The value of one ingot in millibuckets */
    public static final int ingotLiquidValue = 144;
    public static final int oreLiquidValue = ingotLiquidValue * 2;
    public static final int blockLiquidValue = ingotLiquidValue * 9;
    public static final int chunkLiquidValue = ingotLiquidValue / 2;
    public static final int nuggetLiquidValue = ingotLiquidValue / 9;

    public static final int liquidUpdateAmount = 6;

    // Shared mod logger
    public static final Logger logger = LogManager.getLogger("TConstruct");

    /* Instance of this mod, used for grabbing prototype fields */
    @Instance("TConstruct")
    public static TConstruct instance;
    /* Proxies for sides, used for graphics processing */
    @SidedProxy(clientSide = "tconstruct.client.TProxyClient", serverSide = "tconstruct.common.TProxyCommon")
    public static TProxyCommon proxy;

    //The name of the enum is accompanied by numbers because I have no idea what will happen if another mod will try to add the same enum, just to be safe
    public static EnumCreatureType creatureTypePlayer = EnumHelper.addCreatureType("PLAYER_5821443", EntityPlayer.class, 0, Material.field_151579_a, true);
    
    //The packet pipeline
    public static final PacketPipeline packetPipeline = new PacketPipeline();
    
    public TConstruct()
    {
        LoggerConfig fml = new LoggerConfig(FMLCommonHandler.instance().getFMLLogger().getName(), Level.ALL, true);
        LoggerConfig modConf = new LoggerConfig(logger.getName(), Level.ALL, true);
        modConf.setParent(fml);

        //logger.setParent(FMLCommonHandler.instance().getFMLLogger());
        if (Loader.isModLoaded("Natura"))
        {
            logger.info("Natura, what are we going to do tomorrow night?");
            LogManager.getLogger("Natura").info("TConstruct, we're going to take over the world!");
        }
        else
        {
            logger.info("Preparing to take over the world");
        }

        EnvironmentChecks.verifyEnvironmentSanity();
        MinecraftForge.EVENT_BUS.register(events = new TEventHandler());
        PluginController.getController().registerBuiltins();
    }

    @EventHandler
    public void preInit (FMLPreInitializationEvent event)
    {
    	
        PHConstruct.initProps(event.getSuggestedConfigurationFile());
        TConstructRegistry.materialTab = new TabTools("TConstructMaterials");
        TConstructRegistry.toolTab = new TabTools("TConstructTools");
        TConstructRegistry.blockTab = new TabTools("TConstructBlocks");

        tableCasting = new LiquidCasting();
        basinCasting = new LiquidCasting();
        chiselDetailing = new Detailing();

        recipes = new TRecipes();
        content = new TContent();

        MinecraftForge.EVENT_BUS.register(new TEventHandlerAchievement());
        recipes.oreRegistry();

        proxy.registerRenderer();
        proxy.addNames();
        proxy.readManuals();
        proxy.registerKeys();
        proxy.registerTickHandler();

        GameRegistry.registerWorldGenerator(new TBaseWorldGenerator(), 0);
        MinecraftForge.TERRAIN_GEN_BUS.register(new TerrainGenEventHandler());
        GameRegistry.registerFuelHandler(content);
        GameRegistry.registerCraftingHandler(new TCraftingHandler());
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, proxy);

        if (PHConstruct.addToVillages)
        {
            // adds to the villager spawner egg
            VillagerRegistry.instance().registerVillagerId(78943);
            // moved down, not needed if 'addToVillages' is false
            VillagerRegistry.instance().registerVillageTradeHandler(78943, new TVillageTrades());
            VillagerRegistry.instance().registerVillageCreationHandler(new VillageToolStationHandler());
            VillagerRegistry.instance().registerVillageCreationHandler(new VillageSmelteryHandler());
            try
            {
                // if (new CallableMinecraftVersion(null).minecraftVersion().equals("1.6.4"))
                // {
                MapGenStructureIO.func_143031_a(ComponentToolWorkshop.class, "TConstruct:ToolWorkshopStructure");
                MapGenStructureIO.func_143031_a(ComponentSmeltery.class, "TConstruct:SmelteryStructure");
                // }
            }
            catch (Throwable e)
            {
                logger.error("Error registering TConstruct Structures with Vanilla Minecraft: this is expected in versions earlier than 1.6.4");
            }
        }

        playerTracker = new TPlayerHandler();
        GameRegistry.registerPlayerTracker(playerTracker);
        MinecraftForge.EVENT_BUS.register(playerTracker);

        PluginController.getController().preInit();
    }

    @EventHandler
    public void init (FMLInitializationEvent event)
    {
    	packetPipeline.initalise();
        if (event.getSide() == Side.CLIENT)
        {
            MinecraftForge.EVENT_BUS.register(new EventCloakRender());
        }

        DimensionBlacklist.getBadBimensions();
        GameRegistry.registerWorldGenerator(new SlimeIslandGen(TRepo.slimePool,2), 2);

        PluginController.getController().init();

        if (PHConstruct.achievementsEnabled)
        {
            TAchievements.init();
        }
    }

    @EventHandler
    public void postInit (FMLPostInitializationEvent evt)
    {
        proxy.postInit();
        packetPipeline.postInitialise();
        Behavior.registerBuiltInBehaviors();
        SpecialStackHandler.registerBuiltInStackHandlers();
        recipes.modIntegration();
        recipes.addOreDictionarySmelteryRecipes();
        content.createEntities();
        recipes.modRecipes();

        PluginController.getController().postInit();
    }

    public static LiquidCasting getTableCasting ()
    {
        return tableCasting;
    }

    public static LiquidCasting getBasinCasting ()
    {
        return basinCasting;
    }

    public static Detailing getChiselDetailing ()
    {
        return chiselDetailing;
    }

    public static TContent content;
    public static TRecipes recipes;
    public static TEventHandler events;
    public static TPlayerHandler playerTracker;
    public static LiquidCasting tableCasting;
    public static LiquidCasting basinCasting;
    public static Detailing chiselDetailing;
}
