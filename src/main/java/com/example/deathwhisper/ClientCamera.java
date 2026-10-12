package com.example.deathwhisper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-local live camera. The player entity stays on the server at its original position.
 * View controls work with a CAPTURED mouse, rather than Gui Screen.mouseMoved absolute
 * coordinates which stop updating at the edge of the window.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid=DeathWhisper.ID,
    bus=net.neoforged.fml.common.EventBusSubscriber.Bus.GAME,
    value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientCamera {
    private ClientCamera(){}
    private static ArmorStand pawn;
    private static ClientLevel world;
    private static CameraType oldType;
    private static CameraScreen screen;
    private static String mode="";
    private static int index;
    private static float yaw,pitch;
    private static boolean fired,closing;
    private static double lastX,lastY;
    private static boolean haveMouse;

    public static void receive(CameraPackets.View view){
        Minecraft mc=Minecraft.getInstance();
        if(view.mode().equals("close")){stop(false);return;}
        if(mc.level==null || mc.player==null)return;
        BlockPos target=BlockPos.containing(view.x(),view.y(),view.z());
        if(!mc.level.hasChunkAt(target)){
            mc.player.displayClientMessage(Component.literal(
                "Camera offline: that chunk is not loaded by your client."),true);
            if(pawn!=null)stop(true);
            return;
        }
        if(view.mode().equals("follow")){
            if(pawn!=null && mode.equals("gateway"))setPos(view.x(),view.y(),view.z());
            return;
        }
        boolean fresh=pawn==null || world!=mc.level || !mode.equals(view.mode());
        boolean switching=!fresh && view.mode().equals("security") && view.index()!=index;
        if(fresh)stop(false);
        world=mc.level;mode=view.mode();index=view.index();
        if(fresh || switching){
            yaw=view.yaw();pitch=view.pitch();fired=false;
        }
        if(pawn==null){
            pawn=new ArmorStand(EntityType.ARMOR_STAND,mc.level);
            oldType=mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            screen=new CameraScreen();
        }
        setPos(view.x(),view.y(),view.z());
        // Reset the mouse after any camera transition. Do not keep the old GUI cursor
        // position: it caused enormous orientation jumps when the view changed.
        haveMouse=false;
        if(mc.screen!=screen)mc.setScreen(screen);
        mc.setCameraEntity(pawn);
        captureCursor(mc);
    }

    private static void setPos(double x,double y,double z){
        if(pawn==null)return;
        pawn.moveTo(x,y-1.45,z,yaw,pitch);
        pawn.setYRot(yaw);pawn.setXRot(pitch);
    }
    private static void look(float dx,float dy){
        yaw=Mth.wrapDegrees(yaw+dx);
        pitch=Mth.clamp(pitch+dy,-80,80);
        if(pawn!=null){
            pawn.setYRot(yaw);pawn.setXRot(pitch);
            pawn.setYHeadRot(yaw);
        }
    }
    private static void send(String action){
        if(pawn!=null)PacketDistributor.sendToServer(new CameraPackets.Control(action,yaw,pitch));
    }
    private static void captureCursor(Minecraft mc){
        if(mc.screen!=screen || pawn==null)return;
        long handle=mc.getWindow().getWindow();
        if(GLFW.glfwGetInputMode(handle,GLFW.GLFW_CURSOR)!=GLFW.GLFW_CURSOR_DISABLED){
            GLFW.glfwSetInputMode(handle,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);
            haveMouse=false;
        }
        double[] x=new double[1],y=new double[1];
        GLFW.glfwGetCursorPos(handle,x,y);
        lastX=x[0];lastY=y[0];haveMouse=true;
    }
    private static void sampleMouse(){
        Minecraft mc=Minecraft.getInstance();
        if(pawn==null || mc.screen!=screen || !mc.isWindowActive())return;
        long handle=mc.getWindow().getWindow();
        if(GLFW.glfwGetInputMode(handle,GLFW.GLFW_CURSOR)!=GLFW.GLFW_CURSOR_DISABLED){
            captureCursor(mc);return;
        }
        double[] x=new double[1],y=new double[1];
        GLFW.glfwGetCursorPos(handle,x,y);
        if(!haveMouse){lastX=x[0];lastY=y[0];haveMouse=true;return;}
        double deltaX=x[0]-lastX,deltaY=y[0]-lastY;
        lastX=x[0];lastY=y[0];
        // Reject focus-change deltas, never cap normal accumulated steering.
        if(Math.abs(deltaX)>600 || Math.abs(deltaY)>600)return;
        if(Math.abs(deltaX)>.001 || Math.abs(deltaY)>.001)
            look((float)(deltaX*.18),(float)(deltaY*.18));
    }
    private static void stop(boolean notifyServer){
        if(closing)return;
        closing=true;
        try{
            Minecraft mc=Minecraft.getInstance();
            if(notifyServer && pawn!=null && mc.getConnection()!=null)send("close");
            CameraScreen active=screen;
            ArmorStand oldPawn=pawn;
            pawn=null;world=null;mode="";screen=null;oldTypeReset(mc);
            haveMouse=false;fired=false;
            if(mc.player!=null && (mc.getCameraEntity()==oldPawn || oldPawn!=null))
                mc.setCameraEntity(mc.player);
            if(active!=null && mc.screen==active)mc.setScreen(null);
            else if(mc.getWindow()!=null)
                GLFW.glfwSetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
        }finally{closing=false;}
    }
    private static void oldTypeReset(Minecraft mc){
        if(oldType!=null)mc.options.setCameraType(oldType);
        oldType=null;
    }
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event){
        if(pawn==null || closing)return;
        Minecraft mc=Minecraft.getInstance();
        // Independent failsafe even if another mod replaces the GUI without
        // calling Screen.removed(), or the player disconnects/dies.
        if(mc.level!=world || mc.player==null || !mc.player.isAlive() || mc.screen!=screen){
            stop(true);
        }
    }

    private static final class CameraScreen extends Screen {
        private int ticks;
        CameraScreen(){super(Component.literal("Death Whisper Camera"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick(){
            super.tick();
            Minecraft mc=Minecraft.getInstance();
            if(pawn==null || mc.player==null || mc.level!=world || !mc.player.isAlive()){
                stop(true);return;
            }
            if(!mc.level.hasChunkAt(pawn.blockPosition().above())){
                mc.player.displayClientMessage(Component.literal("Camera range lost."),true);
                stop(true);return;
            }
            if(mode.equals("gateway") && ++ticks%1==0)send("aim");
            // Force capture again after alt-tab/focus changes.
            if(mc.isWindowActive() && GLFW.glfwGetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR)
                    !=GLFW.GLFW_CURSOR_DISABLED)captureCursor(mc);
        }
        @Override public void mouseMoved(double x,double y){
            // Ignored intentionally: GUI pixel coordinates clamp at the window edge.
            // Raw GLFW relative movement is sampled in render().
        }
        @Override public boolean mouseClicked(double x,double y,int button){
            if(mode.equals("gateway") && button==0){
                if(!fired){fired=true;send("fire");}
                return true;
            }
            if(button==1){
                if(mode.equals("security"))send("next");
                else stop(true);
                return true;
            }
            return super.mouseClicked(x,y,button);
        }
        @Override public boolean keyPressed(int code,int scanCode,int mods){
            if(code==GLFW.GLFW_KEY_ESCAPE || code==GLFW.GLFW_KEY_X){stop(true);return true;}
            if(code==GLFW.GLFW_KEY_LEFT){look(-8,0);return true;}
            if(code==GLFW.GLFW_KEY_RIGHT){look(8,0);return true;}
            if(code==GLFW.GLFW_KEY_UP){look(0,-6);return true;}
            if(code==GLFW.GLFW_KEY_DOWN){look(0,6);return true;}
            if(mode.equals("gateway") && (code==GLFW.GLFW_KEY_F || code==GLFW.GLFW_KEY_SPACE)){
                if(!fired){fired=true;send("fire");}
                return true;
            }
            if(mode.equals("security") && (code==GLFW.GLFW_KEY_N || code==GLFW.GLFW_KEY_PAGE_DOWN)){
                send("next");return true;
            }
            if(mode.equals("security") && (code==GLFW.GLFW_KEY_P || code==GLFW.GLFW_KEY_PAGE_UP)){
                send("previous");return true;
            }
            return super.keyPressed(code,scanCode,mods);
        }
        @Override public void renderBackground(GuiGraphics gui,int x,int y,float delta){}
        @Override public void render(GuiGraphics gui,int x,int y,float delta){
            sampleMouse();
            gui.fill(0,0,width,38,0xAA090019);
            gui.drawString(font,mode.equals("gateway")?"SUMMONS OF PAIN - LIVE GATEWAY":
                "SECURITY NETWORK - DOOR "+index,12,8,0xFFDDA9FF,false);
            gui.drawString(font,mode.equals("gateway")
                ? fired?"Steer with mouse / arrows | X / ESC exit":
                    "Aim with mouse / arrows | Left-click / F fire | X / ESC exit"
                :"Pan with mouse / arrows | N / P cameras | X / ESC exit",
                12,23,0xFFFFFFFF,false);
            gui.drawCenteredString(font,"+",width/2,height/2-4,0xFFE3C7FF);
            super.render(gui,x,y,delta);
        }
        @Override public void onClose(){stop(true);}
        @Override public void removed(){if(pawn!=null && !closing)stop(true);}
    }
}
