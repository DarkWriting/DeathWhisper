package com.example.deathwhisper;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Inverted original player skin, cached per Mojang texture URL.
 * Work is asynchronous; the render thread only registers completed textures.
 * No remote URL may be supplied by the mod server: URL comes from the client's own authenticated player list.
 */
public final class MirrorSkinCache {
    private MirrorSkinCache(){}
    private static final ResourceLocation FALLBACK=ResourceLocation.fromNamespaceAndPath("deathwhisper","textures/entity/watcher.png");
    private static final Map<String,ResourceLocation> READY=new ConcurrentHashMap<>();
    private static final Map<String,Boolean> PENDING=new ConcurrentHashMap<>();
    private static final Map<String,Long> RETRY_AFTER=new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2,task->{
        Thread t=new Thread(task,"deathwhisper-skin");
        t.setDaemon(true);
        return t;
    });
    public static ResourceLocation resolve(UUID target) {
        if(target==null)return FALLBACK;
        Minecraft mc=Minecraft.getInstance();
        if(mc.getConnection()==null)return FALLBACK;
        var info=mc.getConnection().getPlayerInfo(target);
        if(info==null)return FALLBACK;
        PlayerSkin skin=info.getSkin();
        if(skin==null || skin.textureUrl()==null)return FALLBACK;
        String url=skin.textureUrl();
        ResourceLocation ready=READY.get(url);
        if(ready!=null)return ready;
        if(!PENDING.containsKey(url) && PENDING.size()<32 && READY.size()<128 && trusted(url)
             && System.currentTimeMillis()>=RETRY_AFTER.getOrDefault(url,0L)) {
            if(PENDING.putIfAbsent(url,true)==null) {
                CompletableFuture.runAsync(()->download(url),WORKERS);
            }
        }
        return FALLBACK;
    }
    private static boolean trusted(String url) {
        try {
            URI uri=URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && "textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo()==null && uri.getPort()==-1
                && uri.getPath().matches("/texture/[0-9a-fA-F]{32,128}")
                && uri.getRawQuery()==null && uri.getRawFragment()==null;
        } catch(Exception e){return false;}
    }
    private static void download(String url) {
        try {
            // Some Minecraft skin records use HTTP URLs. Fetch the validated Mojang host only over HTTPS.
            URI original=URI.create(url);
            HttpURLConnection conn=(HttpURLConnection)URI.create("https://textures.minecraft.net"+original.getRawPath()).toURL().openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","DeathWhisper/0.2 MinecraftSkin");
            int code=conn.getResponseCode();
            if(code!=200)throw new IllegalStateException("Skin HTTP "+code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(var input=conn.getInputStream()){
                byte[] buffer=new byte[8192];
                int len;while((len=input.read(buffer))!=-1){
                    if(bytes.size()+len>1024*1024)throw new IllegalStateException("Skin is too large");
                    bytes.write(buffer,0,len);
                }
            } finally {conn.disconnect();}
            try(NativeImage image=NativeImage.read(new ByteArrayInputStream(bytes.toByteArray()))) {
                if(image.getWidth()!=64 || (image.getHeight()!=64 && image.getHeight()!=32))
                    throw new IllegalStateException("Unexpected skin size");
                NativeImage inverted=new NativeImage(64,64,false);
                // Old 64x32 skins need a valid 64x64 fallback for empty lower half.
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                    int pixel=y<image.getHeight()?image.getPixelRGBA(x,y):0;
                    inverted.setPixelRGBA(x,y,(pixel & 0xFF000000)|((~pixel)&0x00FFFFFF));
                }
                Minecraft.getInstance().execute(()->{
                    if(READY.size()>=128){inverted.close();PENDING.remove(url);return;}
                    try {
                        ResourceLocation location=Minecraft.getInstance().getTextureManager().register("deathwhisper_inverted",new DynamicTexture(inverted));
                        READY.put(url,location);
                    } catch(Exception e){ inverted.close(); }
                    finally {PENDING.remove(url);}
                });
            }
        } catch(Exception e) {
            // Offline clients and unavailable public Mojang skins fall back to a built-in distorted test skin.
            RETRY_AFTER.put(url,System.currentTimeMillis()+300000L);
            PENDING.remove(url);
        }
    }
}
