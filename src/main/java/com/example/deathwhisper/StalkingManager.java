package com.example.deathwhisper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class StalkingManager {
    private static final int CHECK_EVERY_TICKS = 200;
    private static final int MIN_COOLDOWN_TICKS = 12000;
    private static final int RANDOM_EXTRA_COOLDOWN_TICKS = 18000;
    private static final double CHANCE_AFTER_COOLDOWN = 0.18;
    private static final int MIN_DISTANCE = 22;
    private static final int MAX_DISTANCE = 49;
    private final Map<UUID, Long> nextEncounter = new HashMap<>();
    private long ticks;

    @SubscribeEvent public void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % CHECK_EVERY_TICKS != 0) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) { nextEncounter.clear(); return; }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            if (!(player.level() instanceof ServerLevel level)) continue;
            UUID id = player.getUUID();
            if (!nextEncounter.containsKey(id)) {
                nextEncounter.put(id, ticks + 1800 + level.random.nextInt(5400));
                continue;
            }
            if (ticks < nextEncounter.get(id)) continue;
            if (level.random.nextDouble() >= CHANCE_AFTER_COOLDOWN) continue;
            if (trySpawn(level, player)) {
                nextEncounter.put(id, ticks + MIN_COOLDOWN_TICKS + level.random.nextInt(RANDOM_EXTRA_COOLDOWN_TICKS));
            }
        }
    }
    private boolean trySpawn(ServerLevel level, ServerPlayer player) {
        if (!level.getEntitiesOfClass(WatcherEntity.class, player.getBoundingBox().inflate(90)).isEmpty()) return false;
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = level.random.nextDouble() * Mth.TWO_PI;
            int distance = MIN_DISTANCE + level.random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            for (int offset = -6; offset <= 6; offset++) {
                BlockPos stand = new BlockPos(x, player.blockPosition().getY() + offset, z);
                if (!level.hasChunkAt(stand) || !level.getBlockState(stand.below()).isSolidRender(level, stand.below())) continue;
                if (!level.getBlockState(stand).isAir() || !level.getBlockState(stand.above()).isAir()) continue;
                WatcherEntity entity = DeathWhisper.WATCHER.get().create(level);
                if (entity == null) return false;
                entity.moveTo(x + 0.5, stand.getY(), z + 0.5, 0, 0);
                entity.setChosenPlayer(player.getUUID());
                if (level.addFreshEntity(entity)) return true;
            }
        }
        return false;
    }
}
