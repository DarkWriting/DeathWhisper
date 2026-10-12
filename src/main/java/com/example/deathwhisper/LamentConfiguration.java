package com.example.deathwhisper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Separate puzzle-object counterplay to the gateway caster item.
 * Sneak and use, then keep sneaking near the starting spot for six seconds.
 */
public final class LamentConfiguration extends Item {
    public LamentConfiguration(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(held);
        if(!(player instanceof ServerPlayer server) || !server.isShiftKeyDown())return InteractionResultHolder.fail(held);
        boolean started=DeathWhisper.CENOBITE.startSolving(server);
        return started?InteractionResultHolder.success(held):InteractionResultHolder.fail(held);
    }
}
