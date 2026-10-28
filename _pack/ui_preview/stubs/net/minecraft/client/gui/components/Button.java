
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.GuiGraphics; import net.minecraft.network.chat.Component;
public class Button {
 public interface OnPress{void onPress(Button button);} public interface CreateNarration{}
 protected static final CreateNarration DEFAULT_NARRATION=null;
 protected int x,y,w,h; protected Component message; private boolean hovered,focused; protected OnPress press;
 public boolean active=true,visible=true;
 protected Button(int x,int y,int w,int h,Component message,OnPress press,CreateNarration n){this.x=x;this.y=y;this.w=w;this.h=h;this.message=message;this.press=press;}
 public int getX(){return x;} public int getY(){return y;} public int getWidth(){return w;} public int getHeight(){return h;}
 public Component getMessage(){return message;} public void setMessage(Component m){message=m;}
 public boolean isHoveredOrFocused(){return hovered||focused;} public boolean isFocused(){return focused;} public void setFocused(boolean f){focused=f;}
 public boolean hit(double px,double py){return visible&&px>=x&&py>=y&&px<x+w&&py<y+h;}
 public boolean click(double px,double py){if(active&&hit(px,py)){press.onPress(this);return true;}return false;}
 protected void renderWidget(GuiGraphics g,int mx,int my,float p){}
 public void render(GuiGraphics g,int mx,int my,float p){hovered=hit(mx,my);if(visible)renderWidget(g,mx,my,p);}
}