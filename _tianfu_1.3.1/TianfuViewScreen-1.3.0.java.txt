// src/main/java/net/guzhenren/tianfu/client/TianfuViewScreen.java
package net.guzhenren.tianfu.client;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.tianfu.TianfuRoll;
import net.guzhenren.tianfu.TianfuTalents;
import net.guzhenren.tianfu.v2.TianfuPageTable;
import net.guzhenren.tianfu.v2.TianfuMindTable;
import net.guzhenren.tianfu.v2.TianfuOriginTable;
import net.guzhenren.tianfu.net.TianfuV2StatePayload;
import net.guzhenren.tianfu.net.TianfuBuyPayload;
import net.guzhenren.tianfu.net.TianfuRevokePayload;
import net.guzhenren.tianfu.net.TianfuDifficultyPayload;
import net.guzhenren.tianfu.net.TianfuOriginPayload;
import net.guzhenren.tianfu.net.TianfuRollPayload;
import net.guzhenren.tianfu.net.TianfuRollAcceptPayload;
import net.guzhenren.tianfu.net.TianfuConfirmPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuPool;
import net.guzhenren.tianfu.v2.TianfuPage;
import net.guzhenren.tianfu.v2.TianfuSelection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 服务端权威的七页天赋界面；布局命中与绘制分离，所有交易只发送意图。 */
public class TianfuViewScreen extends Screen {
    private enum Tab {
        DIFFICULTY("难度"), ORIGIN("出身"), APTITUDE("资质"), LIUPAI("流派"),
        PHYSIQUE("体质"), MIND("心智"), QIYUN("气运");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private int kongqiao, points, difficulty, rolls;
    private boolean confirmed, dragging, debug;
    private List<String> purchased = List.of(), pending = List.of();
    private final List<Row> rows = new ArrayList<>();
    private final List<Hit> nav = new ArrayList<>(), rowHits = new ArrayList<>();
    private final Map<String,List<String>> wrapped = new HashMap<>();
    private int left, right, top, bottom, maxScroll;
    private double mx, my;
    private Hit bar, rollHit, acceptHit, confirmHit, yesHit, noHit;
    private String region="", background="", action, pendingId="", message="";
    private long pendingRevision=-1;
    /** Immutable geometry computed by layout; painting never changes hit regions. */
    private record Hit(int x0,int y0,int x1,int y1) {
        boolean contains(double x,double y) { return x>=x0&&x<x1&&y>=y0&&y<y1; }
    }
    /** Copies authority fields without changing legacy-only aperture and roll state. */
    private void mergeSnapshot() {
        points=snapshot.points(); difficulty=snapshot.difficulty();
        confirmed=snapshot.confirmed(); purchased=List.copyOf(snapshot.purchased());
    }
    /** Restores only the server-selected origin; missing origin remains unselected. */
    private void restoreOrigin() {
        var o=snapshot==null?null:snapshot.origin();
        region=o==null?"":o.regionId(); background=o==null?"":o.backgroundId();
    }
    /** Looks up unavailable historical talents without dropping their purchase record. */
    private TianfuPool.Spec specFor(String id) {
        var s=TianfuPool.byId(id); if(s!=null)return s;
        var t=TianfuTalents.byIdIncludingUnavailable(id);
        return t==null?null:new TianfuPool.Spec(t.id(),t.name(),TianfuPool.Kind.ENTRY,t.quality(),t.cost(),0,t.desc());
    }
    /** Opens a local intent dialog tied to the current authority revision. */
    private void ask(String kind,String id,String text) {
        action=kind; pendingId=id; message=text; pendingRevision=snapshot.revision();
    }
    /** Draws a precomputed button without allocating geometry. */
    private void drawButton(GuiGraphics g,Hit h,String text,boolean active) {
        g.fill(h.x0,h.y0,h.x1,h.y1,active?0xFF405166:0xFF273341);
        g.drawString(font,fit(text,h.x1-h.x0-6),h.x0+3,h.y0+6,active?0xFFF0DDA2:0xFF929AA6);
    }
    /** Shows exact historical receipt prices, including same-family replacement. */
    private String receiptHint(TianfuPool.Spec s) {
        if(snapshot==null)return "；成交价待同步";
        var receipt=snapshot.receipts().get(s.id());
        if(receipt!=null)return "；原成交成本 "+receipt.paidCost()+"，撤销按原价";
        var level=TianfuMindTable.LEVELS.get(s.id());
        if(level!=null) for(String id:purchased) {
            var old=TianfuMindTable.LEVELS.get(id);
            if(old!=null&&old.family().equals(level.family())) {
                var r=snapshot.receipts().get(id);
                return r==null?"；同族重选，旧成交价缺失，等待服务端校验":"；同族互斥：替换 "+id+"，按旧成交价 "+r.paidCost()+" 退款后扣新价";
            }
        }
        return "";
    }
    /** Maps the legacy aperture code to a read-only native grade. */
    private String aptitudeLabel() {
        return switch(kongqiao){case 2->"甲等";case 1,4->"乙等";case 5,6,7->"丙等";case 8,9,10->"丁等";case 3->"十绝体";case 0->"未开窍";default->"未知档位 "+kongqiao;};
    }
    /** Describes region data without changing native field names or values. */
    private String regionDesc(TianfuOriginTable.Region r) {
        return (r.id().equals("zhongzhou")?"44项非汇总道痕各+30":r.daohen().toString())+(r.lianguBonus()==0?"":"；炼蛊 +10%");
    }
    /** Describes the explicit origin effect data; no prices or balances are inferred. */
    private String backgroundDesc(TianfuOriginTable.Background b) {
        return "气运上限 "+Math.round(b.luckCapBonus()*100)+"%；炼蛊 +"+Math.round(b.lianguBonus()*100)+"%；确认一次性元石 +"+Math.round(b.yuanshiBonus()*100)+"%";
    }

    private TianfuPage page = TianfuPage.DIFFICULTY;
    private final EnumMap<TianfuPage, Integer> pageScroll = new EnumMap<>(TianfuPage.class);
    private TianfuSelection.Snapshot snapshot;
    private record Row(String header, TianfuPool.Spec spec, TianfuDifficulty.Option option) {
        static Row header(String h) { return new Row(h, null, null); }
        static Row spec(TianfuPool.Spec s) { return new Row(null, s, null); }
        static Row option(TianfuDifficulty.Option o) { return new Row(null, null, o); }
    }
    /**
     * 保留旧7参数构造器；kongqiao为只读档位，points/difficulty/purchased来自服务端。
     * confirmed控制锁定；rolls/pending兼容旧随机协议；出身通过refreshV2单独同步。
     */
    public TianfuViewScreen(int kongqiao, int points, int difficulty, List<String> purchased,
                            boolean confirmed, int rolls, List<String> pending) {
        super(Component.literal("天赋"));
        load(kongqiao, points, difficulty, purchased, confirmed, rolls, pending);
    }
    /**
     * 认可合法原生kongqiao或已购SIJUE以及历史机缘，旧JIYUAN不再新售，具体见FILL-SPEC的U05。
     * @return 按方法职责返回展示结果
     */
    private boolean hasAnchorSelection(){
        return kongqiao >= 1 && kongqiao <= 10 || purchased.stream().map(this::specFor).anyMatch(s -> s != null && (s.kind() == TianfuPool.Kind.SIJUE || s.kind() == TianfuPool.Kind.JIYUAN));
    }

    /**
     * 接收原7参数状态；保留当前页和7页各自滚动偏移，不用客户端余额做权威扣点。
     * @param kongqiao 服务端只读状态
     * @param points 服务端只读状态
     * @param difficulty 服务端只读状态
     * @param purchased 服务端只读状态
     * @param confirmed 服务端只读状态
     * @param rolls 服务端只读状态
     * @param pending 服务端只读状态
     */
    private void load(int kongqiao, int points, int difficulty, List<String> purchased,
                      boolean confirmed, int rolls, List<String> pending){
        this.kongqiao = kongqiao;
        this.rolls = rolls;
        this.pending = List.copyOf(pending);
        this.points = points; this.difficulty = difficulty;
        this.purchased = List.copyOf(purchased); this.confirmed = confirmed;
        refreshV2(TianfuV2StatePayload.latest());
        if (snapshot != null) mergeSnapshot();
        buildRows();
    }

    /**
     * 原地更新服务端状态；同时等待V2出身快照，不重建Screen。
     * @param kongqiao 服务端只读状态
     * @param points 服务端只读状态
     * @param difficulty 服务端只读状态
     * @param purchased 服务端只读状态
     * @param confirmed 服务端只读状态
     * @param rolls 服务端只读状态
     * @param pending 服务端只读状态
     */
    public void refresh(int kongqiao, int points, int difficulty, List<String> purchased,
                        boolean confirmed, int rolls, List<String> pending){
        load(kongqiao, points, difficulty, purchased, confirmed, rolls, pending); layout();
    }

    /**
     * 保留随机状态兼容，随机结果按归属页展示不引入第八页。
     * @return 按方法职责返回展示结果
     */
    private boolean rollPreview(){
        return !pending.isEmpty();
    }

    /**
     * 仅比较服务端rolls与既有MAX_ROLLS，达到上限返回true，不能在客户端重置次数。
     * @return 按方法职责返回展示结果
     */
    private boolean rollExhausted(){
        return rolls >= TianfuRoll.MAX_ROLLS;
    }

    /**
     * 根据服务端rolls生成剩余重摇次数文案，显示值下限为0，不修改次数。
     * @return 按方法职责返回展示结果
     */
    private String rollAgainLabel(){
        return "随机（剩余 " + Math.max(0, TianfuRoll.MAX_ROLLS - rolls) + " 次）";
    }

    /**
     * 仅按TianfuPageTable构建当前页行；资质只读kongqiao加历史机缘和十绝体，心智有损仅只读引用。
     */
    private void buildRows(){
        rows.clear();
        if (page == TianfuPage.DIFFICULTY) {
            for (TianfuDifficulty.Option o : TianfuDifficulty.OPTIONS) rows.add(Row.option(o));
        } else if (page == TianfuPage.ORIGIN) {
            for (var r : TianfuOriginTable.REGIONS) rows.add(Row.header("地域 " + r.name() + " · 成本 " + r.cost() + (r.id().equals(region) ? " [已选]" : "") + " · " + regionDesc(r)));
            for (var b : TianfuOriginTable.BACKGROUNDS) rows.add(Row.header("类型 " + b.name() + " · 成本 " + b.cost() + (b.id().equals(background) ? " [已选]" : "") + " · " + backgroundDesc(b)));
        } else {
            if (page == TianfuPage.APTITUDE) rows.add(Row.header("本体资质（只读）：" + aptitudeLabel()));
            if (page == TianfuPage.MIND) rows.add(Row.header("心智有损在体质页购买；同族层级重选按旧成交价退款"));
            for (String id : TianfuPageTable.BY_ID.keySet().stream().sorted().toList()) {
                if (TianfuPageTable.BY_ID.get(id) != page) continue;
                TianfuPool.Spec spec = specFor(id);
                if (spec != null) rows.add(Row.spec(spec));
            }
        }
    }

    /**
     * 将pool内已购条目追加至out并返回追加数量，保持目录顺序，仅允许当前页条目。
     * @param out 仅用于当前页行展示的输出列表
     * @param pool 待筛选的当前页池条目，不得改写
     * @return 按方法职责返回展示结果
     */
    private int addOwned(List<Row> out, List<TianfuPool.Spec> pool){
        int n = 0; for (var s : pool) if (TianfuPageTable.BY_ID.get(s.id()) == page && isOwned(s)) { out.add(Row.spec(s)); n++; } return n;
    }

    /**
     * 按品质生成成本提示；负面必须提示2或5或8及统一24上限，不再固定返还2。
     * @param quality 品质字符串，仅用于成本提示或颜色
     * @return 按方法职责返回展示结果
     */
    private static String costHint(String quality){
        return switch (quality) { case "负" -> "补点 2/5/8，统一上限 24"; case "黄" -> "1 点"; case "玄" -> "2 点"; case "地" -> "3 点"; case "天" -> "5 点"; case "神" -> "12 点"; case "极", "十绝体" -> "16 点"; default -> "以目录成本为准"; };
    }

    /**
     * 初始化七页控件与命中区并恢复当前页偏移，不新建业务状态或默认收费出身。
     */
    protected void init(){
        refreshV2(TianfuV2StatePayload.latest()); buildRows(); layout();
    }

    /**
     * 统一分配7页导航和当前页可滚动命中区；窄屏分两行导航，任何页都必须可达。
     */
    private void layout(){
        left = 8; right = Math.max(left + 1, width - 8);
        int columns = width < 440 ? 4 : 7;
        nav.clear();
        for (int i = 0; i < 7; i++) {
            int col = i % columns, line = i / columns;
            nav.add(new Hit(left + (right-left)*col/columns, 24+line*23, left+(right-left)*(col+1)/columns-2, 45+line*23));
        }
        top = 28 + ((7 + columns - 1) / columns) * 23;
        bottom = Math.max(top + 1, height - 58);
        rowHits.clear();
        int total = rows.stream().mapToInt(this::rowHeight).sum();
        maxScroll = Math.max(0, total - (bottom-top));
        int offset = (int)clamp(pageScroll.getOrDefault(page, 0), 0, maxScroll);
        pageScroll.put(page, offset);
        int y = top-offset;
        for (Row row : rows) { int h = rowHeight(row); rowHits.add(new Hit(left, y, right-10, y+h)); y+=h; }
        bar = new Hit(right-8, top, right, bottom);
        rollHit = new Hit(left, height-48, width/2-2, height-28);
        acceptHit = new Hit(width/2+2, height-48, right, height-28);
        confirmHit = new Hit(left, height-24, right, height-4);
        yesHit = new Hit(width/2-90, height/2+26, width/2-4, height/2+46);
        noHit = new Hit(width/2+4, height/2+26, width/2+90, height/2+46);
    }

    /**
     * 购买或撤销或难度或出身或最终确认弹窗任一待定时返回true。
     * @return 按方法职责返回展示结果
     */
    private boolean modal(){
        return action != null;
    }

    /**
     * 使用当前鼠标逻辑坐标判断是否落入左闭右开矩形，坐标必须与layout一致。
     * @param x0 矩形或线段左边界逻辑像素
     * @param y0 矩形上边界逻辑像素
     * @param x1 矩形或线段右边界逻辑像素
     * @param y1 矩形下边界逻辑像素
     * @return 按方法职责返回展示结果
     */
    private boolean R(int x0, int y0, int x1, int y1){
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /**
     * 判断第i行是否在视口内且被鼠标命中，越界索引返回false。
     * @param i 当前页行索引，零起始
     * @return 按方法职责返回展示结果
     */
    private boolean listHit(int i){
        return i >= 0 && i < rowHits.size() && my >= top && my < bottom && rowHits.get(i).contains(mx, my);
    }

    /**
     * 按服务端购买集合判断spec.id已购，识别未安装附属的旧购记录。
     * @param spec 池中条目只读定义，ID为稳定购买键
     * @return 按方法职责返回展示结果
     */
    private boolean isOwned(TianfuPool.Spec spec){
        return purchased.contains(spec.id());
    }

    /**
     * 综合已购BODY或SIJUE与本体tizhi非0判断体质唯一约束，不能因换页重复购买。
     * @return 按方法职责返回展示结果
     */
    private boolean hasBody(){
        if (minecraft != null && minecraft.player != null && minecraft.player.getData(GuzhenrenModVariables.PLAYER_VARIABLES).tizhi != 0) return true;
        return purchased.stream().map(this::specFor).anyMatch(s -> s != null && (s.kind() == TianfuPool.Kind.BODY || s.kind() == TianfuPool.Kind.SIJUE));
    }

    /**
     * 展示服务端同口径预算与24补点限额，不能替代服务端校验。
     * @param spec 池中条目只读定义，ID为稳定购买键
     * @return 按方法职责返回展示结果
     */
    private boolean canBuy(TianfuPool.Spec spec){
        if (confirmed || snapshot == null || difficulty < 0 || isOwned(spec)) return false;
        if (TianfuPool.byId(spec.id()) == null) return false;
        if ((spec.kind() == TianfuPool.Kind.BODY || spec.kind() == TianfuPool.Kind.SIJUE) && hasBody()) return false;
        if (spec.kind() == TianfuPool.Kind.JIYUAN && hasJiyuan()) return false;
        // 仅提示可提交意图；余额、价格、层级替换与24上限全部交服务端校验。
        return true;
    }

    /**
     * 只读已购集合判断是否有JIYUAN，避免覆写式机缘重复收费。
     * @return 按方法职责返回展示结果
     */
    private boolean hasJiyuan(){
        return purchased.stream().map(this::specFor).anyMatch(s -> s != null && s.kind() == TianfuPool.Kind.JIYUAN);
    }

    /**
     * 按屏幕逻辑宽高绘制底色与装饰，不发包不触碰业务数据。
     * @param g 本次渲染的GuiGraphics画布
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param partialTick 当前帧插值比例
     */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick){
        g.fill(0, 0, width, height, 0xFF111720);
    }

    /**
     * 渲染七页导航和当前页专属列表、出身两组单选、补点已用24与原成交价退款提示。
     * @param g 本次渲染的GuiGraphics画布
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param partialTick 当前帧插值比例
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick){
        mx = mouseX; my = mouseY;
        refreshV2(TianfuV2StatePayload.latest()); layout();
        renderBackground(g, mouseX, mouseY, partialTick);
        g.drawString(font, fit("天赋 · 余额 " + points + " · 补点 " + (snapshot == null ? "加载中" : snapshot.negativeGrant()+"/24") + (confirmed ? " · 已确认，只读" : ""), right-left), left, 8, 0xFFE4D3A2);
        renderChips(g, left, right);
        g.enableScissor(left, top, right, bottom);
        for (int i=0; i<rows.size(); i++) {
            Row row=rows.get(i); Hit h=rowHits.get(i);
            if (h.y1<=top || h.y0>=bottom) continue;
            g.fill(h.x0, h.y0, h.x1, h.y1-1, i%2==0 ? 0xFF202A36 : 0xFF19232F);
            if (row.header()!=null) {
                List<String> lines=wrapDesc(row.header());
                for (int j=0;j<lines.size();j++) g.drawString(font, lines.get(j),left+4,h.y0+5+j*11,0xFFB6C9DD);
            } else if (row.option()!=null) {
                var o=row.option(); boolean chosen=TianfuDifficulty.OPTIONS.indexOf(o)==difficulty;
                g.drawString(font,fit(o.name()+" · "+o.points()+" 点"+(chosen ? " [已选]" : ""),descWidth()),left+4,h.y0+5,0xFFE4D3A2);
                drawDesc(g,o.desc(),left,right,h.y0,true);
            } else {
                var s=row.spec();
                g.drawString(font,fit(s.name()+" · "+s.quality()+" · 成本 "+s.cost()+(isOwned(s)?" [已购]":"")+(pending.contains(s.id())?" [随机候选]":""),descWidth()),left+4,h.y0+5,qualityColor(s.quality()));
                drawDesc(g,s.desc(),left,right,h.y0,canBuy(s)||isOwned(s));
                String label=confirmed?"只读":isOwned(s)?"撤销":canBuy(s)?"购买":"不可购";
                g.drawString(font,label,right-48,h.y0+5,confirmed?0xFF888888:0xFFDDC783);
            }
        }
        g.disableScissor();
        if(maxScroll>0) { g.fill(bar.x0,top,bar.x1,bottom,0xFF344252); int bh=Math.max(10,(bottom-top)*(bottom-top)/(bottom-top+maxScroll)); int by=top+(bottom-top-bh)*pageScroll.getOrDefault(page,0)/maxScroll; g.fill(bar.x0,by,bar.x1,by+bh,0xFFB6C9DD); }
        drawButton(g,rollHit,rollAgainLabel(),!confirmed&&!rollExhausted()&&snapshot!=null);
        drawButton(g,acceptHit,"接受随机结果",!confirmed&&rollPreview()&&snapshot!=null);
        drawButton(g,confirmHit,snapshot==null?"等待 V2 快照":confirmed?"本世已确认":"最终确认",!confirmed&&snapshot!=null);
        if(debug) renderHitOverlay(g,left,right);
        if(modal()) renderConfirm(g);
        else for(int i=0;i<rows.size();i++) if(listHit(i)&&rows.get(i).spec()!=null) { var s=rows.get(i).spec(); g.renderTooltip(font,Component.literal(s.name()+" · "+s.quality()+" · 成本 "+s.cost()+"；"+s.desc()+"；"+costHint(s.quality())+receiptHint(s)),mouseX,mouseY); break; }
    }

    /**
     * 绘制固定七页，神极不另设页，品质筛选仅筛当前页。
     * @param g 本次渲染的GuiGraphics画布
     * @param left 当前面板左边界
     * @param right 当前面板右边界
     */
    private void renderChips(GuiGraphics g, int left, int right){
        for(int i=0;i<7;i++) drawButton(g,nav.get(i),TianfuPage.values()[i].label,page.ordinal()==i);
    }

    /**
     * 仅调试时绘制当前视口行和按钮命中矩形，不参与真实命中判定。
     * @param g 本次渲染的GuiGraphics画布
     * @param left 当前面板左边界
     * @param right 当前面板右边界
     */
    private void renderHitOverlay(GuiGraphics g, int left, int right){
        for(Hit h:rowHits) if(h.y0>=top&&h.y1<=bottom) box(g,h.x0,h.y0,h.x1,h.y1,0xFF00FF00); for(Hit h:nav) box(g,h.x0,h.y0,h.x1,h.y1,0xFFFF00FF);
    }

    /**
     * 绘制待提交意图与服务端成交价和出身差额，最终确认展示硬代价风险。
     * @param g 本次渲染的GuiGraphics画布
     */
    private void renderConfirm(GuiGraphics g){
        g.fill(0,0,width,height,0xC0000000);
        g.fill(8,height/2-62,width-8,height/2+52,0xFF263342);
        var lines=wrapDesc(message);
        for(int i=0;i<lines.size();i++) g.drawString(font,lines.get(i),left+4,height/2-50+i*12,0xFFF1DFC0);
        drawButton(g,yesHit,"确定",true); drawButton(g,noHit,"取消",true);
    }

    /**
     * 按输入边界与ARGB绘制矩形描边，空或反向矩形不绘制。
     * @param g 本次渲染的GuiGraphics画布
     * @param x0 矩形或线段左边界逻辑像素
     * @param y0 矩形上边界逻辑像素
     * @param x1 矩形或线段右边界逻辑像素
     * @param y1 矩形下边界逻辑像素
     * @param color 输入ARGB颜色
     */
    private void box(GuiGraphics g, int x0, int y0, int x1, int y1, int color){
        if(x1<=x0||y1<=y0)return; g.fill(x0,y0,x1,y0+1,color); g.fill(x0,y1-1,x1,y1,color); g.fill(x0,y0,x0+1,y1,color); g.fill(x1-1,y0,x1,y1,color);
    }

    /**
     * 保留color低24位RGB并以夹到0至255的alpha替换透明度位，返回ARGB。
     * @param color 输入ARGB颜色
     * @param alpha 目标透明度0至255
     * @return 按方法职责返回展示结果
     */
    private static int withAlpha(int color, int alpha){
        return (color & 0xFFFFFF) | ((int)clamp(alpha,0,255)<<24);
    }

    /**
     * 按字体像素宽截断s并补省略号，null保持null，maxWidth不足时返回可容纳文本。
     * @param s 待截断文本，可为null
     * @param maxWidth 可用字体像素宽
     * @return 按方法职责返回展示结果
     */
    private String fit(String s, int maxWidth){
        if(s==null)return null; if(maxWidth<=0)return ""; if(font.width(s)<=maxWidth)return s; String suffix=font.width("…")<=maxWidth?"…":""; return font.plainSubstrByWidth(s,Math.max(0,maxWidth-font.width(suffix)))+suffix;
    }

    /**
     * 返回当前页描述列可用像素宽，窄屏最小为一个字宽，禁止负宽。
     * @return 按方法职责返回展示结果
     */
    private int descWidth(){
        return Math.max(1,right-left-64);
    }

    /**
     * 按当前描述像素宽断行并缓存，至多3行，空或null返回空列表，末行允许省略。
     * @param desc 条目描述文本，可为空
     * @return 按方法职责返回展示结果
     */
    private List<String> wrapDesc(String desc){
        if(desc==null||desc.isEmpty())return List.of();
        String key=descWidth()+":"+desc;
        return wrapped.computeIfAbsent(key,k->{
            List<String> lines=new ArrayList<>(); String rest=desc;
            while(!rest.isEmpty()&&lines.size()<3){
                if(lines.size()==2){lines.add(fit(rest,descWidth()));break;}
                String part=font.plainSubstrByWidth(rest,descWidth());
                if(part.isEmpty())part=rest.substring(0,Character.charCount(rest.codePointAt(0)));
                lines.add(part);rest=rest.substring(part.length());
            } return List.copyOf(lines);
        });
    }

    /**
     * 表头固定高度，条目按描述换行数增高，必须与实际绘制命中高度一致。
     * @param row 待测量的行记录
     * @return 按方法职责返回展示结果
     */
    private int rowHeight(Row row){
        if(font==null)return 56; return row.header()!=null?Math.max(30,10+11*wrapDesc(row.header()).size()):24+11*wrapDesc(row.spec()!=null?row.spec().desc():row.option().desc()).size();
    }

    /**
     * 在rowY对应行绘制最多3行描述，usable控制展示色，越宽末行省略。
     * @param g 本次渲染的GuiGraphics画布
     * @param desc 条目描述文本，可为空
     * @param left 当前面板左边界
     * @param right 当前面板右边界
     * @param rowY 该行上边缘逻辑纵坐标
     * @param usable 是否按可操作状态展示，仅影响颜色
     */
    private void drawDesc(GuiGraphics g, String desc, int left, int right, int rowY, boolean usable){
        var lines=wrapDesc(desc); for(int i=0;i<lines.size();i++) g.drawString(font,lines.get(i),left+4,rowY+19+i*11,usable?0xFFB6C9DD:0xFF8893A0);
    }

    /**
     * 在x0至x1绘制渐隐装饰线，brightAtEnd控制亮端；反向长度不绘制。
     * @param g 本次渲染的GuiGraphics画布
     * @param x0 矩形或线段左边界逻辑像素
     * @param x1 矩形或线段右边界逻辑像素
     * @param y 装饰线纵坐标逻辑像素
     * @param color 输入ARGB颜色
     * @param brightAtEnd true表示线段右端更亮
     */
    private void tail(GuiGraphics g, int x0, int x1, int y, int color, boolean brightAtEnd){
        for(int x=x0;x<x1;x++) g.fill(x,y,x+1,y+1,withAlpha(color,(int)(255.0*(brightAtEnd?x-x0+1:x1-x)/Math.max(1,x1-x0))));
    }

    /**
     * 返回品质对应ARGB展示色，未知品质使用默认色，不根据颜色推断页面。
     * @param quality 品质字符串，仅用于成本提示或颜色
     * @return 按方法职责返回展示结果
     */
    private int qualityColor(String quality){
        return switch(quality){case "极"->0xFFFF7777;case "神"->0xFFFFCA62;case "天","十绝体"->0xFFDD99FF;case "地"->0xFF77BBFF;case "玄"->0xFF77DDCC;case "负"->0xFFEE9999;default->0xFFDDDDCC;};
    }

    /**
     * 只发送购买撤销难度出身确认意图，页面切换恢复对应偏移，确认后只读。
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param button 鼠标按钮编号，0为左键
     * @return 按方法职责返回展示结果
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        mx=mouseX; my=mouseY; refreshV2(TianfuV2StatePayload.latest()); layout();
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button);
        if(modal()) {
            if(noHit.contains(mx,my)){clearPending();restoreOrigin();buildRows();layout();return true;}
            if(yesHit.contains(mx,my)) {
                if(!confirmed&&snapshot!=null&&snapshot.revision()==pendingRevision) {
                    switch(action){
                        case "buy" -> PacketDistributor.sendToServer(new TianfuBuyPayload(pendingId));
                        case "revoke" -> PacketDistributor.sendToServer(new TianfuRevokePayload(pendingId));
                        case "difficulty" -> PacketDistributor.sendToServer(new TianfuDifficultyPayload(pendingId));
                        case "origin" -> PacketDistributor.sendToServer(new TianfuOriginPayload(region,background,pendingRevision));
                        case "roll" -> PacketDistributor.sendToServer(new TianfuRollPayload(pendingId));
                        case "accept" -> PacketDistributor.sendToServer(new TianfuRollAcceptPayload());
                        case "confirm" -> PacketDistributor.sendToServer(new TianfuConfirmPayload());
                        default -> { }
                    }
                } clearPending();return true;
            } return true;
        }
        for(int i=0;i<nav.size();i++) if(nav.get(i).contains(mx,my)){page=TianfuPage.values()[i];buildRows();layout();return true;}
        if(maxScroll>0&&bar.contains(mx,my)){dragging=true;applyBar((int)my);return true;}
        if(confirmed||snapshot==null)return true;
        if(rollHit.contains(mx,my)&&!rollExhausted()&&difficulty>=0){ask("roll",TianfuDifficulty.byIndex(difficulty).id(),"随机生成候选；接受前不会扣点。现有选择的替换由服务端验证。");return true;}
        if(acceptHit.contains(mx,my)&&rollPreview()){ask("accept","","接受服务端随机结果？预算、24补点上限及互斥由服务端验证。");return true;}
        if(confirmHit.contains(mx,my)&&difficulty>=0&&hasAnchorSelection()){ask("confirm","","最终确认后本世不可更改。负面含资质锁定、气运归零、寿元禁增等硬代价，请核对全部已购与出身。");return true;}
        for(int i=0;i<rows.size();i++) if(listHit(i)) {
            Row row=rows.get(i);
            if(page==TianfuPage.ORIGIN) {
                int n=TianfuOriginTable.REGIONS.size();
                if(i<n)region=TianfuOriginTable.REGIONS.get(i).id();else background=TianfuOriginTable.BACKGROUNDS.get(i-n).id();
                buildRows();layout();
                if(!region.isEmpty()&&!background.isEmpty())ask("origin","","提交地域与类型？按旧出身成交价退款，新成本见所选条目；余额与差额由服务端结算。");
            } else if(row.option()!=null&&difficulty<0) ask("difficulty",row.option().id(),"选择 "+row.option().name()+"，基础点 "+row.option().points()+"，选定后不可更改。");
            else if(row.spec()!=null&&mx>=right-54) {
                var s=row.spec();
                if(isOwned(s))ask("revoke",s.id(),"撤销 "+s.name()+receiptHint(s)+"；负成本撤销需要扣回原补点。");
                else if(canBuy(s))ask("buy",s.id(),"购买 "+s.name()+"，目录成本 "+s.cost()+"。"+costHint(s.quality())+receiptHint(s));
            } return true;
        } return super.mouseClicked(mouseX,mouseY,button);
    }

    /**
     * 清空所有待确认意图，包含新增出身意图，但保留已同步业务状态。
     */
    private void clearPending(){
        action=null; pendingId=""; message=""; pendingRevision=-1;
    }

    /**
     * 按mouseY在滚动轨道中的比例计算当前页scroll并夹取合法区间，不改变其他页偏移。
     * @param mouseY 鼠标屏幕逻辑纵坐标
     */
    private void applyBar(int mouseY){
        pageScroll.put(page,(int)Math.round(clamp((mouseY-top)/(double)Math.max(1,bottom-top),0,1)*maxScroll)); layout();
    }

    /**
     * 仅左键拖动已命中滚动条时更新当前页偏移，处理则true，否则交Screen默认行为。
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param button 鼠标按钮编号，0为左键
     * @param dragX 本帧拖拽横向增量
     * @param dragY 本帧拖拽纵向增量
     * @return 按方法职责返回展示结果
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY){
        if(button==0&&dragging&&!modal()){applyBar((int)mouseY);return true;} return super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
    }

    /**
     * 结束滚动条拖拽并释放捕获，未处理交Screen默认行为。
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param button 鼠标按钮编号，0为左键
     * @return 按方法职责返回展示结果
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button){
        if(button==0&&dragging){dragging=false;return true;} return super.mouseReleased(mouseX,mouseY,button);
    }

    /**
     * 只滚当前页并夹在0和内容高度减视口高度之间。
     * @param mouseX 鼠标屏幕逻辑横坐标
     * @param mouseY 鼠标屏幕逻辑纵坐标
     * @param scrollX 横向滚动增量
     * @param scrollY 纵向滚动增量，正值向上
     * @return 按方法职责返回展示结果
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        if(modal())return true; if(mouseY>=top&&mouseY<bottom){pageScroll.put(page,(int)clamp(pageScroll.getOrDefault(page,0)-scrollY*30,0,maxScroll));layout();return true;} return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    /**
     * F6切调试命中框，ESC优先取消模态再关闭界面，处理返回true。
     * @param keyCode GLFW逻辑按键码
     * @param scanCode 平台扫描码
     * @param modifiers GLFW修饰键位掩码
     * @return 按方法职责返回展示结果
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(keyCode==GLFW.GLFW_KEY_F6){debug=!debug;return true;} if(keyCode==GLFW.GLFW_KEY_ESCAPE&&modal()){clearPending();restoreOrigin();buildRows();return true;} return super.keyPressed(keyCode,scanCode,modifiers);
    }

    /**
     * 返回false以允许联机世界持续运行，不为界面冻结服务端。
     * @return 按方法职责返回展示结果
     */
    public boolean isPauseScreen(){
        return false;
    }

    /**
     * 关闭当前天赋Screen并清理本地待定意图，不能撤销已提交购买或删除NBT。
     */
    public void onClose(){
        clearPending(); wrapped.clear(); super.onClose();
    }

    /**
     * 将有限输入v夹到闭区间min至max，调用方保证min不大于max，返回夹取值。
     * @param v 待夹取有限数
     * @param min 闭区间下界
     * @param max 闭区间上界
     * @return 按方法职责返回展示结果
     */
    private static double clamp(double v, double min, double max){
        return Math.max(min,Math.min(max,v));
    }

    /**
     * V2 快照与旧状态更新同一Screen；revision过时必须忽略；不凭缺失字段推断收费出身。
     * @param state 服务端发来的不可变快照，含出身、成交账和补点已用
     */
    public void refreshV2(TianfuSelection.Snapshot state) {
        TianfuSelection.Snapshot latest=TianfuV2StatePayload.latest();
        if(latest==null)return;
        state=latest;
        if(snapshot!=null&&state.revision()<=snapshot.revision())return;
        snapshot=state; mergeSnapshot(); clearPending(); restoreOrigin(); wrapped.clear(); buildRows();
    }
}
