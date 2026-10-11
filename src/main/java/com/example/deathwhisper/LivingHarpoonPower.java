package com.example.deathwhisper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A purely non-damaging, players-only grappling hook. No mob target is ever valid. */
public final class LivingHarpoonPower implements SupernaturalAbility {
    private record Hook(UUID owner,UUID victim,long expires){}
    private final Map<UUID,Hook> hooks=new HashMap<>();
    @Override public String id(){return "living_harpoon";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.HARPOON_COOLDOWN),2,120);
    }
    @Override public boolean activate(ServerPlayer owner){
        ServerLevel level=owner.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.HARPOON_PVP)
            || !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)){
            owner.displayClientMessage(Component.literal("Harpoon player-grabbing is disabled by gamerule."),true);
            return false;
        }
        int distance=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_RANGE),6,32);
        Vec3 eye=owner.getEyePosition(),forward=owner.getLookAngle();
        ServerPlayer best=null;
        double nearest=distance*distance;
        for(ServerPlayer other:level.players()){
            if(other==owner||!other.isAlive()||other.isSpectator()||other.isCreative())continue;
            double d=eye.distanceToSqr(other.getEyePosition());
            if(d>nearest||d<1||!owner.hasLineOfSight(other))continue;
            if(other.getEyePosition().subtract(eye).normalize().dot(forward)<.975)continue;
            nearest=d;best=other;
        }
        if(best==null) {
            owner.displayClientMessage(Component.literal("Aim directly at another player."),true);
            return false;
        }
        // Both parties must be in the same dimension, and the grappling line stays visible.
        hooks.put(owner.getUUID(),new Hook(owner.getUUID(),best.getUUID(),
            owner.getServer().overworld().getGameTime()
              +20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_SECONDS),1,8)));
        level.playSound(null,owner.blockPosition(),SoundEvents.CROSSBOW_SHOOT,SoundSource.PLAYERS,1.1f,.75f);
        owner.displayClientMessage(Component.literal("Hooked "+best.getName().getString()),true);
        best.displayClientMessage(Component.literal("You've been grabbed by a living harpoon!"),true);
        return true;
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        hooks.entrySet().removeIf(entry->{
            Hook h=entry.getValue();
            ServerPlayer owner=server.getPlayerList().getPlayer(h.owner());
            ServerPlayer victim=server.getPlayerList().getPlayer(h.victim());
            if(owner==null||victim==null||!owner.isAlive()||!victim.isAlive()
                ||owner.level()!=victim.level()||victim.isSpectator()||victim.isCreative()
                ||now>=h.expires())return true;
            ServerLevel level=owner.serverLevel();
            if(!level.getGameRules().getBoolean(WhisperRules.HARPOON_PVP)
                || !level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return true;
            double max=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_RANGE),6,32);
            double d=owner.distanceToSqr(victim);
            if(d>max*max||!owner.hasLineOfSight(victim))return true;
            Vec3 diff=owner.getEyePosition().subtract(victim.getEyePosition());
            if(diff.length()<2.8)return true;
            double pull=Mth.clamp(level.getGameRules().getInt(WhisperRules.HARPOON_PULL),5,90)/100.0;
            // Pull toward the owner without dealing damage or teleporting.
            Vec3 v=diff.normalize().scale(pull);
            victim.setDeltaMovement(victim.getDeltaMovement().scale(.40).add(v));
            victim.hurtMarked=true;
            for(int i=0;i<Math.min(24,(int)(diff.length()*1.5));i++){
                Vec3 pt=victim.getEyePosition().add(diff.scale(i/Math.max(1.0,diff.length()*1.5)));
                level.sendParticles(ParticleTypes.CRIT,pt.x,pt.y,pt.z,1,0,0,0,0);
            }
            return false;
        });
    }
}
