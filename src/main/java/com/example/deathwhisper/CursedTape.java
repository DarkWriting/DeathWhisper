package com.example.deathwhisper;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

public final class CursedTape extends Item {
    public CursedTape(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack tape=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(tape);
        if(!(player instanceof ServerPlayer p))return InteractionResultHolder.fail(tape);
        if(p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(tape);
        CompoundTag tag=tape.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String phrase=tag.getString("GhostMessage");
        if(phrase.isEmpty())phrase="...unintelligible..."; // unlabelled tapes are only echoes
        p.sendSystemMessage(Component.literal("§5[Cursed Playback] §d"+phrase));
        p.serverLevel().playSound(null,p.blockPosition(),DeathWhisper.WHISTLE_LOW.get(),SoundSource.RECORDS,.7f,.57f);
        p.getCooldowns().addCooldown(this,60);
        return InteractionResultHolder.success(tape);
    }
}
