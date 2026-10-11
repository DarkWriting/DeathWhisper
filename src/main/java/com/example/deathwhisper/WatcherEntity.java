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
    private static final EntityDataAccessor<String> TARGET_SKIN = SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.INT);
    private UUID chosenPlayer;
    private EncounterKind encounter=EncounterKind.WATCH;
    private boolean sightingRecorded, chaseRecorded, escaped;
    private int lastFootsteps, peekCycles, lastWhistle;

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
    public void setChosenPlayer(UUID id) { chosenPlayer = id; entityData.set(TARGET_SKIN, id.toString()); }
    public UUID skinTarget() { try { return UUID.fromString(entityData.get(TARGET_SKIN)); } catch(Exception e) { return null; } }
    public UUID getChosenPlayer() { return chosenPlayer; }
    public void setEncounterKind(EncounterKind kind) {
        encounter=kind;
        entityData.set(MOOD, kind.ordinal());
    }
    public EncounterKind getEncounterKind() {
        int id=entityData.get(MOOD);
        return EncounterKind.values()[Mth.clamp(id,0,EncounterKind.values().length-1)];
    }
    public String debugState() {
        return getEncounterKind().name()+" phase="+phase+" age="+lifetime+" ticks"
           +" route="+routeTimeout+" peek="+isPeeking()+" cover="+coverSpot+" relocations="+relocations;
    }

    public int peekSide() { return entityData.get(PEEK_SIDE); }
    public boolean isPeeking() { return entityData.get(PEEK_ACTIVE) != 0; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PEEK_SIDE, 1);
        builder.define(PEEK_ACTIVE, 0);
        builder.define(MOOD, 0);
        builder.define(TARGET_SKIN, "");
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
            if (nearest != null) setChosenPlayer(nearest.getUUID());
        }
        ServerPlayer target = chosenPlayer == null ? null : level.getServer().getPlayerList().getPlayer(chosenPlayer);
        if (target == null || target.level() != level || target.isSpectator()
                || !level.getGameRules().getBoolean(WhisperRules.ENABLED)) { discard(); return; }
        int maxTicks = Mth.clamp(level.getGameRules().getInt(WhisperRules.DURATION), 5, 180) * 20;
        if (encounter==EncounterKind.FALSE_SIGHTING || encounter==EncounterKind.PERIPHERAL) maxTicks=Math.min(maxTicks,110);
        if (encounter==EncounterKind.FAKE_DEPARTURE) maxTicks+=160;
        if (lifetime > maxTicks || distanceToSqr(target) > 120*120) {
            if (!escaped) target.getPersistentData().putInt("deathwhisper_escapes",
                target.getPersistentData().getInt("deathwhisper_escapes")+1);
            discard();return;
        }
        getLookControl().setLookAt(target, 30f, 30f);
        // setNoAi prevents other AI goals from fighting with our directed movement.
        var delta = target.position().subtract(position());
        setYRot((float)(Mth.atan2(-delta.x, delta.z) * (180 / Math.PI)));

        if (!whistled && lifetime >= 35 && encounter != EncounterKind.SILENT && encounter != EncounterKind.FALSE_SIGHTING
                && !(level.getGameRules().getBoolean(WhisperRules.SILENT_ENCOUNTERS)
                    && level.random.nextInt(100)<10)) {
            whistled = true; lastWhistle=lifetime;
            int variant=level.random.nextInt(3);
            var sound=variant==0?DeathWhisper.WHISTLE.get():variant==1?DeathWhisper.WHISTLE_ALT.get():DeathWhisper.WHISTLE_LOW.get();
            level.playSound(null, blockPosition(), sound, SoundSource.HOSTILE,
                Mth.clamp(level.getGameRules().getInt(WhisperRules.WHISTLE_VOLUME),0,200)/100f*1.9f, .9f+level.random.nextFloat()*.16f);
            target.getPersistentData().putInt("deathwhisper_whistles",target.getPersistentData().getInt("deathwhisper_whistles")+1);
        }
        if (level.getGameRules().getBoolean(WhisperRules.AMBIENCE) && lifetime%90==0)
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,getX(),getY()+1,getZ(),3,.3,.4,.3,.005);
        if (level.getGameRules().getBoolean(WhisperRules.FOOTSTEPS) && !charging && lifetime>65 && lifetime-lastFootsteps>110
             && (encounter==EncounterKind.FOOTSTEPS || (phase==1 && level.random.nextInt(100)<25))) {
            lastFootsteps=lifetime;
            level.playSound(null,blockPosition(),DeathWhisper.FOOTSTEPS.get(),SoundSource.HOSTILE,.65f,.8f+level.random.nextFloat()*.4f);
        }
        if (distanceToSqr(target) < 49) {
            if(!chaseRecorded) {
                target.getPersistentData().putInt("deathwhisper_chases",target.getPersistentData().getInt("deathwhisper_chases")+1);
                chaseRecorded=true;
            }
            discard();return;
        }
        boolean seen = target.hasLineOfSight(this) && playerFacing(target);
        lastSeenTicks = seen ? lastSeenTicks+1 : 0;
        if(seen && !sightingRecorded && lastSeenTicks>12) {
            sightingRecorded=true;
            target.getPersistentData().putInt("deathwhisper_sightings", target.getPersistentData().getInt("deathwhisper_sightings")+1);
            target.getPersistentData().putInt("deathwhisper_last", (int)(level.getGameTime()%2147483647));
        }
        if(seen && lastSeenTicks==50) {
            target.getPersistentData().putInt("deathwhisper_curiosity",target.getPersistentData().getInt("deathwhisper_curiosity")+1);
        }
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
                if (++coverTicks > 35 && peekSpot != null
                        && level.getGameRules().getBoolean(WhisperRules.PEEKING)) {
                    if (getNavigation().moveTo(peekSpot.getX()+0.5, peekSpot.getY(), peekSpot.getZ()+0.5, 0.65)) {
                        phase = 3; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 1); peekCycles++;
                    }
                }
            } else if (phase == 3) {
                if (getNavigation().isDone() || routeTimeout > Mth.clamp(level.getGameRules().getInt(WhisperRules.PEEK_SECONDS),1,10)*20) {
                    coverTicks = 0; phase = 4; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 0);
                    if (coverSpot != null) getNavigation().moveTo(coverSpot.getX()+0.5, coverSpot.getY(), coverSpot.getZ()+0.5, 0.85);
                }
            } else if (phase == 4 && (getNavigation().isDone() || routeTimeout > 50)) {
                phase = 2; coverTicks = -40; routeTimeout = 0; entityData.set(PEEK_ACTIVE, 0);
            }
            return;
        }
        if (lastSeenTicks > Mth.clamp(level.getGameRules().getInt(WhisperRules.STARE_SECONDS),2,20)*20 && lifetime > 125) {
            if (level.getGameRules().getBoolean(WhisperRules.LAUGHTER) && level.random.nextInt(3)==0)
                level.playSound(null,blockPosition(),DeathWhisper.LAUGHTER.get(),SoundSource.HOSTILE,.75f,1f);
            discard(); return;
        }
        if (lifetime > 100 && lifetime % 85 == 0) {
            int chargeChance=Mth.clamp(level.getGameRules().getInt(WhisperRules.CHARGE_CHANCE),0,100);
            if(level.getGameRules().getBoolean(WhisperRules.BEHAVIOR_ADAPT))
                chargeChance=Mth.clamp(chargeChance+target.getPersistentData().getInt("deathwhisper_chases")/4,0,80);
            if (level.getGameRules().getBoolean(WhisperRules.FAKE_CHARGES)
                 && level.random.nextInt(100) < chargeChance && encounter!=EncounterKind.FALSE_SIGHTING) {
                charging = true;
                level.playSound(null, blockPosition(), DeathWhisper.WHISTLE.get(), SoundSource.HOSTILE, 2.3f, 1.2f);
                return;
            }
            if (level.getGameRules().getBoolean(WhisperRules.REPOSITION)
                 && relocations < Mth.clamp(level.getGameRules().getInt(WhisperRules.RELOCATIONS),0,12) && !seen) {
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
        int budget=Mth.clamp(level.getGameRules().getInt(WhisperRules.PATH_BUDGET),1,40);
        int near=Mth.clamp(level.getGameRules().getInt(WhisperRules.MIN_PATH_DISTANCE),2,20);
        int far=Mth.clamp(level.getGameRules().getInt(WhisperRules.MAX_PATH_DISTANCE),near+1,35);
        for (int attempt = 0; attempt < budget; attempt++) {
            int dx=level.random.nextInt(far*2+1)-far;
            int dz=level.random.nextInt(far*2+1)-far;
            if(dx*dx+dz*dz<near*near)continue;
            int x = blockPosition().getX()+dx;
            int z = blockPosition().getZ()+dz;
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
        tag.putString("Encounter", encounter.name());
        tag.putInt("PeekCycles",peekCycles);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ChosenPlayer")) setChosenPlayer(tag.getUUID("ChosenPlayer"));
        lifetime = tag.getInt("Lifetime");
        relocations = tag.getInt("Relocations");
        whistled = tag.getBoolean("Whistled");
        charging = tag.getBoolean("Charging");
        chargeTicks = tag.getInt("ChargeTicks");
        if(tag.contains("Encounter")) {
            EncounterKind parsed=EncounterKind.parse(tag.getString("Encounter"));
            if(parsed!=null)setEncounterKind(parsed);
        }
        peekCycles=tag.getInt("PeekCycles");
        // Recalculate paths after a chunk/server reload.
        phase = 0; coverSpot = null; peekSpot = null; entityData.set(PEEK_ACTIVE, 0);
    }
}
