package com.example.deathwhisper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Artist-inspired supernatural crows. Server-tracked wing-shaped smoke wisps
 * home toward living entities and reveal those they strike. No chunk tickets,
 * permanent entities or client packets: bounded visuals use vanilla particles.
 */
public final class ArtistPower implements SupernaturalAbility {
    private static final class Crow {
        final UUID owner; final ResourceKey<Level> dimension;
        Vec3 pos, velocity; int ticks=0;
        Crow(UUID owner,ResourceKey<Level> dimension,Vec3 pos,Vec3 velocity){
            this.owner=owner;this.dimension=dimension;this.pos=pos;this.velocity=velocity;
        }
    }
    private final List<Crow> flying=new ArrayList<>();
    @Override public String id(){return "ink_feather";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.CROW_COOLDOWN),2,120);
    }
    @Override public boolean activate(ServerPlayer p){
        if(flying.size()>=48)return false; // Keep per-server swarms bounded.
        int count=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.CROW_COUNT),1,5);
        double speed=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.CROW_SPEED),50,200)/100.0;
        Vec3 facing=p.getLookAngle().normalize();
        Vec3 sideways=new Vec3(-facing.z,0,facing.x).normalize();
        for(int i=0;i<count;i++){
            double offset=i-(count-1)/2.0;
            Vec3 start=p.getEyePosition().add(facing.scale(1.2)).add(sideways.scale(offset*.55));
            Vec3 velocity=facing.add(sideways.scale(offset*.065)).normalize().scale(speed);
            flying.add(new Crow(p.getUUID(),p.level().dimension(),start,velocity));
        }
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PHANTOM_FLAP,SoundSource.PLAYERS,1.3f,1.3f);
        p.displayClientMessage(Component.literal(count+" ink crows unleashed"),true);
        return true;
    }
    @Override public void serverTick(MinecraftServer server){
        Iterator<Crow> iterator=flying.iterator();
        while(iterator.hasNext()){
            Crow crow=iterator.next();
            ServerPlayer owner=server.getPlayerList().getPlayer(crow.owner);
            ServerLevel level=server.getLevel(crow.dimension);
            if(owner==null || level==null || owner.level()!=level || !owner.isAlive() ||
                 !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)){iterator.remove();continue;}
            int limit=Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_RANGE),8,48);
            if(++crow.ticks>limit*2 || crow.pos.distanceToSqr(owner.getEyePosition())>limit*limit
                   || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(crow.pos))){iterator.remove();continue;}
            // Small bounded target scan around each crow, avoiding whole-world lookups.
            List<LivingEntity> candidates=level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(crow.pos,crow.pos).inflate(7.0),
                e->PowerFramework.canAffect(owner,e)&&e.distanceToSqr(crow.pos)<49);
            LivingEntity nearest=null;
            double best=49;
            for(var target:candidates){
                double dist=target.getEyePosition().distanceToSqr(crow.pos);
                if(dist<best){best=dist;nearest=target;}
            }
            if(nearest!=null && best>0.01) {
                Vec3 toward=nearest.getEyePosition().subtract(crow.pos).normalize();
                crow.velocity=crow.velocity.scale(.87).add(toward.scale(.13*crow.velocity.length())).normalize().scale(crow.velocity.length());
            }
            Vec3 next=crow.pos.add(crow.velocity.scale(2)); // serverTick runs every 2 ticks
            HitResult block=level.clip(new ClipContext(crow.pos,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(block.getType()!=HitResult.Type.MISS){splash(level,crow.pos);iterator.remove();continue;}
            crow.pos=next;
            if(nearest!=null && nearest.getEyePosition().distanceToSqr(crow.pos)<2.8) {
                int glow=20*Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_REVEAL_SECONDS),2,30);
                nearest.addEffect(new MobEffectInstance(MobEffects.GLOWING,glow,0,false,true,true));
                nearest.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,35,0,false,true,true));
                splash(level,crow.pos);
                owner.displayClientMessage(Component.literal("Crow revealed "+nearest.getDisplayName().getString()),true);
                iterator.remove();continue;
            }
            // Feathered silhouette: dense core and two flickering black-smoke wing tips.
            Vec3 side=new Vec3(-crow.velocity.z,0,crow.velocity.x).normalize();
            double wing=Math.sin(crow.ticks*.83)*.36;
            level.sendParticles(ParticleTypes.SQUID_INK,crow.pos.x,crow.pos.y,crow.pos.z,2,.08,.09,.08,0);
            Vec3 left=crow.pos.add(side.scale(.43)).add(0,wing,0);
            Vec3 right=crow.pos.subtract(side.scale(.43)).add(0,-wing,0);
            level.sendParticles(ParticleTypes.SMOKE,left.x,left.y,left.z,1,0,0,0,0);
            level.sendParticles(ParticleTypes.SMOKE,right.x,right.y,right.z,1,0,0,0,0);
        }
    }
    private static void splash(ServerLevel level,Vec3 point){
        level.sendParticles(ParticleTypes.SQUID_INK,point.x,point.y,point.z,13,.24,.2,.24,.035);
        level.playSound(null,net.minecraft.core.BlockPos.containing(point),SoundEvents.PHANTOM_FLAP,
            SoundSource.PLAYERS,.45f,.75f);
    }
}
