package com.example.deathwhisper;

import net.minecraft.client.renderer.entity.ZombieRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

@net.neoforged.fml.common.EventBusSubscriber(modid = DeathWhisper.ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeathWhisper.WATCHER.get(), WatcherRenderer::new);
    }
    public static final class WatcherRenderer extends ZombieRenderer {
        private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("deathwhisper", "textures/entity/watcher.png");
        public WatcherRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public void render(net.minecraft.world.entity.monster.Zombie zombie, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
            WatcherEntity watcher = (WatcherEntity) zombie;
            poseStack.pushPose();
            if (watcher.isPeeking()) {
                // Brief side lean toward the visible edge of cover; body returns upright on retreat.
                float direction = watcher.peekSide();
                poseStack.translate(direction * 0.17f, 0, 0);
                poseStack.translate(0, 0.95, 0);
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 13));
                poseStack.translate(0, -0.95, 0);
            }
            super.render(zombie, yaw, partialTick, poseStack, buffers, packedLight);
            poseStack.popPose();
        }
        @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return SKIN; }
    }
}
