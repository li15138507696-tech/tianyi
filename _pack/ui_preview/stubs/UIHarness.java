
import java.util.*; import java.lang.reflect.*;
import net.guzhenren.tianyi.client.*; import net.guzhenren.tianyi.net.*; import net.guzhenren.tianyi.journal.*; import net.minecraft.client.gui.*; import net.minecraft.client.gui.components.*;
public class UIHarness {
 static int checks; static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 static void call(Object o,String key)throws Exception{Method m=o.getClass().getDeclaredMethod(key);m.setAccessible(true);m.invoke(o);}
 static GuiGraphics draw(JournalScreen s){GuiGraphics g=new GuiGraphics();s.render(g,-1,-1,0);check(g.clips==0,"clipping balanced");return g;}
 static void click(JournalScreen s,int x,int y){s.mouseClicked(x,y,0);s.mouseReleased(x,y,0);draw(s);}
 static List<JournalSyncPayload.Row> data(){List<JournalSyncPayload.Row> rows=new ArrayList<>();String[] titles={"北坡产地采收记录","旧商路的兽踪","来历未明的蛊方","过路修士的询问"};for(int i=0;i<20;i++)rows.add(new JournalSyncPayload.Row("entry-"+i,0,i%3,titles[i%4]+(i>3?" · "+i:""),"此前记录的产出与这次采收有所不同。\n消息的最初来源尚未核实；兽群活动、其他人的采集或环境变化，都可能影响此地。应当保留不同解释，继续收集依据。","minecraft:overworld",0,24000*(20-i),24000*(20-i),i==0,i==2,i<3?"case-a":"",i<3?"沿途得方":"",i<3?"尚缺：消息的最初来源与独立证词。":""));rows.add(new JournalSyncPayload.Row("knowledge",1,0,"一次炼蛊尝试","产物尚未得到可靠核实。","minecraft:overworld",0,24000,24000,false,false,"","",""));rows.add(new JournalSyncPayload.Row("trace",2,0,"公开行动的痕迹","此处只保留有依据的自身行踪。","minecraft:overworld",0,24000,24000,false,false,"","",""));return rows;}
 
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
 public static void main(String[] args)throws Exception{
  JournalClientState.accept(new JournalSyncPayload(true,600000,data(),"窥天录"));
  JournalScreen wide=new JournalScreen();wide.testInit(720,420);GuiGraphics g=draw(wide);g.save(args[0]+"/wide.draw");
  click(wide,((JournalLayout)field(wide,"book")).list().x()+12,((JournalLayout)field(wide,"book")).list().y()+50);g=draw(wide);check(g.text().contains("来处与时序"),"wide view shows evidence");
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
  wide=new JournalScreen();wide.testInit(720,420);draw(wide);l=(JournalLayout)field(wide,"book");click(wide,l.list().x()+12,l.list().y()+50);
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
  check(g.text().contains("静候机缘"),"waiting state explains next steps");check(JournalClientState.rows().isEmpty(),"waiting view creates no journal records");
  check(!g.text().contains("北坡"),"previous session detail is gone");
  empty.keyPressed(256,0,0);check(empty.closed,"Esc closes book");
  encounterChecks(args[0]);System.out.println("UIHarness: "+checks+" interaction checks passed (adapter, not in-game test)");
 }
}