
package net.minecraft.client.gui.screens;
import java.util.*; import net.minecraft.client.Minecraft; import net.minecraft.client.gui.*; import net.minecraft.client.gui.components.*; import net.minecraft.network.chat.Component;
public class Screen {
 protected Minecraft minecraft=Minecraft.getInstance(); public int width,height; protected Font font=Minecraft.getInstance().font; public boolean closed; public static boolean control;
 public final List<Button> renderables=new ArrayList<>();
 public Screen(Component c){} protected void init(){} public void testInit(int w,int h){width=w;height=h;renderables.clear();init();}
 protected <T extends Button>T addRenderableWidget(T w){renderables.add(w);return w;}
 public void clearFocus(){for(Button b:renderables)b.setFocused(false);} public void setFocused(Button w){clearFocus();w.setFocused(true);}
 public static boolean hasControlDown(){return control;}
 public void onClose(){closed=true;} public boolean isPauseScreen(){return true;}
 public void renderBackground(GuiGraphics g,int x,int y,float p){} public void render(GuiGraphics g,int x,int y,float p){}
 public boolean mouseClicked(double x,double y,int b){if(b!=0)return false;for(Button w:renderables)if(w.click(x,y)){setFocused(w);return true;}return false;}
 public boolean mouseDragged(double x,double y,int b,double dx,double dy){return false;} public boolean mouseReleased(double x,double y,int b){return false;}
 public boolean mouseScrolled(double x,double y,double dx,double dy){return false;}
 public boolean keyPressed(int k,int s,int m){if(k==256){onClose();return true;}return false;}
}