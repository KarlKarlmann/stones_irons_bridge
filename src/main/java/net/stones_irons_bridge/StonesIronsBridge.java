package net.stones_irons_bridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.network.PacketDistributor;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.BridgeServerConfig;
import net.stones_irons_bridge.init.StonesIronsAttributes;
import net.stones_irons_bridge.logic.SpellCastHandler;
import net.stones_irons_bridge.network.PacketSyncSpellConfigs;
import net.stones_irons_bridge.network.PacketSyncServerSettings;
import net.stones_irons_bridge.network.PacketAdminSetupResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StonesIronsBridge.MODID)
public class StonesIronsBridge {
    public static final String MODID = "stones_irons_bridge";
    public static final Logger LOGGER = LogManager.getLogger();
    
    // ==========================================
    // NETWORK CHANNEL
    // ==========================================
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    // ==========================================
    // REGISTRIES
    // ==========================================
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    
    public static final RegistryObject<SoundEvent> SPELL_FIZZLE = SOUND_EVENTS.register("spell_fizzle", 
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "spell_fizzle")));
            
    public static final RegistryObject<SoundEvent> OBLITERATE = SOUND_EVENTS.register("obliterate", 
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "obliterate")));
            
    public static final RegistryObject<MobEffect> OVERDRIVE = MOB_EFFECTS.register("overdrive", 
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF00FF) {}
            .addAttributeModifier(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.CAST_TIME_REDUCTION.get(), 
                "934b2165-27a0-4b2a-89a1-5d9c2b4c5e31", 10.0D, AttributeModifier.Operation.MULTIPLY_TOTAL)
    );

    public static final RegistryObject<MobEffect> CLEARCAST = MOB_EFFECTS.register("clearcast", 
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x33FF33) {}
    );

    public static final RegistryObject<MobEffect> QUICKCAST = MOB_EFFECTS.register("quickcast", 
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFCC00) {}
    );

    public StonesIronsBridge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onAttributeModification);
        
        SOUND_EVENTS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        StonesIronsAttributes.REGISTRY.register(modEventBus);
        
        MinecraftForge.EVENT_BUS.register(new SpellCastHandler());
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
    }

    private void setup(final FMLCommonSetupEvent event) {
        int id = 0;
        PACKET_HANDLER.registerMessage(id++, PacketSyncSpellConfigs.class, PacketSyncSpellConfigs::toBytes, PacketSyncSpellConfigs::new, PacketSyncSpellConfigs::handle);
        PACKET_HANDLER.registerMessage(id++, PacketSyncServerSettings.class, PacketSyncServerSettings::toBytes, PacketSyncServerSettings::new, PacketSyncServerSettings::handle);
        PACKET_HANDLER.registerMessage(id++, PacketAdminSetupResult.class, PacketAdminSetupResult::toBytes, PacketAdminSetupResult::new, PacketAdminSetupResult::handle);

        SpellConfigManager.loadAndGenerateConfigs();
        LOGGER.info("Stones-Irons Bridge initialisiert!");
    }

    private void onAttributeModification(EntityAttributeModificationEvent event) {
        for (RegistryObject<Attribute> attr : StonesIronsAttributes.KNOWLEDGE_ATTRIBUTES.values()) {
            event.add(EntityType.PLAYER, attr.get());
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        BridgeServerConfig.load();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // NEU: Berechnet den Admin Status anhand der Permission-Level 2 (Op)
            boolean isPlayerAdmin = serverPlayer.hasPermissions(2);
            
            PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> serverPlayer), 
                new PacketSyncServerSettings(BridgeServerConfig.useReagents, BridgeServerConfig.promptAnswered, isPlayerAdmin));
        }
    }
}