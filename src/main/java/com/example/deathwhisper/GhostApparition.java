package com.example.deathwhisper;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** An intangible, non-damaging, temporary ghost entity, not a zombie encounter. */
public final class GhostApparition extends Zombie {
    private UUID witness;
    private int lifespan=320, age;
    public GhostApparition(EntityType<? extends Zombie> type,Level level){
        super(type,level);
        setInvulnerable(true);setSilent(true);setNoAi(true);setNoGravity(true);
        noPhysics=true;
    }
    @Override protected void registerGoals(){}
    @Override protected boolean isSunSensitive(){return false;}
    @Override public boolean doHurtTarget(Entity victim){return false;}
    @Override public boolean isPushable(){return false;}
    public void manifestFor(UUID player,int duration){witness=player;lifespan=Mth.clamp(duration,60,800);}
    @Override public void tick(){
        super.tick();
        setNoGravity(true);noPhysics=true;
        if(level().isClientSide || !(level() instanceof ServerLevel level))return;
        if(++age>lifespan){discard();return;}
        ServerPlayer observer=witness==null?null:level.getServer().getPlayerList().getPlayer(witness);
        if(observer==null || observer.level()!=level || !observer.isAlive() ||
           !level.getGameRules().getBoolean(WhisperRules.MANIFESTATIONS_ENABLED)){discard();return;}
        Vec3 toward=observer.position().subtract(position());
        if(toward.lengthSqr()>6*6 && age%2==0){
            Vec3 step=toward.normalize().scale(.12);
            setPos(getX()+step.x,getY()+step.y*.25,getZ()+step.z);
        }else if(toward.lengthSqr()<10){discard();return;}
        setYRot((float)(Math.atan2(-toward.x,toward.z)*180/Math.PI));
        if(age%10==0)level.sendParticles(ParticleTypes.SOUL,getX(),getY()+1,getZ(),3,.5,.7,.5,.01);
    }
    @Override public void addAdditionalSaveData(CompoundTag nbt){
        super.addAdditionalSaveData(nbt);
        if(witness!=null)nbt.putUUID("Witness",witness);
        nbt.putInt("Life",lifespan);nbt.putInt("Age",age);
    }
    @Override public void readAdditionalSaveData(CompoundTag nbt){
        super.readAdditionalSaveData(nbt);
        witness=nbt.hasUUID("Witness")?nbt.getUUID("Witness"):null;
        lifespan=nbt.getInt("Life");age=nbt.getInt("Age");
    }
}
