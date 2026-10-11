package com.example.deathwhisper;

import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Loads only public Mojang textures.minecraft.net skin URLs from the player's signed game profile.
 * Downloads off-thread and uploads on the render thread. Falls back to the test texture.
 */
public final class InvertedSkins {
    private static final ResourceLocation FALLBACK=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher.png");
    private static final Map<UUID,ResourceLocation> SKINS=new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> IN_FLIGHT=ConcurrentHashMap.newKeySet();
    private InvertedSkins(){}
    public static ResourceLocation skin(UUID target) {
        if(target==null)return FALLBACK;
        var existing=SKINS.get(target);
        if(existing!=null)return existing;
        if(!IN_FLIGHT.add(target))return FALLBACK;
        Minecraft mc=Minecraft.getInstance();
        var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(target);
        if(info==null){IN_FLIGHT.remove(target);return FALLBACK;}
        Property property=info.getProfile().getProperties().get("textures").stream().findFirst().orElse(null);
        if(property==null){IN_FLIGHT.remove(target);return FALLBACK;}
        try {
            String json=new String(Base64.getDecoder().decode(property.value()),java.nio.charset.StandardCharsets.UTF_8);
            String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                 .getAsJsonObject("SKIN").get("url").getAsString();
            URI uri=URI.create(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) {
                IN_FLIGHT.remove(target);return FALLBACK;
            }
            Thread.ofVirtual().start(()->{
                try {
                    var conn=uri.toURL().openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    try(var input=conn.getInputStream(); NativeImage img=NativeImage.read(input)) {
                        int w=img.getWidth(),h=img.getHeight();
                        if(w!=64 || (h!=64 && h!=32))return;
                        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                            int pixel=img.getPixelRGBA(x,y);
                            // NativeImage RGBA bytes are ABGR packed; flip RGB, retain alpha.
                            img.setPixelRGBA(x,y,(pixel & 0xFF000000) | (~pixel & 0x00FFFFFF));
                        }
                        NativeImage copy=new NativeImage(w,h,true);
                        copy.copyFrom(img);
                        mc.execute(()->{
                            DynamicTexture texture=new DynamicTexture(copy);
                            ResourceLocation name=ResourceLocation.fromNamespaceAndPath("deathwhisper","inverted_"+target.toString().replace("-",""));
                            mc.getTextureManager().register(name,texture);
                            SKINS.put(target,name);
                            IN_FLIGHT.remove(target);
                        });
                    }
                }catch(Exception ignored){ IN_FLIGHT.remove(target); }
            });
        }catch(Exception ignored){IN_FLIGHT.remove(target);}
        return FALLBACK;
    }
}
