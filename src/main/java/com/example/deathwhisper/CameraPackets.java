package com.example.deathwhisper;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Camera session packets: no entity teleportation or chunk forcing. */
public final class CameraPackets {
    private CameraPackets(){}

    /** Modes: gateway, follow, security, close. Only server-validated camera positions are accepted. */
    public record View(String mode,int index,double x,double y,double z,float yaw,float pitch)
            implements CustomPacketPayload {
        public static final Type<View> TYPE=new Type<>(
            ResourceLocation.fromNamespaceAndPath(DeathWhisper.ID,"camera_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{
                buf.writeUtf(p.mode,24);buf.writeVarInt(p.index);
                buf.writeDouble(p.x);buf.writeDouble(p.y);buf.writeDouble(p.z);
                buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);
            },
            buf->new View(buf.readUtf(24),buf.readVarInt(),
                buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readFloat(),buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    /** Input is accepted only with a validated active camera session on the server. */
    public record Control(String action,float yaw,float pitch) implements CustomPacketPayload {
        public static final Type<Control> TYPE=new Type<>(
            ResourceLocation.fromNamespaceAndPath(DeathWhisper.ID,"camera_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Control> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUtf(p.action,16);buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);},
            buf->new Control(buf.readUtf(16),buf.readFloat(),buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    private record Session(String mode,int index,long expires,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension){}
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();

    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("camera-1");
        registrar.playToClient(View.TYPE,View.STREAM_CODEC,CameraPackets::onView);
        registrar.playToServer(Control.TYPE,Control.STREAM_CODEC,CameraPackets::onControl);
    }
    private static void onView(View packet,IPayloadContext context){
        // Screen and client camera state MUST be mutated on the client render thread.
        if(FMLEnvironment.dist==Dist.CLIENT)
            context.enqueueWork(()->ClientCamera.receive(packet));
    }
    private static void onControl(Control packet,IPayloadContext context){
        // Input packets arrive from networking; all world and session state is server-main-thread.
        context.enqueueWork(()->processControl(packet,context));
    }
    private static void processControl(Control packet,IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer player))return;
        if(!Float.isFinite(packet.yaw()) || !Float.isFinite(packet.pitch()))return;
        Session session=ACTIVE.get(player.getUUID());
        if(session==null)return;
        long now=player.getServer().overworld().getGameTime();
        if(now>session.expires || session.dimension!=player.level().dimension()
                || !player.isAlive()){
            close(player);return;
        }
        float yaw=Mth.wrapDegrees(packet.yaw());
        float pitch=Mth.clamp(packet.pitch(),-85,85);
        switch(packet.action()){
            case "close" -> {
                if(session.mode.equals("gateway"))DeathWhisper.CENOBITE.cancelCamera(player);
                close(player);
            }
            case "aim" -> {
                if(session.mode.equals("gateway"))
                    DeathWhisper.CENOBITE.cameraAim(player,yaw,pitch);
            }
            case "fire" -> {
                if(session.mode.equals("gateway") && DeathWhisper.CENOBITE.fireFromCamera(player,yaw,pitch))
                    ACTIVE.put(player.getUUID(),new Session("gateway",0,now+100,session.dimension));
            }
            case "next","previous" -> {
                if(session.mode.equals("security")){
                    SecurityDoorController.switchCamera(player,session.index,
                        packet.action().equals("next")?1:-1);
                }
            }
        }
    }
    public static void gateway(ServerPlayer player,net.minecraft.world.phys.Vec3 point){
        long now=player.getServer().overworld().getGameTime();
        ACTIVE.put(player.getUUID(),new Session("gateway",0,now+120,player.level().dimension()));
        send(player,new View("gateway",0,point.x,point.y,point.z,player.getYRot(),player.getXRot()));
    }
    public static void security(ServerPlayer player,int index,net.minecraft.world.phys.Vec3 point,float yaw){
        long now=player.getServer().overworld().getGameTime();
        ACTIVE.put(player.getUUID(),new Session("security",index,now+400,player.level().dimension()));
        send(player,new View("security",index,point.x,point.y,point.z,yaw,0));
    }
    public static boolean isGatewayViewing(ServerPlayer player){
        Session s=ACTIVE.get(player.getUUID());
        return s!=null && s.mode.equals("gateway") && s.dimension==player.level().dimension()
            && player.getServer().overworld().getGameTime()<=s.expires;
    }
    public static void follow(ServerPlayer player,net.minecraft.world.phys.Vec3 pos){
        if(isGatewayViewing(player))
            send(player,new View("follow",0,pos.x,pos.y,pos.z,0,0));
    }
    public static void close(ServerPlayer player){
        if(ACTIVE.remove(player.getUUID())!=null)
            send(player,new View("close",0,0,0,0,0,0));
    }
    /** Drop stale sessions after timeout/disconnect; restore the client's normal POV. */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        long now=event.getServer().overworld().getGameTime();
        var expired=new java.util.ArrayList<UUID>();
        for(var entry:ACTIVE.entrySet()){
            ServerPlayer player=event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session=entry.getValue();
            if(player==null || !player.isAlive() || now>session.expires
                || player.level().dimension()!=session.dimension){
                if(player!=null)send(player,new View("close",0,0,0,0,0,0));
                expired.add(entry.getKey());
            }
        }
        for(UUID id:expired)ACTIVE.remove(id);
    }
    private static void send(ServerPlayer p,View packet){
        PacketDistributor.sendToPlayer(p,packet);
    }
}