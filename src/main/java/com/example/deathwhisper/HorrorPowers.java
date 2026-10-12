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
        long started, expires, dashStart, dashUntil, nextAllowed, reboundUntil;
        int charges, used;
        double speed;
        Vec3 direction = Vec3.ZERO;
        boolean dashing, canChain;
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

    /**
     * A Blight combo MUST hit a wall to continue. Every successive rebound rush
     * accelerates, while running out of time with no wall causes fatigue.
     */
    public boolean canRecastSerum(ServerPlayer player) {
        RushState state=rushing.get(player.getUUID());
        if(state==null)return false;
        long now=player.getServer().overworld().getGameTime();
        return state.canChain && !state.dashing && state.charges>0
            && now<=state.expires && now<=state.reboundUntil
            && now>=state.nextAllowed && player.isAlive();
    }
    public boolean useSerum(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return false;
        long now=level.getServer().overworld().getGameTime();
        RushState state=rushing.get(player.getUUID());
        if(state==null || now>state.expires){
            state=new RushState();
            state.started=now;
            state.charges=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_CHARGES),1,10);
            state.expires=now+20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_WINDOW),5,90);
            rushing.put(player.getUUID(),state);
        }else if(!canRecastSerum(player)){
            player.displayClientMessage(Component.literal("Hit a wall first to chain another rush."),true);
            return false;
        }
        if(now<state.nextAllowed || state.charges<=0)return false;
        state.charges--;
        state.used++;
        state.canChain=false;
        state.dashing=true;
        state.direction=player.getLookAngle().normalize();
        state.dashStart=now;
        state.dashUntil=now+Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_TICKS),5,25);
        state.nextAllowed=now+8;
        // The speed accelerates with every SUCCESSFUL wall rebound.
        // Eg default 1.15, 1.33, 1.51, 1.69, 1.87 blocks/tick.
        int baseline=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_SPEED),40,185);
        int increment=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_ACCELERATION),0,50);
        state.speed=Math.min(2.75,(baseline+(state.used-1)*increment)/100.0);
        player.fallDistance=0;
        level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.BREEZE_SHOOT,
            net.minecraft.sounds.SoundSource.PLAYERS,.95f,1.35f+Math.min(.45f,(state.used-1)*.09f));
        player.displayClientMessage(Component.literal("Blighted rush "+state.used+
            " • "+String.format(java.util.Locale.ROOT,"%.2f",state.speed)+" speed • "
            +state.charges+" charges remain"),true);
        return true;
    }

    private void fatigue(ServerPlayer player,RushState state,String message) {
        ServerLevel level=player.serverLevel();
        int ticks=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_FATIGUE_TICKS),10,100);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ticks,2,false,true,true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,ticks,0,false,true,true));
        player.setDeltaMovement(player.getDeltaMovement().scale(.25));
        player.hurtMarked=true;
        player.fallDistance=0;
        state.dashing=false; state.canChain=false; state.charges=0;
        state.expires=player.getServer().overworld().getGameTime();
        // The framework's full recovery starts with the first rush.
        long remaining=state.started+
            20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_COOLDOWN),5,180)
            -player.getServer().overworld().getGameTime();
        player.getCooldowns().addCooldown(DeathWhisper.RUSH_SERUM.get(),(int)Math.max(1,remaining));
        level.sendParticles(ParticleTypes.SMOKE,player.getX(),player.getY()+1,player.getZ(),10,.3,.4,.3,.015);
        player.displayClientMessage(Component.literal(message+" Fatigued for "+
            String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0)+"s."),true);
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
            if(player==null || !player.isAlive() ||
               !player.serverLevel().getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return true;
            ServerLevel level=player.serverLevel();
            if(now>state.expires) {
                if(state.dashing || state.canChain)fatigue(player,state,"The blight fades.");
                return true;
            }
            if(!state.dashing){
                if(state.canChain && now>state.reboundUntil){
                    fatigue(player,state,"Rebound window missed.");
                    return true;
                }
                return false;
            }
            // Running for the full duration without colliding is a failed rush:
            // stop the entire combo and apply fatigue; NO free chain on open ground.
            if(now>state.dashUntil) {
                fatigue(player,state,"No wall hit: rush ended.");
                return true;
            }
            // Skip the first two ticks so a stale collision from the prior move
            // cannot falsely count as a new rebound.
            if(player.horizontalCollision && now>=state.dashStart+2) {
                state.dashing=false;
                state.dashUntil=now;
                state.nextAllowed=now+6;
                state.canChain=state.charges>0;
                state.reboundUntil=now+
                    Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_REBOUND_TICKS),10,60);
                Vec3 recoil=state.direction.scale(-.2);
                player.setDeltaMovement(recoil.x,.13,recoil.z);
                player.hurtMarked=true;
                level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.PLAYERS,.5f,1.4f);
                level.sendParticles(ParticleTypes.SMOKE,player.getX(),player.getY()+1,player.getZ(),9,.4,.4,.4,.02);
                if(state.charges==0){
                    fatigue(player,state,"Final wall rebound.");
                    return true;
                }
                player.displayClientMessage(Component.literal("Wall rebound! "+
                    state.charges+" faster rushes available. Re-aim and use serum."),true);
                return false;
            }
            // In DBD the initial rush cannot attack; after at least one wall slam,
            // a Lethal Rush can injure a target. Minecraft maps this to contact.
            if(state.used>=2){
                var targets=level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(1.1),
                    target->PowerFramework.canAffect(player,target) &&
                        target.getEyePosition().subtract(player.getEyePosition()).dot(state.direction)>-.5);
                if(!targets.isEmpty()){
                    LivingEntity victim=targets.get(0);
                    int amount=Mth.clamp(level.getGameRules().getInt(WhisperRules.RUSH_LETHAL_DAMAGE),1,20);
                    if(victim.hurt(player.damageSources().playerAttack(player),amount)){
                        level.playSound(null,player.blockPosition(),
                            net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                            net.minecraft.sounds.SoundSource.PLAYERS,.6f,1.7f);
                        fatigue(player,state,"Lethal Rush hit "+victim.getName().getString()+".");
                        return true;
                    }
                }
            }
            Vec3 direction=state.direction;
            player.setDeltaMovement(direction.x*state.speed,
                Math.max(.045,direction.y*state.speed*.5),direction.z*state.speed);
            player.hurtMarked=true;
            player.fallDistance=0;
            if(now%2==0)level.sendParticles(ParticleTypes.WITCH,
                player.getX(),player.getY()+.6,player.getZ(),4,.26,.25,.26,.025);
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
