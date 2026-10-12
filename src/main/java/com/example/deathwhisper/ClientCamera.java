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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A client-only camera pawn: the PLAYER never teleports or changes dimension.
 * The camera sees the actual live loaded world, not a faked image/snapshot.
 * No chunk packets are requested: off-range cameras are deliberately refused.
 */
public final class ClientCamera {
    private ClientCamera(){}
    private static ArmorStand pawn;
    private static String mode="";
    private static int index;
    private static ClientLevel world;
    private static CameraType originalType;
    private static float yaw,pitch;
    private static boolean fired;
    private static CameraScreen screen;

    public static void receive(CameraPackets.View view){
        Minecraft mc=Minecraft.getInstance();
        if(view.mode().equals("close")){
            stop(false);return;
        }
        if(mc.level==null || mc.player==null)return;
        BlockPos pos=BlockPos.containing(view.x(),view.y(),view.z());
        if(!mc.level.hasChunkAt(pos)){
            mc.player.displayClientMessage(Component.literal(
                "§cCamera offline: target chunk isn't loaded by your client. Move closer to the door."),false);
            if(screen!=null)stop(true);
            return;
        }
        if(view.mode().equals("follow")){
            if(pawn!=null && mode.equals("gateway")){
                setPos(view.x(),view.y(),view.z());
            }
            return;
        }
        boolean replace=pawn==null || world!=mc.level || !mode.equals(view.mode());
        boolean switchedDoor=!replace && view.mode().equals("security") && view.index()!=index;
        if(replace)stop(false);
        world=mc.level;
        mode=view.mode();index=view.index();fired=false;
        if(switchedDoor){yaw=view.yaw();pitch=view.pitch();}
        if(pawn==null){
            // Server will never track this dummy: it only changes local rendering.
            pawn=new ArmorStand(EntityType.ARMOR_STAND,mc.level);
            originalType=mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            yaw=view.yaw();pitch=view.pitch();
            screen=new CameraScreen();
        }
        setPos(view.x(),view.y(),view.z());
        if(replace){
            mc.setCameraEntity(pawn);
            mc.setScreen(screen);
        }
    }
    private static void setPos(double x,double y,double z){
        if(pawn==null)return;
        // Armor stand camera height is not at its feet; place feet lower so
        // the actual viewport origin is the requested world point.
        pawn.moveTo(x,y-1.45,z,yaw,pitch);
        pawn.setYRot(yaw);
        pawn.setXRot(pitch);
    }
    private static void look(float dyaw,float dpitch){
        yaw=Mth.wrapDegrees(yaw+dyaw);
        pitch=Mth.clamp(pitch+dpitch,-80,80);
        if(pawn!=null){
            pawn.setYRot(yaw);pawn.setXRot(pitch);
        }
    }
    private static void control(String action){
        if(pawn!=null)PacketDistributor.sendToServer(new CameraPackets.Control(action,yaw,pitch));
    }
    private static void stop(boolean informServer){
        Minecraft mc=Minecraft.getInstance();
        if(informServer && pawn!=null)control("close");
        if(mc.player!=null && mc.getCameraEntity()==pawn)mc.setCameraEntity(mc.player);
        if(originalType!=null)mc.options.setCameraType(originalType);
        // Avoid setScreen recursive calls from Screen.removed().
        ArmorStand old=pawn;pawn=null;world=null;mode="";screen=null;originalType=null;
        if(old!=null && mc.screen instanceof CameraScreen)mc.setScreen(null);
    }

    private static final class CameraScreen extends Screen {
        private double previousX=Double.NaN,previousY=Double.NaN;
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
                mc.player.displayClientMessage(Component.literal("§cCamera range lost."),true);
                stop(true);return;
            }
            if(mode.equals("gateway") && ++ticks%4==0 && !fired)control("aim");
        }
        @Override public void mouseMoved(double mouseX,double mouseY){
            if(Double.isFinite(previousX)&&Double.isFinite(previousY)){
                look((float)(-(mouseX-previousX)*.27),(float)((mouseY-previousY)*.27));
            }
            previousX=mouseX;previousY=mouseY;
        }
        @Override public boolean mouseClicked(double x,double y,int button){
            if(mode.equals("gateway") && button==0){
                fired=true;control("fire");return true;
            }
            if(button==1) {
                if(mode.equals("security")){control("next");return true;}
                stop(true);return true;
            }
            return super.mouseClicked(x,y,button);
        }
        @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
            if(keyCode==GLFW.GLFW_KEY_ESCAPE || keyCode==GLFW.GLFW_KEY_X){
                stop(true);return true;
            }
            if(keyCode==GLFW.GLFW_KEY_LEFT) {look(-8,0);return true;}
            if(keyCode==GLFW.GLFW_KEY_RIGHT){look(8,0);return true;}
            if(keyCode==GLFW.GLFW_KEY_UP)   {look(0,-6);return true;}
            if(keyCode==GLFW.GLFW_KEY_DOWN) {look(0,6);return true;}
            if(mode.equals("gateway") && (keyCode==GLFW.GLFW_KEY_F ||
                keyCode==GLFW.GLFW_KEY_SPACE)){
                fired=true;control("fire");return true;
            }
            if(mode.equals("security") && (keyCode==GLFW.GLFW_KEY_N ||
                keyCode==GLFW.GLFW_KEY_PAGE_DOWN)){
                control("next");return true;
            }
            if(mode.equals("security") && (keyCode==GLFW.GLFW_KEY_P ||
                keyCode==GLFW.GLFW_KEY_PAGE_UP)){
                control("previous");return true;
            }
            return super.keyPressed(keyCode,scanCode,modifiers);
        }
        @Override public void renderBackground(GuiGraphics gui,int mouseX,int mouseY,float delta){
            // Transparent overlay: keep the real world visible behind the controls.
        }
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float delta){
            gui.fill(0,0,width,37,0x99090019);
            String title=mode.equals("gateway")?"SUMMONS OF PAIN • GATEWAY":"SECURITY NETWORK • DOOR "+index;
            gui.drawString(font,title,12,8,0xFFDDA9FF,false);
            String help=mode.equals("gateway")
                ? fired?"Chain launched • aim to steer • X / ESC exit":
                  "Move mouse / arrows to aim • Left-click / F launch • X / ESC exit"
                : "Mouse / arrows pan • N / P next/previous door • X / ESC exit";
            gui.drawString(font,help,12,23,0xFFFFFFFF,false);
            gui.fill(0,height-23,width,height,0x77000000);
            gui.drawCenteredString(font,"LIVE CAMERA • loaded chunks only • player stays at their location",
                width/2,height-16,0xFF88E0DF);
            super.render(gui,mouseX,mouseY,delta);
        }
        @Override public void onClose(){stop(true);}
        @Override public void removed(){
            if(pawn!=null)stop(true);
        }
    }
}
