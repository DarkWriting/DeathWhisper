package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * One server-wide scheduler, never loads chunks, bounds encounters and path search.
 * Persistent per-player observations live in vanilla player NBT.
 */
public final class StalkingManager {
    private final Map<UUID, Long> nextEncounter = new HashMap<>();
    private final Map<UUID, UUID> active = new HashMap<>();
    private long ticks;
    private long successful, attempted;
    public static final String[] KEYS={"deaths","sightings","escapes","curiosity","chases","whistles","encounters","last"};
    private static String key(String s) { return "deathwhisper_"+s; }
    public int get(ServerPlayer p, String k) { return p.getPersistentData().getInt(key(k)); }
    public void set(ServerPlayer p, String k, int value) { p.getPersistentData().putInt(key(k), Math.max(0,value)); }
    public void add(ServerPlayer p, String k, int amount) { set(p,k,Math.min(100000,get(p,k)+amount)); }
    public int threat(ServerPlayer p) { return Mth.clamp(get(p,"threat"),0,100); }
    public void setThreat(ServerPlayer p,int value) { set(p,"threat", Mth.clamp(value,0,100)); }
    public String memory(ServerPlayer p) {
        StringJoiner join=new StringJoiner(", ",p.getName().getString()+": ","");
        for(String k:KEYS) join.add(k+"="+get(p,k));
        return join.toString()+", threat="+threat(p);
    }
    public String status(ServerPlayer p) {
        long cd=Math.max(0,nextEncounter.getOrDefault(p.getUUID(),ticks)-ticks)/20;
        WatcherEntity w=watcherFor(p);
        return "Death Whisper: "+p.getName().getString()+" threat="+threat(p)+"/100"
           +", remaining="+cd+"s, watcher="+(w==null?"none":w.debugState())
           +", current="+active.size()+", attempts="+attempted+", encounters="+successful;
    }
    public String diagnostics(ServerPlayer p) {
        WatcherEntity w=watcherFor(p);
        return w==null ? "No active watcher. "+status(p) : w.debugState()
            +", position="+w.blockPosition()+", target="+p.getName().getString()
            +", path="+(w.getNavigation().isInProgress()?"running":"idle");
    }
    private WatcherEntity watcherFor(ServerPlayer p) {
        UUID uuid=active.get(p.getUUID());
        if(uuid!=null && p.level() instanceof ServerLevel level) {
            var entity=level.getEntity(uuid);
            if (entity instanceof WatcherEntity w && w.isAlive()) return w;
        }
        if(p.level() instanceof ServerLevel level) {
            for(var w:level.getEntitiesOfClass(WatcherEntity.class,p.getBoundingBox().inflate(110)))
                if(p.getUUID().equals(w.getChosenPlayer())) return w;
        }
        return null;
    }
    public void clear(ServerPlayer p) {
        nextEncounter.remove(p.getUUID());
        active.remove(p.getUUID());
        setThreat(p,0);
        for(String k:KEYS) set(p,k,0);
        if(p.level() instanceof ServerLevel level)
            level.getEntitiesOfClass(WatcherEntity.class,p.getBoundingBox().inflate(128)).stream()
               .filter(w->p.getUUID().equals(w.getChosenPlayer())).forEach(WatcherEntity::discard);
    }
    @SubscribeEvent public void onDeath(LivingDeathEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {
            add(p,"deaths",1);
            setThreat(p,threat(p)+18);
        }
    }
    @SubscribeEvent public void clonePlayer(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getOriginal() instanceof ServerPlayer old) {
            for(String k:KEYS) set(p,k,old.getPersistentData().getInt(key(k)));
            setThreat(p,old.getPersistentData().getInt(key("threat")));
        }
    }
    @SubscribeEvent public void onTick(ServerTickEvent.Post e) {
        ticks++;
        if(ticks%200!=0) return;
        var players=e.getServer().getPlayerList().getPlayers();
        if(players.isEmpty()) {active.clear();return;}
        active.entrySet().removeIf(entry->{
            ServerPlayer p=e.getServer().getPlayerList().getPlayer(entry.getKey());
            return p==null || !(p.level() instanceof ServerLevel level) || !(level.getEntity(entry.getValue()) instanceof WatcherEntity w) || !w.isAlive();
        });
        if(ticks%12000==0)
            for(ServerPlayer p:players) setThreat(p,threat(p)-Math.max(0,p.level().getGameRules().getInt(WhisperRules.THREAT_DECAY)));
        for(ServerPlayer p:players) {
            if(p.isSpectator() || p.isCreative() || !p.isAlive() || !(p.level() instanceof ServerLevel level)) continue;
            if(!level.getGameRules().getBoolean(WhisperRules.ENABLED)) continue;
            long cooldown=Mth.clamp(level.getGameRules().getInt(WhisperRules.COOLDOWN),10,7200)*20L;
            Long next=nextEncounter.get(p.getUUID());
            if(next==null) {nextEncounter.put(p.getUUID(),ticks+cooldown/2);continue;}
            if(ticks<next)continue;
            int chance=level.getGameRules().getInt(WhisperRules.CHANCE);
            if(level.getGameRules().getBoolean(WhisperRules.MEMORY)) chance+=threat(p)/3;
            int nearby=nearbyPlayers(p);
            if(level.getGameRules().getBoolean(WhisperRules.GROUP_AVOIDANCE) && nearby>0) chance-=nearby*12;
            if(level.getGameRules().getBoolean(WhisperRules.ISOLATION) && nearby==0) chance+=level.getGameRules().getInt(WhisperRules.ISOLATION_BONUS);
            attempted++;
            if(level.random.nextInt(100)<Mth.clamp(chance,0,100) && force(p)) {
                nextEncounter.put(p.getUUID(),ticks+cooldown+level.random.nextInt(20*90));
                successful++;
            } else nextEncounter.put(p.getUUID(),ticks+200);
        }
    }
    private static int nearbyPlayers(ServerPlayer p) {
        int r=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.GROUP_RADIUS),8,96);
        int n=0;
        for(var other:p.serverLevel().players()) if(other!=p && other.isAlive() && other.distanceToSqr(p)<r*r) n++;
        return n;
    }
    public boolean force(ServerPlayer p) { return force(p,null); }
    public boolean force(ServerPlayer p,EncounterKind requested) {
        if(!(p.level() instanceof ServerLevel level) || !level.getGameRules().getBoolean(WhisperRules.ENABLED)) return false;
        int max=Mth.clamp(level.getGameRules().getInt(WhisperRules.MAX_ACTIVE),1,10);
        if(active.size()>=max || watcherFor(p)!=null) return false;
        EncounterKind kind=requested==null?chooseKind(level,p):requested;
        BlockPos at=findPosition(level,p,28,kind);
        if(at==null) return false;
        WatcherEntity w=DeathWhisper.WATCHER.get().create(level);
        if(w==null) return false;
        w.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        w.setChosenPlayer(p.getUUID());
        w.setEncounterKind(kind);
        if(!level.addFreshEntity(w)) return false;
        active.put(p.getUUID(),w.getUUID());
        add(p,"encounters",1);
        setThreat(p,threat(p)+Mth.clamp(level.getGameRules().getInt(WhisperRules.THREAT_GAIN),0,100));
        return true;
    }
    public void record(ServerPlayer p,String event) {add(p,event,1);}
    public int purge(ServerLevel level) {
        int count=0;
        for(var p:level.players()) for(var w:level.getEntitiesOfClass(WatcherEntity.class,p.getBoundingBox().inflate(144))) {
            w.discard();count++;
        }
        active.clear();return count;
    }
    public static EncounterKind chooseKind(ServerLevel level,ServerPlayer p) {
        boolean group=nearbyPlayers(p)>0;
        String biome=String.valueOf(level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(p.blockPosition()).value())).toLowerCase(Locale.ROOT);
        if(level.getGameRules().getBoolean(WhisperRules.STRUCTURE_BEHAVIOR)) {
            boolean bed=false,camp=false,door=false;
            BlockPos center=p.blockPosition();
            for(int x=-6;x<=6;x+=2)for(int z=-6;z<=6;z+=2)for(int y=-1;y<=2;y++){
                BlockPos b=center.offset(x,y,z);
                if(!level.hasChunkAt(b))continue;
                var block=level.getBlockState(b).getBlock();
                camp|=block==Blocks.CAMPFIRE || block==Blocks.SOUL_CAMPFIRE;
                bed|=block instanceof BedBlock;
                door|=block instanceof DoorBlock;
            }
            if(camp && level.random.nextInt(3)==0)return EncounterKind.CAMPFIRE;
            if(bed && level.random.nextInt(4)==0)return EncounterKind.HOME;
            if(door && level.random.nextInt(4)==0)return EncounterKind.DOORWAY;
        }
        if(group && level.random.nextInt(3)==0)return EncounterKind.GROUP;
        if(level.getGameRules().getBoolean(WhisperRules.WEATHER) && level.isThundering())return EncounterKind.RAIN;
        if(p.blockPosition().getY()<45 && level.random.nextBoolean())return EncounterKind.MINING;
        if(!level.canSeeSky(p.blockPosition()) && level.random.nextInt(3)==0)return EncounterKind.CAVE;
        if(level.getGameRules().getBoolean(WhisperRules.BIOME_BEHAVIOR) && (biome.contains("forest") || biome.contains("jungle") || biome.contains("taiga")))
            if(level.random.nextInt(3)==0)return EncounterKind.FOREST;
        int roll=level.random.nextInt(100);
        if(level.getGameRules().getBoolean(WhisperRules.SILENT_ENCOUNTERS) && roll<Mth.clamp(level.getGameRules().getInt(WhisperRules.SILENT_CHANCE),0,100))return EncounterKind.SILENT;
        if(level.getGameRules().getBoolean(WhisperRules.FALSE_SIGHTINGS) && roll<38)return EncounterKind.FALSE_SIGHTING;
        if(level.getGameRules().getBoolean(WhisperRules.FAKE_DEPARTURES) && roll<46)return EncounterKind.FAKE_DEPARTURE;
        return EncounterKind.values()[level.random.nextInt(EncounterKind.values().length)];
    }
    public static BlockPos findPosition(ServerLevel level,ServerPlayer p,int attempts) {
        return findPosition(level,p,attempts,EncounterKind.WATCH);
    }
    public static BlockPos findPosition(ServerLevel level,ServerPlayer p,int attempts,EncounterKind kind) {
        int min=Mth.clamp(level.getGameRules().getInt(WhisperRules.MIN_DISTANCE),10,64);
        int max=Mth.clamp(level.getGameRules().getInt(WhisperRules.MAX_DISTANCE),min+1,96);
        Vec3 ahead=level.getGameRules().getBoolean(WhisperRules.PREDICTION)?p.getDeltaMovement().scale(15):Vec3.ZERO;
        for(int i=0;i<Math.min(attempts,48);i++){
            double angle=level.random.nextDouble()*Math.PI*2;
            int dist=min+level.random.nextInt(max-min+1);
            int x=Mth.floor(p.getX()+Math.cos(angle)*dist+ahead.x);
            int z=Mth.floor(p.getZ()+Math.sin(angle)*dist+ahead.z);
            for(int dy=-7;dy<=7;dy++){
                BlockPos at=new BlockPos(x,p.blockPosition().getY()+dy,z);
                if(!level.hasChunkAt(at)||!level.getBlockState(at.below()).isSolidRender(level,at.below()))continue;
                if(!level.getBlockState(at).isAir()||!level.getBlockState(at.above()).isAir())continue;
                if(!level.getGameRules().getBoolean(WhisperRules.UNDERGROUND)&&!level.canSeeSky(at))continue;
                if(level.getGameRules().getBoolean(WhisperRules.ELEVATED_WATCH) && kind==EncounterKind.HILL
                     && at.getY()<p.getBlockY()+3)continue;
                return at;
            }
        }
        return null;
    }
}
