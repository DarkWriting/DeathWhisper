package com.example.deathwhisper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Every active artefact uses the same execution, validation and cooldown path. */
public class PowerItem extends Item {
    private final String abilityId;
    public PowerItem(Properties properties,String abilityId) {
        super(properties);
        this.abilityId=abilityId;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack item=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(item);
        if(!(player instanceof ServerPlayer server))return InteractionResultHolder.fail(item);
        return DeathWhisper.FRAMEWORK.activate(server,this,abilityId)
             ? InteractionResultHolder.success(item) : InteractionResultHolder.fail(item);
    }
}
