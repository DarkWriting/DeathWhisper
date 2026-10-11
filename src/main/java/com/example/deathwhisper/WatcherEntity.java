package com.example.deathwhisper;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

public final class WatcherEntity extends Zombie {
    private UUID chosenPlayer;
    private int lifetime;
    private int relocations;
    private boolean whistled;
    private boolean charging;
    private int chargeTicks;
    private int lastSeenTicks;

    public WatcherEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setSilent(true);
        setNoAi(true);
    }
    @Override protected boolean isSunSensitive() { return false; }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity entity) { return false; }
    @Override protected void registerGoals() { }
    public void setChosenPlayer(UUID id) { chosenPlayer = id; }
    public UUID getChosenPlayer() { return chosenPlayer; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel level)) return;
        lifetime++;
        if (chosenPlayer == null) {
            ServerPlayer nearest = null;
            double best = 80*80;
            for (ServerPlayer candidate : level.getServer().getPlayerList().getPlayers()) {
                if (candidate.level() != level || candidate.isSpectator() || !candidate.isAlive()) continue;
                double d = distanceToSqr(candidate);
                if (d < best) { nearest = candidate; best = d; }
            }
            if (nearest != null) chosenPlayer = nearest.getUUID();
        }
        ServerPlayer target = chosenPlayer == null ? null : level.getServer().getPlayerList().getPlayer(chosenPlayer);
        if (target == null || target.level() != level || target.isSpectator()
                || !level.getGameRules().getBoolean(WhisperRules.ENABLED)) { discard(); return; }
        int maxTicks = Mth.clamp(level.getGameRules().getInt(WhisperRules.DURATION), 5, 180) * 20;
        if (lifetime > maxTicks || distanceToSqr(target) > 120*120) { discard(); return; }
        getLookControl().setLookAt(target, 30f, 30f);
        // setNoAi prevents other AI goals from fighting with our directed movement.
        var delta = target.position().subtract(position());
        setYRot((float)(Mth.atan2(-delta.x, delta.z) * (180 / Math.PI)));

        if (!whistled && lifetime >= 35) {
            whistled = true;
            level.playSound(null, blockPosition(), DeathWhisper.WHISTLE.get(), SoundSource.HOSTILE, 1.9f, 0.95f);
        }
        if (distanceToSqr(target) < 49) { discard(); return; }
        boolean seen = target.hasLineOfSight(this) && playerFacing(target);
        lastSeenTicks = seen ? lastSeenTicks+1 : 0;
        if (charging) {
            chargeTicks++;
            if (chargeTicks > 35 || distanceToSqr(target) < 80) { discard(); return; }
            var movement = target.position().subtract(position());
            if (movement.lengthSqr() > 0.01) {
                movement = movement.normalize().scale(0.42);
                setPos(getX()+movement.x, getY(), getZ()+movement.z);
            }
            return;
        }
        // An observed watcher stays briefly, then vanishes; unseen watchers reposition.
        if (lastSeenTicks > 60 && lifetime > 100) { discard(); return; }
        if (lifetime > 100 && lifetime % 85 == 0) {
            if (level.getGameRules().getBoolean(WhisperRules.FAKE_CHARGES)
                 && level.random.nextInt(100) < Mth.clamp(level.getGameRules().getInt(WhisperRules.CHARGE_CHANCE), 0, 100)) {
                charging = true;
                level.playSound(null, blockPosition(), DeathWhisper.WHISTLE.get(), SoundSource.HOSTILE, 2.3f, 1.2f);
                return;
            }
            if (level.getGameRules().getBoolean(WhisperRules.REPOSITION) && relocations < 3 && !seen) {
                BlockPos next = StalkingManager.findPosition(level, target, 14);
                if (next != null) {
                    teleportTo(next.getX()+.5, next.getY(), next.getZ()+.5);
                    relocations++;
                    whistled = false;
                    lifetime = Math.max(lifetime, 70);
                }
            }
        }
    }
    private boolean playerFacing(ServerPlayer player) {
        var direction = position().subtract(player.getEyePosition()).normalize();
        return player.getLookAngle().dot(direction) > 0.91;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (chosenPlayer != null) tag.putUUID("ChosenPlayer", chosenPlayer);
        tag.putInt("Lifetime", lifetime);
        tag.putInt("Relocations", relocations);
        tag.putBoolean("Whistled", whistled);
        tag.putBoolean("Charging", charging);
        tag.putInt("ChargeTicks", chargeTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ChosenPlayer")) chosenPlayer = tag.getUUID("ChosenPlayer");
        lifetime = tag.getInt("Lifetime");
        relocations = tag.getInt("Relocations");
        whistled = tag.getBoolean("Whistled");
        charging = tag.getBoolean("Charging");
        chargeTicks = tag.getInt("ChargeTicks");
    }
}
