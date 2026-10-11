package com.example.deathwhisper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class GhostHuntKit extends Item {
    public GhostHuntKit(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer p))return InteractionResultHolder.fail(stack);
        return DeathWhisper.INVESTIGATIONS.beginHunt(p)
             ?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
    }
}
