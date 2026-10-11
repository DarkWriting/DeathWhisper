package com.example.deathwhisper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import java.util.*;

/**
 * Security network uses real vanilla iron doors, not fabricated camera screens.
 * Sneak-click an iron door to register it, then open a clickable access panel
 * (right-click air) from which you can scan or traverse linked, loaded doors.
 */
public final class SecurityDoorController extends Item {
    private static final String KEY="deathwhisper_security_doors";
    public SecurityDoorController(Properties p){super(p);}
    private static ListTag list(ServerPlayer p){return p.getPersistentData().getList(KEY,Tag.TAG_COMPOUND);}
    public static boolean holding(ServerPlayer p){
        return p.getMainHandItem().is(DeathWhisper.SECURITY_CONTROLLER.get())
            ||p.getOffhandItem().is(DeathWhisper.SECURITY_CONTROLLER.get());
    }
    @Override public InteractionResult useOn(UseOnContext ctx){
        if(ctx.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(ctx.getPlayer() instanceof ServerPlayer p))return InteractionResult.PASS;
        BlockPos pos=ctx.getClickedPos();
        if(!ctx.getLevel().getBlockState(pos).is(Blocks.IRON_DOOR)){
            p.displayClientMessage(Component.literal("This controller only links iron doors."),true);
            return InteractionResult.PASS;
        }
        if(!p.isShiftKeyDown()){
            showPanel(p);
            return InteractionResult.SUCCESS;
        }
        if(!p.level().getGameRules().getBoolean(WhisperRules.SECURITY_ENABLED))return InteractionResult.FAIL;
        ListTag saved=list(p);
        String dimension=p.level().dimension().location().toString();
        for(int i=0;i<saved.size();i++){
            CompoundTag item=saved.getCompound(i);
            if(item.getString("dimension").equals(dimension) && item.getInt("x")==pos.getX() &&
               item.getInt("z")==pos.getZ() && Math.abs(item.getInt("y")-pos.getY())<=1){
                p.displayClientMessage(Component.literal("Already registered as door "+(i+1)),true);
                return InteractionResult.SUCCESS;
            }
        }
        if(saved.size()>=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SECURITY_MAX_DOORS),2,12)){
            p.displayClientMessage(Component.literal("Controller network is full."),true);
            return InteractionResult.FAIL;
        }
        CompoundTag door=new CompoundTag();
        door.putString("dimension",dimension);
        door.putInt("x",pos.getX());door.putInt("y",pos.getY());door.putInt("z",pos.getZ());
        saved.add(door);
        p.getPersistentData().put(KEY,saved);
        p.displayClientMessage(Component.literal("Registered security door "+saved.size()+". Right-click air for panel."),false);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(held);
        if(player instanceof ServerPlayer p)showPanel(p);
        return InteractionResultHolder.success(held);
    }
    public static void showPanel(ServerPlayer p) {
        if(!holding(p)){p.displayClientMessage(Component.literal("Hold a security controller."),true);return;}
        if(!p.level().getGameRules().getBoolean(WhisperRules.SECURITY_ENABLED)){
            p.displayClientMessage(Component.literal("Security network disabled."),true);return;
        }
        ListTag saved=list(p);
        p.sendSystemMessage(Component.literal("§8[§5Security Door Network§8] §7"+saved.size()+" linked doors"));
        if(saved.isEmpty()){
            p.sendSystemMessage(Component.literal("Sneak-right-click an iron door to register it."));
            return;
        }
        for(int i=0;i<saved.size();i++){
            int n=i+1;CompoundTag d=saved.getCompound(i);
            String xyz=d.getInt("x")+" "+d.getInt("y")+" "+d.getInt("z");
            Component prefix=Component.literal("Door "+n+" ("+xyz+") ");
            Component scan=Component.literal("[SCAN]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/deathwhispersecurity scan "+n)));
            Component travel=Component.literal(" [TRAVEL]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/deathwhispersecurity travel "+n)));
            p.sendSystemMessage(prefix.copy().append(scan).append(travel));
        }
        p.sendSystemMessage(Component.literal("Travel: stand near any registered iron door in the same dimension."));
    }
    public static int scan(ServerPlayer p,int index) {
        if(!holding(p)||!p.level().getGameRules().getBoolean(WhisperRules.SECURITY_ENABLED))return 0;
        ListTag saved=list(p);
        if(index<1||index>saved.size())return 0;
        CompoundTag d=saved.getCompound(index-1);
        if(!d.getString("dimension").equals(p.level().dimension().location().toString())){
            p.sendSystemMessage(Component.literal("Camera offline: different dimension."));return 0;
        }
        BlockPos pos=new BlockPos(d.getInt("x"),d.getInt("y"),d.getInt("z"));
        if(!p.level().hasChunkAt(pos)){
            p.sendSystemMessage(Component.literal("Camera offline: destination chunk is unloaded."));return 0;
        }
        if(!p.level().getBlockState(pos).is(Blocks.IRON_DOOR)){
            p.sendSystemMessage(Component.literal("Camera offline: iron door missing."));return 0;
        }
        int radius=Mth.clamp(p.level().getGameRules().getInt(WhisperRules.SECURITY_SCAN_RADIUS),3,12);
        AABB box=new AABB(pos).inflate(radius);
        int players=p.serverLevel().getEntitiesOfClass(ServerPlayer.class,box).size();
        int monsters=p.serverLevel().getEntitiesOfClass(Monster.class,box).size();
        boolean open=p.level().getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
        p.sendSystemMessage(Component.literal("§dDoor "+index+" snapshot: "+(open?"OPEN":"CLOSED")
            +" | players "+players+" | hostiles "+monsters+" | radius "+radius+" blocks."));
        return 1;
    }
    public static int travel(ServerPlayer p,int index) {
        if(!holding(p)||!p.level().getGameRules().getBoolean(WhisperRules.SECURITY_ENABLED))return 0;
        ListTag saved=list(p);
        if(!p.level().getGameRules().getBoolean(WhisperRules.SECURITY_TRAVEL)){
            p.sendSystemMessage(Component.literal("Security travel disabled. Enable deathWhisperSecurityTravel after verifying protected claims."));
            return 0;
        }
        if(saved.size()<2||index<1||index>saved.size())return 0;
        String dimension=p.level().dimension().location().toString();
        boolean near=false;
        for(int i=0;i<saved.size();i++){
            CompoundTag b=saved.getCompound(i);
            if(!b.getString("dimension").equals(dimension))continue;
            BlockPos src=new BlockPos(b.getInt("x"),b.getInt("y"),b.getInt("z"));
            if(p.distanceToSqr(src.getX()+.5,src.getY()+.5,src.getZ()+.5)<25 &&
              p.level().hasChunkAt(src) && p.level().getBlockState(src).is(Blocks.IRON_DOOR)){
                near=true;break;
            }
        }
        if(!near){p.sendSystemMessage(Component.literal("Travel denied: stand within five blocks of a linked iron door."));return 0;}
        CompoundTag d=saved.getCompound(index-1);
        if(!d.getString("dimension").equals(dimension)){
            p.sendSystemMessage(Component.literal("Travel denied: doors must be in the same dimension."));return 0;
        }
        BlockPos pos=new BlockPos(d.getInt("x"),d.getInt("y"),d.getInt("z"));
        if(!p.level().hasChunkAt(pos)||!p.level().getBlockState(pos).is(Blocks.IRON_DOOR)){
            p.sendSystemMessage(Component.literal("Travel denied: destination unloaded or destroyed."));return 0;
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            BlockPos stand=pos.offset(dx,0,dz);
            if(!p.level().hasChunkAt(stand))continue;
            if(p.level().getBlockState(stand).isAir()&&p.level().getBlockState(stand.above()).isAir()
                &&p.level().getBlockState(stand.below()).isSolidRender(p.level(),stand.below())){
                p.teleportTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);
                p.fallDistance=0;
                p.displayClientMessage(Component.literal("Security transit complete."),true);
                return 1;
            }
        }
        p.sendSystemMessage(Component.literal("Travel denied: no safe landing space."));return 0;
    }
}
