package com.example.deathwhisper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BlightSerum extends Item {
    public BlightSerum(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack item=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(item);
        if(!(player instanceof ServerPlayer server))return InteractionResultHolder.pass(item);
        if(server.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(item);
        if(!DeathWhisper.POWERS.useSerum(server))return InteractionResultHolder.fail(item);
        server.getCooldowns().addCooldown(this,7);
        return InteractionResultHolder.success(item);
    }
}
