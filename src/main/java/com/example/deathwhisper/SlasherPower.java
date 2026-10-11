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
    @Override public boolean allowRecastOnCooldown(ServerPlayer p){
        State state=active.get(p.getUUID());
        long now=p.getServer().overworld().getGameTime();
        return state!=null && !state.lunged && now>=state.lastCast+12 && now<state.until;
    }
    @Override public boolean activate(ServerPlayer p){
        long now=p.getServer().overworld().getGameTime();
        State state=active.get(p.getUUID());
        if(state!=null && !state.lunged && now<state.until && now>=state.lastCast+12){
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
        int duration=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SLASHER_SECONDS),3,35);
        active.put(p.getUUID(),new State(now+duration*20L,now));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,duration*20,0,false,false,true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,duration*20,0,false,false,true));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.ENDERMAN_AMBIENT,SoundSource.PLAYERS,1f,.7f);
        p.displayClientMessage(Component.literal("Cloaked. Follow spectral tracks; right-click again to lunge."),true);
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
