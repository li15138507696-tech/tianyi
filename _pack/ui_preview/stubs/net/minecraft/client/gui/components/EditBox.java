
package net.minecraft.client.gui.components;
import java.util.function.Consumer; import net.minecraft.client.gui.Font; import net.minecraft.client.gui.GuiGraphics; import net.minecraft.network.chat.Component;
public class EditBox extends Button {
 private String value=""; private Component hint=Component.literal(""); private Consumer<String> responder=v->{}; private final Font font;
 public EditBox(Font f,int x,int y,int w,int h,Component label){super(x,y,w,h,label,b->{},null);font=f;}
 public void setBordered(boolean b){} public void setTextColor(int c){} public void setTextColorUneditable(int c){} public void setMaxLength(int n){}
 public void setHint(Component h){hint=h;} public void setValue(String s){value=s;responder.accept(s);} public void setResponder(Consumer<String> r){responder=r;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float p){g.drawString(font,font.plainSubstrByWidth(value.isEmpty()?hint.text():value,w),x,y,value.isEmpty()?0xFF7E8377:0xFFD8D0BA,false);}
}