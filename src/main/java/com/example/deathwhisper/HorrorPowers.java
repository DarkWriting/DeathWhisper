package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Independent from the stalker: powers are activated by players, not by Death's encounter director. */
public final class HorrorPowers {
    private final Map<UUID, ChainLink> links = new HashMap<>();
    private final Map<UUID, RushState> rushing = new HashMap<>();

    private record ChainLink(UUID owner,UUID target,ResourceKey<Level> dimension,long expires) {}
    private static final class RushState {
        long expires, dashUntil, nextAllowed;
        int charges;
        Vec3 direction = Vec3.ZERO;
        boolean collided;
    }

    private static boolean canHit(ServerPlayer owner,LivingEntity target) {
        return PowerFramework.canAffect(owner,target);
    }
    public int fireChains(ServerPlayer owner) {
        ServerLevel level=owner.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return 0;
        int max=Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_TARGETS),1,6);
        double range=Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_RANGE),5,36);
        double bestAim=.73;
        List<LivingEntity> targets=new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
             owner.getBoundingBox().inflate(range), e -> canHit(owner,e) && e.distanceToSqr(owner)<=range*range));
        Vec3 eye=owner.getEyePosition(), look=owner.getLookAngle();
        targets.removeIf(e->{
            Vec3 difference=e.getEyePosition().subtract(eye);
            return difference.lengthSqr()<.001 || difference.normalize().dot(look)<bestAim || !owner.hasLineOfSight(e);
        });
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(owner)));
        int selected=0;
        long now=level.getGameTime();
        for(LivingEntity target:targets) {
            if(selected>=max)break;
            links.put(target.getUUID(),new ChainLink(owner.getUUID(),target.getUUID(),level.dimension(),
                now + Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_SECONDS),1,15)*20L));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,35,2,false,true,true));
            showChain(level,owner.getEyePosition(),target.getEyePosition());
            selected++;
        }
        if(selected>0) {
            level.playSound(null,owner.blockPosition(),net.minecraft.sounds.SoundEvents.CHAIN_PLACE,
                net.minecraft.sounds.SoundSource.PLAYERS,1.25f,.85f);
        }
        return selected;
    }

    /** Subsequent serum activations can bypass the long cooldown only during an active rush session. */
    public boolean canRecastSerum(ServerPlayer player) {
        RushState state=rushing.get(player.getUUID());
        if(state==null)return false;
        long now=player.getServer().overworld().getGameTime();
        return now<=state.expires && state.charges>0 && now>=state.dashUntil
            && now>=state.nextAllowed && player.isAlive();
    }
    public boolean useSerum(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return false;
        long now=level.getServer().overworld().getGameTime();
        RushState state=rushing.get(player.getUUID());
        if(state==null || state.expires<now){
            state=new RushState();
            state.charges=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_CHARGES),1,10);
            state.expires=now+20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_WINDOW),5,90);
            rushing.put(player.getUUID(),state);
        }
        if(state.charges<=0 || now<state.nextAllowed || now<state.dashUntil){
            player.displayClientMessage(Component.literal("Serum cooling down or already rushing."),true);
            return false;
        }
        state.charges--;
        state.direction=player.getLookAngle().normalize();
        state.collided=false;
        state.dashUntil=now+Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_TICKS),5,25);
        state.nextAllowed=now+8;
        player.fallDistance=0;
        level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.BREEZE_SHOOT,
            net.minecraft.sounds.SoundSource.PLAYERS,.95f,1.35f);
        player.displayClientMessage(Component.literal("Blighted rush • "+state.charges+
            " charges remain • aim at a wall to rebound"),true);
        return true;
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        long now=server.overworld().getGameTime();
        if(now%4==0) {
            links.entrySet().removeIf(entry->{
                ChainLink link=entry.getValue();
                ServerLevel level=server.getLevel(link.dimension());
                if(level==null || now>=link.expires())return true;
                var source=server.getPlayerList().getPlayer(link.owner());
                var found=level.getEntity(link.target());
                if(source==null || source.level()!=level || !(found instanceof LivingEntity target)
                    || !source.isAlive() || !target.isAlive()
                    || !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)
                    || (target instanceof ServerPlayer && !level.getGameRules().getBoolean(WhisperRules.POWERS_PVP)))
                    return true;
                if(target.distanceToSqr(source)>36*36 || !source.hasLineOfSight(target))return true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,2,false,true,true));
                if(target.distanceToSqr(source)>7*7) {
                    Vec3 pull=source.position().subtract(target.position()).normalize().scale(.085);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull.x,.02,pull.z));
                    target.hurtMarked=true;
                }
                showChain(level,source.getEyePosition(),target.getEyePosition());
                return false;
            });
        }
        rushing.entrySet().removeIf(entry->{
            RushState state=entry.getValue();
            ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null || !player.isAlive() || !player.serverLevel().getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return true;
            if(now>state.expires) return true;
            if(now>state.dashUntil) return false;
            ServerLevel level=player.serverLevel();
            if(player.horizontalCollision && now+2<state.dashUntil) {
                state.dashUntil=now;
                state.nextAllowed=now+6;
                state.collided=true;
                Vec3 recoil=state.direction.scale(-.20);
                player.setDeltaMovement(recoil.x,.13,recoil.z);
                player.hurtMarked=true;
                level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.PLAYERS,.5f,1.4f);
                level.sendParticles(ParticleTypes.SMOKE,player.getX(),player.getY()+1,player.getZ(),9,.4,.4,.4,.02);
                player.displayClientMessage(Component.literal(state.charges>0 ?
                    "Wall rebound! Re-aim and use serum again." : "Final rebound. Serum exhausted."),true);
                return false;
            }
            double speed=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_SPEED),40,185)/100.0;
            Vec3 direction=state.direction;
            player.setDeltaMovement(direction.x*speed,Math.max(.045,direction.y*speed*.5),direction.z*speed);
            player.hurtMarked=true;
            player.fallDistance=0;
            if(now%2==0)
                level.sendParticles(ParticleTypes.WITCH,player.getX(),player.getY()+.6,player.getZ(),4,.26,.25,.26,.025);
            return false;
        });
    }
    private static void showChain(ServerLevel level,Vec3 a,Vec3 b) {
        Vec3 delta=b.subtract(a);
        int steps=Math.min(45,Math.max(1,Mth.floor(delta.length()*2)));
        for(int i=0;i<=steps;i++) {
            Vec3 pos=a.add(delta.scale(i/(double)steps));
            level.sendParticles(i%3==0?ParticleTypes.ENCHANTED_HIT:ParticleTypes.CRIT,
                pos.x,pos.y,pos.z,1,0,0,0,0);
        }
    }
}
