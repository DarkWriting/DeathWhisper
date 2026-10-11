package com.example.deathwhisper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Use a true Minecraft player model: skin UV, outer hat/jacket/sleeves, and both arm widths. */
@net.neoforged.fml.common.EventBusSubscriber(modid=DeathWhisper.ID,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
    value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup(){}
    public static final ModelLayerLocation WIDE=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("deathwhisper","mirror_wide"),"main");
    public static final ModelLayerLocation SLIM=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("deathwhisper","mirror_slim"),"main");
    @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WIDE,()->MirrorModel.createLayer(false));
        event.registerLayerDefinition(SLIM,()->MirrorModel.createLayer(true));
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeathWhisper.WATCHER.get(),MirrorRenderer::new);
    }
    public static final class MirrorRenderer extends MobRenderer<WatcherEntity,MirrorModel> {
        private static final ResourceLocation GLOW=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher_eyes.png");
        private final MirrorModel wide,slim;
        public MirrorRenderer(EntityRendererProvider.Context context) {
            super(context,new MirrorModel(context.bakeLayer(WIDE),false),.35f);
            wide=this.model;
            slim=new MirrorModel(context.bakeLayer(SLIM),true);
            this.addLayer(new EyesLayer<WatcherEntity,MirrorModel>(this) {
                @Override public RenderType renderType() {return RenderType.eyes(GLOW);}
            });
        }
        @Override public boolean shouldRender(WatcherEntity watcher,
             net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z) {
            var self=Minecraft.getInstance().player;
            // Local-only visual: the server still tracks the entity, but bystanders don't draw it.
            if(watcher.isPrivateFocus() && (self==null || !self.getUUID().equals(watcher.getSkinTarget())))
                return false;
            return super.shouldRender(watcher,frustum,x,y,z);
        }
        @Override public ResourceLocation getTextureLocation(WatcherEntity watcher) {
            return MirrorSkinCache.resolve(watcher.getSkinTarget());
        }
        @Override public void render(WatcherEntity watcher,float yaw,float frame,
             com.mojang.blaze3d.vertex.PoseStack pose,
             net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
            this.model=slimFor(watcher) ? slim : wide;
            super.render(watcher,yaw,frame,pose,buffers,light);
        }
        private boolean slimFor(WatcherEntity watcher) {
            var id=watcher.getSkinTarget();
            var conn=Minecraft.getInstance().getConnection();
            if(id==null || conn==null)return false;
            var info=conn.getPlayerInfo(id);
            return info!=null && info.getSkin().model()==PlayerSkin.Model.SLIM;
        }
    }
}
