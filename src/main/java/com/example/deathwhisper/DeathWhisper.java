package com.example.deathwhisper;

import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher", () ->
        EntityType.Builder.<WatcherEntity>of(WatcherEntity::new, MobCategory.MISC)
            .sized(0.65f, 1.95f).clientTrackingRange(80).updateInterval(2).build("deathwhisper:watcher"));
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE = SOUNDS.register("whistle", () ->
        SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, "whistle")));

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WATCHER.get(), Zombie.createAttributes().build());
    }

    public DeathWhisper(IEventBus modBus) {
        // Force gamerule registration before the vanilla gamerule registry is frozen.
        WhisperRules.ENABLED.toString();
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(this::registerAttributes);
        StalkingManager manager = new StalkingManager();
        NeoForge.EVENT_BUS.register(manager);
        NeoForge.EVENT_BUS.register(new WhisperCommands(manager));
    }
}
