package com.example.deathwhisper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class ChainPuzzleBox extends Item {
    public ChainPuzzleBox(Properties props) {super(props);}
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide) return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer server))return InteractionResultHolder.pass(stack);
        if(server.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        int hit=DeathWhisper.POWERS.fireChains(server);
        if(hit==0) {
            server.displayClientMessage(Component.literal("No target in sight"),true);
            return InteractionResultHolder.fail(stack);
        }
        server.getCooldowns().addCooldown(this,
            Mth.clamp(level.getGameRules().getInt(WhisperRules.CHAINS_COOLDOWN),1,180)*20);
        server.displayClientMessage(Component.literal("Chains summoned: "+hit),true);
        return InteractionResultHolder.success(stack);
    }
}
