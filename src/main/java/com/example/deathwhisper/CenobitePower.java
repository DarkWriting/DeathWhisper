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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gateway -> steerable possessed chain -> three breakable bindings.
 * A periodic Lament Hunt is interrupted by solving a separate configuration item.
 * All entities stay server-side; never loads chunks, edits blocks or deals direct damage.
 */
public final class CenobitePower implements SupernaturalAbility {
    private static final class Gateway {
        final ResourceKey<Level> dimension;
        Vec3 point;
        long expires;
        Gateway(ResourceKey<Level> dimension,Vec3 point,long expires){
            this.dimension=dimension;this.point=point;this.expires=expires;
        }
    }
    private static final class FlyingChain {
        final UUID caster;
        final ResourceKey<Level> dimension;
        Vec3 point,velocity;
        int steps;
        FlyingChain(UUID caster,ResourceKey<Level> dimension,Vec3 point,Vec3 velocity){
            this.caster=caster;this.dimension=dimension;this.point=point;this.velocity=velocity;
        }
    }
    private static final class Bound {
        final UUID caster,target;
        final ResourceKey<Level> dimension;
        int chains=3, struggle;
        long expires;
        Bound(UUID caster,UUID target,ResourceKey<Level> dimension,long expires){
            this.caster=caster;this.target=target;this.dimension=dimension;this.expires=expires;
        }
    }
    private static final class Hunt {
        final UUID caster;
        final ResourceKey<Level> dimension;
        long activation,nextChain,ends;
        boolean active;
        Hunt(UUID caster,ResourceKey<Level> dimension,long now,long delay){
            this.caster=caster;this.dimension=dimension;this.activation=now+delay;this.nextChain=activation;this.ends=activation+800;
        }
    }
    private static final class Solve {
        final UUID player;
        final ResourceKey<Level> dimension;
        final Vec3 origin;
        final long expires;
        Solve(ServerPlayer p,long now){
            player=p.getUUID();dimension=p.level().dimension();origin=p.position();expires=now+120;
        }
    }

    private final Map<UUID,Gateway> gateways=new HashMap<>();
    private final Map<UUID,Vec3> cameraAim=new HashMap<>();
    private final List<FlyingChain> flying=new ArrayList<>();
    private final Map<UUID,Bound> bound=new HashMap<>();
    private final Map<UUID,Hunt> hunts=new HashMap<>();
    private final Map<UUID,Solve> solving=new HashMap<>();

    @Override public String id(){return "chain_puzzle_box";}
    @Override public int cooldownTicks(ServerPlayer p){
        return 20*Mth.clamp(p.level().getGameRules().getInt(WhisperRules.CHAINS_COOLDOWN),2,120);
    }
    @Override public boolean hasRecastStage(){return true;}
    @Override public boolean allowRecastOnCooldown(ServerPlayer p){
        Gateway g=gateways.get(p.getUUID());
        long now=p.getServer().overworld().getGameTime();
        return g!=null && g.dimension==p.level().dimension() && now<=g.expires;
    }
    @Override public boolean activate(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        long now=p.getServer().overworld().getGameTime();
        Gateway g=gateways.remove(p.getUUID());
        if(g!=null && now<=g.expires && g.dimension==level.dimension()){
            return launch(p,g,p.getLookAngle().normalize(),now);
        }
        int range=Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_RANGE),5,25);
        Vec3 eye=p.getEyePosition();
        Vec3 end=eye.add(p.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        Vec3 point=block.getType()==HitResult.Type.MISS?end:block.getLocation().subtract(p.getLookAngle().scale(.8));
        if(!level.hasChunkAt(BlockPos.containing(point)))return false;
        gateways.put(p.getUUID(),new Gateway(level.dimension(),point,now+120));
        level.sendParticles(ParticleTypes.PORTAL,point.x,point.y,point.z,28,.4,.6,.4,.12);
        p.displayClientMessage(Component.literal("Gateway opened. Camera: F or left-click to launch; move mouse to steer; ESC to exit."),true);
        CameraPackets.gateway(p,point);
        return true;
    }
    /** Client perspective is never trusted: this method verifies a real active gateway. */
    public void cameraAim(ServerPlayer p,float yaw,float pitch){
        if(!CameraPackets.isGatewayViewing(p))return;
        cameraAim.put(p.getUUID(),Vec3.directionFromRotation(pitch,yaw).normalize());
    }
    public boolean fireFromCamera(ServerPlayer p,float yaw,float pitch){
        if(!CameraPackets.isGatewayViewing(p))return false;
        Gateway g=gateways.get(p.getUUID());
        long now=p.getServer().overworld().getGameTime();
        if(g==null || g.dimension!=p.level().dimension() || now>g.expires)return false;
        gateways.remove(p.getUUID());
        Vec3 look=Vec3.directionFromRotation(pitch,yaw).normalize();
        cameraAim.put(p.getUUID(),look);
        return launch(p,g,look,now);
    }
    private boolean launch(ServerPlayer p,Gateway g,Vec3 direction,long now){
        ServerLevel level=p.serverLevel();
        if(flying.size()>=36)return false;
        flying.add(new FlyingChain(p.getUUID(),level.dimension(),g.point,
            direction.scale(Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAIN_SPEED),4,18)/10.0)));
        level.playSound(null,BlockPos.containing(g.point),SoundEvents.CHAIN_PLACE,
            SoundSource.PLAYERS,1.2f,.8f);
        p.displayClientMessage(Component.literal("Possessed chain launched from gateway. Aim to steer."),true);
        if(level.getGameRules().getBoolean(WhisperRules.CHAIN_HUNTS_ENABLED)
            && !hunts.containsKey(p.getUUID())){
            hunts.put(p.getUUID(),new Hunt(p.getUUID(),level.dimension(),now,
                20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAIN_HUNT_DELAY),15,180)));
            Vec3 drop=p.position().add(p.getLookAngle().multiply(-3,0,-3));
            if(level.hasChunkAt(BlockPos.containing(drop))){
                var item=new net.minecraft.world.entity.item.ItemEntity(level,
                    drop.x,p.getY()+.4,drop.z,
                    new net.minecraft.world.item.ItemStack(DeathWhisper.LAMENT_CONFIGURATION.get()));
                item.setPickUpDelay(20);
                level.addFreshEntity(item);
                p.sendSystemMessage(Component.literal("§5The Lament Configuration has appeared nearby."));
            }
        }
        return true;
    }

    public boolean startSolving(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        if(!level.getGameRules().getBoolean(WhisperRules.POWERS_ENABLED)
            || !level.getGameRules().getBoolean(WhisperRules.CHAIN_HUNTS_ENABLED))return false;
        boolean pending=hunts.values().stream().anyMatch(h->h.dimension==level.dimension());
        if(!pending){
            p.displayClientMessage(Component.literal("No active Lament Configuration hunt to solve."),true);
            return false;
        }
        long now=p.getServer().overworld().getGameTime();
        solving.put(p.getUUID(),new Solve(p,now));
        p.displayClientMessage(Component.literal("Solving the Configuration: stay still and hold the item for six seconds."),true);
        level.playSound(null,p.blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.PLAYERS,.8f,.7f);
        return true;
    }
    private void solveComplete(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        int before=hunts.size();
        hunts.entrySet().removeIf(e->e.getValue().dimension==level.dimension());
        // Chain Hunt bindings disappear when the puzzle is solved. Single gateway bindings
        // can also be broken manually by sneaking.
        bound.entrySet().removeIf(e->e.getValue().dimension==level.dimension());
        level.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+1,p.getZ(),35,1,.6,1,.2);
        level.playSound(null,p.blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.PLAYERS,1.3f,1.25f);
        p.sendSystemMessage(Component.literal("§dThe Lament Configuration is solved. "+
            Math.max(1,before)+" Chain Hunt(s) end."));
    }
    @Override public void serverTick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        gateways.entrySet().removeIf(e->{
            Gateway g=e.getValue();
            ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());
            if(now>g.expires || p==null || p.level().dimension()!=g.dimension)return true;
            if(now%4==0) {
                ServerLevel l=p.serverLevel();
                l.sendParticles(ParticleTypes.PORTAL,g.point.x,g.point.y,g.point.z,4,.3,.35,.3,.04);
            }
            return false;
        });
        Iterator<FlyingChain> flight=flying.iterator();
        while(flight.hasNext()){
            FlyingChain chain=flight.next();
            ServerPlayer owner=server.getPlayerList().getPlayer(chain.caster);
            if(owner==null || !owner.isAlive() || owner.level().dimension()!=chain.dimension){
                if(owner!=null)CameraPackets.close(owner);
                flight.remove();continue;
            }
            ServerLevel level=owner.serverLevel();
            Vec3 look=CameraPackets.isGatewayViewing(owner)
                ? cameraAim.getOrDefault(owner.getUUID(),owner.getLookAngle())
                : owner.getLookAngle();
            Vec3 desired=look.normalize().scale(chain.velocity.length());
            // The caster can steer by looking in another direction. Not a remote camera.
            chain.velocity=chain.velocity.scale(.72).add(desired.scale(.28));
            Vec3 end=chain.point.add(chain.velocity.scale(2));
            if(!level.hasChunkAt(BlockPos.containing(end))
                || level.clip(new ClipContext(chain.point,end,ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS){
                level.sendParticles(ParticleTypes.SMOKE,chain.point.x,chain.point.y,chain.point.z,
                    6,.2,.2,.2,.02);
                CameraPackets.close(owner);
                cameraAim.remove(owner.getUUID());
                flight.remove();continue;
            }
            LivingEntity hit=null;double nearest=Double.POSITIVE_INFINITY;
            for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(chain.point,end).inflate(1.0),
                    e->PowerFramework.canAffect(owner,e))){
                var intersection=victim.getBoundingBox().inflate(.18).clip(chain.point,end);
                if(intersection.isEmpty())continue;
                double d=chain.point.distanceToSqr(intersection.get());
                if(d<nearest){nearest=d;hit=victim;}
            }
            for(int i=0;i<=5;i++){
                Vec3 point=chain.point.lerp(end,i/5.0);
                level.sendParticles(ParticleTypes.CRIT,point.x,point.y,point.z,1,0,0,0,0);
            }
            chain.point=end;
            chain.steps+=2;
            CameraPackets.follow(owner,end);
            if(hit!=null){
                bound.put(hit.getUUID(),new Bound(owner.getUUID(),hit.getUUID(),level.dimension(),
                    now+20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_SECONDS),3,20)));
                level.playSound(null,hit.blockPosition(),SoundEvents.CHAIN_PLACE,SoundSource.PLAYERS,1.2f,.8f);
                owner.displayClientMessage(Component.literal("Three chains attached to "+hit.getName().getString()),true);
                if(hit instanceof ServerPlayer victim)
                    victim.sendSystemMessage(Component.literal("§dBound by three spectral chains! Hold SNEAK to break them one at a time."));
                CameraPackets.close(owner);
                cameraAim.remove(owner.getUUID());
                flight.remove();
            } else if(chain.steps>=Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAIN_FLIGHT_TICKS),8,60)){
                CameraPackets.close(owner);
                cameraAim.remove(owner.getUUID());
                flight.remove();
            }
        }
        bound.entrySet().removeIf(e->{
            Bound b=e.getValue();
            ServerLevel level=server.getLevel(b.dimension);
            if(level==null || now>b.expires || b.chains<=0)return true;
            var entity=level.getEntity(b.target);
            if(!(entity instanceof LivingEntity victim) || !victim.isAlive())return true;
            if(victim instanceof ServerPlayer player){
                if(!level.getGameRules().getBoolean(WhisperRules.POWERS_PVP))return true;
                if(player.isShiftKeyDown()) {
                    b.struggle+=2;
                    if(b.struggle>=20) {
                        b.struggle=0;b.chains--;
                        player.displayClientMessage(Component.literal("Chain broken — "+b.chains+" remain"),true);
                    }
                }else b.struggle=0;
            }else if(now%30==0)b.chains--;
            if(b.chains<=0)return true;
            int amplifier=b.chains==3?3:b.chains==2?2:1;
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,amplifier,false,true,true));
            if(now%4==0) {
                level.sendParticles(ParticleTypes.CRIT,victim.getX(),victim.getY()+.9,
                    victim.getZ(),b.chains*2,.45,.45,.45,.0);
            }
            return false;
        });
        solving.entrySet().removeIf(e->{
            Solve s=e.getValue();
            ServerPlayer p=server.getPlayerList().getPlayer(s.player);
            if(p==null || !p.isAlive() || p.level().dimension()!=s.dimension ||
                p.position().distanceToSqr(s.origin)>2.25 || !p.isShiftKeyDown() ||
                (!p.getMainHandItem().is(DeathWhisper.LAMENT_CONFIGURATION.get()) &&
                 !p.getOffhandItem().is(DeathWhisper.LAMENT_CONFIGURATION.get())))return true;
            if(now>=s.expires){solveComplete(p);return true;}
            if(now%20==0)p.displayClientMessage(Component.literal(
                "Solving configuration: "+Math.max(0,(s.expires-now)/20)+" seconds left"),true);
            return false;
        });
        hunts.entrySet().removeIf(entry->{
            Hunt hunt=entry.getValue();
            ServerLevel level=server.getLevel(hunt.dimension);
            ServerPlayer owner=server.getPlayerList().getPlayer(hunt.caster);
            if(level==null || owner==null || owner.level()!=level || !owner.isAlive() ||
                now>hunt.ends || !level.getGameRules().getBoolean(WhisperRules.CHAIN_HUNTS_ENABLED))return true;
            if(now<hunt.activation || now<hunt.nextChain)return false;
            hunt.active=true;
            hunt.nextChain=now+40;
            LivingEntity victim=null;
            for(LivingEntity candidate:level.getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(24),target->PowerFramework.canAffect(owner,target))){
                if(candidate.distanceToSqr(owner)>24*24 || bound.containsKey(candidate.getUUID()))continue;
                if(!level.hasChunkAt(candidate.blockPosition()))continue;
                victim=candidate;break;
            }
            if(victim!=null){
                bound.put(victim.getUUID(),new Bound(owner.getUUID(),victim.getUUID(),
                    level.dimension(),now+120));
                level.sendParticles(ParticleTypes.PORTAL,victim.getX(),victim.getY()+1,
                    victim.getZ(),12,.5,.5,.5,.02);
                if(victim instanceof ServerPlayer p)
                    p.sendSystemMessage(Component.literal("§5Chain Hunt! Solve a Lament Configuration to end it."));
            }
            return false;
        });
    }
}
