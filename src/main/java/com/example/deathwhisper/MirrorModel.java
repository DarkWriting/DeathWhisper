package com.example.deathwhisper;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;

/** Vanilla-compatible player shape, with torso and head leaning at the cover edge. */
public final class MirrorModel extends PlayerModel<WatcherEntity> {
    public MirrorModel(ModelPart root, boolean slim) {super(root,slim);}
    public static LayerDefinition createLayer(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64);
    }
    @Override public void setupAnim(WatcherEntity w,float limbSwing,float limbSwingAmount,float age,float yaw,float pitch) {
        super.setupAnim(w,limbSwing,limbSwingAmount,age,yaw,pitch);
        this.rightArm.xRot = w.isCharging() ? -1.1f : this.rightArm.xRot;
        this.leftArm.xRot = w.isCharging() ? -.9f : this.leftArm.xRot;
        if(w.isPeeking()) {
            float side=w.peekSide();
            float intensity=w.peekStyle()==0?.6f:w.peekStyle()==1?1.25f:1f;
            head.x+=side*2.2f*intensity;
            head.zRot=side*.28f*intensity;
            hat.x=head.x;
            hat.zRot=head.zRot;
            body.zRot=side*.09f*intensity;
            jacket.zRot=body.zRot;
            rightArm.zRot+=side*.05f;
            leftArm.zRot+=side*.05f;
        }
    }
}
