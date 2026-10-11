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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class StalkingManager {
    private final Map<UUID, Long> nextEncounter = new HashMap<>();
    private long ticks;

    public int threat(ServerPlayer p) { return Math.max(0, Math.min(100, p.getPersistentData().getInt("deathwhisper_threat"))); }
    public void setThreat(ServerPlayer p, int value) { p.getPersistentData().putInt("deathwhisper_threat", Mth.clamp(value, 0, 100)); }
    public String status(ServerPlayer p) {
        long remaining = Math.max(0, nextEncounter.getOrDefault(p.getUUID(), ticks) - ticks) / 20;
        long active = ((ServerLevel)p.level()).getEntitiesOfClass(WatcherEntity.class, p.getBoundingBox().inflate(90)).stream()
             .filter(w -> p.getUUID().equals(w.getChosenPlayer())).count();
        return "Death Whisper: threat=" + threat(p) + "/100, cooldown=" + remaining + "s, active=" + active
            + ", enabled=" + p.level().getGameRules().getBoolean(WhisperRules.ENABLED);
    }
    public void clear(ServerPlayer p) {
        nextEncounter.remove(p.getUUID());
        setThreat(p, 0);
        ((ServerLevel)p.level()).getEntitiesOfClass(WatcherEntity.class, p.getBoundingBox().inflate(128))
            .stream().filter(w -> p.getUUID().equals(w.getChosenPlayer())).forEach(WatcherEntity::discard);
    }
    @SubscribeEvent public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) setThreat(p, threat(p) + 18);
    }
    @SubscribeEvent public void onTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % 200 != 0) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive() || !(p.level() instanceof ServerLevel level)) continue;
            if (!level.getGameRules().getBoolean(WhisperRules.ENABLED)) continue;
            long cooldown = Math.max(10, level.getGameRules().getInt(WhisperRules.COOLDOWN)) * 20L;
            Long next = nextEncounter.get(p.getUUID());
            if (next == null) { nextEncounter.put(p.getUUID(), ticks + cooldown / 2); continue; }
            if (ticks < next) continue;
            int chance = Mth.clamp(level.getGameRules().getInt(WhisperRules.CHANCE) + threat(p) / 3, 0, 100);
            if (level.random.nextInt(100) < chance && force(p)) {
                setThreat(p, threat(p) + level.getGameRules().getInt(WhisperRules.THREAT_GAIN));
                nextEncounter.put(p.getUUID(), ticks + cooldown);
            } else nextEncounter.put(p.getUUID(), ticks + 200);
        }
    }
    public boolean force(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        if (!level.getEntitiesOfClass(WatcherEntity.class, p.getBoundingBox().inflate(95)).isEmpty()) return false;
        BlockPos pos = findPosition(level, p, 25);
        if (pos == null) return false;
        WatcherEntity w = DeathWhisper.WATCHER.get().create(level);
        if (w == null) return false;
        w.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        w.setChosenPlayer(p.getUUID());
        return level.addFreshEntity(w);
    }
    public static BlockPos findPosition(ServerLevel level, ServerPlayer p, int attempts) {
        int min = Mth.clamp(level.getGameRules().getInt(WhisperRules.MIN_DISTANCE), 10, 64);
        int max = Mth.clamp(level.getGameRules().getInt(WhisperRules.MAX_DISTANCE), min+1, 96);
        for (int attempt=0; attempt<attempts; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int distance = min + level.random.nextInt(max-min+1);
            int x = Mth.floor(p.getX()+Math.cos(angle)*distance);
            int z = Mth.floor(p.getZ()+Math.sin(angle)*distance);
            for(int dy=-7; dy<=7; dy++) {
                BlockPos at = new BlockPos(x,p.blockPosition().getY()+dy,z);
                if (!level.hasChunkAt(at) || !level.getBlockState(at.below()).isSolidRender(level,at.below())) continue;
                if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
                if (!level.getGameRules().getBoolean(WhisperRules.UNDERGROUND) && !level.canSeeSky(at)) continue;
                return at;
            }
        }
        return null;
    }
}
