package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Artist-inspired placed Dire Crows, directed flights and counterable crow swarms. */
public final class ArtistPower implements SupernaturalAbility {
    private record Placement(Vec3 pos,Vec3 direction){}
    private static final class Nest {
        final ResourceKey<Level> dimension;
        final List<Placement> birds=new ArrayList<>();
        long expires;
        Nest(ResourceKey<Level> d,long expiry){dimension=d;expires=expiry;}
    }
    private static final class Crow {
        final UUID owner;
        final ResourceKey<Level> dimension;
        Vec3 pos,velocity;
        int ticks;
        double travelled;
        Crow(UUID owner,ResourceKey<Level> dimension,Vec3 pos,Vec3 velocity){
            this.owner=owner;this.dimension=dimension;this.pos=pos;this.velocity=velocity;
        }
    }
    private static final class Swarm {
        final UUID owner;
        final ResourceKey<Level> dimension;
        long expires;
        int repelTicks;
        Swarm(UUID owner,ResourceKey<Level> d,long expires){
            this.owner=owner;dimension=d;this.expires=expires;
        }
    }
    private final Map<UUID,Nest> nests=new HashMap<>();
    private final List<Crow> flying=new ArrayList<>();
    private final Map<UUID,Swarm> swarmed=new HashMap<>();
    @Override public String id(){return "ink_feather";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.CROW_COOLDOWN),2,120);
    }
    @Override public boolean hasRecastStage(){return true;}
    @Override public boolean allowRecastOnCooldown(ServerPlayer p){
        Nest n=nests.get(p.getUUID());
        return n!=null && n.dimension==p.level().dimension()
            && p.getServer().overworld().getGameTime()<=n.expires;
    }
    @Override public boolean activate(ServerPlayer player){
        ServerLevel level=player.serverLevel();
        long now=player.getServer().overworld().getGameTime();
        Nest nest=nests.get(player.getUUID());
        if(nest==null || now>nest.expires || nest.dimension!=level.dimension()){
            nest=new Nest(level.dimension(),now+240);
            nests.put(player.getUUID(),nest);
        }
        int maximum=Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_COUNT),1,3);
        // Sneak-use or next click after filling the nest releases every placed crow.
        if((player.isShiftKeyDown() && !nest.birds.isEmpty()) || nest.birds.size()>=maximum){
            if(flying.size()+nest.birds.size()>48)return false;
            double speed=Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_SPEED),50,200)/100.0;
            for(Placement placed:nest.birds){
                flying.add(new Crow(player.getUUID(),level.dimension(),placed.pos,
                    placed.direction.normalize().scale(speed)));
                level.sendParticles(ParticleTypes.SQUID_INK,placed.pos.x,placed.pos.y,placed.pos.z,
                    12,.25,.3,.25,.025);
            }
            int count=nest.birds.size();
            nests.remove(player.getUUID());
            level.playSound(null,player.blockPosition(),SoundEvents.PHANTOM_FLAP,
                SoundSource.PLAYERS,1.3f,.85f);
            player.displayClientMessage(Component.literal(count+" Dire Crows launched along their aimed flight paths."),true);
            return true;
        }
        Vec3 forward=player.getLookAngle().normalize();
        Vec3 pos=player.getEyePosition().add(forward.scale(1.3));
        if(!level.hasChunkAt(BlockPos.containing(pos)))return false;
        nest.birds.add(new Placement(pos,forward));
        level.sendParticles(ParticleTypes.SQUID_INK,pos.x,pos.y,pos.z,12,.18,.25,.18,.01);
        player.displayClientMessage(Component.literal("Placed crow "+nest.birds.size()+"/"+maximum+
            ". Place another, or SNEAK-use to launch them."),true);
        return true;
    }
    private void affect(ServerLevel level,ServerPlayer caster,LivingEntity target,boolean closeFlight,long now){
        Swarm existing=swarmed.get(target.getUUID());
        if(closeFlight || (existing!=null && now<existing.expires && existing.dimension==level.dimension())){
            // A fresh Dire Crow crossing its direct flight path can injure; after
            // that, a second hit is needed to injure a previously swarmed target.
            target.hurt(caster.damageSources().magic(),4f);
            swarmed.remove(target.getUUID());
            level.sendParticles(ParticleTypes.SQUID_INK,target.getX(),target.getY()+1,target.getZ(),
                20,.3,.4,.3,.04);
        }else{
            swarmed.put(target.getUUID(),new Swarm(caster.getUUID(),level.dimension(),
                now+20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_SWARM_SECONDS),4,18)));
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,120,0,false,true,true));
            caster.displayClientMessage(Component.literal("Crows swarming "+target.getName().getString()+
                " — hit again to injure."),true);
        }
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        nests.entrySet().removeIf(e->{
            Nest nest=e.getValue();
            ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());
            if(p==null || !p.isAlive() || p.level().dimension()!=nest.dimension || now>nest.expires)return true;
            if(now%10==0)for(Placement place:nest.birds){
                p.serverLevel().sendParticles(ParticleTypes.SQUID_INK,
                    place.pos.x,place.pos.y,place.pos.z,3,.12,.2,.12,0);
            }
            return false;
        });
        Iterator<Crow> flight=flying.iterator();
        while(flight.hasNext()){
            Crow crow=flight.next();
            ServerPlayer owner=server.getPlayerList().getPlayer(crow.owner);
            ServerLevel level=server.getLevel(crow.dimension);
            if(owner==null || level==null || !owner.isAlive() || owner.level()!=level ||
                !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)){
                flight.remove();continue;
            }
            int range=Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_RANGE),8,48);
            if(crow.travelled>=range || crow.ticks++>range*2){flight.remove();continue;}
            Vec3 end=crow.pos.add(crow.velocity.scale(2));
            if(!level.hasChunkAt(BlockPos.containing(end))){flight.remove();continue;}
            boolean closeFlight=crow.travelled<8;
            if(closeFlight) {
                var collision=level.clip(new ClipContext(crow.pos,end,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
                if(collision.getType()!=HitResult.Type.MISS) {
                    // After the direct flight path, swarms pass through obstacles.
                    // At a wall the direct-hit phase is lost, not the projectile.
                    closeFlight=false;
                }
            }
            LivingEntity hit=null;
            double nearest=Double.POSITIVE_INFINITY;
            for(LivingEntity candidate:level.getEntitiesOfClass(LivingEntity.class,
                new AABB(crow.pos,end).inflate(.85),e->PowerFramework.canAffect(owner,e))){
                var intersection=candidate.getBoundingBox().inflate(.12).clip(crow.pos,end);
                if(intersection.isEmpty())continue;
                double d=crow.pos.distanceToSqr(intersection.get());
                if(d<nearest){nearest=d;hit=candidate;}
            }
            if(hit!=null){
                affect(level,owner,hit,closeFlight,now);
                flight.remove();continue;
            }
            Vec3 side=new Vec3(-crow.velocity.z,0,crow.velocity.x).normalize();
            double wing=Math.sin(crow.ticks*.75)*.32;
            for(int i=0;i<5;i++){
                Vec3 v=crow.pos.lerp(end,i/4.0);
                level.sendParticles(ParticleTypes.SQUID_INK,v.x,v.y,v.z,1,0,0,0,0);
            }
            for(int sign:new int[]{-1,1}){
                Vec3 wingtip=end.add(side.scale(sign*.45)).add(0,sign*wing,0);
                level.sendParticles(ParticleTypes.SMOKE,wingtip.x,wingtip.y,wingtip.z,1,0,0,0,0);
            }
            crow.pos=end;
            crow.travelled+=crow.velocity.length()*2;
        }
        swarmed.entrySet().removeIf(e->{
            Swarm s=e.getValue();
            ServerLevel level=server.getLevel(s.dimension);
            if(level==null || now>s.expires)return true;
            var victim=level.getEntity(e.getKey());
            if(!(victim instanceof LivingEntity entity) || !entity.isAlive())return true;
            if(entity instanceof ServerPlayer p){
                if(!level.getGameRules().getBoolean(WhisperRules.POWERS_PVP))return true;
                if(p.isShiftKeyDown()){
                    s.repelTicks+=2;
                    if(s.repelTicks>=Mth.clamp(level.getGameRules().getInt(WhisperRules.CROW_REPEL_TICKS),20,200)){
                        p.displayClientMessage(Component.literal("You repelled the crow swarm."),true);
                        return true;
                    }
                }else s.repelTicks=0;
            }
            if(now%10==0)level.sendParticles(ParticleTypes.SQUID_INK,
                entity.getX(),entity.getY()+1,entity.getZ(),4,.35,.5,.35,.02);
            return false;
        });
    }
}
