package com.example.deathwhisper;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;

public final class WatcherEntity extends Zombie {
    private static final EntityDataAccessor<Integer> PEEK_SIDE = SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PEEK_ACTIVE = SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.INT);
    private UUID chosenPlayer;
    private int lifetime;
    private int relocations;
    private boolean whistled;
    private boolean charging;
    private int chargeTicks;
    private int lastSeenTicks;
    private BlockPos coverSpot;
    private BlockPos peekSpot;
    private int coverTicks;
    private int routeTimeout;
    private int phase; // 0 watch, 1 run for cover, 2 hide, 3 peek, 4 return


    public WatcherEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setSilent(true);
        setNoAi(false);
    }
    @Override protected boolean isSunSensitive() { return false; }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity entity) { return false; }
    @Override protected void registerGoals() { }
    public void setChosenPlayer(UUID id) { chosenPlayer = id; }
    public UUID getChosenPlayer() { return chosenPlayer; }
    public int peekSide() { return entityData.get(PEEK_SIDE); }
    public boolean isPeeking() { return entityData.get(PEEK_ACTIVE) != 0; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PEEK_SIDE, 1);
        builder.define(PEEK_ACTIVE, 0);
    }

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
            if (chargeTicks % 12 == 1) getNavigation().moveTo(target, 1.65);
            return;
        }
        // Run using Minecraft's pathfinder rather than teleporting.
        if (phase != 0) {
            routeTimeout++;
            if (routeTimeout > 145) { getNavigation().stop(); phase = 0; coverSpot = null; entityData.set(PEEK_ACTIVE, 0); }
            else if (phase == 1) {
                if (coverSpot != null && distanceToSqr(Vec3.atCenterOf(coverSpot)) < 3.5) {
                    getNavigation().stop(); phase = 2; coverTicks = 0; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 0);
                } else if (getNavigation().isDone()) {
                    phase = 0; coverSpot = null;
                }
            } else if (phase == 2) {
                getNavigation().stop();
                entityData.set(PEEK_ACTIVE, 0);
                if (++coverTicks > 35 && peekSpot != null) {
                    if (getNavigation().moveTo(peekSpot.getX()+0.5, peekSpot.getY(), peekSpot.getZ()+0.5, 0.65)) {
                        phase = 3; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 1);
                    }
                }
            } else if (phase == 3) {
                if (getNavigation().isDone() || routeTimeout > 45) {
                    coverTicks = 0; phase = 4; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 0);
                    if (coverSpot != null) getNavigation().moveTo(coverSpot.getX()+0.5, coverSpot.getY(), coverSpot.getZ()+0.5, 0.85);
                }
            } else if (phase == 4 && (getNavigation().isDone() || routeTimeout > 50)) {
                phase = 2; coverTicks = -40; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 0);
            }
            return;
        }
        if (lastSeenTicks > 80 && lifetime > 125) { discard(); return; }
        if (lifetime > 100 && lifetime % 85 == 0) {
            if (level.getGameRules().getBoolean(WhisperRules.FAKE_CHARGES)
                 && level.random.nextInt(100) < Mth.clamp(level.getGameRules().getInt(WhisperRules.CHARGE_CHANCE), 0, 100)) {
                charging = true;
                level.playSound(null, blockPosition(), DeathWhisper.WHISTLE.get(), SoundSource.HOSTILE, 2.3f, 1.2f);
                return;
            }
            if (level.getGameRules().getBoolean(WhisperRules.REPOSITION) && relocations < 3 && !seen) {
                if (seekCover(level, target)) {
                    relocations++;
                    whistled = false;
                }
            }
        }
    }
    // Find reachable cover with an opaque block between player and hiding spot,
    // then a nearby peek tile from which the player can be seen.
    private boolean seekCover(ServerLevel level, ServerPlayer target) {
        for (int attempt = 0; attempt < 35; attempt++) {
            int x = blockPosition().getX() + level.random.nextInt(29)-14;
            int z = blockPosition().getZ() + level.random.nextInt(29)-14;
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos spot = new BlockPos(x, blockPosition().getY()+dy, z);
                if (!walkable(level, spot) || spot.distSqr(target.blockPosition()) < 140) continue;
                if (visibleFrom(level, target, spot)) continue;
                var path = getNavigation().createPath(spot, 1);
                if (path == null || !path.canReach()) continue;
                BlockPos peek = null;
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos side = spot.relative(direction);
                    if (walkable(level, side) && visibleFrom(level, target, side)) { peek = side; break; }
                }
                if (peek == null) continue;
                if (!getNavigation().moveTo(path, 1.25)) continue;
                coverSpot = spot;
                peekSpot = peek;
                // Use the target-facing cross product to choose the tilt direction.
                Vec3 toTarget = target.position().subtract(position());
                Vec3 toPeek = Vec3.atCenterOf(peek).subtract(Vec3.atCenterOf(spot));
                entityData.set(PEEK_SIDE, (toTarget.x * toPeek.z - toTarget.z * toPeek.x) >= 0 ? 1 : -1);
                phase = 1; routeTimeout = 0;
                return true;
            }
        }
        return false;
    }
    private boolean walkable(ServerLevel level, BlockPos p) {
        return level.hasChunkAt(p) && level.getBlockState(p).isAir()
            && level.getBlockState(p.above()).isAir()
            && level.getBlockState(p.below()).isSolidRender(level, p.below());
    }
    private boolean visibleFrom(ServerLevel level, ServerPlayer player, BlockPos p) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = Vec3.atCenterOf(p).add(0, 0.65, 0);
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
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
        tag.putInt("Phase", phase);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ChosenPlayer")) chosenPlayer = tag.getUUID("ChosenPlayer");
        lifetime = tag.getInt("Lifetime");
        relocations = tag.getInt("Relocations");
        whistled = tag.getBoolean("Whistled");
        charging = tag.getBoolean("Charging");
        chargeTicks = tag.getInt("ChargeTicks");
        // Recalculate paths after a chunk/server reload.
        phase = 0; coverSpot = null; peekSpot = null; entityData.set(PEEK_ACTIVE, 0);
    }
}
