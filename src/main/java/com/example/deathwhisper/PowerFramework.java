package com.example.deathwhisper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Shared power framework: one server-side activation point, cooldowns, safe
 * target validation, deterministic tick callbacks and damage hooks.
 */
public final class PowerFramework {
    private final Map<String, SupernaturalAbility> registered=new LinkedHashMap<>();
    private final Map<String, Map<UUID, Long>> cooldownEnds=new LinkedHashMap<>();
    private final HorrorPowers existing;

    public PowerFramework(HorrorPowers existing) {
        this.existing=existing;
        register(new SupernaturalAbility() {
            public String id(){return "chain_puzzle_box";}
            public boolean activate(ServerPlayer p){return existing.fireChains(p)>0;}
            public int cooldownTicks(ServerPlayer p){
                return Math.max(1,p.level().getGameRules().getInt(WhisperRules.CHAINS_COOLDOWN))*20;
            }
        });
        register(new SupernaturalAbility() {
            public String id(){return "blight_serum";}
            public boolean activate(ServerPlayer p){return existing.useSerum(p);}
            public int cooldownTicks(ServerPlayer p){
                return 20*Math.max(5,p.level().getGameRules().getInt(WhisperRules.RUSH_COOLDOWN));
            }
            public boolean hasRecastStage(){return true;}
            public boolean allowRecastOnCooldown(ServerPlayer p){return existing.canRecastSerum(p);}
        });
        register(new GhostPower());
        register(new ArtistPower());
        register(new LivingHarpoonPower());
        register(new SlasherPower());
    }
    public void register(SupernaturalAbility ability){
        if(registered.putIfAbsent(ability.id(),ability)!=null)
            throw new IllegalArgumentException("Duplicate power: "+ability.id());
        cooldownEnds.put(ability.id(),new java.util.HashMap<>());
    }
    public boolean activate(ServerPlayer player, Item item, String abilityId){
        if(player.isSpectator() || !player.isAlive() ||
            !player.serverLevel().getGameRules().getBoolean(WhisperRules.POWERS_ENABLED))return false;
        SupernaturalAbility ability=registered.get(abilityId);
        if(ability==null)return false;
        long now=player.serverLevel().getServer().overworld().getGameTime();
        Long expires=cooldownEnds.get(abilityId).get(player.getUUID());

        boolean recast=ability.allowRecastOnCooldown(player);
        if(expires!=null && expires>now && !recast)return false;
        if(player.getCooldowns().isOnCooldown(item) && !recast)return false;
        if(!ability.activate(player))return false;
        if(!recast) {
            int ticks=Math.max(1,ability.cooldownTicks(player));
            cooldownEnds.get(abilityId).put(player.getUUID(),now+ticks);
            player.getCooldowns().addCooldown(item,ability.hasRecastStage()?8:ticks);
        } else if(expires!=null && expires>now) {
            player.getCooldowns().addCooldown(item,(int)Math.max(1,expires-now));
        }
        return true;
    }
    public String status(ServerPlayer player){
        long now=player.serverLevel().getServer().overworld().getGameTime();
        java.util.StringJoiner join=new java.util.StringJoiner(", ","Powers: ","");
        for(String name:registered.keySet()){
            long left=Math.max(0,cooldownEnds.get(name).getOrDefault(player.getUUID(),0L)-now);
            join.add(name+"="+(left==0?"ready":(left/20)+"s"));
        }
        return join.toString();
    }
    public static boolean canAffect(ServerPlayer source, LivingEntity target){
        if(target==source || target instanceof WatcherEntity || target instanceof GhostApparition
            || !target.isAlive() || target.isSpectator())
            return false;
        if(target instanceof ServerPlayer player)
            return source.serverLevel().getGameRules().getBoolean(WhisperRules.POWERS_PVP)
                && !player.isSpectator() && !player.isCreative();
        return true;
    }
    @SubscribeEvent public void serverTick(ServerTickEvent.Post e){
        if(e.getServer().getTickCount()%2==0)
            for(SupernaturalAbility ability:registered.values())ability.serverTick(e.getServer());
        if(e.getServer().getTickCount()%1200==0) {
            long now=e.getServer().overworld().getGameTime();
            for(var map:cooldownEnds.values())map.entrySet().removeIf(x->x.getValue()<=now);
        }
    }
    @SubscribeEvent public void beforeDamage(LivingDamageEvent.Pre e){
        for(SupernaturalAbility ability:registered.values())ability.beforeDamage(e);
    }
}
