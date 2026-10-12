package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Deathslinger-inspired player-only harpoon: fired projectile, reeling, chain break. */
public final class LivingHarpoonPower implements SupernaturalAbility {
    private static final class Bolt {
        final UUID shooter;final ResourceKey<Level> dimension;
        Vec3 pos,velocity;double distance;
        Bolt(ServerPlayer p){
            shooter=p.getUUID();dimension=p.level().dimension();
            pos=p.getEyePosition().add(p.getLookAngle().scale(.8));
            velocity=p.getLookAngle().normalize().scale(1.85);
        }
    }
    private static final class Hook {
        final UUID owner,victim;final ResourceKey<Level> dimension;final long expires;
        int struggle;
        Hook(ServerPlayer source,ServerPlayer target,long expiry){
            owner=source.getUUID();victim=target.getUUID();dimension=source.level().dimension();expires=expiry;
        }
    }
    private final Map<UUID,Hook> hooks=new HashMap<>();
    private final List<Bolt> bolts=new ArrayList<>();
    @Override public String id(){return "living_harpoon";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.HARPOON_COOLDOWN),2,120);
    }
    @Override public boolean activate(ServerPlayer owner){
        ServerLevel level=owner.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.HARPOON_PVP)){
            owner.displayClientMessage(Component.literal("Enable deathWhisperHarpoonPvp to hook other players."),true);
            return false;
        }
        if(bolts.size()>=24)return false;
        bolts.add(new Bolt(owner));
        level.playSound(null,owner.blockPosition(),SoundEvents.CROSSBOW_SHOOT,SoundSource.PLAYERS,1.1f,.73f);
        owner.displayClientMessage(Component.literal("Harpoon fired — aim ahead of moving players."),true);
        return true;
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        Iterator<Bolt> flight=bolts.iterator();
        while(flight.hasNext()){
            Bolt b=flight.next();
            ServerPlayer shooter=server.getPlayerList().getPlayer(b.shooter);
            if(shooter==null || !shooter.isAlive() || shooter.level().dimension()!=b.dimension
                || !shooter.level().getGameRules().getBoolean(WhisperRules.HARPOON_PVP)){
                flight.remove();continue;
            }
            ServerLevel level=shooter.serverLevel();
            double max=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_RANGE),6,32);
            if(b.distance>max){flight.remove();continue;}
            Vec3 next=b.pos.add(b.velocity.scale(2));
            if(!level.hasChunkAt(BlockPos.containing(next))){flight.remove();continue;}
            var worldHit=level.clip(new ClipContext(b.pos,next,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,shooter));
            Vec3 end=worldHit.getType()==HitResult.Type.MISS?next:worldHit.getLocation();
            ServerPlayer hit=null;double nearest=Double.POSITIVE_INFINITY;
            for(ServerPlayer candidate:level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(b.pos,end).inflate(.7),p->p!=shooter && p.isAlive()
                    && !p.isCreative() && !p.isSpectator())){
                var intersection=candidate.getBoundingBox().inflate(.16).clip(b.pos,end);
                if(intersection.isEmpty())continue;
                double d=b.pos.distanceToSqr(intersection.get());
                if(d<nearest){nearest=d;hit=candidate;}
            }
            for(int i=0;i<=6;i++){
                Vec3 at=b.pos.lerp(end,i/6.0);
                level.sendParticles(ParticleTypes.CRIT,at.x,at.y,at.z,1,0,0,0,0);
            }
            b.distance+=b.pos.distanceTo(end);b.pos=end;
            if(hit!=null){
                hooks.put(shooter.getUUID(),new Hook(shooter,hit,
                    now+20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_SECONDS),1,8)));
                hit.displayClientMessage(Component.literal("Harpooned! Hold SNEAK to break the tether."),true);
                shooter.displayClientMessage(Component.literal("Reeling "+hit.getName().getString()),true);
                level.playSound(null,hit.blockPosition(),SoundEvents.CHAIN_PLACE,SoundSource.PLAYERS,.95f,.8f);
                flight.remove();
            }else if(worldHit.getType()!=HitResult.Type.MISS || b.distance>=max){
                shooter.displayClientMessage(Component.literal("Harpoon missed or struck a wall."),true);
                flight.remove();
            }
        }
        hooks.entrySet().removeIf(entry->{
            Hook h=entry.getValue();
            ServerPlayer owner=server.getPlayerList().getPlayer(h.owner);
            ServerPlayer victim=server.getPlayerList().getPlayer(h.victim);
            if(owner==null || victim==null || !owner.isAlive() || !victim.isAlive()
                || owner.level()!=victim.level() || victim.isSpectator() || victim.isCreative()
                || now>=h.expires)return true;
            ServerLevel level=owner.serverLevel();
            if(!level.getGameRules().getBoolean(WhisperRules.HARPOON_PVP)
                || !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return true;
            int max=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_RANGE),6,32);
            double distance=owner.distanceToSqr(victim);
            if(distance>max*max || !owner.hasLineOfSight(victim))return true;
            if(victim.isShiftKeyDown()){
                h.struggle+=2;
                if(h.struggle>=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_BREAK_TICKS),10,100)){
                    victim.displayClientMessage(Component.literal("Harpoon chain broken!"),true);
                    return true;
                }
            }else h.struggle=0;
            Vec3 pull=owner.getEyePosition().subtract(victim.getEyePosition());
            if(pull.length()<2.8)return true;
            double strength=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_PULL),5,90)/100.0;
            Vec3 d=pull.normalize().scale(strength);
            victim.setDeltaMovement(victim.getDeltaMovement().scale(.4).add(d));
            victim.hurtMarked=true;
            for(int i=0;i<=16;i++){
                Vec3 at=victim.getEyePosition().lerp(owner.getEyePosition(),i/16.0);
                level.sendParticles(ParticleTypes.CRIT,at.x,at.y,at.z,1,0,0,0,0);
            }
            return false;
        });
    }
}
