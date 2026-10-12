package com.example.deathwhisper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Ghost Face-inspired shroud. Stare at a visible living target while holding the mask
 * to complete a mark. A successful mark highlights the victim and gives the owner
 * temporary, owner-specific bonus damage. PvP is gated centrally.
 */
public final class GhostPower implements SupernaturalAbility {
    private static final class Shroud {
        long until; int revealTicks;
        Shroud(long until) {this.until=until;}
    }
    private record Mark(UUID owner,UUID victim,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,long until){}
    private final Map<UUID,Shroud> active=new HashMap<>();
    private final Map<UUID,Mark> marked=new HashMap<>();
    /** Ghost Face stalk progress persists on each individual victim when you look away. */
    private final Map<UUID,Map<UUID,Integer>> progress=new HashMap<>();
    @Override public String id(){return "shrouded_mask";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GHOST_COOLDOWN),2,180);
    }
    @Override public boolean activate(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        long until=level.getServer().overworld().getGameTime()+20L*Mth.clamp(
             level.getGameRules().getInt(WhisperRules.GHOST_SHROUD_SECONDS),3,45);
        active.put(p.getUUID(),new Shroud(until));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,(int)Math.max(1,until-level.getServer().overworld().getGameTime()),0,false,false,true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,60,0,false,false,true));
        level.playSound(null,p.blockPosition(),SoundEvents.ILLUSIONER_MIRROR_MOVE,SoundSource.PLAYERS,.7f,.85f);
        p.displayClientMessage(Component.literal("Shrouded. Keep the mask in hand and watch a target to mark it."),true);
        return true;
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        marked.entrySet().removeIf(x->x.getValue().until()<=now);
        // Individual progress is tracked only while the owner holds the shrouded mask.
        Iterator<Map.Entry<UUID,Shroud>> it=active.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            Shroud state=entry.getValue();
            if(p==null || !p.isAlive() || !p.serverLevel().getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)
                 || state.until<=now){it.remove();continue;}
            // The target (another player) can break Night Shroud by maintaining line
            // of sight while directly looking at the shrouded owner for 1.5 seconds.
            boolean revealed=false;
            for(ServerPlayer other:p.serverLevel().players()){
                if(other==p || !other.isAlive() || other.isSpectator() ||
                    other.distanceToSqr(p)>32*32 || !other.hasLineOfSight(p))continue;
                Vec3 sight=p.getEyePosition().subtract(other.getEyePosition());
                if(sight.lengthSqr()<.01)continue;
                if(other.getLookAngle().dot(sight.normalize())>.94){
                    state.revealTicks+=2;revealed=true;break;
                }
            }
            if(!revealed)state.revealTicks=Math.max(0,state.revealTicks-3);
            if(state.revealTicks>=30){
                p.removeEffect(MobEffects.INVISIBILITY);
                p.removeEffect(MobEffects.MOVEMENT_SPEED);
                p.displayClientMessage(Component.literal("Night Shroud broken — you were spotted!"),true);
                it.remove();
                continue;
            }
            boolean holding=p.getMainHandItem().is(DeathWhisper.GHOST_MASK.get())
                 ||p.getOffhandItem().is(DeathWhisper.GHOST_MASK.get());
            if(!holding)continue;
            LivingEntity target=aimedAt(p,Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GHOST_RANGE),5,38));
            if(target==null)continue;
            int threshold=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GHOST_MARK_TICKS),10,140);
            Map<UUID,Integer> playerMarks=progress.computeIfAbsent(p.getUUID(),id->new HashMap<>());
            // Leaning / stalking while behind an obstacle gives 50% more progress.
            // Minecraft does not expose a native lean key: standing beside solid cover
            // within two blocks approximates Ghost Face's "Lean and Stalk".
            boolean nearCover=!p.level().getBlockState(p.blockPosition().relative(
                 p.getDirection().getClockWise())).isAir()
                 || !p.level().getBlockState(p.blockPosition().relative(
                     p.getDirection().getCounterClockWise())).isAir();
            int gain=nearCover?3:2;
            int amount=Math.min(threshold,playerMarks.getOrDefault(target.getUUID(),0)+gain);
            playerMarks.put(target.getUUID(),amount);
            if(now%10==0)p.displayClientMessage(Component.literal(
                "Stalking "+target.getName().getString()+": "+100*amount/threshold+"%"+
                (nearCover?" (lean bonus)":"")),true);
            if(amount>=threshold) {
                long expiry=now+20L*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GHOST_MARK_SECONDS),3,90);
                marked.put(target.getUUID(),new Mark(p.getUUID(),target.getUUID(),p.level().dimension(),expiry));
                p.displayClientMessage(Component.literal("EXPOSED: "+target.getDisplayName().getString()),true);
                p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.SCULK_SHRIEKER_SHRIEK,
                     SoundSource.PLAYERS,.4f,1.35f);
                playerMarks.remove(target.getUUID());
                it.remove();
            }
        }
    }
    private LivingEntity aimedAt(ServerPlayer p,int range){
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        Vec3 rayEnd=eye.add(look.scale(range));
        LivingEntity best=null;
        double closest=range*range;
        for(var e:p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),
             target->PowerFramework.canAffect(p,target)&&target.distanceToSqr(p)<=range*range)){
            // Use the actual target bounding box, not a tight angle to their eye position.
            // This lets players stalk creatures of different heights and sizes reliably.
            var intersection=e.getBoundingBox().inflate(.34).clip(eye,rayEnd);
            if(intersection.isEmpty() || !p.hasLineOfSight(e))continue;
            double distance=intersection.get().distanceToSqr(eye);
            if(distance<closest){closest=distance;best=e;}
        }
        return best;
    }
    @Override public void beforeDamage(LivingDamageEvent.Pre event){
        Mark mark=marked.get(event.getEntity().getUUID());
        if(mark==null)return;
        if(!(event.getSource().getEntity() instanceof ServerPlayer p))return;
        long now=p.serverLevel().getServer().overworld().getGameTime();
        if(!p.getUUID().equals(mark.owner()) || p.level().dimension()!=mark.dimension() || now>=mark.until()){
            if(now>=mark.until())marked.remove(event.getEntity().getUUID());
            return;
        }
        if(!PowerFramework.canAffect(p,event.getEntity()))return;
        float amount=event.getNewDamage();
        int percentage=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GHOST_DAMAGE_BONUS),0,100);
        if(amount>0) {
            event.setNewDamage(amount*(100+percentage)/100f);
            // An Exposed strike consumes the mark rather than buffing all hits for a minute.
            marked.remove(event.getEntity().getUUID());
        }
    }
}
