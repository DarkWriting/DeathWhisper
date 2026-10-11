package com.example.deathwhisper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DeathWhisper.ID)
public final class DeathWhisper {
    public static final String ID = "deathwhisper";
    public static final HorrorPowers POWERS = new HorrorPowers();
    public static final PowerFramework FRAMEWORK = new PowerFramework(POWERS);
    public static final InvestigationManager INVESTIGATIONS = new InvestigationManager();
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<MotionSensorBlock> MOTION_SENSOR_BLOCK = BLOCKS.registerBlock(
        "motion_sensor",MotionSensorBlock::new,
        BlockBehaviour.Properties.of().strength(1.4f).sound(SoundType.METAL).noOcclusion());
    public static final net.neoforged.neoforge.registries.DeferredItem<BlockItem> MOTION_SENSOR_ITEM=
        ITEMS.registerSimpleBlockItem(MOTION_SENSOR_BLOCK);
    public static final net.neoforged.neoforge.registries.DeferredItem<ChainPuzzleBox> CHAIN_BOX =
        ITEMS.registerItem("chain_puzzle_box",ChainPuzzleBox::new,new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<BlightSerum> RUSH_SERUM =
        ITEMS.registerItem("blight_serum",BlightSerum::new,new Item.Properties().stacksTo(1));

    public static final net.neoforged.neoforge.registries.DeferredItem<PowerItem> GHOST_MASK =
        ITEMS.registerItem("shrouded_mask",p->new PowerItem(p,"shrouded_mask"),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<PowerItem> ARTIST_FEATHER =
        ITEMS.registerItem("ink_feather",p->new PowerItem(p,"ink_feather"),new Item.Properties().stacksTo(1));


    public static final net.neoforged.neoforge.registries.DeferredItem<PowerItem> LIVING_HARPOON =
        ITEMS.registerItem("living_harpoon",p->new PowerItem(p,"living_harpoon"),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<PowerItem> SLASHER_RELIC =
        ITEMS.registerItem("slasher_relic",p->new PowerItem(p,"slasher_relic"),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<SecurityDoorController> SECURITY_CONTROLLER =
        ITEMS.registerItem("security_door_controller",SecurityDoorController::new,new Item.Properties().stacksTo(1));

    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> SEANCE_CHALK =
        ITEMS.registerItem("seance_chalk",p->new InvestigationItem(p,InvestigationItem.Tool.CHALK),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> FIELD_JOURNAL =
        ITEMS.registerItem("field_journal",p->new InvestigationItem(p,InvestigationItem.Tool.JOURNAL),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> EMF_READER =
        ITEMS.registerItem("emf_reader",p->new InvestigationItem(p,InvestigationItem.Tool.EMF),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> SPIRIT_RADIO =
        ITEMS.registerItem("spirit_radio",p->new InvestigationItem(p,InvestigationItem.Tool.SPIRIT_RADIO),new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> GHOST_THERMOMETER =
        ITEMS.registerItem("ghost_thermometer",p->new InvestigationItem(p,InvestigationItem.Tool.THERMOMETER),new Item.Properties().stacksTo(1));

    public static final net.neoforged.neoforge.registries.DeferredItem<InvestigationItem> UV_LAMP =
        ITEMS.registerItem("uv_lamp",p->new InvestigationItem(p,InvestigationItem.Tool.UV),new Item.Properties().stacksTo(1));

    public static final net.neoforged.neoforge.registries.DeferredItem<CursedRecorder> CURSED_RECORDER=
        ITEMS.registerItem("cursed_recorder",CursedRecorder::new,new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<CursedTape> CURSED_TAPE=
        ITEMS.registerItem("cursed_tape",CursedTape::new,new Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<GhostHuntKit> HUNT_KIT=
        ITEMS.registerItem("ghost_hunt_kit",GhostHuntKit::new,new Item.Properties().stacksTo(1));
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher", () ->
        EntityType.Builder.<WatcherEntity>of(WatcherEntity::new, MobCategory.MISC)
            .sized(0.65f, 1.95f).clientTrackingRange(80).updateInterval(2).build("deathwhisper:watcher"));
    public static final DeferredHolder<EntityType<?>, EntityType<GhostApparition>> GHOST_APPARITION=
        ENTITIES.register("ghost_apparition",()->EntityType.Builder.<GhostApparition>of(
            GhostApparition::new,MobCategory.MISC).sized(.65f,1.95f)
            .clientTrackingRange(48).updateInterval(2).build("deathwhisper:ghost_apparition"));
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID,name)));
    }
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE = sound("whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE_ALT = sound("whistle_alt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE_LOW = sound("whistle_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> FOOTSTEPS = sound("footsteps");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUGHTER = sound("laughter");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE = sound("ambience");

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.COMBAT) {
            event.accept(CHAIN_BOX.get());
            event.accept(RUSH_SERUM.get());
            event.accept(GHOST_MASK.get());
            event.accept(ARTIST_FEATHER.get());
            event.accept(LIVING_HARPOON.get());
            event.accept(SLASHER_RELIC.get());
            event.accept(SECURITY_CONTROLLER.get());
            event.accept(SEANCE_CHALK.get());
            event.accept(FIELD_JOURNAL.get());
            event.accept(EMF_READER.get());
            event.accept(SPIRIT_RADIO.get());
            event.accept(GHOST_THERMOMETER.get());
            event.accept(UV_LAMP.get());
            event.accept(MOTION_SENSOR_ITEM.get());
            event.accept(CURSED_RECORDER.get());
            event.accept(CURSED_TAPE.get());
            event.accept(HUNT_KIT.get());
        }
    }
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WATCHER.get(), Zombie.createAttributes().build());
        event.put(GHOST_APPARITION.get(),Zombie.createAttributes().build());
    }

    public DeathWhisper(IEventBus modBus) {
        // Force gamerule registration before the vanilla gamerule registry is frozen.
        WhisperRules.ENABLED.toString();
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeItems);
        SOUNDS.register(modBus);
        modBus.addListener(this::registerAttributes);
        StalkingManager manager = new StalkingManager();
        NeoForge.EVENT_BUS.register(manager);
        NeoForge.EVENT_BUS.register(POWERS);
        NeoForge.EVENT_BUS.register(FRAMEWORK);
        NeoForge.EVENT_BUS.register(INVESTIGATIONS);
        NeoForge.EVENT_BUS.register(new InvestigationCommands(INVESTIGATIONS));
        NeoForge.EVENT_BUS.register(new WhisperCommands(manager));
    }
}
