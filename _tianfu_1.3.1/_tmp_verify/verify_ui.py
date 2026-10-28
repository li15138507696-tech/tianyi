"""Production Screen and economic decisions run with explicit UI/catalog fixtures.
Draw-command previews are labelled; this does not start Minecraft.
"""
from pathlib import Path
import base64
import subprocess
from PIL import Image, ImageDraw, ImageFont, ImageChops

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / '_tmp_verify/ui'
SRC = HERE / 'src'
OUT = HERE / 'classes'
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')
stubs = {
'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;public record Component(String text){public static Component literal(String s){return new Component(s);}public String getString(){return text;}}''',
'net/minecraft/client/gui/Font.java': '''package net.minecraft.client.gui;
public class Font {public int width(String s){return s.codePoints().map(c->c<128?5:9).sum();}public String plainSubstrByWidth(String s,int max){int n=0,end=0;while(end<s.length()){int c=s.codePointAt(end),w=c<128?5:9;if(n+w>max)break;n+=w;end+=Character.charCount(c);}return s.substring(0,end);}}''',
'net/minecraft/client/gui/GuiGraphics.java': '''package net.minecraft.client.gui;
public class GuiGraphics {public final java.util.List<String> commands=new java.util.ArrayList<>();
public void fill(int x,int y,int r,int b,int c){commands.add("R|"+x+"|"+y+"|"+(r-x)+"|"+(b-y)+"|"+Integer.toUnsignedLong(c));}
public void drawString(Font f,String s,int x,int y,int c,boolean shadow){commands.add("T|"+x+"|"+y+"|"+Integer.toUnsignedLong(c)+"|"+java.util.Base64.getEncoder().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
public void enableScissor(int x,int y,int r,int b){if(r<x||b<y)throw new AssertionError("invalid clip");commands.add("C|"+x+"|"+y+"|"+r+"|"+b);}public void disableScissor(){commands.add("E");}}''',
'net/minecraft/client/gui/screens/Screen.java': '''package net.minecraft.client.gui.screens;
public class Screen {public int width,height;protected net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();public Screen(net.minecraft.network.chat.Component t){}protected void init(){}protected <T> T addRenderableWidget(T w){return w;}public static boolean hasControlDown(){return false;}
public void tick(){}public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){}public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){}
public boolean mouseClicked(double x,double y,int b){return false;}public boolean mouseDragged(double x,double y,int b,double dx,double dy){return false;}public boolean mouseReleased(double x,double y,int b){return false;}public boolean mouseScrolled(double x,double y,double sx,double sy){return false;}public boolean keyPressed(int k,int s,int m){return false;}public boolean charTyped(char c,int m){return false;}public boolean isPauseScreen(){return false;}public void onClose(){}}''',
'net/minecraft/client/gui/components/EditBox.java': '''package net.minecraft.client.gui.components;
public class EditBox {public boolean visible=true,active=true;private boolean focused;private String value="";private int x,y,w;private java.util.function.Consumer<String> listener=s->{};
public EditBox(net.minecraft.client.gui.Font f,int x,int y,int w,int h,net.minecraft.network.chat.Component c){this.x=x;this.y=y;this.w=w;}public void setBordered(boolean b){}public void setTextColor(int c){}public void setTextColorUneditable(int c){}public void setMaxLength(int n){}public void setHint(net.minecraft.network.chat.Component c){}
public void setValue(String s){value=s;listener.accept(s);}public String getValue(){return value;}public void setResponder(java.util.function.Consumer<String> r){listener=r;}public void setX(int v){x=v;}public void setY(int v){y=v;}public void setWidth(int v){w=v;}public void setFocused(boolean b){focused=b;}public boolean isFocused(){return focused;}
public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){g.drawString(new net.minecraft.client.gui.Font(),value.isEmpty()?"查找名称、效果…":value,this.x,this.y,0xFFB0A58D,false);}public boolean mouseClicked(double x,double y,int b){if(visible&&active&&x>=this.x&&x<this.x+w&&y>=this.y&&y<this.y+16){focused=true;return true;}return false;}public boolean keyPressed(int k,int s,int m){return false;}public boolean charTyped(char c,int m){if(!focused)return false;setValue(value+c);return true;}}''',
'org/lwjgl/glfw/GLFW.java': '''package org.lwjgl.glfw;public class GLFW{public static final int GLFW_KEY_ESCAPE=256,GLFW_KEY_ENTER=257,GLFW_KEY_KP_ENTER=335,GLFW_KEY_F=70,GLFW_KEY_F6=295;}''',
'net/neoforged/neoforge/network/PacketDistributor.java': '''package net.neoforged.neoforge.network;public class PacketDistributor{public static final java.util.List<Object> sent=new java.util.ArrayList<>();public static void sendToServer(Object o){sent.add(o);}}''',
'net/guzhenren/tianfu/TianfuRoll.java': '''package net.guzhenren.tianfu;public class TianfuRoll{public static final int MAX_ROLLS=3;}''',
'net/guzhenren/tianfu/TianfuTalents.java': '''package net.guzhenren.tianfu;
public class TianfuTalents {
public record Talent(String id,String name,String quality,int cost,String desc){}
private static final java.util.List<Talent> ALL=java.util.List.of(new Talent("v2_mind_xiulian_1","修炼速度 I","玄",2,"修炼速度 +20%。这是完整效果说明，用于检查详情区域按实际内容滚动，长说明不会在三行后丢失。"),new Talent("v2_mind_xiulian_2","修炼速度 II","地",3,"修炼速度 +40%"),new Talent("v2_lifespan_scar","命烛残尽","负",-8,"确认时寿元减少400，且本世寿元增加无效；寿元不足不得确认。"));
public static java.util.List<Talent> all(){return ALL;}public static Talent byId(String id){return ALL.stream().filter(t->t.id().equals(id)).findFirst().orElse(null);}public static boolean isAvailable(String id){return !id.equals("malice_undying");}
public static java.util.List<TianfuPool.Spec> specs(){return ALL.stream().map(TianfuTalents::spec).toList();}public static java.util.List<TianfuPool.Spec> maliceSpecs(){return java.util.List.of();}
private static TianfuPool.Spec spec(Talent t){return new TianfuPool.Spec(t.id(),t.name(),TianfuPool.Kind.ENTRY,t.quality(),t.cost(),0,t.desc());}public static TianfuPool.Spec byIdIncludingUnavailable(String id){var t=byId(id);return t==null?null:spec(t);}}
''',
'net/guzhenren/tianfu/net/TianfuV2StatePayload.java': '''package net.guzhenren.tianfu.net;public class TianfuV2StatePayload{public static net.guzhenren.tianfu.v2.TianfuSelection.Snapshot value;public static net.guzhenren.tianfu.v2.TianfuSelection.Snapshot latest(){return value;}}''',
}
for name, fields in {'TianfuBuyPayload':'String id','TianfuRevokePayload':'String id','TianfuDifficultyPayload':'String id','TianfuOriginPayload':'String region,String background,long revision','TianfuRollPayload':'String id','TianfuRollAcceptPayload':'','TianfuConfirmPayload':''}.items():
    stubs['net/guzhenren/tianfu/net/'+name+'.java']='package net.guzhenren.tianfu.net;public record '+name+'('+fields+'){}'
for name, code in stubs.items():
    p=SRC/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8')
production=['client/TianfuViewScreen.java','client/TianfuUiRules.java','TianfuPool.java','TianfuDifficulty.java','v2/TianfuSelection.java','v2/TianfuEconomy.java','v2/TianfuMindTable.java','v2/TianfuOriginTable.java','v2/TianfuPage.java','v2/TianfuPageTable.java']
OUT.mkdir(parents=True,exist_ok=True)
sources=list(SRC.rglob('*.java'))+[ROOT/'src/main/java/net/guzhenren/tianfu'/p for p in production]+[ROOT/'_tmp_verify/UiCheck.java']
for command in [[JDK/'javac.exe','-encoding','UTF-8','-d',OUT,*sources],[JDK/'java.exe','-Dfile.encoding=UTF-8','-cp',OUT,'UiCheck',HERE]]:
    r=subprocess.run([str(v) for v in command],capture_output=True,encoding='utf-8',errors='replace')
    print(r.stdout)
    if r.returncode:print(r.stderr);raise SystemExit(r.returncode)

def preview(name,w,h):
    scale=3;img=Image.new('RGBA',(w*scale,(h+24)*scale),'#14100E');font=ImageFont.truetype('C:/Windows/Fonts/simsun.ttc',9*scale)
    clips=[];clip=(0,0,w*scale,h*scale)
    for line in (HERE/(name+'.draw')).read_text(encoding='utf-8').splitlines():
        f=line.split('|')
        if f[0]=='C':clips.append(clip);clip=tuple(int(v)*scale for v in f[1:]);continue
        if f[0]=='E':clip=clips.pop();continue
        layer=Image.new('RGBA',img.size);draw=ImageDraw.Draw(layer)
        if f[0]=='R':
            x,y,rw,rh=map(int,f[1:5]);c=int(f[5])
            if rw<=0 or rh<=0:continue
            draw.rectangle((x*scale,y*scale,(x+rw)*scale-1,(y+rh)*scale-1),fill=((c>>16)&255,(c>>8)&255,c&255,(c>>24)&255))
        else:
            x,y=map(int,f[1:3]);c=int(f[3]);text=base64.b64decode(f[4]).decode('utf-8');draw.text((x*scale,y*scale),text,font=font,anchor='lt',fill=((c>>16)&255,(c>>8)&255,c&255,255))
        mask=Image.new('L',img.size);ImageDraw.Draw(mask).rectangle((clip[0],clip[1],clip[2]-1,clip[3]-1),fill=255);layer.putalpha(ImageChops.multiply(layer.getchannel('A'),mask));img=Image.alpha_composite(img,layer)
    ImageDraw.Draw(img).text((12*scale,(h+7)*scale),'布局预览 · 示例数据 · 非游戏截图',font=font,fill='#B0A58D');img.convert('RGB').save(HERE/(name+'.png'))
for name,w,h in [('wide',760,420),('compact-list',360,240),('compact-detail',360,240),('confirmation',760,420),('readonly',760,420)]:preview(name,w,h)
print('Five labelled previews generated from production Screen commands.')
