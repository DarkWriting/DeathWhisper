package com.example.deathwhisper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/** Instruments all delegate to one per-world investigation director. No terrain edits. */
public final class InvestigationItem extends Item {
    public enum Tool { CHALK, JOURNAL, EMF, SPIRIT_RADIO, THERMOMETER }
    private final Tool tool;
    public InvestigationItem(Properties properties,Tool tool){super(properties);this.tool=tool;}
    @Override public InteractionResult useOn(UseOnContext context){
        if(tool!=Tool.CHALK)return super.useOn(context);
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(context.getPlayer() instanceof ServerPlayer p)
            return DeathWhisper.INVESTIGATIONS.joinSeance(p,context.getClickedPos())?InteractionResult.SUCCESS:InteractionResult.FAIL;
        return InteractionResult.FAIL;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer p))return InteractionResultHolder.fail(stack);
        if(tool==Tool.CHALK){
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("Use ritual chalk on the same block with another player to begin a seance."),true);
            return InteractionResultHolder.success(stack);
        }
        if(!p.level().getGameRules().getBoolean(WhisperRules.INVESTIGATIONS_ENABLED))
            return InteractionResultHolder.fail(stack);
        if(p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        boolean success=tool==Tool.JOURNAL
          ? DeathWhisper.INVESTIGATIONS.showJournal(p)
          : DeathWhisper.INVESTIGATIONS.measure(p,tool);
        if(success && tool!=Tool.JOURNAL)p.getCooldowns().addCooldown(this,35);
        return success?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
    }
}
