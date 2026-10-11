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
    default void serverTick(MinecraftServer server) {}
    default void beforeDamage(LivingDamageEvent.Pre event) {}
}
