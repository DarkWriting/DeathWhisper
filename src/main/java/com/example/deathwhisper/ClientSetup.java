package com.example.deathwhisper;

import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid=DeathWhisper.ID,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
    value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup(){}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeathWhisper.WATCHER.get(),WatcherRenderer::new);
    }
    public static final class WatcherRenderer extends ZombieRenderer {
        private static final ResourceLocation FALLBACK=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher.png");
        public WatcherRenderer(EntityRendererProvider.Context context) {super(context);}
        @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie zombie) {
            return zombie instanceof WatcherEntity w ? MirrorSkinCache.resolve(w.getSkinTarget()) : FALLBACK;
        }
        @Override public void render(net.minecraft.world.entity.monster.Zombie zombie,float yaw,float frame,
             com.mojang.blaze3d.vertex.PoseStack pose,
             net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
            // Existing sidelong peek. No custom wolf geometry: a faithful dark mirror of its victim.
            pose.pushPose();
            if(zombie instanceof WatcherEntity w && w.isPeeking()) {
                int direction=w.peekSide();
                pose.translate(direction*.18f,0,0);
                pose.translate(0,.95,0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(direction*13));
                pose.translate(0,-.95,0);
            }
            super.render(zombie,yaw,frame,pose,buffers,light);
            pose.popPose();
        }
    }
}
