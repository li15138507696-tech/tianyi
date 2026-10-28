import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.guzhenren.tianfu.*;
import net.guzhenren.tianfu.client.*;
import net.guzhenren.tianfu.net.*;
import net.guzhenren.tianfu.v2.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runs real Screen decisions with explicitly substituted rendering and catalog fixtures. */
public class UiCheck {
    static int checks;static Path output;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String n,Class<?>[] types,Object...v)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,v);}
    static int c(Object h,String n)throws Exception{Method m=h.getClass().getDeclaredMethod(n);m.setAccessible(true);return (int)m.invoke(h);}
    static void click(TianfuViewScreen s,String field)throws Exception{Object h=get(s,field);s.mouseClicked((c(h,"x0")+c(h,"x1"))/2.,(c(h,"y0")+c(h,"y1"))/2.,0);}
    static TianfuSelection.Snapshot state(long revision,int points,boolean locked,List<String> owned){Map<String,TianfuSelection.Receipt> receipts=new HashMap<>();for(String id:owned){var s=TianfuPool.byId(id);receipts.put(id,new TianfuSelection.Receipt(id,s.cost(),Math.max(0,-s.cost()),false));}return new TianfuSelection.Snapshot(2,revision,points,1,locked,new TianfuSelection.Origin("beiyuan","wanderer"),null,owned,receipts,0,Set.of());}
    static TianfuViewScreen screen(int w,int h,int points,boolean locked,List<String> owned)throws Exception{TianfuV2StatePayload.value=state(1,points,locked,owned);var s=new TianfuViewScreen(2,points,1,owned,locked,0,List.of());s.width=w;s.height=h;call(s,"init",new Class<?>[0]);return s;}
    static void page(TianfuViewScreen s,TianfuPage p)throws Exception{call(s,"switchPage",new Class<?>[]{TianfuPage.class},p);}
    static void draw(TianfuViewScreen s,String file)throws Exception{GuiGraphics g=new GuiGraphics();s.render(g,-1,-1,0);Files.write(output.resolve(file+".draw"),g.commands);}
    public static void main(String[] args)throws Exception{
        output=Path.of(args[0]);
        for(int[] size:new int[][]{{320,240},{360,240},{426,240},{640,300},{640,360},{760,420},{1120,600}}){
            var s=screen(size[0],size[1],8,false,List.of());List<?> nav=(List<?>)get(s,"nav");check(nav.size()==7,"seven tabs reachable");
            for(Object h:nav){check(c(h,"x0")>=0&&c(h,"x1")<=size[0],"nav x bounds");check(c(h,"y1")<=size[1]-53,"nav avoids action footer");}
            check((int)get(s,"bottom")>(int)get(s,"top"),"list viewport positive");
            check(c(get(s,"detailBody"),"y1")<=c(get(s,"detailAction"),"y0"),"details avoid action");
            for(var p:TianfuPage.values()){page(s,p);s.render(new GuiGraphics(),-1,-1,0);}
        }
        var s=screen(760,420,8,false,List.of());page(s,TianfuPage.MIND);set(s,"selectedId","v2_mind_xiulian_1");
        PacketDistributor.sent.clear();click(s,"detailAction");check("buy".equals(get(s,"action")),"expanded purchase opens confirmation");check(PacketDistributor.sent.isEmpty(),"dialog has no debit intent");
        click(s,"yesHit");check(PacketDistributor.sent.size()==1&&PacketDistributor.sent.getFirst() instanceof TianfuBuyPayload,"one confirmed purchase intent");click(s,"detailAction");check(PacketDistributor.sent.size()==1,"inflight prevents repeat submit");
        var poor=state(3,0,false,List.of());var expanded=TianfuPool.byId("v2_mind_xiulian_1");check(!TianfuUiRules.purchase(poor,expanded).allowed(),"insufficient points disabled");
        var enough=state(3,8,false,List.of());check(TianfuUiRules.purchase(enough,expanded).allowed(),"expanded talent follows economy");
        var body=TianfuPool.sijues().getFirst();check(!TianfuUiRules.revoke(enough,body).allowed(),"sijue cannot offer false revoke");
        var old=state(3,8,false,List.of("v2_mind_xiulian_1"));check(TianfuUiRules.purchase(old,TianfuPool.byId("v2_mind_xiulian_2")).afterPoints()==7,"replacement uses receipt refund");
        var neg=TianfuPool.byId("v2_lifespan_scar");var cap=new TianfuSelection.Snapshot(2,1,8,1,false,null,null,List.of(),Map.of(),24,Set.of());check(!TianfuUiRules.purchase(cap,neg).allowed(),"24 cap disabled");
        s=screen(760,420,8,false,List.of());page(s,TianfuPage.PHYSIQUE);var input=(EditBox)get(s,"search");input.setValue("筋骨");check(((List<?>)get(s,"rows")).size()==1,"search current page");
        page(s,TianfuPage.MIND);check(input.getValue().isEmpty(),"separate page query");page(s,TianfuPage.PHYSIQUE);check(input.getValue().equals("筋骨"),"restore page query");input.setValue("");
        s.refreshV2(state(9,17,false,List.of()));check((int)get(s,"points")==17,"uses supplied snapshot rather than cache");s.refreshV2(state(8,99,true,List.of()));check((int)get(s,"points")==17,"stale revision rejected");
        s.refresh(2,999,0,List.of(),true,2,List.of("v2_mind_xiulian_1"));check((int)get(s,"points")==17&&!(boolean)get(s,"confirmed"),"legacy payload cannot overwrite authority");
        s=screen(360,240,8,false,List.of());page(s,TianfuPage.MIND);draw(s,"compact-list");Object h=((List<?>)get(s,"rowHits")).getFirst();s.mouseClicked(c(h,"x0")+10,c(h,"y0")+5,0);check((boolean)get(s,"detailOpen"),"compact opens detail");draw(s,"compact-detail");click(s,"backHit");check(!(boolean)get(s,"detailOpen"),"compact return");
        s=screen(760,420,8,false,List.of("jingu_qiangjian"));page(s,TianfuPage.PHYSIQUE);draw(s,"wide");click(s,"confirmHit");check("confirm".equals(get(s,"action")),"eligible final confirm opens review");set(s,"message",((String)get(s,"message"))+"\n"+"完整代价必须能读完。".repeat(120));draw(s,"confirmation");check((int)get(s,"modalMax")>0,"long review scrolls");s.mouseScrolled(300,200,0,-100);check((int)get(s,"modalScroll")== (int)get(s,"modalMax"),"review reaches final text");s.keyPressed(256,0,0);check(get(s,"action")==null,"escape cancels modal");
        s=screen(760,420,8,true,List.of("jingu_qiangjian"));page(s,TianfuPage.PHYSIQUE);draw(s,"readonly");PacketDistributor.sent.clear();click(s,"detailAction");click(s,"confirmHit");check(PacketDistributor.sent.isEmpty()&&get(s,"action")==null,"readonly has no mutation intents");
        s=screen(760,420,8,false,List.of());set(s,"difficulty",-1);check(!(boolean)call(s,"canRoll",new Class<?>[0]),"no difficulty no roll");set(s,"difficulty",1);set(s,"rolls",3);check(!(boolean)call(s,"canRoll",new Class<?>[0]),"exhausted roll disabled");set(s,"kongqiao",0);check(!(boolean)call(s,"canConfirm",new Class<?>[0]),"no anchor no final confirmation");
        System.out.println("UiCheck: "+checks+" checks passed (render/catalog adapters; not in-game)");
    }
}
