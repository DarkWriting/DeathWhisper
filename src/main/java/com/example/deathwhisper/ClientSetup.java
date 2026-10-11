package com.example.deathwhisper;

import net.minecraft.client.renderer.entity.ZombieRenderer;
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
        @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return SKIN; }
    }
}
