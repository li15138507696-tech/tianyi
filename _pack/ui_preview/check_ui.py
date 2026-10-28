"""Headless interaction/layout adapter for the production Screen.

No Minecraft is launched. Stub APIs record actual Screen drawing commands;
Pillow renders an explicitly labelled layout preview with fixture-only records.
The regular javac_check still verifies the screen against the real MC APIs.
"""
from pathlib import Path
import base64
import subprocess
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
STUBS = HERE / 'stubs'
OUT = HERE / 'classes'
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')

sources = {
'net/minecraft/network/chat/Component.java': r'''
package net.minecraft.network.chat;
public record Component(String text) {
 public static Component literal(String s){return new Component(s);}
 public String getString(){return text;}
}''',
'net/minecraft/util/FormattedCharSequence.java': r'''
package net.minecraft.util;
public record FormattedCharSequence(String text) {public static final FormattedCharSequence EMPTY=new FormattedCharSequence("");}''',
'net/minecraft/client/gui/Font.java': r'''
package net.minecraft.client.gui;
import java.util.*; import net.minecraft.network.chat.Component; import net.minecraft.util.FormattedCharSequence;
public class Font {
 public int width(String s){return s.codePoints().map(c->c==' '?4:c>255?9:6).sum();}
 public String plainSubstrByWidth(String s,int max){StringBuilder out=new StringBuilder();for(int c:s.codePoints().toArray()){String n=out.toString()+new String(Character.toChars(c));if(width(n)>max)break;out.appendCodePoint(c);}return out.toString();}
 public List<FormattedCharSequence> split(Component c,int max){List<FormattedCharSequence> out=new ArrayList<>();for(String p:c.text().split("\\n",-1)){while(!p.isEmpty()){String part=plainSubstrByWidth(p,max);if(part.isEmpty())part=p.substring(0,1);out.add(new FormattedCharSequence(part));p=p.substring(part.length());}}return out;}
}''',
'net/minecraft/client/gui/GuiGraphics.java': r'''
package net.minecraft.client.gui;
import java.nio.file.*; import java.nio.charset.StandardCharsets; import java.util.*; import net.minecraft.util.FormattedCharSequence;
public class GuiGraphics {
 public final List<String> commands=new ArrayList<>(); public int clips;
 public void fill(int x,int y,int x2,int y2,int color){commands.add("R|"+x+"|"+y+"|"+(x2-x)+"|"+(y2-y)+"|"+Integer.toUnsignedString(color));}
 public int drawString(Font f,String s,int x,int y,int color,boolean shadow){commands.add("T|"+x+"|"+y+"|"+Integer.toUnsignedString(color)+"|"+Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));return f.width(s);}
 public int drawString(Font f,FormattedCharSequence s,int x,int y,int color,boolean shadow){return drawString(f,s.text(),x,y,color,shadow);}
 public void enableScissor(int x,int y,int x2,int y2){commands.add("C|"+x+"|"+y+"|"+x2+"|"+y2);clips++;}
 public void disableScissor(){commands.add("E");clips--;if(clips<0)throw new AssertionError("unbalanced clip");}
 public String text(){StringBuilder b=new StringBuilder();for(String s:commands)if(s.startsWith("T|"))b.append(new String(Base64.getDecoder().decode(s.split("\\|",5)[4]),StandardCharsets.UTF_8));return b.toString();}
 public void save(String file)throws Exception{if(clips!=0)throw new AssertionError("leaked clip");Files.write(Path.of(file),commands,StandardCharsets.UTF_8);}
}''',
'net/minecraft/client/Minecraft.java': r'''
package net.minecraft.client; import net.minecraft.client.gui.Font;
public class Minecraft {private static final Minecraft INSTANCE=new Minecraft(); public final Font font=new Font(); public static Minecraft getInstance(){return INSTANCE;}}''',
'net/minecraft/client/gui/components/Button.java': r'''
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
}''',
'net/minecraft/client/gui/components/EditBox.java': r'''
package net.minecraft.client.gui.components;
import java.util.function.Consumer; import net.minecraft.client.gui.Font; import net.minecraft.client.gui.GuiGraphics; import net.minecraft.network.chat.Component;
public class EditBox extends Button {
 private String value=""; private Component hint=Component.literal(""); private Consumer<String> responder=v->{}; private final Font font;
 public EditBox(Font f,int x,int y,int w,int h,Component label){super(x,y,w,h,label,b->{},null);font=f;}
 public void setBordered(boolean b){} public void setTextColor(int c){} public void setTextColorUneditable(int c){} public void setMaxLength(int n){}
 public void setHint(Component h){hint=h;} public void setValue(String s){value=s;responder.accept(s);} public void setResponder(Consumer<String> r){responder=r;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float p){g.drawString(font,font.plainSubstrByWidth(value.isEmpty()?hint.text():value,w),x,y,value.isEmpty()?0xFF7E8377:0xFFD8D0BA,false);}
}''',
'net/minecraft/client/gui/screens/Screen.java': r'''
package net.minecraft.client.gui.screens;
import java.util.*; import net.minecraft.client.Minecraft; import net.minecraft.client.gui.*; import net.minecraft.client.gui.components.*; import net.minecraft.network.chat.Component;
public class Screen {
 public int width,height; protected Font font=Minecraft.getInstance().font; public boolean closed; public static boolean control;
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
}''',
'org/lwjgl/glfw/GLFW.java': r'''
package org.lwjgl.glfw; public class GLFW {public static final int GLFW_KEY_F=70,GLFW_KEY_UP=265,GLFW_KEY_DOWN=264,GLFW_KEY_ENTER=257,GLFW_KEY_PAGE_UP=266,GLFW_KEY_PAGE_DOWN=267;}''',
'net/guzhenren/tianyi/net/JournalActionPayload.java': r'''
package net.guzhenren.tianyi.net; public class JournalActionPayload {public enum Action {FAVORITE,DOUBTFUL,TOGGLE_CASE,REQUEST}}''',
'net/guzhenren/tianyi/net/JournalNet.java': r'''
package net.guzhenren.tianyi.net; import java.util.*; public class JournalNet {public static final List<String> sent=new ArrayList<>(); public static void send(JournalActionPayload.Action action,String id){sent.add(action+":"+id);}}''',
'net/guzhenren/tianyi/net/JournalSyncPayload.java': r'''
package net.guzhenren.tianyi.net; import java.util.*;
public record JournalSyncPayload(boolean titleUnlocked,long now,List<Row> rows,String title){
 public record Row(String id,int kind,int source,String title,String detail,String dim,long pos,long observedAt,long verifiedAt,boolean favorite,boolean doubtful,String caseId,String caseTitle,String caseMissing){}
}''',
'net/guzhenren/tianyi/client/JournalKeys.java': r'''
package net.guzhenren.tianyi.client; public class JournalKeys {public static final Key OPEN_JOURNAL=new Key();public static class Key {public boolean matches(int k,int s){return k==74;}}}''',
'UIHarness.java': r'''
import java.util.*; import java.lang.reflect.*;
import net.guzhenren.tianyi.client.*; import net.guzhenren.tianyi.net.*; import net.guzhenren.tianyi.journal.*; import net.minecraft.client.gui.*; import net.minecraft.client.gui.components.*;
public class UIHarness {
 static int checks; static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 static void call(Object o,String key)throws Exception{Method m=o.getClass().getDeclaredMethod(key);m.setAccessible(true);m.invoke(o);}
 static GuiGraphics draw(JournalScreen s){GuiGraphics g=new GuiGraphics();s.render(g,-1,-1,0);check(g.clips==0,"clipping balanced");return g;}
 static void click(JournalScreen s,int x,int y){s.mouseClicked(x,y,0);s.mouseReleased(x,y,0);draw(s);}
 static List<JournalSyncPayload.Row> data(){List<JournalSyncPayload.Row> rows=new ArrayList<>();String[] titles={"北坡产地采收记录","旧商路的兽踪","来历未明的蛊方","过路修士的询问"};for(int i=0;i<20;i++)rows.add(new JournalSyncPayload.Row("entry-"+i,0,i%3,titles[i%4]+(i>3?" · "+i:""),"此前记录的产出与这次采收有所不同。\n消息的最初来源尚未核实；兽群活动、其他人的采集或环境变化，都可能影响此地。应当保留不同解释，继续收集依据。","minecraft:overworld",0,24000*(20-i),24000*(20-i),i==0,i==2,i<3?"case-a":"",i<3?"沿途得方":"",i<3?"尚缺：消息的最初来源与独立证词。":""));rows.add(new JournalSyncPayload.Row("knowledge",1,0,"一次炼蛊尝试","产物尚未得到可靠核实。","minecraft:overworld",0,24000,24000,false,false,"","",""));rows.add(new JournalSyncPayload.Row("trace",2,0,"公开行动的痕迹","此处只保留有依据的自身行踪。","minecraft:overworld",0,24000,24000,false,false,"","",""));return rows;}
 public static void main(String[] args)throws Exception{
  JournalClientState.accept(new JournalSyncPayload(true,600000,data(),"窥天录"));
  JournalScreen wide=new JournalScreen();wide.testInit(720,420);GuiGraphics g=draw(wide);g.save(args[0]+"/wide.draw");
  check(g.text().contains("来处与时序"),"wide view shows evidence");
  check(!g.text().contains("事件时间："),"does not invent an event-time field");
  JournalLayout l=(JournalLayout)field(wide,"book");
  click(wide,l.tabs().x()+10,l.tabs().y()+2*42+10);
  check(field(wide,"selectedId").equals("knowledge"),"switching tab cannot keep old detail");
  EditBox search=(EditBox)field(wide,"search");search.setValue("不存在");
  check(draw(wide).text().contains("未找到相符的记载"),"search has truthful empty state");
  check(field(wide,"selectedId").equals(""),"filtered-out record loses detail");
  search.setValue("");draw(wide);
  click(wide,l.actions().x()+10,l.actions().y()+8);
  check(JournalNet.sent.getLast().equals("FAVORITE:knowledge"),"action targets selected server record");
  var longRows=new ArrayList<>(data());var first=longRows.getFirst();
  longRows.set(0,new JournalSyncPayload.Row(first.id(),first.kind(),first.source(),first.title(),first.detail().repeat(6),first.dim(),first.pos(),first.observedAt(),first.verifiedAt(),first.favorite(),first.doubtful(),first.caseId(),first.caseTitle(),first.caseMissing()));
  JournalClientState.accept(new JournalSyncPayload(true,600000,longRows,"窥天录"));
  wide=new JournalScreen();wide.testInit(720,420);draw(wide);l=(JournalLayout)field(wide,"book");
  wide.mouseScrolled(l.list().x()+10,l.list().y()+10,0,-1);draw(wide);
  check((int)field(wide,"listScroll")==1,"list scroll works");
  check((int)field(wide,"detailScroll")==0,"list scrolling does not move detail");
  wide.mouseScrolled(l.detail().x()+10,l.detail().y()+10,0,-1);draw(wide);
  check((int)field(wide,"detailScroll")>0,"detail scroll works");
  wide.mouseScrolled(0,0,0,-1);check((int)field(wide,"listScroll")==1,"outside scroll does not move list");
  JournalClientState.accept(new JournalSyncPayload(true,600000,data(),"窥天录"));
  for(int[] size:new int[][]{{240,180},{320,240},{426,240},{640,360},{960,540}}){
   JournalScreen s=new JournalScreen();s.testInit(size[0],size[1]);draw(s);l=(JournalLayout)field(s,"book");
   if(l.compact()){
    check(!((Button)field(s,"favorite")).visible,"actions hidden in compact list");
    click(s,l.list().x()+12,l.list().y()+8);
    check((boolean)field(s,"detailOpen"),"compact row opens detail");
    check(((Button)field(s,"back")).visible,"compact detail has back button");
    check(!((EditBox)field(s,"search")).visible,"search is not over detail");
    if(size[0]==320)draw(s).save(args[0]+"/compact-detail.draw");
    click(s,l.toolbar().x()+10,l.toolbar().y()+8);
    check(!(boolean)field(s,"detailOpen"),"back restores list");
    if(size[0]==320)draw(s).save(args[0]+"/compact-list.draw");
   }
  }
  JournalClientState.accept(new JournalSyncPayload(false,0,List.of(),"见闻录"));
  JournalScreen empty=new JournalScreen();empty.testInit(720,420);g=draw(empty);g.save(args[0]+"/empty.draw");
  check(g.text().contains("尚无相关见闻"),"empty journal has no fake records");
  check(!g.text().contains("北坡"),"previous session detail is gone");
  empty.keyPressed(256,0,0);check(empty.closed,"Esc closes book");
  System.out.println("UIHarness: "+checks+" interaction checks passed (adapter, not in-game test)");
 }
}'''
}

# Extra adapter surfaces for the owner-specific encounter UI. Networking is recorded,
# while the regular javac_check validates production payload codecs against Minecraft.
sources['net/minecraft/client/Minecraft.java'] = r'''
package net.minecraft.client;
import net.minecraft.client.gui.Font; import net.minecraft.client.gui.screens.Screen;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft(); public final Font font=new Font();
 public Screen screen; public Player player=new Player(); public static Minecraft getInstance(){return INSTANCE;}
 public void setScreen(Screen s){screen=s;if(s!=null&&s.width==0)s.testInit(720,420);}
 public static class Player {public double getX(){return 0;} public double getZ(){return 0;} public Level level(){return new Level();}}
 public static class Level {public Key dimension(){return new Key();}}
 public static class Key {public Object location(){return "minecraft:overworld";}}
}'''
sources['net/minecraft/client/gui/screens/Screen.java'] = sources['net/minecraft/client/gui/screens/Screen.java'].replace(
    'public int width,height;', 'protected Minecraft minecraft=Minecraft.getInstance(); public int width,height;')
sources['net/minecraft/client/gui/screens/ConfirmScreen.java'] = r'''
package net.minecraft.client.gui.screens;
import java.util.function.Consumer; import net.minecraft.network.chat.Component;
public class ConfirmScreen extends Screen {
 public final Consumer<Boolean> callback; public final Component warning;
 public ConfirmScreen(Consumer<Boolean> c,Component t,Component w,Component yes,Component no){super(t);callback=c;warning=w;}
 public void answer(boolean answer){callback.accept(answer);}
}'''
sources['net/minecraft/core/BlockPos.java'] = r'''
package net.minecraft.core; public record BlockPos(int x,int y,int z){
 public static BlockPos of(long p){return new BlockPos((int)(p>>38),(int)(p<<52>>52),(int)(p<<26>>38));}
 public int getX(){return x;} public int getZ(){return z;}
}'''
sources['net/neoforged/neoforge/network/PacketDistributor.java'] = r'''
package net.neoforged.neoforge.network; import java.util.*;
public class PacketDistributor {public static final List<Object> sent=new ArrayList<>();public static void sendToServer(Object p){sent.add(p);}}
'''
sources['net/guzhenren/tianyi/net/EncounterSyncPayload.java'] = r'''
package net.guzhenren.tianyi.net;
public record EncounterSyncPayload(String id,String phase,String dimension,long position,int secondsLeft,
 String enemyId,String enemyName,int baseReward,int stake,int bonus,String message){}
'''
sources['net/guzhenren/tianyi/net/EncounterActionPayload.java'] = r'''
package net.guzhenren.tianyi.net;
public record EncounterActionPayload(Action action,String encounterId){public enum Action{INVESTIGATE,WAGER,FINISH}}
'''
# With a real encounter view row ahead of journal entries, select the journal row
# explicitly for the existing evidence and scrolling checks.
sources['UIHarness.java'] = sources['UIHarness.java'].replace(
    'check(g.text().contains("来处与时序")',
    'click(wide,((JournalLayout)field(wide,"book")).list().x()+12,((JournalLayout)field(wide,"book")).list().y()+50);g=draw(wide);check(g.text().contains("来处与时序")')
sources['UIHarness.java'] = sources['UIHarness.java'].replace(
    'wide=new JournalScreen();wide.testInit(720,420);draw(wide);l=(JournalLayout)field(wide,"book");',
    'wide=new JournalScreen();wide.testInit(720,420);draw(wide);l=(JournalLayout)field(wide,"book");click(wide,l.list().x()+12,l.list().y()+50);')
sources['UIHarness.java'] = sources['UIHarness.java'].replace(
    'check(g.text().contains("尚无相关见闻"),"empty journal has no fake records");',
    'check(g.text().contains("静候机缘"),"waiting state explains next steps");check(JournalClientState.rows().isEmpty(),"waiting view creates no journal records");')
sources['UIHarness.java'] = sources['UIHarness.java'].replace(
    'System.out.println("UIHarness:', 'encounterChecks(args[0]);System.out.println("UIHarness:')
sources['UIHarness.java'] = sources['UIHarness.java'].replace(
    'public static void main(String[] args)', r'''
 static EncounterSyncPayload encounter(String phase){return new EncounterSyncPayload("owner-one",phase,"minecraft:overworld",(20L<<38)|(20L<<12),phase.equals("FIGHT")?180:phase.equals("WON")||phase.equals("LOST")?480:0,"guzhenren:hui_lang","灰狼",32,32,128,phase.equals("WON")?"挑战成功：返还32元石，额外获得128元石，速度I持续120秒。":phase.equals("LOST")?"挑战失败：认输离场，押注已失去，当前真元减少50%，虚弱I持续60秒。":"你亲见一道异象。");}
 static void expirePending(JournalScreen s)throws Exception {Field f=JournalScreen.class.getDeclaredField("pendingUntil");f.setAccessible(true);f.set(s,0L);}
 static void encounterChecks(String out)throws Exception {
  var mc=net.minecraft.client.Minecraft.getInstance();var sent=net.neoforged.neoforge.network.PacketDistributor.sent;sent.clear();JournalNet.sent.clear();
  JournalClientState.acceptEncounter(encounter("SEEK"));
  JournalScreen s=new JournalScreen();s.testInit(720,420);GuiGraphics g=draw(s);g.save(out+"/encounter-seek.draw");
  var rows=(List<?>)getRows(s);var first=(JournalSyncPayload.Row)rows.getFirst();
  check(first.title().contains("当前机缘"),"current encounter is first");
  check(JournalClientState.rows().isEmpty(),"encounter stays out of journal state");
  check(g.text().contains("东南")&&!g.text().contains("剩余时间：0"),"known position gives direction without inventing SEEK expiry");
  check(((Button)field(s,"favorite")).visible&&!((Button)field(s,"doubtful")).visible,"SEEK offers investigate only");
  Button investigate=(Button)field(s,"favorite");click(s,investigate.getX()+2,investigate.getY()+2);
  check(sent.size()==1&&((EncounterActionPayload)sent.getLast()).action()==EncounterActionPayload.Action.INVESTIGATE,"investigate sends encounter intent");
  check(JournalNet.sent.isEmpty(),"virtual row sends no journal actions");
  JournalClientState.acceptEncounter(encounter("READY"));draw(s);g=draw(s);g.save(out+"/encounter-ready.draw");
  check(g.text().contains("已入袋")&&!g.text().contains("剩余时间：0"),"READY already granted base and has no false expiry");
  Button wager=(Button)field(s,"doubtful");click(s,wager.getX()+2,wager.getY()+2);
  check(sent.size()==1,"first wager click cannot stake");
  check(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen,"wager opens explicit confirmation");
  var confirm=(net.minecraft.client.gui.screens.ConfirmScreen)mc.screen;
  check(confirm.warning.text().contains("32")&&confirm.warning.text().contains("128")&&confirm.warning.text().contains("50%"),"confirmation includes actual cost rewards and loss");
  for(String rule:new String[]{"180秒","32格","存活","死亡","退出游戏","切换维度","64格","5秒","离开生存模式","超时"})check(confirm.warning.text().contains(rule),"confirmation states rule: "+rule);
  confirm.answer(false);check(sent.size()==1,"cancel wager sends nothing");
  draw(s);click(s,wager.getX()+2,wager.getY()+2);((net.minecraft.client.gui.screens.ConfirmScreen)mc.screen).answer(true);
  check(sent.size()==2&&((EncounterActionPayload)sent.getLast()).action()==EncounterActionPayload.Action.WAGER,"second explicit confirmation stakes");
  JournalClientState.acceptEncounter(encounter("FIGHT"));g=draw(s);g.save(out+"/encounter-fight.draw");check(g.text().contains("剩余时间：180")&&g.text().contains("灰狼"),"fight shows actual countdown and catalog beast");Button finish=(Button)field(s,"caseButton");
  check(!((Button)field(s,"favorite")).visible&&!wager.visible&&finish.visible,"fight exposes surrender only");
  click(s,finish.getX()+2,finish.getY()+2);check(sent.size()==2,"surrender also requires confirmation");
  ((net.minecraft.client.gui.screens.ConfirmScreen)mc.screen).answer(true);check(sent.size()==3,"confirmed surrender sends finish");
  for(String outcome:new String[]{"WON","LOST"}){
   JournalClientState.acceptEncounter(encounter(outcome));g=draw(s);g.save(out+"/encounter-"+outcome.toLowerCase()+".draw");
   check(g.text().contains(outcome.equals("WON")?"挑战成功":"挑战失败"),"result shows authoritative settlement: "+outcome);
   check(g.text().contains("下次机缘冷却：480")&&!g.text().contains("剩余时间：480"),"terminal timer is cooldown instead of battle time: "+outcome);
   check(!((Button)field(s,"favorite")).visible&&!((Button)field(s,"doubtful")).visible&&!((Button)field(s,"caseButton")).visible,"settled result offers no repeat reward or surrender buttons: "+outcome);
   int before=sent.size();click(s,finish.getX()+2,finish.getY()+2);check(sent.size()==before,"clicking previous action position cannot claim twice: "+outcome);
  }
  JournalClientState.acceptEncounter(encounter("READY"));draw(s);click(s,wager.getX()+2,wager.getY()+2);
  JournalClientState.acceptEncounter(encounter("FINISHED"));((net.minecraft.client.gui.screens.ConfirmScreen)mc.screen).answer(true);
  check(sent.size()==3,"stale confirmation cannot act on finished encounter");
  JournalClientState.acceptEncounter(encounter("FIGHT"));Field received=JournalClientState.class.getDeclaredField("encounterReceivedAt");received.setAccessible(true);received.set(null,System.currentTimeMillis()-5000);
  check(JournalClientState.encounterSecondsLeft()<=175&&JournalClientState.encounterSecondsLeft()>=174,"countdown accounts for elapsed time");
  JournalClientState.acceptEncounter(new EncounterSyncPayload("owner-one","SEEK","",Long.MIN_VALUE,0,"","",32,32,128,"未明地点"));draw(s);
  check(draw(s).text().contains("尚无确切方位")&&!draw(s).text().contains("东南"),"unknown position invents no direction");
  JournalClientState.clear();check(JournalClientState.encounter().phase().equals("WAITING")&&JournalClientState.encounter().position()==Long.MIN_VALUE,"logout clears owner encounter");
 }
 static Object getRows(JournalScreen s)throws Exception{Method m=JournalScreen.class.getDeclaredMethod("rows");m.setAccessible(true);return m.invoke(s);}
 public static void main(String[] args)''')

def run(args):
    r = subprocess.run([str(v) for v in args], cwd=ROOT, capture_output=True, encoding='utf-8', errors='replace')
    if r.stdout: print(r.stdout.strip())
    if r.returncode:
        print(r.stderr)
        raise SystemExit(r.returncode)

for name, content in sources.items():
    p = STUBS / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')
OUT.mkdir(exist_ok=True)
main = ROOT / 'src/main/java/net/guzhenren/tianyi'
production = [main / p for p in ['journal/JournalLayout.java', 'client/JournalScreen.java',
                                'client/JournalButton.java', 'client/JournalClientState.java']]
run([JDK/'javac.exe','-encoding','UTF-8','-proc:none','-d',OUT,*production,*STUBS.rglob('*.java')])
run([JDK/'java.exe','-Dfile.encoding=UTF-8','-cp',OUT,'UIHarness',HERE])

def preview(name, w, h):
    scale = 3
    image = Image.new('RGBA', (w*scale,(h+24)*scale), '#111A15')
    font = ImageFont.truetype('C:/Windows/Fonts/simsun.ttc', 9*scale)
    stack = []
    clip = (0,0,w*scale,h*scale)
    for line in (HERE/(name+'.draw')).read_text(encoding='utf-8').splitlines():
        fields=line.split('|')
        if fields[0]=='C':
            stack.append(clip)
            clip=tuple(int(v)*scale for v in fields[1:])
            continue
        if fields[0]=='E':
            clip=stack.pop()
            continue
        layer=Image.new('RGBA',image.size)
        draw=ImageDraw.Draw(layer)
        if fields[0]=='R':
            x,y,rw,rh=map(int,fields[1:5]); color=int(fields[5])
            if rw<=0 or rh<=0: continue
            draw.rectangle((x*scale,y*scale,(x+rw)*scale-1,(y+rh)*scale-1),fill=((color>>16)&255,(color>>8)&255,color&255,(color>>24)&255))
        else:
            x,y=map(int,fields[1:3]); color=int(fields[3]); text=base64.b64decode(fields[4]).decode('utf-8')
            draw.text((x*scale,y*scale),text,font=font,anchor='lt',fill=((color>>16)&255,(color>>8)&255,color&255,255))
        mask=Image.new('L',image.size)
        ImageDraw.Draw(mask).rectangle((clip[0],clip[1],clip[2]-1,clip[3]-1),fill=255)
        from PIL import ImageChops
        layer.putalpha(ImageChops.multiply(layer.getchannel('A'),mask))
        image=Image.alpha_composite(image,layer)
    draw=ImageDraw.Draw(image)
    draw.text((12*scale,(h+7)*scale),'布局预览 · 示例记录 · 非游戏截图',font=font,fill='#91A29B')
    image.convert('RGB').save(HERE/(name+'.png'))

for name,w,h in [('wide',720,420),('compact-list',320,240),('compact-detail',320,240),('empty',720,420),('encounter-seek',720,420),('encounter-ready',720,420),('encounter-fight',720,420),('encounter-won',720,420),('encounter-lost',720,420)]:
    preview(name,w,h)
print('Nine labelled layout previews generated from production Screen drawing commands.')
