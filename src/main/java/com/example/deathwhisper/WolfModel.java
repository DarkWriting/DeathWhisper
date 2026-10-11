package com.example.deathwhisper;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Built-in animated lupine head, snout, ears, tail and tattered mantle. No GeckoLib dependency. */
public final class WolfModel extends ZombieModel<Zombie> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("deathwhisper","wolf"),"main");
    private final ModelPart tail;
    private final ModelPart mantle;
    public WolfModel(ModelPart root) {
        super(root);
        this.tail=root.getChild("body").getChild("tail");
        this.mantle=root.getChild("body").getChild("mantle");
    }
    public static LayerDefinition createLayer() {
        MeshDefinition mesh=net.minecraft.client.model.HumanoidModel.createMesh(CubeDeformation.NONE,0.0f);
        PartDefinition root=mesh.getRoot();
        PartDefinition head=root.getChild("head");
        head.addOrReplaceChild("muzzle",CubeListBuilder.create()
          .texOffs(0,48).addBox(-3.0f,-4.5f,-7.0f,6.0f,3.0f,3.0f),PartPose.ZERO);
        head.addOrReplaceChild("ear_left",CubeListBuilder.create()
          .texOffs(18,48).addBox(2.5f,-11.0f,-1.0f,2.0f,4.0f,2.0f),PartPose.ZERO);
        head.addOrReplaceChild("ear_right",CubeListBuilder.create()
          .texOffs(26,48).addBox(-4.5f,-11.0f,-1.0f,2.0f,4.0f,2.0f),PartPose.ZERO);
        PartDefinition body=root.getChild("body");
        body.addOrReplaceChild("mantle",CubeListBuilder.create()
          .texOffs(32,32).addBox(-5.0f,-0.1f,2.1f,10.0f,15.0f,2.0f),PartPose.ZERO);
        body.addOrReplaceChild("tail",CubeListBuilder.create()
          .texOffs(32,50).addBox(-1.6f,6.0f,3.0f,3.0f,10.0f,3.0f),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(Zombie zombie,float limbSwing,float limbSwingAmount,float ageInTicks,
             float netHeadYaw,float headPitch) {
        super.setupAnim(zombie,limbSwing,limbSwingAmount,ageInTicks,netHeadYaw,headPitch);
        WolfModel self=this;
        tail.xRot=(float)Math.sin(ageInTicks*.09)*.2f;
        mantle.xRot=(float)Math.sin(limbSwing*.43)*limbSwingAmount*.22f;
        if(zombie instanceof WatcherEntity w && w.isPeeking()) {
            int side=w.peekSide();
            // Head and upper shoulders peek, hips and legs stay behind cover.
            head.x += side*3.2f;
            head.zRot=side*.29f;
            body.zRot=side*.09f;
            mantle.zRot=side*.09f;
            this.rightArm.zRot+=side*.06f;
            this.leftArm.zRot+=side*.06f;
        }
    }
}
