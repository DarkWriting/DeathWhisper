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
    @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WolfModel.LAYER,WolfModel::createLayer);
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeathWhisper.WATCHER.get(),WatcherRenderer::new);
    }
    public static final class WatcherRenderer extends ZombieRenderer {
        private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher.png");
        private static final ResourceLocation GLOW=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher_eyes.png");
        public WatcherRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.model=new WolfModel(context.bakeLayer(WolfModel.LAYER));
            this.addLayer(new EyesLayer<net.minecraft.world.entity.monster.Zombie,ZombieModel<net.minecraft.world.entity.monster.Zombie>>(this) {
                @Override public RenderType renderType() { return RenderType.eyes(GLOW); }
            });
        }
        @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return SKIN; }
    }
}
