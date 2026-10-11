package com.example.deathwhisper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Placeable redstone sensor; checks only loaded local entities and schedules bounded ticks. */
public final class MotionSensorBlock extends Block {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public MotionSensorBlock(BlockBehaviour.Properties properties){
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(POWERED);
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!level.isClientSide && !old.is(this))level.scheduleTick(pos,this,10);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource rng){
        if(!level.getGameRules().getBoolean(WhisperRules.MOTION_SENSORS_ENABLED)){
            if(state.getValue(POWERED))level.setBlock(pos,state.setValue(POWERED,false),3);
            level.scheduleTick(pos,this,20);return;
        }
        int radius=Mth.clamp(level.getGameRules().getInt(WhisperRules.MOTION_SENSOR_RANGE),2,12);
        AABB box=new AABB(pos).inflate(radius);
        boolean detected=!level.getEntitiesOfClass(GhostApparition.class,box).isEmpty();
        if(!detected && level.getGameRules().getBoolean(WhisperRules.MOTION_SENSOR_PLAYERS))
            detected=level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,box,
                player->player.isAlive()&&!player.isSpectator()).size()>0;
        if(detected && !state.getValue(POWERED)){
            level.setBlock(pos,state.setValue(POWERED,true),3);
            level.updateNeighborsAt(pos,this);
            level.playSound(null,pos,SoundEvents.NOTE_BLOCK_PLING.value(),SoundSource.BLOCKS,.95f,1.65f);
            DeathWhisper.INVESTIGATIONS.sensorTriggered(level,pos);
        }else if(!detected && state.getValue(POWERED)){
            level.setBlock(pos,state.setValue(POWERED,false),3);
            level.updateNeighborsAt(pos,this);
        }
        level.scheduleTick(pos,this,10);
    }
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction direction){
        return state.getValue(POWERED)?15:0;
    }
}
