package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Two-stage Slasher Relic: use to cloak and track footsteps, use a second time
 * to lunge in the direction you are aiming. Particle trails are private to user.
 */
public final class SlasherPower implements SupernaturalAbility {
    private static final class State {
        final long until; long lastCast; boolean lunged;
        State(long until,long lastCast){this.until=until;this.lastCast=lastCast;}
    }
    private final Map<UUID,State> active=new HashMap<>();
    private final Map<UUID,Map<UUID,ArrayDeque<Vec3>>> tracks=new HashMap<>();
    @Override public String id(){return "slasher_relic";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SLASHER_COOLDOWN),5,180);
    }
    @Override public boolean hasRecastStage(){return true;}
    @Override public boolean allowRecastOnCooldown(ServerPlayer p){
        State state=active.get(p.getUUID());
        long now=p.getServer().overworld().getGameTime();
        return state!=null && !state.lunged && now>=state.lastCast+12 && now<state.until;
    }
    /** A single consumable iron-nugget "scrap" projectile, without breaking terrain. */
    private boolean throwScrap(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        boolean found=p.isCreative();
        if(!found)for(var item:p.getInventory().items){
            if(item.is(Items.IRON_NUGGET) && item.getCount()>0){
                item.shrink(1);found=true;break;
            }
        }
        if(!found){
            p.displayClientMessage(Component.literal("Carry an iron nugget as improvised scrap ammunition."),true);
            return false;
        }
        Vec3 eye=p.getEyePosition();
        Vec3 end=eye.add(p.getLookAngle().scale(18));
        var terrain=level.clip(new net.minecraft.world.level.ClipContext(eye,end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        Vec3 finalPoint=terrain.getType()==net.minecraft.world.phys.HitResult.Type.MISS?
            end:terrain.getLocation();
        LivingEntity hit=null;double nearest=Double.POSITIVE_INFINITY;
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,
            new AABB(eye,finalPoint).inflate(.4),entity->PowerFramework.canAffect(p,entity))){
            var intersection=other.getBoundingBox().inflate(.13).clip(eye,finalPoint);
            if(intersection.isEmpty())continue;
            double d=eye.distanceToSqr(intersection.get());
            if(d<nearest){nearest=d;hit=other;}
        }
        Vec3 target=hit==null?finalPoint:hit.getEyePosition();
        for(int i=0;i<=16;i++){
            Vec3 at=eye.lerp(target,i/16.0);
            level.sendParticles(ParticleTypes.CRIT,at.x,at.y,at.z,1,0,0,0,0);
        }
        level.playSound(null,p.blockPosition(),SoundEvents.TRIDENT_THROW.value(),SoundSource.PLAYERS,1f,.85f);
        if(hit!=null){
            hit.hurt(p.damageSources().magic(),3f);
            hit.knockback(1.3,-p.getLookAngle().x,-p.getLookAngle().z);
            net.minecraft.core.BlockPos behind=net.minecraft.core.BlockPos.containing(
                hit.position().add(p.getLookAngle().scale(1.1)));
            if(level.hasChunkAt(behind) && !level.getBlockState(behind).getCollisionShape(level,behind).isEmpty()){
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,4,false,true,true));
                p.displayClientMessage(Component.literal("Scrap impaled "+hit.getName().getString()+" against cover."),true);
            }else p.displayClientMessage(Component.literal("Scrap struck "+hit.getName().getString()),true);
        }else p.displayClientMessage(Component.literal("Scrap missed."),true);
        return true;
    }
    @Override public boolean activate(ServerPlayer p){
        long now=p.getServer().overworld().getGameTime();
        State state=active.get(p.getUUID());
        if(state!=null && !state.lunged && now<state.until && now>=state.lastCast+12){
            if(p.isShiftKeyDown())return throwScrap(p);
            state.lunged=true;
            p.removeEffect(MobEffects.INVISIBILITY);
            double speed=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SLASHER_LUNGE_SPEED),70,220)/100.0;
            Vec3 direction=p.getLookAngle();
            p.setDeltaMovement(direction.x*speed,Math.max(.12,direction.y*speed*.55),direction.z*speed);
            p.hurtMarked=true;
            p.fallDistance=0;
            p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.RAVAGER_ROAR,SoundSource.PLAYERS,.55f,1.35f);
            p.displayClientMessage(Component.literal("SLASHER LUNGE"),true);
            return true;
        }
        if(state!=null && now<state.until)return false;
        // Crouch + use fires scrap instead of activating the stealth stance.
        if(p.isShiftKeyDown())return throwScrap(p);
        int duration=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SLASHER_SECONDS),3,35);
        active.put(p.getUUID(),new State(now+duration*20L,now));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,duration*20,0,false,false,true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,duration*20,0,false,false,true));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.ENDERMAN_AMBIENT,SoundSource.PLAYERS,1f,.7f);
        p.displayClientMessage(Component.literal("Omnipresent Evil: invisible; follow footprints. Use again to jump-scare dash, or sneak-use to throw scrap."),true);
        return true;
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        active.entrySet().removeIf(entry->{
            var p=server.getPlayerList().getPlayer(entry.getKey());
            State state=entry.getValue();
            if(p==null||!p.isAlive()||now>=state.until||
                !p.level().getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)){
                tracks.remove(entry.getKey());return true;
            }
            if(now%8!=0)return false;
            if(state.lunged)return false;
            ServerLevel level=p.serverLevel();
            int range=Mth.clamp(level.getGameRules().getInt(WhisperRules.SLASHER_TRACK_RANGE),8,40);
            Map<UUID,ArrayDeque<Vec3>> record=tracks.computeIfAbsent(entry.getKey(),x->new HashMap<>());
            // Only allow tracking players if independently opted in.
            boolean showPlayers=level.getGameRules().getBoolean(WhisperRules.SLASHER_TRACK_PLAYERS);
            List<LivingEntity> nearby=level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),
                e->e!=p && e.isAlive() && !(e instanceof WatcherEntity) && e.distanceToSqr(p)<range*range
                   && (!(e instanceof ServerPlayer sp) || (showPlayers && !sp.isSpectator()&&!sp.isCreative())));
            nearby.sort(Comparator.comparingDouble(e->e.distanceToSqr(p)));
            int count=0;
            for(var entity:nearby) {
                if(count++>=12)break;
                ArrayDeque<Vec3> steps=record.computeIfAbsent(entity.getUUID(),x->new ArrayDeque<>());
                steps.addLast(entity.position());
                while(steps.size()>7)steps.removeFirst();
            }
            if(record.size()>24) record.clear();
            int shown=0;
            for(var trail:record.values())for(var point:trail){
                if(point.distanceToSqr(p.position())>range*range)continue;
                // Target the activator only, rather than broadcasting trails across multiplayer.
                level.sendParticles(p,ParticleTypes.SOUL_FIRE_FLAME,false,
                    point.x,point.y+.12,point.z,1,.04,0,.04,0);
                if(++shown>=42)break;
            }
            return false;
        });
    }
}
