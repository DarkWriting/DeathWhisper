package com.example.deathwhisper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Interface for any active horror ability. One instance is registered per ability ID. */
public interface SupernaturalAbility {
    String id();
    /** Returns whether the activation actually succeeded. No cooldown on failure. */
    boolean activate(ServerPlayer player);
    /** Gamerule-driven cooldown, in ticks. */
    int cooldownTicks(ServerPlayer player);
    /** Allows a second activation while its first effect is still live, without resetting the main cooldown. */
    default boolean allowRecastOnCooldown(ServerPlayer player) {return false;}
    default boolean hasRecastStage() {return false;}
    default void serverTick(MinecraftServer server) {}
    default void beforeDamage(LivingDamageEvent.Pre event) {}
}
