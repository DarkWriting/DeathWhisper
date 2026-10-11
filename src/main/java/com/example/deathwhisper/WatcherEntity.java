package com.example.deathwhisper;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;

public final class WatcherEntity extends Zombie {
    private UUID chosenPlayer;
    private int lifetime = 0;
    private boolean whistled;

    public WatcherEntity(EntityType<? extends Zombie> type, Level level) { super(type, level); setInvulnerable(true); setSilent(true); }
    @Override protected boolean isSunSensitive() { return false; }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity entity) { return false; }
    @Override protected void registerGoals() { }
    public void setChosenPlayer(UUID id) { chosenPlayer = id; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        lifetime++;
        ServerPlayer target = chosenPlayer == null ? null : level().getServer().getPlayerList().getPlayer(chosenPlayer);
        if (target == null || target.level() != level() || target.isSpectator() || lifetime > 320) { discard(); return; }
        getLookControl().setLookAt(target, 30f, 30f);
        if (!whistled && lifetime >= 35) {
            whistled = true;
            level().playSound(null, blockPosition(), DeathWhisper.WHISTLE.get(), SoundSource.HOSTILE, 1.9f, 0.95f);
        }
        if (distanceToSqr(target) < 64) { discard(); return; }
        if (lifetime > 115 && target.hasLineOfSight(this) && playerFacing(target)) discard();
    }
    private boolean playerFacing(ServerPlayer player) {
        var toward = position().subtract(player.getEyePosition()).normalize();
        return player.getLookAngle().dot(toward) > 0.93;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (chosenPlayer != null) tag.putUUID("ChosenPlayer", chosenPlayer);
        tag.putInt("Lifetime", lifetime);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ChosenPlayer")) chosenPlayer = tag.getUUID("ChosenPlayer");
        lifetime = tag.getInt("Lifetime");
    }
}
