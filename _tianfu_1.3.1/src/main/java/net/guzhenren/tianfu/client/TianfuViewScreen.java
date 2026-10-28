package net.guzhenren.tianfu.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuPool;
import net.guzhenren.tianfu.TianfuRoll;
import net.guzhenren.tianfu.TianfuTalents;
import net.guzhenren.tianfu.net.*;
import net.guzhenren.tianfu.v2.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Native-pixel destiny folio. Only intents are sent; the server commits every change. */
public class TianfuViewScreen extends Screen {
    private static final int INK=0xFF14100E,PANEL=0xFF1C1814,TILE=0xFF242019;
    private static final int GOLD=0xFFC9A227,LIGHT=0xFFE8C86A,LINE=0xFF6B4F1D;
    private static final int TEXT=0xFFE2D8C3,MUTED=0xFFB0A58D,DIM=0xFF786E5C,RED=0xFFD57664;
    private record Hit(int x0,int y0,int x1,int y1) {
        boolean contains(double x,double y){return x>=x0&&x<x1&&y>=y0&&y<y1;}
        int w(){return x1-x0;} int h(){return y1-y0;}
    }
    private record Row(String id,String title,String desc,TianfuPool.Spec spec,TianfuDifficulty.Option option) {}
    private final List<Row> rows=new ArrayList<>();
    private final List<Hit> nav=new ArrayList<>(),rowHits=new ArrayList<>();
    private final EnumMap<TianfuPage,Integer> pageScroll=new EnumMap<>(TianfuPage.class);
    private final EnumMap<TianfuPage,String> pageSelection=new EnumMap<>(TianfuPage.class),pageQuery=new EnumMap<>(TianfuPage.class);
    private TianfuPage page=TianfuPage.DIFFICULTY;
    private TianfuSelection.Snapshot snapshot;
    private List<String> purchased=List.of(),pending=List.of();
    private int kongqiao,points,difficulty,rolls,detailScroll,detailMax,modalScroll,modalMax;
    private int left,right,top,bottom,listLeft,listRight,maxScroll,waitTicks;
    private boolean confirmed,wide,detailOpen,ownedOnly,dragging,debug,loadingSearch;
    private double mx,my;
    private String region="",background="",selectedId="",action,pendingId="",message="",notice="";
    private long pendingRevision=-1;
    private EditBox search;
    private Hit listBox,detailBox,detailBody,bar,detailAction,backHit,filterHit,closeHit;
    private Hit rollHit,acceptHit,confirmHit,modalBox,modalBody,yesHit,noHit;

    public TianfuViewScreen(int q,int balance,int mode,List<String> owned,boolean locked,int count,List<String> candidates){
        super(Component.literal("天命 · 命格录"));load(q,balance,mode,owned,locked,count,candidates);
    }
    private void load(int q,int balance,int mode,List<String> owned,boolean locked,int count,List<String> candidates){
        kongqiao=q;points=balance;difficulty=mode;purchased=List.copyOf(owned);confirmed=locked;
        rolls=count;pending=List.copyOf(candidates);waitTicks=0;
        refreshV2(TianfuV2StatePayload.latest());if(snapshot!=null)mergeSnapshot();buildRows();
    }
    public void refresh(int q,int balance,int mode,List<String> owned,boolean locked,int count,List<String> candidates){load(q,balance,mode,owned,locked,count,candidates);layout();}
    /** Honor the supplied state rather than replacing it with an unrelated static cache. */
    public void refreshV2(TianfuSelection.Snapshot state){
        if(state==null||snapshot!=null&&state.revision()<=snapshot.revision())return;
        snapshot=state;mergeSnapshot();clearPending();restoreOrigin();buildRows();
    }
    private void mergeSnapshot(){points=snapshot.points();difficulty=snapshot.difficulty();confirmed=snapshot.confirmed();purchased=List.copyOf(snapshot.purchased());}
    private void restoreOrigin(){var o=snapshot==null?null:snapshot.origin();region=o==null||o.regionId()==null?"":o.regionId();background=o==null||o.backgroundId()==null?"":o.backgroundId();}
    @Override protected void init(){
        refreshV2(TianfuV2StatePayload.latest());buildRows();layout();
        search=new EditBox(font,listLeft+5,top-22,Math.max(24,listRight-listLeft-65),16,Component.literal("查找当前页"));
        search.setBordered(false);search.setTextColor(TEXT);search.setTextColorUneditable(DIM);search.setMaxLength(64);search.setHint(Component.literal("查找名称、效果…"));
        search.setValue(pageQuery.getOrDefault(page,""));
        search.setResponder(q->{if(!loadingSearch){pageQuery.put(page,q);pageScroll.put(page,0);buildRows();layout();}});addRenderableWidget(search);
    }
    @Override public void tick(){if(waitTicks>0&&--waitTicks==0)notice="若状态未更新，请关闭后按K重新查询。";refreshV2(TianfuV2StatePayload.latest());}
    private TianfuPool.Spec specFor(String id){var s=TianfuPool.byId(id);return s!=null?s:TianfuTalents.byIdIncludingUnavailable(id);}
    private boolean owned(TianfuPool.Spec s){return purchased.contains(s.id());}
    private boolean rowChosen(Row r){if(r.spec()!=null)return owned(r.spec());if(r.option()!=null)return TianfuDifficulty.OPTIONS.indexOf(r.option())==difficulty;return r.id().equals("region:"+region)||r.id().equals("background:"+background);}
    private void buildRows(){
        rows.clear();
        if(page==TianfuPage.DIFFICULTY){for(var o:TianfuDifficulty.OPTIONS)rows.add(new Row("difficulty:"+o.id(),o.name(),o.desc(),null,o));}
        else if(page==TianfuPage.ORIGIN){
            for(var r:TianfuOriginTable.REGIONS)rows.add(new Row("region:"+r.id(),r.name(),regionDesc(r),null,null));
            for(var b:TianfuOriginTable.BACKGROUNDS)rows.add(new Row("background:"+b.id(),b.name(),backgroundDesc(b),null,null));
        }else{
            if(page==TianfuPage.APTITUDE)rows.add(new Row("native","本体资质 · "+aptitudeLabel(),"本体资质只读。十绝体与历史机缘记录在本页；旧机缘不再新售。",null,null));
            for(String id:TianfuPageTable.BY_ID.keySet().stream().sorted().toList()){
                if(TianfuPageTable.BY_ID.get(id)!=page)continue;
                var s=specFor(id);if(s==null||!TianfuTalents.isAvailable(id)&&!owned(s)&&!pending.contains(id))continue;
                rows.add(new Row(id,s.name(),s.desc(),s,null));
            }
        }
        String q=pageQuery.getOrDefault(page,"").toLowerCase(Locale.ROOT).trim();
        rows.removeIf(r->(!q.isEmpty()&&!(r.title()+" "+r.desc()+" "+(r.spec()==null?"":r.spec().quality())).toLowerCase(Locale.ROOT).contains(q))||ownedOnly&&!rowChosen(r));
        String remembered=pageSelection.getOrDefault(page,selectedId);boolean exists=false;
        for(Row r:rows)if(r.id().equals(remembered)){exists=true;break;}
        selectedId=exists?remembered:rows.isEmpty()?"":rows.getFirst().id();pageSelection.put(page,selectedId);
    }
    private Row selected(){for(Row r:rows)if(r.id().equals(selectedId))return r;return null;}
    private void layout(){
        left=Math.max(6,(width-1120)/2);right=width-left;wide=width>=620&&height>=320;nav.clear();
        if(wide)for(int i=0;i<7;i++)nav.add(new Hit(left+8,62+i*26,left+78,85+i*26));
        else for(int i=0;i<7;i++)nav.add(new Hit(left+(right-left)*i/7,39,left+(right-left)*(i+1)/7-2,58));
        listLeft=wide?left+91:left+8;listRight=wide?listLeft+Math.min(286,(right-listLeft)*45/100):right-8;
        top=wide?85:87;bottom=Math.max(top+20,height-62);listBox=new Hit(listLeft,top,listRight,bottom);
        detailBox=wide?new Hit(listRight+12,62,right-8,bottom):new Hit(listLeft,62,listRight,bottom);
        backHit=new Hit(detailBox.x0+4,detailBox.y0+4,detailBox.x0+47,detailBox.y0+22);
        detailAction=new Hit(detailBox.x0+6,bottom-23,detailBox.x1-6,bottom-3);
        detailBody=new Hit(detailBox.x0+8,detailBox.y0+30,detailBox.x1-8,Math.max(detailBox.y0+42,bottom-30));
        maxScroll=Math.max(0,rows.size()*35-listBox.h());int offset=(int)clamp(pageScroll.getOrDefault(page,0),0,maxScroll);pageScroll.put(page,offset);
        rowHits.clear();for(int i=0;i<rows.size();i++)rowHits.add(new Hit(listLeft,top+i*35-offset,listRight-7,top+(i+1)*35-offset));
        bar=new Hit(listRight-5,top,listRight,bottom);filterHit=new Hit(listRight-52,top-24,listRight,top-5);closeHit=new Hit(right-25,8,right-7,26);
        rollHit=new Hit(left+8,height-53,width/2-3,height-32);acceptHit=new Hit(width/2+3,height-53,right-8,height-32);confirmHit=new Hit(left+8,height-27,right-8,height-6);
        if(search!=null){search.setX(listLeft+5);search.setY(top-21);search.setWidth(Math.max(24,listRight-listLeft-65));search.visible=!modal()&&(wide||!detailOpen);search.active=search.visible;}
        int mw=Math.min(560,width-20),mh=Math.min(height-16,260);modalBox=new Hit((width-mw)/2,(height-mh)/2,(width+mw)/2,(height+mh)/2);
        modalBody=new Hit(modalBox.x0+12,modalBox.y0+29,modalBox.x1-12,modalBox.y1-36);yesHit=new Hit(modalBox.x0+12,modalBox.y1-27,width/2-4,modalBox.y1-7);noHit=new Hit(width/2+4,modalBox.y1-27,modalBox.x1-12,modalBox.y1-7);
    }
    private boolean modal(){return action!=null;}
    private boolean actionable(){return snapshot!=null&&!confirmed&&waitTicks==0;}
    private boolean hasAnchorSelection(){return kongqiao>=1&&kongqiao<=10||purchased.stream().map(this::specFor).anyMatch(s->s!=null&&(s.kind()==TianfuPool.Kind.SIJUE||s.kind()==TianfuPool.Kind.JIYUAN));}
    private boolean canConfirm(){return actionable()&&TianfuDifficulty.byIndex(difficulty)!=null&&hasAnchorSelection();}
    private boolean canRoll(){return actionable()&&TianfuDifficulty.byIndex(difficulty)!=null&&rolls<TianfuRoll.MAX_ROLLS;}
    private boolean canAccept(){return actionable()&&!pending.isEmpty()&&TianfuDifficulty.byIndex(difficulty)!=null;}
    private TianfuUiRules.Decision decision(Row r){
        if(!actionable())return new TianfuUiRules.Decision(false,confirmed?"本世已确认，只供查阅":snapshot==null?"等待服务端同步":"正在等待服务端",points);
        if(r==null)return new TianfuUiRules.Decision(false,"请选择一个条目",points);
        if(r.spec()!=null)return owned(r.spec())?TianfuUiRules.revoke(snapshot,r.spec()):TianfuUiRules.purchase(snapshot,r.spec());
        if(page==TianfuPage.ORIGIN&&TianfuDifficulty.byIndex(difficulty)==null)
            return new TianfuUiRules.Decision(false,"请先选择开局难度",points);
        boolean allowed=r.option()!=null?difficulty<0:r.id().startsWith("region:")||r.id().startsWith("background:");
        return new TianfuUiRules.Decision(allowed,allowed?"确认前不会生效":"此项只读",points);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,INK);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        mx=mouseX;my=mouseY;refreshV2(TianfuV2StatePayload.latest());layout();renderBackground(g,mouseX,mouseY,partial);frame(g,new Hit(left,4,right,height-3),LINE);drawTablet(g,left+12,12);
        g.drawString(font,"天命",left+29,12,LIGHT,false);
        String summary="自由点 "+points+"  /  补点 "+(snapshot==null?"同步中":snapshot.negativeGrant()+"/24")+"  /  "+(confirmed?"本世已定":"命格未定");
        g.drawString(font,fit(summary,right-left-62),left+29,26,MUTED,false);button(g,closeHit,"×",true,false);
        for(int i=0;i<nav.size();i++)button(g,nav.get(i),TianfuPage.values()[i].label,true,page.ordinal()==i);
        if(wide)g.fill(left+84,61,left+85,bottom,LINE);
        if(wide||!detailOpen){
            g.fill(listLeft,top-25,listRight,top-3,PANEL);button(g,filterHit,ownedOnly?"全部":"已选",true,ownedOnly);g.enableScissor(listLeft,top,listRight,bottom);
            for(int i=0;i<rows.size();i++){
                Hit h=rowHits.get(i);if(h.y1<=top||h.y0>=bottom)continue;Row r=rows.get(i);boolean chosen=r.id().equals(selectedId);
                g.fill(h.x0,h.y0,h.x1,h.y1-3,chosen?0xFF342B1B:i%2==0?PANEL:0xFF201C16);if(chosen)g.fill(h.x0,h.y0,h.x0+2,h.y1-3,GOLD);
                g.drawString(font,fit((rowChosen(r)?"◆ ":"")+r.title(),h.w()-13),h.x0+7,h.y0+5,r.spec()==null?LIGHT:qualityColor(r.spec().quality()),false);
                g.drawString(font,fit(rowMeta(r),h.w()-13),h.x0+7,h.y0+19,MUTED,false);
            }
            if(rows.isEmpty())g.drawString(font,fit("没有匹配项，清空搜索或切回全部。",listBox.w()-12),listLeft+6,top+13,MUTED,false);
            g.disableScissor();drawScroll(g,bar,pageScroll.getOrDefault(page,0),maxScroll);if(search!=null)search.render(g,mouseX,mouseY,partial);
        }
        if(wide||detailOpen)renderDetail(g);
        String rollLabel=confirmed?"本世已锁定":difficulty<0?"先选难度再随机":"随机候选（余"+Math.max(0,TianfuRoll.MAX_ROLLS-rolls)+"次）";
        button(g,rollHit,rollLabel,canRoll(),false);button(g,acceptHit,pending.isEmpty()?"尚无随机候选":"接受候选（"+pending.size()+"项）",canAccept(),false);
        String label=snapshot==null?"正在同步命格…":waitTicks>0?"等待服务端核对…":confirmed?"本世命格已定 · 只读":difficulty<0?"先选择开局难度":!hasAnchorSelection()?"请到资质页选择十绝体，或取得本体资质":"核对全部选择 · 最终确认";
        button(g,confirmHit,label,canConfirm(),true);
        if(!notice.isEmpty()&&!modal()&&height>280)g.drawString(font,fit(notice,right-left-20),left+10,height-65,MUTED,false);
        if(debug)for(Hit h:rowHits)if(h.y0>=top&&h.y1<=bottom)frame(g,h,0xFF55DD77);if(modal())renderConfirm(g);
    }
    private void renderDetail(GuiGraphics g){
        g.fill(detailBox.x0,detailBox.y0,detailBox.x1,detailBox.y1,PANEL);Row r=selected();if(!wide)button(g,backHit,"返回",true,false);
        int tx=wide?detailBox.x0+8:backHit.x1+7;g.drawString(font,fit(r==null?"命格详录":r.title(),detailBox.x1-tx-8),tx,detailBox.y0+9,LIGHT,false);g.fill(detailBox.x0+8,detailBox.y0+24,detailBox.x1-8,detailBox.y0+25,LINE);
        var lines=wrap(r==null?"先选择一项查看效果与代价。":detailText(r),detailBody.w()-7);detailMax=Math.max(0,lines.size()*12-detailBody.h());detailScroll=(int)clamp(detailScroll,0,detailMax);
        g.enableScissor(detailBody.x0,detailBody.y0,detailBody.x1,detailBody.y1);for(int i=0;i<lines.size();i++){int y=detailBody.y0+i*12-detailScroll;if(y+10>=detailBody.y0&&y<detailBody.y1)g.drawString(font,lines.get(i),detailBody.x0,y,TEXT,false);}g.disableScissor();
        drawScroll(g,new Hit(detailBody.x1-3,detailBody.y0,detailBody.x1,detailBody.y1),detailScroll,detailMax);var d=decision(r);button(g,detailAction,primaryLabel(r,d),d.allowed(),d.allowed());
    }
    private String primaryLabel(Row r,TianfuUiRules.Decision d){if(!d.allowed())return d.reason();if(r.spec()!=null)return owned(r.spec())?"撤销此项":"选择此命格 · "+TianfuUiRules.price(r.spec().cost());if(r.option()!=null)return "选定难度 · 获得"+r.option().points()+"点";return r.id().startsWith("region:")?"选用这个地域":"选用这个出身";}
    private String detailText(Row r){
        String s=r.desc()+"\n\n";
        if(r.spec()!=null){var spec=r.spec();s+="品质："+spec.quality()+"\n目录："+TianfuUiRules.price(spec.cost())+"\n";var receipt=snapshot==null?null:snapshot.receipts().get(spec.id());
            if(receipt!=null)s+="原成交："+TianfuUiRules.price(receipt.paidCost())+"；撤销按此记录。\n";
            var d=decision(r);s+="状态："+(owned(spec)?"已选择":"尚未选择")+"\n"+d.reason();if(d.allowed())s+="\n本次操作后余额："+d.afterPoints()+"点（服务端复核）";
            if(pending.contains(spec.id()))s+="\n随机候选：尚未接受，尚未扣点。";if(spec.cost()<0)s+="\n负面补点计入24点总上限，撤销时扣回原补点。硬代价请逐项核对。";
            if(TianfuMindTable.LEVELS.containsKey(spec.id()))s+="\n同族层级互斥：改选时先按旧成交价退款，再扣新价。";
        }else if(r.option()!=null)s+="难度选定后本世不可更改，基础点只发放一次。\n随机与手动配点共用当前余额，接受候选后仍须最终确认。";
        else if(page==TianfuPage.ORIGIN)s+="当前组合："+regionName()+" / "+backgroundName()+"\n地域和类型需要一并提交；取消弹窗会恢复原出身。\n效果在本世最终确认时结算。";
        return s;
    }
    private String rowMeta(Row r){if(r.spec()!=null)return r.spec().quality()+"  "+TianfuUiRules.price(r.spec().cost())+(pending.contains(r.id())?"  随机候选":"");if(r.option()!=null)return "基础 "+r.option().points()+" 点"+(rowChosen(r)?"  本世已定":"  不可改选");return r.id().startsWith("region:")?"地域 / "+(rowChosen(r)?"已选":"查看效果"):r.id().startsWith("background:")?"出身类型 / "+(rowChosen(r)?"已选":"查看代价"):"本体只读";}
    private void renderConfirm(GuiGraphics g){
        g.fill(0,0,width,height,0xD9000000);g.fill(modalBox.x0,modalBox.y0,modalBox.x1,modalBox.y1,PANEL);frame(g,modalBox,GOLD);g.drawString(font,action.equals("confirm")?"定下此生命格":"核对这次选择",modalBox.x0+12,modalBox.y0+11,LIGHT,false);
        var lines=wrap(message,modalBody.w()-8);modalMax=Math.max(0,lines.size()*12-modalBody.h());modalScroll=(int)clamp(modalScroll,0,modalMax);g.enableScissor(modalBody.x0,modalBody.y0,modalBody.x1,modalBody.y1);
        for(int i=0;i<lines.size();i++){int y=modalBody.y0+i*12-modalScroll;if(y+10>=modalBody.y0&&y<modalBody.y1)g.drawString(font,lines.get(i),modalBody.x0,y,TEXT,false);}g.disableScissor();
        drawScroll(g,new Hit(modalBody.x1-3,modalBody.y0,modalBody.x1,modalBody.y1),modalScroll,modalMax);button(g,yesHit,"确定提交",actionable()&&snapshot.revision()==pendingRevision,true);button(g,noHit,"返回修改",true,false);
    }
    private void ask(String kind,String id,String text){action=kind;pendingId=id;message=text;pendingRevision=snapshot.revision();modalScroll=0;dragging=false;if(search!=null)search.setFocused(false);layout();}
    private void clearPending(){action=null;pendingId="";message="";pendingRevision=-1;modalScroll=0;}
    private void activateSelected(){
        Row r=selected();if(r==null||!decision(r).allowed())return;
        if(r.spec()!=null){var d=decision(r);ask(owned(r.spec())?"revoke":"buy",r.id(),r.title()+"\n\n"+detailText(r)+"\n\n提交后余额预计 "+d.afterPoints()+"点，最终以服务端为准。");}
        else if(r.option()!=null)ask("difficulty",r.option().id(),"难度："+r.title()+"\n获得基础点："+r.option().points()+"\n\n难度本世不可更改，基础点只发一次。\n"+r.desc());
        else if(page==TianfuPage.ORIGIN){
            if(r.id().startsWith("region:"))region=r.id().substring(7);else if(r.id().startsWith("background:"))background=r.id().substring(11);buildRows();
            if(region.isEmpty()||background.isEmpty()){notice="再选择一种"+(region.isEmpty()?"地域":"出身类型")+"组成出身。";return;}
            var result=TianfuEconomy.selectOrigin(snapshot,new TianfuSelection.Origin(region,background));
            if(!result.ok()){notice="该组合无法提交：余额、补点或确认状态不满足条件。";restoreOrigin();return;}
            ask("origin","","地域："+regionName()+"\n类型："+backgroundName()+"\n\n旧出身按原成交记录结算，新的组合提交后余额 "+result.snapshot().points()+"点。\n负面补点合计 "+result.snapshot().negativeGrant()+"/24。\n最终确认之前，此处只记录选择。");
        }
    }
    private String finalReview(){
        StringBuilder b=new StringBuilder("最终确认后，本世不能再购买、撤销或改选。\n\n");var mode=TianfuDifficulty.byIndex(difficulty);
        b.append("难度：").append(mode==null?"未选":mode.name()).append("\n出身：").append(regionName()).append(" / ").append(backgroundName()).append("\n余额：").append(points).append("点\n\n已选命格：\n");
        if(purchased.isEmpty())b.append("无额外词条，保留本体资质。\n");for(String id:purchased){var s=specFor(id);b.append(s==null?id:s.name()).append("：").append(s==null?"历史记录，请核实":s.desc()).append("\n\n");}
        if(!pending.isEmpty())b.append("尚有").append(pending.size()).append("项随机候选未接受；本次确认不会购买这些候选。\n\n");b.append("逐项核对负面代价，包括资质锁定、气运归零和寿元禁增。由服务端预检后结算。");return b.toString();
    }
    private void submit(){
        if(!actionable()||snapshot.revision()!=pendingRevision){clearPending();return;}
        switch(action){case "buy"->PacketDistributor.sendToServer(new TianfuBuyPayload(pendingId));case "revoke"->PacketDistributor.sendToServer(new TianfuRevokePayload(pendingId));case "difficulty"->PacketDistributor.sendToServer(new TianfuDifficultyPayload(pendingId));case "origin"->PacketDistributor.sendToServer(new TianfuOriginPayload(region,background,pendingRevision));case "roll"->PacketDistributor.sendToServer(new TianfuRollPayload(TianfuDifficulty.byIndex(difficulty).id()));case "accept"->PacketDistributor.sendToServer(new TianfuRollAcceptPayload());case "confirm"->PacketDistributor.sendToServer(new TianfuConfirmPayload());default->{clearPending();return;}}
        clearPending();waitTicks=60;notice="已提交，等待服务端核对。";
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        mx=x;my=y;refreshV2(TianfuV2StatePayload.latest());layout();if(button!=0)return super.mouseClicked(x,y,button);
        if(modal()){if(noHit.contains(x,y)){clearPending();restoreOrigin();buildRows();}else if(yesHit.contains(x,y))submit();return true;}
        if(closeHit.contains(x,y)){onClose();return true;}for(int i=0;i<nav.size();i++)if(nav.get(i).contains(x,y)){switchPage(TianfuPage.values()[i]);return true;}
        if(rollHit.contains(x,y)){if(canRoll())ask("roll","","生成一组随机候选，仅成功生成才使用一次机会。\n\n候选基于当前余额和已有命格；接受后追加到已选，不会替换原选择。\n剩余 "+Math.max(0,TianfuRoll.MAX_ROLLS-rolls)+"次。\n接受后仍须最终确认；资质不足时请到资质页补选。");return true;}
        if(acceptHit.contains(x,y)){if(canAccept())ask("accept","","接受以下随机候选？\n\n"+candidateText()+"\n成功后结算并加入已选，失败保留候选。接受不会自动最终确认。");return true;}
        if(confirmHit.contains(x,y)){if(canConfirm())ask("confirm","",finalReview());return true;}
        if((wide||detailOpen)&&detailAction.contains(x,y)){activateSelected();return true;}if(!wide&&detailOpen){if(backHit.contains(x,y)){detailOpen=false;layout();}return true;}
        if(filterHit.contains(x,y)){ownedOnly=!ownedOnly;pageScroll.put(page,0);buildRows();layout();return true;}if(search!=null&&search.mouseClicked(x,y,button))return true;if(search!=null)search.setFocused(false);
        if(bar.contains(x,y)&&maxScroll>0){dragging=true;applyBar(y);return true;}if(listBox.contains(x,y))for(int i=0;i<rowHits.size();i++)if(rowHits.get(i).contains(x,y)){selectedId=rows.get(i).id();pageSelection.put(page,selectedId);detailScroll=0;detailOpen=true;layout();return true;}
        return super.mouseClicked(x,y,button);
    }
    private String candidateText(){StringBuilder b=new StringBuilder();for(String id:pending){var s=specFor(id);b.append(s==null?id:s.name()+" · "+TianfuUiRules.price(s.cost())).append("\n");}return b.toString();}
    private void switchPage(TianfuPage next){page=next;selectedId=pageSelection.getOrDefault(page,"");detailScroll=0;detailOpen=false;ownedOnly=false;loadingSearch=true;if(search!=null)search.setValue(pageQuery.getOrDefault(page,""));loadingSearch=false;buildRows();layout();}
    private void applyBar(double y){int thumb=Math.max(12,bar.h()*bar.h()/Math.max(1,bar.h()+maxScroll));pageScroll.put(page,(int)Math.round(clamp((y-bar.y0-thumb/2.0)/Math.max(1,bar.h()-thumb),0,1)*maxScroll));layout();}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging&&!modal()){applyBar(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(modal()){modalScroll=(int)clamp(modalScroll-sy*24,0,modalMax);return true;}if((wide||detailOpen)&&detailBox.contains(x,y)){detailScroll=(int)clamp(detailScroll-sy*24,0,detailMax);return true;}if((wide||!detailOpen)&&listBox.contains(x,y)){pageScroll.put(page,(int)clamp(pageScroll.getOrDefault(page,0)-sy*35,0,maxScroll));layout();return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(modal()){clearPending();restoreOrigin();buildRows();layout();return true;}if(search!=null&&search.isFocused()){search.setFocused(false);return true;}if(!wide&&detailOpen){detailOpen=false;layout();return true;}}
        if(modal()){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)submit();return true;}if(hasControlDown()&&key==GLFW.GLFW_KEY_F){if(!wide)detailOpen=false;layout();search.setFocused(true);return true;}
        if(search!=null&&search.isFocused()&&search.keyPressed(key,scan,mods))return true;if(key==GLFW.GLFW_KEY_F6){debug=!debug;return true;}return super.keyPressed(key,scan,mods);
    }
    @Override public boolean charTyped(char character,int mods){return !modal()&&search!=null&&search.isFocused()?search.charTyped(character,mods):super.charTyped(character,mods);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){clearPending();super.onClose();}
    private void button(GuiGraphics g,Hit h,String text,boolean active,boolean chosen){boolean bright=active&&chosen;g.fill(h.x0,h.y0,h.x1,h.y1,bright?0xFF3D311A:active?TILE:PANEL);frame(g,h,bright?GOLD:active&&h.contains(mx,my)?LINE:0xFF393125);String label=fit(text,h.w()-10);g.drawString(font,label,h.x0+Math.max(4,(h.w()-font.width(label))/2),h.y0+(h.h()-8)/2,active?bright?LIGHT:TEXT:DIM,false);}
    private void frame(GuiGraphics g,Hit h,int c){if(h.w()<=0||h.h()<=0)return;g.fill(h.x0,h.y0,h.x1,h.y0+1,c);g.fill(h.x0,h.y1-1,h.x1,h.y1,c);g.fill(h.x0,h.y0,h.x0+1,h.y1,c);g.fill(h.x1-1,h.y0,h.x1,h.y1,c);}
    private void drawScroll(GuiGraphics g,Hit h,int offset,int max){if(max<=0||h.h()<=0)return;g.fill(h.x0,h.y0,h.x1,h.y1,0xFF30281C);int thumb=Math.min(h.h(),Math.max(10,h.h()*h.h()/Math.max(1,h.h()+max)));int y=h.y0+(h.h()-thumb)*offset/max;g.fill(h.x0,y,h.x1,y+thumb,LINE);}
    private void drawTablet(GuiGraphics g,int x,int y){g.fill(x,y,x+10,y+18,0xFF343B2D);frame(g,new Hit(x,y,x+10,y+18),LINE);g.fill(x+2,y+5,x+7,y+6,GOLD);g.fill(x+5,y+6,x+6,y+11,LIGHT);g.fill(x+3,y+11,x+8,y+12,GOLD);}
    private String fit(String s,int available){if(s==null||available<=0)return "";if(font.width(s)<=available)return s;String suffix=font.width("…")<=available?"…":"";return font.plainSubstrByWidth(s,Math.max(0,available-font.width(suffix)))+suffix;}
    private List<String> wrap(String text,int available){List<String> out=new ArrayList<>();if(text==null)return out;for(String paragraph:text.split("\n",-1)){String rest=paragraph;if(rest.isEmpty()){out.add("");continue;}while(!rest.isEmpty()){String line=font.plainSubstrByWidth(rest,Math.max(1,available));if(line.isEmpty())line=rest.substring(0,Character.charCount(rest.codePointAt(0)));out.add(line);rest=rest.substring(line.length());}}return out;}
    private String aptitudeLabel(){return switch(kongqiao){case 2->"甲等";case 1,4->"乙等";case 5,6,7->"丙等";case 8,9,10->"丁等";case 3->"十绝体";case 0->"未开窍";default->"未知档位";};}
    private String regionDesc(TianfuOriginTable.Region r){return switch(r.id()){case "beiyuan"->"力道道痕 +200";case "nanjiang"->"毒道道痕 +200，炼蛊加成 +10%";case "ximo"->"金道道痕 +200";case "donghai"->"水道道痕 +200";case "zhongzhou"->"44项非汇总道痕各 +30";default->"以本体数据为准";};}
    private String backgroundDesc(TianfuOriginTable.Background b){return TianfuUiRules.price(b.cost())+"\n气运上限 "+signed(b.luckCapBonus())+"\n炼蛊加成 "+signed(b.lianguBonus())+"\n确认时一次性元石 "+signed(b.yuanshiBonus());}
    private String signed(double v){return(v>=0?"+":"")+Math.round(v*100)+"%";}
    private String regionName(){return TianfuOriginTable.REGIONS.stream().filter(r->r.id().equals(region)).map(TianfuOriginTable.Region::name).findFirst().orElse("未选地域");}
    private String backgroundName(){return TianfuOriginTable.BACKGROUNDS.stream().filter(b->b.id().equals(background)).map(TianfuOriginTable.Background::name).findFirst().orElse("未选类型");}
    private int qualityColor(String q){return switch(q){case "极"->RED;case "神"->LIGHT;case "天","十绝体"->0xFFD3ABCA;case "地"->0xFFA3BAC7;case "玄"->0xFFA2BEAA;case "负"->RED;default->TEXT;};}
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
