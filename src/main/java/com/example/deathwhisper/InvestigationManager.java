package com.example.deathwhisper;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Cooperative and non-destructive ghost investigation. No block edits or chunk tickets.
 * Ghost profiles are stable for a world seed + block anchor, cases saved to per-player NBT.
 */
public final class InvestigationManager {
    private static final String DATA="DeathWhisperInvestigation";
    private static final String CASES="cases";
    private final Map<String,Haunting> sites=new LinkedHashMap<>();
    private final Map<String,Seance> rituals=new HashMap<>();
    private final Map<UUID,FootTrail> trails=new HashMap<>();
    private final Map<UUID,Long> nextFootstep=new HashMap<>();
    private final Map<String,GhostHunt> hunts=new LinkedHashMap<>();
    private long ticks=0;
    private static final class Haunting {
        final ResourceKey<Level> dimension;
        final BlockPos focus;
        final GhostProfile ghost;
        long lastActive;
        Haunting(ResourceKey<Level> dimension,BlockPos focus,GhostProfile ghost,long lastActive){
            this.dimension=dimension;this.focus=focus.immutable();this.ghost=ghost;this.lastActive=lastActive;
        }
    }
    private static final class Seance {
        final Haunting site;
        final Set<UUID> members=new LinkedHashSet<>();
        final long started,expires;
        long finishing=-1;
        Seance(Haunting site,long started){
            this.site=site;this.started=started;this.expires=started+1200;
        }
    }
    private static final class FootTrail {
        final ResourceKey<Level> dimension;
        final UUID viewer;
        final List<Vec3> positions;
        final long expires;
        FootTrail(ResourceKey<Level> dimension,UUID viewer,List<Vec3> positions,long expires){
            this.dimension=dimension;this.viewer=viewer;this.positions=positions;this.expires=expires;
        }
    }
    private static final class GhostHunt {
        final Haunting site; final Set<UUID> team; final long ends;
        long nextManifest; int clues; int sightings;
        GhostHunt(Haunting site,Set<UUID> team,long started,int duration){
            this.site=site;this.team=team;this.ends=started+duration*20L;
            this.nextManifest=started+40;
        }
    }
    private static String key(ResourceKey<Level> dimension,BlockPos pos){
        return dimension.location().toString()+"_"+pos.asLong();
    }
    private static CompoundTag data(ServerPlayer p){return p.getPersistentData().getCompound(DATA);}
    private static CompoundTag cases(ServerPlayer p){return data(p).getCompound(CASES);}
    private static void saveCases(ServerPlayer p,CompoundTag cases){
        CompoundTag root=data(p);
        root.put(CASES,cases);
        p.getPersistentData().put(DATA,root);
    }
    private static boolean enabled(ServerLevel level){return level.getGameRules().getBoolean(WhisperRules.INVESTIGATIONS_ENABLED);}
    private Haunting create(ServerLevel level,BlockPos origin){
        String k=key(level.dimension(),origin);
        Haunting existing=sites.get(k);
        if(existing!=null){existing.lastActive=ticks;return existing;}
        if(sites.size()>=64){
            sites.entrySet().removeIf(e->ticks-e.getValue().lastActive>1200*20);
            if(sites.size()>=64)return null;
        }
        long input=level.getSeed() ^ origin.asLong() ^ level.dimension().location().hashCode();
        input^=input>>>33;input*=0xff51afd7ed558ccdL;input^=input>>>33;
        input*=0xc4ceb9fe1a85ec53L;input^=input>>>33;
        GhostProfile ghost=GhostProfile.values()[Math.floorMod(input,GhostProfile.values().length)];
        Haunting site=new Haunting(level.dimension(),origin,ghost,ticks);
        sites.put(k,site);
        return site;
    }
    private Haunting nearby(ServerPlayer p,double distance){
        Haunting selected=null;
        double best=distance*distance;
        for(Haunting site:sites.values()){
            if(site.dimension!=p.level().dimension())continue;
            double d=site.focus.distSqr(p.blockPosition());
            if(d<best){selected=site;best=d;}
        }
        if(selected!=null)selected.lastActive=ticks;
        return selected;
    }
    private void record(ServerPlayer p,Haunting site,int evidence){
        String k=key(site.dimension,site.focus);
        CompoundTag all=cases(p);
        CompoundTag page=all.getCompound(k);
        boolean first=!all.contains(k);
        if(first && all.getAllKeys().size()>=24){
            String oldest=all.getAllKeys().iterator().next();
            all.remove(oldest);
        }
        page.putString("dimension",site.dimension.location().toString());
        page.putInt("x",site.focus.getX());
        page.putInt("y",site.focus.getY());
        page.putInt("z",site.focus.getZ());
        int before=page.getInt("evidence");
        page.putInt("evidence",before|evidence);
        all.put(k,page);saveCases(p,all);
        if(evidence!=0){
            GhostHunt active=hunts.get(k);
            if(active!=null && active.team.contains(p.getUUID()))
                active.clues|=evidence;
        }
        int gained=evidence & ~before;
        if(gained!=0)
            p.sendSystemMessage(Component.literal("§b[Field Journal] New evidence: "+GhostProfile.evidenceText(gained)));
        else if(first)p.sendSystemMessage(Component.literal("§7A new investigation has begun."));
    }
    private boolean feature(ServerLevel level,net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> k){
        return enabled(level)&&level.getGameRules().getBoolean(k);
    }

    /** Audio tape captures a case-specific ghost response without exposing ghost type. */
    public boolean recordTape(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        if(!feature(level,WhisperRules.CURSED_RECORDINGS))return false;
        Haunting site=nearby(p,Math.min(32,level.getGameRules().getInt(WhisperRules.INVESTIGATION_RANGE)));
        if(site==null){
            p.displayClientMessage(Component.literal("Recorder detects only static. Investigate a painting or ritual first."),true);
            return false;
        }
        String[] statements={"...do not open it...","...the lights are moving...",
            "...I heard you return...","...it remembers this place...","...look behind the door..."};
        boolean voice=site.ghost.has(GhostProfile.VOICE);
        String clip=voice?statements[Math.floorMod(site.focus.hashCode()+site.ghost.ordinal(),statements.length)]
            :"...krrrr...static...krrrr...";
        ItemStack tape=new ItemStack(DeathWhisper.CURSED_TAPE.get());
        CustomData.update(DataComponents.CUSTOM_DATA,tape,tag->{
            tag.putString("GhostMessage",clip);
            tag.putString("Dimension",site.dimension.location().toString());
            tag.putLong("Anchor",site.focus.asLong());
        });
        tape.set(DataComponents.CUSTOM_NAME,Component.literal("Cursed Tape - "+site.focus.getX()+", "+site.focus.getZ()));
        if(!p.getInventory().add(tape))p.drop(tape,false);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.NOTE_BLOCK_PLING.value(),
           SoundSource.PLAYERS,.5f,.76f);
        p.sendSystemMessage(Component.literal("§5A recording materializes. Play the tape to hear what was captured."));
        record(p,site,voice?GhostProfile.VOICE:0);
        return true;
    }
    private boolean equipped(ServerPlayer player){
        return player.getInventory().items.stream().anyMatch(item->
            item.is(DeathWhisper.EMF_READER.get()) ||item.is(DeathWhisper.SPIRIT_RADIO.get()) ||
            item.is(DeathWhisper.GHOST_THERMOMETER.get()) ||item.is(DeathWhisper.UV_LAMP.get()) ||
            item.is(DeathWhisper.CURSED_RECORDER.get()));
    }
    public boolean beginHunt(ServerPlayer caller){
        ServerLevel level=caller.serverLevel();
        if(!feature(level,WhisperRules.GHOST_HUNTS_ENABLED))return false;
        Haunting site=nearby(caller,24);
        if(site==null){
            caller.sendSystemMessage(Component.literal("Begin a haunting with ritual chalk or a painting first."));
            return false;
        }
        String id=key(site.dimension,site.focus);
        if(hunts.containsKey(id)){
            caller.sendSystemMessage(Component.literal("A ghost hunt is already underway here."));
            return false;
        }
        if(hunts.size()>=4){
            caller.sendSystemMessage(Component.literal("Too many investigations are active."));
            return false;
        }
        Set<UUID> team=new LinkedHashSet<>();
        for(ServerPlayer candidate:level.players()){
            if(candidate.isAlive() && !candidate.isSpectator() &&
                candidate.distanceToSqr(site.focus.getX()+.5,site.focus.getY()+1,site.focus.getZ()+.5)<18*18
                && equipped(candidate)) team.add(candidate.getUUID());
        }
        int required=Mth.clamp(level.getGameRules().getInt(WhisperRules.HUNT_MIN_PLAYERS),1,4);
        if(team.size()<required){
            caller.sendSystemMessage(Component.literal("Need "+required+" nearby investigator(s) carrying EMF, UV, radio, thermometer, or recorder. Found "+team.size()+"."));
            return false;
        }
        int duration=Mth.clamp(level.getGameRules().getInt(WhisperRules.HUNT_SECONDS),20,180);
        hunts.put(id,new GhostHunt(site,team,ticks,duration));
        site.lastActive=ticks;
        for(UUID idPlayer:team){
            ServerPlayer member=caller.getServer().getPlayerList().getPlayer(idPlayer);
            if(member!=null)member.sendSystemMessage(Component.literal(
                "§5[Paranormal Hunt] Ghost activity is escalating. Collect TWO distinct evidence types in "+duration+" seconds!"));
        }
        level.playSound(null,site.focus,DeathWhisper.WHISTLE_LOW.get(),SoundSource.AMBIENT,1.5f,.45f);
        return true;
    }
    /** Called by a placed redstone sensor. No terrain or journal mutations except the sensor itself. */
    public void sensorTriggered(ServerLevel level,BlockPos pos){
        Haunting closest=null;
        double distance=20*20;
        for(Haunting site:sites.values()) {
            if(site.dimension!=level.dimension())continue;
            double d=site.focus.distSqr(pos);
            if(d<distance){closest=site;distance=d;}
        }
        if(closest==null)return;
        for(ServerPlayer player:level.players()){
            if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<16*16){
                player.displayClientMessage(Component.literal("§b[Motion Sensor] Something crossed the detection zone."),true);
                CompoundTag all=cases(player),page=all.getCompound(key(closest.dimension,closest.focus));
                page.putInt("motion",page.getInt("motion")+1);
                all.put(key(closest.dimension,closest.focus),page);
                saveCases(player,all);
            }
        }
    }
    public boolean forceManifest(ServerPlayer caller){
        Haunting site=nearby(caller,25);
        if(site==null){
            caller.sendSystemMessage(Component.literal("No active ghost case to manifest."));
            return false;
        }
        return manifest(caller.serverLevel(),site,caller);
    }
    private boolean manifest(ServerLevel level,Haunting site,ServerPlayer witness){
        if(!feature(level,WhisperRules.MANIFESTATIONS_ENABLED) || !witness.isAlive())return false;
        if(!level.getEntitiesOfClass(GhostApparition.class,new AABB(site.focus).inflate(35)).isEmpty())return false;
        for(int i=0;i<16;i++){
            double theta=level.random.nextDouble()*Math.PI*2;
            int distance=7+level.random.nextInt(10);
            int x=Mth.floor(witness.getX()+Math.cos(theta)*distance);
            int z=Mth.floor(witness.getZ()+Math.sin(theta)*distance);
            for(int dy=-3;dy<=3;dy++){
                BlockPos at=new BlockPos(x,witness.blockPosition().getY()+dy,z);
                if(!level.hasChunkAt(at) || !level.getBlockState(at).isAir() ||
                   !level.getBlockState(at.above()).isAir() ||
                   !level.getBlockState(at.below()).isSolidRender(level,at.below()))continue;
                GhostApparition ghost=DeathWhisper.GHOST_APPARITION.get().create(level);
                if(ghost==null)return false;
                ghost.moveTo(x+.5,at.getY(),z+.5,0,0);
                ghost.manifestFor(witness.getUUID(),
                    20*Mth.clamp(level.getGameRules().getInt(WhisperRules.MANIFEST_SECONDS),4,30));
                if(!level.addFreshEntity(ghost))return false;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,x+.5,at.getY()+1,z+.5,18,.5,.6,.5,.02);
                level.playSound(null,at,DeathWhisper.WHISTLE_LOW.get(),SoundSource.AMBIENT,.85f,.62f);
                return true;
            }
        }
        return false;
    }
    private void tickHunts(MinecraftServer server){
        Iterator<Map.Entry<String,GhostHunt>> iterator=hunts.entrySet().iterator();
        while(iterator.hasNext()){
            GhostHunt hunt=iterator.next().getValue();
            ServerLevel level=server.getLevel(hunt.site.dimension);
            if(level==null || !enabled(level) ||
                !level.getGameRules().getBoolean(WhisperRules.GHOST_HUNTS_ENABLED) ||
                ticks>hunt.ends || !level.hasChunkAt(hunt.site.focus)){
                iterator.remove();continue;
            }
            hunt.site.lastActive=ticks;
            hunt.team.removeIf(uuid->{
                ServerPlayer p=server.getPlayerList().getPlayer(uuid);
                return p==null ||p.level()!=level || !p.isAlive()||
                   p.distanceToSqr(hunt.site.focus.getX()+.5,hunt.site.focus.getY()+1,hunt.site.focus.getZ()+.5)>28*28;
            });
            if(hunt.team.isEmpty()){iterator.remove();continue;}
            if(Integer.bitCount(hunt.clues&hunt.site.ghost.evidence)>=2){
                for(UUID id:hunt.team){
                    ServerPlayer p=server.getPlayerList().getPlayer(id);
                    if(p==null)continue;
                    p.giveExperiencePoints(25);
                    p.sendSystemMessage(Component.literal("§a[Hunt Complete] Two ghost clues confirmed. Check your field journal and identify the ghost."));
                }
                iterator.remove();continue;
            }
            if(ticks>=hunt.nextManifest){
                hunt.nextManifest=ticks+Mth.clamp(level.getGameRules().getInt(WhisperRules.HUNT_ACTIVITY_TICKS),45,240);
                if(hunt.sightings<3 &&
                   level.random.nextInt(100)<Mth.clamp(level.getGameRules().getInt(WhisperRules.HUNT_MANIFEST_CHANCE),0,100)){
                    for(UUID id:hunt.team){
                        ServerPlayer p=server.getPlayerList().getPlayer(id);
                        if(p!=null && manifest(level,hunt.site,p)){hunt.sightings++;break;}
                    }
                }else {
                    level.sendParticles(ParticleTypes.SOUL,hunt.site.focus.getX()+.5,hunt.site.focus.getY()+1,
                        hunt.site.focus.getZ()+.5,12,1,.9,1,.02);
                    level.playSound(null,hunt.site.focus,SoundEvents.SCULK_SHRIEKER_SHRIEK,SoundSource.AMBIENT,.25f,.6f);
                }
            }
        }
    }

    public boolean showJournal(ServerPlayer p){
        CompoundTag all=cases(p);
        p.sendSystemMessage(Component.literal("§8[§5Investigation Journal§8] §7"+all.getAllKeys().size()+" recorded cases"));
        if(all.getAllKeys().isEmpty()){
            p.sendSystemMessage(Component.literal("§7Start a séance, examine a painting, or activate an investigator's instrument near a haunting."));
            return true;
        }
        int count=0;
        for(String k:all.getAllKeys()){
            if(count++>=12)break;
            CompoundTag page=all.getCompound(k);
            String place=page.getString("dimension")+" "+page.getInt("x")+","+page.getInt("y")+","+page.getInt("z");
            String solved=page.getString("solved");
            p.sendSystemMessage(Component.literal("§dCase "+count+" §7"+place+" §b"+GhostProfile.evidenceText(page.getInt("evidence"))
                +(solved.isEmpty()?"":" §aIdentified: "+solved)));
        }
        Haunting near=nearby(p,28);
        if(near!=null){
            CompoundTag page=all.getCompound(key(near.dimension,near.focus));
            p.sendSystemMessage(Component.literal("§6Current case: "+near.focus+" | "+GhostProfile.evidenceText(page.getInt("evidence"))));
            p.sendSystemMessage(Component.literal("§7Try /deathwhisperinvestigate guess <ghost_type> after collecting two clues."));
        }
        return true;
    }
    public boolean measure(ServerPlayer p,InvestigationItem.Tool tool){
        ServerLevel level=p.serverLevel();
        if(!enabled(level))return false;
        Haunting site=nearby(p,Mth.clamp(level.getGameRules().getInt(WhisperRules.INVESTIGATION_RANGE),8,32));
        if(site==null){
            p.displayClientMessage(Component.literal("No active haunting. Start a séance or examine a painting."),true);
            return false;
        }
        if(tool==InvestigationItem.Tool.EMF){
            int strength=site.ghost.has(GhostProfile.EMF)?4+level.random.nextInt(2):level.random.nextInt(3);
            p.sendSystemMessage(Component.literal("§aEMF detector: Level "+strength+"/5"));
            level.playSound(null,p.blockPosition(),SoundEvents.NOTE_BLOCK_PLING.value(),SoundSource.PLAYERS,.5f,1f+strength*.1f);
            if(strength>=4)record(p,site,GhostProfile.EMF);
            else record(p,site,0);
        }else if(tool==InvestigationItem.Tool.SPIRIT_RADIO){
            if(site.ghost.has(GhostProfile.VOICE)){
                String[] messages={"YOU CAME BACK","BEHIND THE GLASS","NOT ALONE","I REMEMBER"};
                p.sendSystemMessage(Component.literal("§5[Spirit Radio] §d"+messages[level.random.nextInt(messages.length)]));
                p.serverLevel().playSound(null,p.blockPosition(),DeathWhisper.WHISTLE_LOW.get(),SoundSource.PLAYERS,.55f,.58f);
                record(p,site,GhostProfile.VOICE);
            }else{
                p.sendSystemMessage(Component.literal("§7[Spirit Radio] ...static..."));
                record(p,site,0);
            }
        }else if(tool==InvestigationItem.Tool.UV){
            if(site.ghost.has(GhostProfile.INK)){
                p.sendSystemMessage(Component.literal("§5UV LAMP: strange ink-like fingerprints glow on nearby surfaces."));
                level.sendParticles(ParticleTypes.SQUID_INK,site.focus.getX()+.5,site.focus.getY()+1,site.focus.getZ()+.5,9,.6,.45,.6,.02);
                record(p,site,GhostProfile.INK);
            }else{
                p.sendSystemMessage(Component.literal("§7UV LAMP: no spectral residue detected."));
                record(p,site,0);
            }
        }else if(tool==InvestigationItem.Tool.THERMOMETER){
            int degrees=site.ghost.has(GhostProfile.FROST)? -4+level.random.nextInt(7): 8+level.random.nextInt(15);
            p.sendSystemMessage(Component.literal("§bThermometer: "+degrees+"°C"));
            if(degrees<=2)record(p,site,GhostProfile.FROST);else record(p,site,0);
        }else return false;
        return true;
    }
    public boolean joinSeance(ServerPlayer p,BlockPos clicked){
        ServerLevel level=p.serverLevel();
        if(!feature(level,WhisperRules.SEANCES_ENABLED))return false;
        if(p.isSpectator()||!p.isAlive())return false;
        if(!level.hasChunkAt(clicked) || !level.getBlockState(clicked).isSolidRender(level,clicked))return false;
        if(rituals.size()>=8 && rituals.values().stream().noneMatch(s->s.site.dimension==level.dimension()
           && s.site.focus.distSqr(clicked)<24))return false;
        String key=null;
        for(var entry:rituals.entrySet()){
            if(entry.getValue().site.dimension==level.dimension()
              && entry.getValue().site.focus.distSqr(clicked)<=16){key=entry.getKey();break;}
        }
        Seance ritual=key==null?null:rituals.get(key);
        if(ritual==null){
            Haunting site=create(level,clicked);
            if(site==null)return false;
            ritual=new Seance(site,ticks);
            rituals.put(key(level.dimension(),clicked),ritual);
        }
        if(!ritual.members.add(p.getUUID())){
            p.displayClientMessage(Component.literal("You're already participating in this séance."),true);
            return true;
        }
        p.sendSystemMessage(Component.literal("§5[Seance] §7Circle joined: "+ritual.members.size()+" investigators. Remain within eight blocks."));
        if(ritual.members.size()>=2 && ritual.finishing<0){
            int seconds=Mth.clamp(level.getGameRules().getInt(WhisperRules.SEANCE_SECONDS),5,45);
            ritual.finishing=ticks+20L*seconds;
            for(UUID id:ritual.members){
                ServerPlayer witness=p.getServer().getPlayerList().getPlayer(id);
                if(witness!=null)witness.sendSystemMessage(Component.literal("§dThe circle answers. Remain near the ritual for "+seconds+" seconds."));
            }
        }
        level.playSound(null,clicked,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,.9f,.7f);
        return true;
    }
    public int guess(ServerPlayer p,String answer){
        GhostProfile hypothesis=GhostProfile.parse(answer);
        if(hypothesis==null){
            p.sendSystemMessage(Component.literal("Known ghosts: "+Arrays.toString(GhostProfile.values())));
            return 0;
        }
        Haunting site=nearby(p,28);
        if(site==null){p.sendSystemMessage(Component.literal("No active ghost case nearby."));return 0;}
        String k=key(site.dimension,site.focus);
        CompoundTag all=cases(p),page=all.getCompound(k);
        if(Integer.bitCount(page.getInt("evidence"))<2){
            p.sendSystemMessage(Component.literal("Collect at least two distinct pieces of evidence first."));
            return 0;
        }
        if(hypothesis!=site.ghost){
            p.sendSystemMessage(Component.literal("§cIncorrect hypothesis. The journal remains unsolved."));
            return 0;
        }
        if(!page.getString("solved").isEmpty()){
            p.sendSystemMessage(Component.literal("This haunting was already identified."));
            return 1;
        }
        page.putString("solved",hypothesis.name());
        all.put(k,page);saveCases(p,all);
        p.giveExperiencePoints(18);
        p.sendSystemMessage(Component.literal("§aCase closed! "+hypothesis.description+". "+hypothesis.clue));
        return 1;
    }
    @SubscribeEvent public void onPainting(PlayerInteractEvent.EntityInteract event){
        if(!(event.getEntity() instanceof ServerPlayer p) ||
            !(event.getTarget() instanceof Painting painting) ||
            event.getHand()!=InteractionHand.MAIN_HAND ||
            !feature(p.serverLevel(),WhisperRules.HAUNTED_PAINTINGS))return;
        boolean looking=p.isShiftKeyDown() || p.getMainHandItem().is(DeathWhisper.FIELD_JOURNAL.get());
        if(!looking && p.getRandom().nextInt(5)!=0)return;
        Haunting site=create(p.serverLevel(),painting.blockPosition());
        if(site==null)return;
        record(p,site,site.ghost.has(GhostProfile.INK)?GhostProfile.INK:0);
        if(p.serverLevel().random.nextInt(100)<Mth.clamp(p.serverLevel().getGameRules().getInt(WhisperRules.PAINTING_MANIFEST_CHANCE),0,100))
            manifest(p.serverLevel(),site,p);
        p.serverLevel().sendParticles(ParticleTypes.SQUID_INK,painting.getX(),painting.getY()+.7,painting.getZ(),12,.3,.45,.3,.02);
        p.serverLevel().sendParticles(ParticleTypes.SOUL,painting.getX(),painting.getY()+.6,painting.getZ(),8,.4,.3,.4,.01);
        p.serverLevel().playSound(null,painting.blockPosition(),SoundEvents.SCULK_SHRIEKER_SHRIEK,
            SoundSource.AMBIENT,.25f,.8f);
        p.sendSystemMessage(Component.literal("§5The painted eyes seem to follow you. A haunting has been registered."));
    }
    @SubscribeEvent public void onClone(PlayerEvent.Clone event){
        if(event.getEntity() instanceof ServerPlayer p && event.getOriginal() instanceof ServerPlayer old)
            p.getPersistentData().put(DATA,old.getPersistentData().getCompound(DATA).copy());
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        ticks++;
        MinecraftServer server=event.getServer();
        if(ticks%10==0){
            rituals.entrySet().removeIf(entry->{
                Seance ritual=entry.getValue();
                ServerLevel level=server.getLevel(ritual.site.dimension);
                if(level==null||ticks>ritual.expires || !enabled(level))return true;
                BlockPos at=ritual.site.focus;
                if(!level.hasChunkAt(at))return true;
                ritual.members.removeIf(id->{
                    ServerPlayer player=server.getPlayerList().getPlayer(id);
                    return player==null || !player.isAlive() || player.level()!=level ||
                        player.distanceToSqr(at.getX()+.5,at.getY()+1,at.getZ()+.5)>64;
                });
                if(ritual.finishing>=0 && ritual.members.size()<2)return true;
                level.sendParticles(ParticleTypes.SOUL,at.getX()+.5,at.getY()+1.1,at.getZ()+.5,
                   5,1.9,.15,1.9,.02);
                if(ritual.finishing>=0 && ticks>=ritual.finishing){
                    for(UUID id:ritual.members){
                        ServerPlayer witness=server.getPlayerList().getPlayer(id);
                        if(witness==null)continue;
                        // Two different evidence events ensure every co-op ritual matters.
                        int clues=ritual.site.ghost.evidence;
                        int first=Integer.lowestOneBit(clues);
                        record(witness,ritual.site,first);
                        witness.sendSystemMessage(Component.literal("§dA presence has answered the ritual. Your journal has been updated."));
                    }
                    level.playSound(null,at,DeathWhisper.WHISTLE_LOW.get(),SoundSource.AMBIENT,1.3f,.62f);
                    if(level.random.nextInt(100)<50){
                        for(UUID member:ritual.members){ServerPlayer p=server.getPlayerList().getPlayer(member);
                            if(p!=null){manifest(level,ritual.site,p);break;}
                        }
                    }
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,at.getX()+.5,at.getY()+1,at.getZ()+.5,35,2,1.3,2,.04);
                    return true;
                }
                return false;
            });
        }
        if(ticks%20==0){
            sites.entrySet().removeIf(e->ticks-e.getValue().lastActive>20L*60*40);
            if(rituals.isEmpty() && sites.isEmpty() && server.getPlayerList().getPlayers().isEmpty())return;
            for(Haunting site:sites.values()){
                ServerLevel hauntedLevel=server.getLevel(site.dimension);
                if(hauntedLevel==null || !hauntedLevel.hasChunkAt(site.focus))continue;
                if(ticks%100==0 && server.getLevel(site.dimension).random.nextInt(5)==0){
                    ServerLevel level=server.getLevel(site.dimension);
                    level.sendParticles(ParticleTypes.ASH,site.focus.getX()+.5,site.focus.getY()+1,site.focus.getZ()+.5,
                       3,.9,.9,.9,.005);
                }
            }
            for(ServerPlayer p:server.getPlayerList().getPlayers()){
                ServerLevel level=p.serverLevel();
                if(!feature(level,WhisperRules.FOOTPRINTS_ENABLED) || p.isSpectator() || p.isCreative())continue;
                long next=nextFootstep.getOrDefault(p.getUUID(),ticks+100);
                if(!nextFootstep.containsKey(p.getUUID()))nextFootstep.put(p.getUUID(),next);
                if(ticks<next)continue;
                long wait=20L*Mth.clamp(level.getGameRules().getInt(WhisperRules.FOOTPRINT_INTERVAL),30,900);
                nextFootstep.put(p.getUUID(),ticks+wait);
                if(trails.size()>=10 || level.random.nextInt(100)>=
                   Mth.clamp(level.getGameRules().getInt(WhisperRules.FOOTPRINT_CHANCE),0,100))continue;
                // Small footsteps behind the player's facing direction, always in loaded chunks.
                Vec3 backwards=p.getLookAngle().multiply(-1,0,-1).normalize();
                if(backwards.lengthSqr()<.1)backwards=new Vec3(0,0,1);
                Vec3 side=new Vec3(-backwards.z,0,backwards.x);
                List<Vec3> positions=new ArrayList<>();
                for(int i=0;i<7;i++){
                    Vec3 origin=p.position().add(backwards.scale(4+i*.67)).add(side.scale(i%2==0?.23:-.23));
                    BlockPos foot=BlockPos.containing(origin);
                    if(!level.hasChunkAt(foot))continue;
                    BlockPos base=null;
                    for(int dy=2;dy>=-3;dy--){
                        BlockPos at=foot.offset(0,dy,0);
                        if(level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolidRender(level,at.below())){
                            base=at;break;
                        }
                    }
                    if(base!=null)positions.add(new Vec3(origin.x,base.getY()+.05,origin.z));
                }
                if(!positions.isEmpty()){
                    trails.put(p.getUUID(),new FootTrail(level.dimension(),p.getUUID(),positions,ticks+20L*10));
                    Vec3 foot=positions.get(positions.size()-1);
                    level.playSound(null,BlockPos.containing(foot),DeathWhisper.FOOTSTEPS.get(),SoundSource.AMBIENT,.55f,.7f);
                    Haunting nearby=nearby(p,24);
                    if(nearby!=null && nearby.ghost.has(GhostProfile.INK))record(p,nearby,GhostProfile.INK);
                }
            }
        }
        if(ticks%6==0){
            trails.entrySet().removeIf(entry->{
                FootTrail trail=entry.getValue();
                ServerPlayer viewer=server.getPlayerList().getPlayer(trail.viewer);
                if(viewer==null||viewer.level().dimension()!=trail.dimension||ticks>=trail.expires)return true;
                ServerLevel level=viewer.serverLevel();
                for(Vec3 at:trail.positions){
                    level.sendParticles(viewer,ParticleTypes.ENCHANTED_HIT,false,at.x,at.y,at.z,2,.07,0,.15,0);
                    level.sendParticles(viewer,ParticleTypes.SOUL,false,at.x,at.y,at.z,1,.02,.01,.02,0);
                }
                return false;
            });
        }
    }
}
