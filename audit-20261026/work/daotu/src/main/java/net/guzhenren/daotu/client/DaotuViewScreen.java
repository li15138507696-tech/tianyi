package net.guzhenren.daotu.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.guzhenren.daotu.net.DaotuMainDaoPayload;
import net.guzhenren.daotu.net.DaotuPickPayload;
import net.guzhenren.daotu.net.DaotuSettingPayload;
import net.guzhenren.daotu.net.DaotuStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 道途主界面。
 *
 * <p><b>v1.4.0 重做</b>：势力 / 流派树 / 觉醒三个页签随功能一起删除，
 * 只剩一页——「成长」。抽选从「事后可无限重选」改成「攒满弹窗、选一次锁定」，
 * 所以界面重心从「翻记录改选」变成「看清这 5 个候选然后下定决心」。
 *
 * <p><b>布局</b>（自上而下）：
 * <pre>
 *   ┌────────────────────────────────────────┐
 *   │ [三转]        道 途        第 3 世·12抽 │  标题带
 *   ├────────────────────────────────────────┤
 *   │ 历练池 ████████████░░░░░  187.3 / 324  │  池区
 *   │ [下一抽·3档] [三转 ×1.96]      还需 137 │
 *   ├────────────────────────────────────────┤
 *   │ ✦ 第 13 抽 · 选择一项（选定后不可更改）✦ │  候选区
 *   │  ① 力量      +4.2%                  ▸  │
 *   │  ② 生命      +4.2%                     │
 *   ├────────────────────────────────────────┤
 *   │ 已获属性 / 特殊增益 / 抽选历史（滚动）     │  列表区
 *   ├────────────────────────────────────────┤
 *   │ 击杀·游历·修炼皆积历练  [自动弹窗◉] M 关闭 │  页脚
 *   └────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>铁律</b>（改这个文件前务必读一遍）：
 * <ol>
 *   <li>{@link #layout()} 是<b>唯一</b>分配命中区的地方，{@code render} 只读不写；</li>
 *   <li>渲染顺序必须与命中测试顺序一致；</li>
 *   <li>虚拟列表：只画可见行，行高固定；</li>
 *   <li><b>绝不调 {@code super.render()}</b>——父类会调
 *       {@code renderBlurredBackground} 把整个面板糊掉；</li>
 *   <li>所有纵向偏移必须从 {@code height} 推导，不能写死 {@code height/2 ± 常量}；</li>
 *   <li>列表必须 {@code enableScissor}，否则半露行会画到页脚上；</li>
 *   <li>面板宽度必须 {@code min(期望宽, width - 16)}，窄屏才不戳出屏幕；</li>
 *   <li>所有行文字过 {@link #fit}，超宽裁 {@code …}，绝不压字。</li>
 * </ol>
 */
public class DaotuViewScreen extends Screen {

    // ---------------------------------------------------------------- 尺寸常量

    /** 期望面板宽（实际会被 {@code width - 16} 夹住）。 */
    private static final int PANEL_W = 420;
    /** 期望列表高（实际会被可用高度夹住）。 */
    private static final int LIST_H = 168;
    /** 常规行高。 */
    private static final int ROW_H = 20;
    /** 分组标题行高。 */
    private static final int HEADER_H = 14;
    /** 候选行高（比常规行略高，因为要放下序号徽标）。 */
    private static final int OFFER_ROW_H = 22;
    /** 字体行高（MC 默认 9px）。 */
    private static final int FONT_H = 9;

    // ---------------------------------------------------------------- 配色

    private static final int C_SCRIM = 0xD8060608;
    private static final int C_PANEL = 0xF00E0D0B;
    private static final int C_PANEL_IN = 0xF0161512;
    private static final int C_BORDER_OUT = 0xFF6B5A2E;
    private static final int C_BORDER_IN = 0xFFC8A44E;
    private static final int C_CINNABAR = 0xFFB33A28;
    private static final int C_CINNABAR_DIM = 0xFF6E2418;
    private static final int C_TEXT = 0xFFE9E2CE;
    private static final int C_DIM = 0xFF9C9484;
    private static final int C_DISABLED = 0xFF5E574C;
    private static final int C_GOLD = 0xFFD8B65A;
    private static final int C_ACCENT = 0xFF7FBFA8;
    private static final int C_RULE = 0x22FFFFFF;

    private static final int C_BAR_TRACK = 0x33FFFFFF;
    private static final int C_BAR_FILL = 0xFFC0392B;
    private static final int C_BAR_FULL = 0xFFD8B65A;
    private static final int C_CHIP = 0xFF1F2A26;
    private static final int C_CHIP_EDGE = 0xFF3E5248;
    private static final int C_ATTR_BAR = 0xFF6FA88F;
    private static final int C_ATTR_BAR_MAX = 0xFFD8B65A;
    private static final int C_OFFER_HOVER = 0x22D8B65A;
    private static final int C_OFFER_EDGE = 0x44D8B65A;

    // ---------------------------------------------------------------- 状态

    private final Map<String, Double> attrs;
    private final List<DaotuStatePayload.Offer> offer;
    private final List<String> history;
    private final List<String> buffs;
    private double pool;
    private double threshold;
    private int draws;
    private int runId;
    private double zhuanshu;
    private boolean openedQiao;
    private int offerDraw;
    /** 攒满历练时是否自动弹窗（页脚开关，值来自服务端）。 */
    private boolean autoPrompt = true;
    /** 主修流派键（空串 = 未设定）。决定特殊奖励发什么。 */
    private String mainDao = "";
    /** 本转抽选上限（一转~五转 100；五转后 Integer.MAX_VALUE）。 */
    private int drawCap = Integer.MAX_VALUE;

    // ---------------------------------------------------------------- 布局

    private int panelLeft;
    private int panelRight;
    private int panelW;
    private int titleY0;
    private int titleY1;
    private int poolY0;
    private int poolY1;
    private int offerY0;
    private int offerY1;
    private int listTop;
    private int listBottom;
    private int scroll;
    private int contentHeight;

    /** 命中区：页脚「自动弹窗」开关。 */
    private int toggleX0;
    private int toggleY0;
    private int toggleX1;
    private int toggleY1;

    /** 命中区：页脚「主修」按钮（v1.5.0）。 */
    private int mainX0;
    private int mainY0;
    private int mainX1;
    private int mainY1;

    /** 主修选择弹层（null = 关着）。点「主修」按钮打开。 */
    private List<String> mainOptions;
    private final List<int[]> mainHit = new ArrayList<>();

    /** 命中区：候选行 {x0,y0,x1,y1,index}。 */
    private final List<int[]> offerHit = new ArrayList<>();

    public DaotuViewScreen(DaotuStatePayload p) {
        super(Component.literal("道途"));
        this.attrs = new LinkedHashMap<>(p.attrs());
        this.offer = new ArrayList<>(p.offer());
        this.history = new ArrayList<>(p.history());
        this.buffs = new ArrayList<>(p.buffs());
        this.pool = p.pool();
        this.threshold = p.threshold();
        this.draws = p.draws();
        this.runId = p.runId();
        this.zhuanshu = p.zhuanshu();
        this.openedQiao = p.openedQiao();
        this.offerDraw = p.offerDraw();
        // 界面上的开关读「客户端自己的偏好」：服务端那份值可能没存住，
        // 但玩家点过的开关必须记住（见 DaotuClientPrefs）。
        // 本地从没设置过时，才采用服务端的值。
        DaotuClientPrefs.adoptIfUnset(p.autoPrompt());
        this.autoPrompt = DaotuClientPrefs.autoPrompt();
        this.mainDao = p.mainDao() == null ? "" : p.mainDao();
        this.drawCap = p.drawCap();
    }

    /** 服务端推来新状态：原地刷新，保留滚动位置。 */
    public void applyState(DaotuStatePayload p) {
        this.attrs.clear();
        this.attrs.putAll(p.attrs());
        this.offer.clear();
        this.offer.addAll(p.offer());
        this.history.clear();
        this.history.addAll(p.history());
        this.buffs.clear();
        this.buffs.addAll(p.buffs());
        this.pool = p.pool();
        this.threshold = p.threshold();
        this.draws = p.draws();
        this.runId = p.runId();
        this.zhuanshu = p.zhuanshu();
        this.openedQiao = p.openedQiao();
        this.offerDraw = p.offerDraw();
        DaotuClientPrefs.adoptIfUnset(p.autoPrompt());
        this.autoPrompt = DaotuClientPrefs.autoPrompt();
        this.mainDao = p.mainDao() == null ? "" : p.mainDao();
        this.drawCap = p.drawCap();
        layout();
    }

    @Override
    protected void init() {
        layout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ 布局

    /**
     * 唯一分配命中区的地方。
     *
     * <p>纵向全部自下而上累加再居中，不用 {@code height/2 ± 常量}——
     * 那个写法在 GUI scale 3（1920×1080 → 640×360）时会让面板戳出屏幕。
     */
    private void layout() {
        offerHit.clear();

        panelW = Math.min(PANEL_W, width - 16);
        panelLeft = width / 2 - panelW / 2;
        panelRight = panelLeft + panelW;

        int titleH = 20;
        int poolH = hasOffer() ? 30 : 34;
        int offerH = hasOffer() ? Math.min(5, offer.size()) * OFFER_ROW_H + 15 : 0;
        int footH = 20;
        int chrome = titleH + poolH + offerH + footH;

        // 可用高度不足时，先压缩候选行高，再压缩列表。
        // 若死守「列表至少 2 行」，427x240 这种极小屏 + 5 个候选会让面板戳出底边。
        int avail = height - chrome - 16;
        int listH = Math.min(LIST_H, avail);
        if (listH < ROW_H * 2) {
            // 列表实在放不下 2 行：把候选行高从 22 压到 18，腾出空间
            int deficit = ROW_H * 2 - listH;
            int compress = Math.min(deficit, offerH > 0 ? (offerH / 4) : 0);
            if (compress > 0) {
                offerH -= compress;
                chrome -= compress;
                avail = height - chrome - 16;
                listH = Math.min(LIST_H, avail);
            }
        }
        listH = Math.max(ROW_H, listH); // 最少留 1 行，绝不 0 或负
        int total = chrome + listH;

        int y = Math.max(4, (height - total) / 2);

        titleY0 = y;
        titleY1 = y + titleH;
        poolY0 = titleY1;
        poolY1 = poolY0 + poolH;
        offerY0 = poolY1;
        offerY1 = offerY0 + offerH;
        listTop = offerY1;
        listBottom = listTop + listH;

        // 候选命中区
        if (hasOffer()) {
            int oy = offerY0 + 14; // 跳过「待选择」小标题
            int n = Math.min(5, offer.size());
            for (int i = 0; i < n; i++) {
                offerHit.add(new int[] { panelLeft + 6, oy, panelRight - 6,
                        oy + OFFER_ROW_H, i });
                oy += OFFER_ROW_H;
            }
        }

        // 页脚右组：「主修」按钮 + 「自动弹窗」开关 + 「M 关闭」（都右对齐）
        // 顺序从右往左排：关闭提示在最右，开关挨着它，主修按钮在最左。
        int closeW = font.width("M 关闭");
        int closeX = panelRight - 10 - closeW;
        toggleX1 = closeX - 10;
        toggleX0 = toggleX1 - toggleWidth();
        toggleY0 = listBottom + 3;
        toggleY1 = toggleY0 + 14;

        // 主修按钮：嵌在开关左边，同样 14px 高
        mainX1 = toggleX0 - 10;
        mainX0 = mainX1 - mainButtonWidth();
        mainY0 = listBottom + 3;
        mainY1 = mainY0 + 14;

        contentHeight = computeContentHeight();
        scroll = Mth.clamp(scroll, 0,
                Math.max(0, contentHeight - (listBottom - listTop)));

        // 主修弹层的命中区（开着才算）
        mainHit.clear();
        if (mainOptions != null) {
            int oy = mainY0 - mainOptions.size() * 13 - 4;
            for (int i = 0; i < mainOptions.size(); i++) {
                mainHit.add(new int[] { mainX0, oy, mainX1, oy + 13, i });
                oy += 13;
            }
        }
    }

    /** 「主修」按钮上的文字。未设定时明确写「未定」，别让玩家以为坏了。 */
    private String mainButtonLabel() {
        return "主修 · " + (mainDao.isEmpty() ? "未定" : mainDaoLabel(mainDao));
    }

    /** 「主修」按钮宽度：左内边距 + 文字 + 右内边距。 */
    private int mainButtonWidth() {
        return 6 + font.width(mainButtonLabel()) + 6;
    }

    /**
     * 主修可选项（键列表）+ 中文名，<b>客户端独立一份</b>。
     *
     * <p>不能引用服务端 {@code DaotuMainDao.ALL}：这个类是客户端 Screen，
     * 服务端专属类在专用服上不存在（类加载直接炸）。已按同一约定维护
     * （见 {@link #attrLabel}）。
     */
    private static final List<String[]> MAIN_DAOS = List.of(
            new String[] { "jiandao", "剑道" },
            new String[] { "jindao", "金道" },
            new String[] { "shuidao", "水道" },
            new String[] { "xuedao", "血道" },
            new String[] { "lidao", "力道" },
            new String[] { "mudao", "木道" },
            new String[] { "yudao", "浴道" },
            new String[] { "fengdao", "风道" },
            new String[] { "bingdao", "冰道" },
            new String[] { "jindao2", "精道" },
            new String[] { "hundao", "魂道" },
            new String[] { "leidao", "雷道" },
            new String[] { "yandao", "炎道" },
            new String[] { "lvdao", "律道" },
            new String[] { "tudao", "土道" });

    private static String mainDaoLabel(String key) {
        for (String[] kv : MAIN_DAOS) {
            if (kv[0].equals(key)) {
                return kv[1];
            }
        }
        return key;
    }

    /** 开关上的文字：带状态，免得只靠滑块位置看不懂开还是关。 */
    private String toggleLabel() {
        return "自动弹窗 " + (autoPrompt ? "开" : "关");
    }

    /** 「自动弹窗」开关宽度：左内边距 + 文字 + 间隙 + 轨道 + 右内边距。 */
    private int toggleWidth() {
        return 5 + font.width(toggleLabel()) + 8 + 16 + 5;
    }

    private boolean hasOffer() {
        return !offer.isEmpty();
    }

    // ================================================================ 行模型

    /**
     * 列表行。
     *
     * @param header 非空 = 分组标题
     * @param kvKey  左侧文字
     * @param kvValue 右侧文字
     * @param kind   0 标题 / 1 键值 / 2 属性（带进度条）
     */
    private record Row(String header, String kvKey, String kvValue, int kind) {
        static Row header(String text) {
            return new Row(text, null, null, 0);
        }

        static Row kv(String key, String value) {
            return new Row(null, key, value, 1);
        }

        static Row attr(String key, String value) {
            return new Row(null, key, value, 2);
        }
    }

    private int rowHeight(Row r) {
        return r.header() != null ? HEADER_H : ROW_H;
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();

        // ---- 已获属性 ----
        rows.add(Row.header("已获属性 · 转世清空"));
        if (attrs.isEmpty()) {
            rows.add(Row.kv("  尚无", "抽选后在此显示"));
        } else {
            List<Map.Entry<String, Double>> list = new ArrayList<>(attrs.entrySet());
            list.sort((a, b) -> Double.compare(Math.abs(b.getValue()),
                    Math.abs(a.getValue())));
            for (Map.Entry<String, Double> e : list) {
                rows.add(Row.attr(attrLabel(e.getKey()),
                        attrValueText(e.getKey(), e.getValue())));
            }
        }

        // ---- 特殊增益 ----
        rows.add(Row.header("特殊增益 · 整局常驻"));
        if (buffs.isEmpty()) {
            rows.add(Row.kv("  尚无", "每 10 抽一次"));
        } else {
            for (String b : buffs) {
                rows.add(Row.kv("  ◆ " + buffName(b), "常驻"));
            }
        }

        // ---- 抽选历史 ----
        rows.add(Row.header("抽选历史 · 已锁定不可改"));
        if (history.isEmpty()) {
            rows.add(Row.kv("  尚无", "攒满历练后弹出选择"));
        } else {
            // 最新的在上面
            for (int i = history.size() - 1; i >= 0; i--) {
                String[] f = history.get(i).split("\\|");
                if (f.length < 4) {
                    continue;
                }
                double amount = parseOr(f[2], 0.0);
                boolean pct = "1".equals(f[3]);
                String val = pct ? fmtPct(amount) : fmt2(amount);
                rows.add(Row.kv("  第 " + f[0] + " 抽 · " + attrLabel(f[1]), val));
            }
        }
        return rows;
    }

    private int computeContentHeight() {
        int h = 0;
        for (Row r : buildRows()) {
            h += rowHeight(r);
        }
        return h;
    }

    // ================================================================ 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 注意：这里不调用 super.render()，否则父类会调 renderBlurredBackground
        // 用模糊层覆盖整个界面，导致面板发糊。
        renderBackground(g, mouseX, mouseY, partialTick);

        layout();

        drawPanel(g);
        drawTitle(g);
        drawPool(g);
        if (hasOffer()) {
            drawOffer(g, mouseX, mouseY);
        }
        drawRows(g);
        drawFooter(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 纯色遮罩，不调父类（父类会触发模糊）
        g.fill(0, 0, width, height, C_SCRIM);
    }

    private void drawPanel(GuiGraphics g) {
        int x0 = panelLeft;
        int x1 = panelRight;
        int y0 = titleY0;
        int y1 = listBottom + 20;

        // 外发光：两圈极淡描边，让面板从背景里浮起来
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0x18C8A44E);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0x30C8A44E);

        // 主体
        g.fill(x0, y0, x1, y1, C_PANEL);
        // 内层稍亮，做出「凹陷」感
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, C_PANEL_IN);

        // 边框：外深内金
        g.fill(x0, y0, x1, y0 + 1, C_BORDER_OUT);
        g.fill(x0, y1 - 1, x1, y1, C_BORDER_OUT);
        g.fill(x0, y0, x0 + 1, y1, C_BORDER_OUT);
        g.fill(x1 - 1, y0, x1, y1, C_BORDER_OUT);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, C_BORDER_IN);
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, C_BORDER_IN);

        // 左右内阴影
        g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, 0x14FFFFFF);
        g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, 0x14FFFFFF);

        // 四角装饰：短金线
        corner(g, x0 + 2, y0 + 2, 1, 1);
        corner(g, x1 - 3, y0 + 2, -1, 1);
        corner(g, x0 + 2, y1 - 3, 1, -1);
        corner(g, x1 - 3, y1 - 3, -1, -1);
    }

    /** 画一个 L 形角饰。 */
    private void corner(GuiGraphics g, int x, int y, int dx, int dy) {
        int len = 8;
        g.fill(x, y, x + (dx > 0 ? len : -len), y + 1, C_BORDER_IN);
        g.fill(x, y, x + 1, y + (dy > 0 ? len : -len), C_BORDER_IN);
    }

    private void drawTitle(GuiGraphics g) {
        int cx = (panelLeft + panelRight) / 2;

        // 朱砂渐变底：顶部浓、底部淡
        for (int i = 0; i < 6; i++) {
            int a = 0x50 - i * 0x0C;
            g.fill(panelLeft + 2, titleY0 + 2 + i, panelRight - 2, titleY0 + 3 + i,
                    (a << 24) | 0xB33A28);
        }

        // 标题
        String title = "道 途";
        int tw = font.width(title);
        g.drawString(font, title, cx - tw / 2, titleY0 + 6, C_GOLD, true);

        // 左侧：转数徽标
        chip(g, panelLeft + 8, titleY0 + 6, zhuanText(zhuanshu), C_ACCENT);

        // 右侧：第几世 · 几抽
        String right = "第 " + (runId + 1) + " 世 · " + draws + " 抽";
        g.drawString(font, right, panelRight - 8 - font.width(right),
                titleY0 + 6, C_DIM, true);

        g.fill(panelLeft + 4, titleY1 - 1, panelRight - 4, titleY1, C_RULE);
    }

    private void drawPool(GuiGraphics g) {
        int x0 = panelLeft + 10;
        int xr = panelRight - 10;

        // ---- 第一行：标签 + 条 + 数值 ----
        int y = poolY0 + 5;
        String label = "历练池";
        g.drawString(font, label, x0, y, C_DIM, true);
        int labelW = font.width(label);

        String num = fmt1(pool) + " / " + fmt1(threshold);
        int numW = font.width(num);
        g.drawString(font, num, xr - numW, y, pool >= threshold ? C_GOLD : C_TEXT, true);

        int barX0 = x0 + labelW + 8;
        int barX1 = xr - numW - 8;
        if (barX1 > barX0 + 8) {
            int barY = y + 1;
            g.fill(barX0, barY, barX1, barY + 6, C_BAR_TRACK);
            boolean full = pool >= threshold;
            float p = (float) Math.max(0.0, Math.min(1.0,
                    threshold <= 0 ? 0 : pool / threshold));
            int fillW = (int) ((barX1 - barX0) * p);
            if (fillW > 0) {
                g.fill(barX0, barY, barX0 + fillW, barY + 6,
                        full ? C_BAR_FULL : C_BAR_FILL);
                // 高光：顶部一条亮线
                g.fill(barX0, barY, barX0 + fillW, barY + 1, 0x40FFFFFF);
            }
            // 描边
            g.fill(barX0, barY, barX1, barY + 1, full ? C_GOLD : 0x33FFFFFF);
            g.fill(barX0, barY + 5, barX1, barY + 6, full ? C_GOLD : 0x33FFFFFF);
        }

        // ---- 第二行：档位徽标 + 提示 ----
        int y2 = y + 13;
        int cx = x0;
        cx = chip(g, cx, y2, "下一抽 · " + tierName(draws + 1) + " 档", C_GOLD) + 4;
        chip(g, cx, y2, zhuanText(zhuanshu) + " ×" + fmt2(zhuanScale(zhuanshu)),
                C_ACCENT);

        // 右侧提示
        String tip;
        int tipColor;
        if (hasOffer()) {
            tip = "请在上方选择";
            tipColor = C_GOLD;
        } else if (pool >= threshold) {
            tip = "可抽选";
            tipColor = C_GOLD;
        } else {
            tip = "还需 " + fmt1(threshold - pool);
            tipColor = C_DIM;
        }
        g.drawString(font, tip, xr - font.width(tip), y2 + 1, tipColor, true);

        g.fill(panelLeft + 4, poolY1 - 1, panelRight - 4, poolY1, C_RULE);
    }

    private void drawOffer(GuiGraphics g, int mouseX, int mouseY) {
        int x1 = panelRight - 6;

        // 小标题
        String head = "◆ 第 " + offerDraw + " 抽 · 选择一项（选定后不可更改）◆";
        g.drawString(font, head, panelLeft + 10, offerY0 + 3, C_GOLD, true);

        for (int[] h : offerHit) {
            int idx = h[4];
            if (idx >= offer.size()) {
                continue;
            }
            DaotuStatePayload.Offer o = offer.get(idx);
            int y0 = h[1];
            int y1 = h[3];
            boolean hover = mouseX >= h[0] && mouseX < h[2]
                    && mouseY >= y0 && mouseY < y1;

            // 底
            g.fill(h[0], y0, h[2], y1 - 1, hover ? C_OFFER_HOVER : 0x14FFFFFF);
            if (hover) {
                g.fill(h[0], y0, h[2], y0 + 1, C_OFFER_EDGE);
                g.fill(h[0], y1 - 2, h[2], y1 - 1, C_OFFER_EDGE);
            }

            int ty = y0 + (OFFER_ROW_H - 1 - FONT_H) / 2;

            // 序号徽标
            String num = String.valueOf(idx + 1);
            int bx = h[0] + 5;
            g.fill(bx, ty - 1, bx + 13, ty + FONT_H + 1,
                    hover ? C_CINNABAR_DIM : 0xFF241F18);
            g.drawString(font, num, bx + (13 - font.width(num)) / 2, ty,
                    hover ? C_GOLD : C_DIM, true);

            // 名称
            String name = o.label();
            int nameX = bx + 19;
            int nameW = x1 - nameX - 16;
            g.drawString(font, fit(name, nameW), nameX, ty, C_TEXT, true);

            // 右侧箭头（提示可点）
            if (hover) {
                String arrow = ">";
                g.drawString(font, arrow, x1 - 8 - font.width(arrow), ty, C_GOLD, true);
            }
        }

        g.fill(panelLeft + 4, offerY1 - 1, panelRight - 4, offerY1, C_RULE);
    }

    private void drawRows(GuiGraphics g) {
        // 底部留一条渐隐带。行只画到 listBottom - FADE_H，**半行直接跳过**——
        // 否则最后一行会被渐隐带盖住，▼ 箭头正好压在文字上（v1.3.1 的老毛病）。
        int fadeReserve = FADE_H;
        int rowLimit = listBottom - fadeReserve;

        g.enableScissor(panelLeft + 3, listTop, panelRight - 3, listBottom);

        int y = listTop - scroll;
        int x0 = panelLeft + 10;
        int xr = panelRight - 10;

        for (Row r : buildRows()) {
            int h = rowHeight(r);
            // 整行放得下才画：不留半行，渐隐带才不会盖住文字
            if (y >= listTop && y + h <= rowLimit) {
                if (r.header() != null) {
                    g.fill(panelLeft + 6, y + 3, panelLeft + 7, y + HEADER_H - 4, C_CINNABAR);
                    g.drawString(font, r.header(), panelLeft + 10, y + 3, C_DIM, true);
                } else if (r.kind() == 2) {
                    drawAttrRow(g, r.kvKey(), r.kvValue(), y, x0, xr);
                } else {
                    drawKvRow(g, r.kvKey(), r.kvValue(), y, x0, xr);
                }
            }
            y += h;
        }

        g.disableScissor();
        drawScrollHints(g, rowLimit);
    }

    /** 属性行：名称 + 横向进度条 + 右对齐数值。 */
    private void drawAttrRow(GuiGraphics g, String name, String value, int y, int x0, int xr) {
        int ty = y + 5;
        g.drawString(font, name, x0, ty, C_DIM, true);

        int nameW = 62;
        int valW = font.width(value);
        int barX0 = x0 + nameW;
        int barX1 = xr - valW - 8;
        if (barX1 > barX0 + 10) {
            int barY = ty + 1;
            g.fill(barX0, barY, barX1, barY + 5, C_BAR_TRACK);
            float p = (float) attrScale(name, value);
            int fw = (int) ((barX1 - barX0) * p);
            boolean maxed = p >= 0.999f;
            if (fw > 0) {
                g.fill(barX0, barY, barX0 + fw, barY + 5,
                        maxed ? C_ATTR_BAR_MAX : C_ATTR_BAR);
            }
        }
        g.drawString(font, value, xr - valW, ty, C_TEXT, true);
    }

    /** 键值行：左灰键 + 右亮值 + 中间点线。 */
    private void drawKvRow(GuiGraphics g, String key, String value, int y, int x0, int xr) {
        int ty = y + 5;
        g.drawString(font, key, x0, ty, C_DIM, true);

        int valW = font.width(value);
        g.drawString(font, value, xr - valW, ty, C_TEXT, true);

        // 点线牵引：间隔够才画，否则会跟文字挤在一起
        int gapX0 = x0 + font.width(key) + 6;
        int gapX1 = xr - valW - 6;
        if (gapX1 - gapX0 > 12) {
            for (int x = gapX0; x < gapX1; x += 4) {
                g.fill(x, ty + 4, Math.min(x + 1, gapX1), ty + 5, 0x33FFFFFF);
            }
        }
    }

    /** 底部渐隐带高度（同时也是列表的保留区，行不得画进这里）。 */
    private static final int FADE_H = 12;

    private void drawScrollHints(GuiGraphics g, int rowLimit) {
        int visible = listBottom - listTop;
        if (contentHeight <= visible) {
            return;
        }
        boolean moreDown = scroll + visible < contentHeight;
        boolean moreUp = scroll > 0;
        int x0 = panelLeft + 3;
        int x1 = panelRight - 3;
        int cx = (panelLeft + panelRight) / 2;

        if (moreUp) {
            for (int i = 0; i < FADE_H; i++) {
                int a = 0x0C + (0xD0 - 0x0C) * (FADE_H - 1 - i) / FADE_H;
                g.fill(x0, listTop + i, x1, listTop + i + 1, (a << 24) | 0x161512);
            }
            g.drawString(font, "▲", cx - font.width("▲") / 2, listTop + 2, C_GOLD, true);
        }
        if (moreDown) {
            // 渐隐带紧贴 rowLimit 下方，箭头画在带子内部，绝不压文字
            for (int i = 0; i < FADE_H; i++) {
                int a = 0x0C + (0xD0 - 0x0C) * i / FADE_H;
                g.fill(x0, rowLimit + i, x1, rowLimit + i + 1, (a << 24) | 0x161512);
            }
            g.drawString(font, "▼", cx - font.width("▼") / 2,
                    rowLimit + 1, C_GOLD, true);
        }
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        int y = listBottom + 5;
        String left = hasOffer() ? "选定即锁定 · 不可更改" : "击杀 · 游历 · 修炼 皆积历练";
        // v1.5.0：一转~五转每转 100 抽，到顶后历练停止累计——必须在界面上说清楚，
        // 否则玩家只会以为「打怪不给经验了」。
        if (atCap()) {
            left = "本转抽选已满 " + draws + " / " + drawCap + " · 历练已停止累计";
        }
        g.drawString(font, left, panelLeft + 10, y,
                hasOffer() || atCap() ? C_GOLD : C_DISABLED, true);

        drawMainDaoButton(g, mouseX, mouseY);
        drawAutoPromptToggle(g, mouseX, mouseY);

        String right = "M 关闭";
        g.drawString(font, right, panelRight - 10 - font.width(right), y, C_DIM, true);

        // 主修弹层画在最上层（盖住列表底部也认了，它本来就该挡住）
        if (mainOptions != null) {
            drawMainDaoPopup(g, mouseX, mouseY);
        }
    }

    /** 是否已达本转抽选上限。 */
    private boolean atCap() {
        return draws >= drawCap;
    }

    /**
     * 页脚「主修」按钮。坐标全部来自 {@link #layout()}，这里只画不另算
     * （类注释「铁律」第 1 条：视觉与点击区绝不能各算一套）。
     */
    private void drawMainDaoButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = mouseX >= mainX0 && mouseX < mainX1
                && mouseY >= mainY0 && mouseY < mainY1;
        boolean unset = mainDao.isEmpty();
        int edge = hover ? C_GOLD : C_CHIP_EDGE;
        int text = unset ? (hover ? C_GOLD : C_DISABLED) : C_TEXT;

        g.fill(mainX0, mainY0, mainX1, mainY1, C_CHIP);
        g.fill(mainX0, mainY0, mainX1, mainY0 + 1, edge);
        g.fill(mainX0, mainY1 - 1, mainX1, mainY1, edge);
        g.fill(mainX0, mainY0, mainX0 + 1, mainY1, edge);
        g.fill(mainX1 - 1, mainY0, mainX1, mainY1, edge);
        g.drawString(font, mainButtonLabel(), mainX0 + 6, mainY0 + 3, text, true);
    }

    /** 主修选择弹层：列出全部可选流派 + 「清除」。 */
    private void drawMainDaoPopup(GuiGraphics g, int mouseX, int mouseY) {
        for (int[] h : mainHit) {
            int idx = h[4];
            if (idx >= mainOptions.size()) {
                continue;
            }
            String key = mainOptions.get(idx);
            boolean hover = mouseX >= h[0] && mouseX < h[2]
                    && mouseY >= h[1] && mouseY < h[3];
            boolean cur = key.equals(mainDao);

            g.fill(h[0], h[1], h[2], h[3] - 1, hover ? C_OFFER_HOVER : 0xF0161512);
            int ty = h[1] + 2;
            // 当前主修打勾；「清除」项用 ✕；其余缩进对齐
            String mark = cur ? "✓ " : (key.isEmpty() ? "✕ " : "  ");
            String name = key.isEmpty() ? "清除主修" : mainDaoLabel(key);
            g.drawString(font, mark + name, h[0] + 5, ty,
                    cur ? C_GOLD : (hover ? C_TEXT : C_DIM), true);
        }
    }

    /**
     * 页脚开关：攒满历练时是否自动弹出本界面。
     *
     * <p>坐标全部来自 {@link #layout()} 分配的命中区，这里只画不另算——
     * 否则视觉与点击区会错位（见类注释「铁律」第 1 条）。
     */
    private void drawAutoPromptToggle(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = mouseX >= toggleX0 && mouseX < toggleX1
                && mouseY >= toggleY0 && mouseY < toggleY1;
        int edge = hover ? C_GOLD : C_CHIP_EDGE;

        g.fill(toggleX0, toggleY0, toggleX1, toggleY1, C_CHIP);
        g.fill(toggleX0, toggleY0, toggleX1, toggleY0 + 1, edge);
        g.fill(toggleX0, toggleY1 - 1, toggleX1, toggleY1, edge);
        g.fill(toggleX0, toggleY0, toggleX0 + 1, toggleY1, edge);
        g.fill(toggleX1 - 1, toggleY0, toggleX1, toggleY1, edge);

        g.drawString(font, toggleLabel(), toggleX0 + 5, toggleY0 + 3,
                autoPrompt ? C_TEXT : C_DISABLED, true);

        // 轨道 + 滑块：开 = 靠右、青绿；关 = 靠左、灰
        int trackX = toggleX0 + 5 + font.width(toggleLabel()) + 8;
        int trackY = toggleY0 + 4;
        g.fill(trackX, trackY, trackX + 16, trackY + 7,
                autoPrompt ? 0x557FBFA8 : 0x33FFFFFF);
        int knobX = autoPrompt ? trackX + 10 : trackX + 1;
        g.fill(knobX, trackY + 1, knobX + 5, trackY + 6,
                autoPrompt ? C_ACCENT : C_DIM);
    }

    // ================================================================ 交互

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 主修弹层开着时，它优先吃掉点击（否则会穿透到底下的候选行）
            if (mainOptions != null) {
                for (int[] h : mainHit) {
                    if (mx >= h[0] && mx < h[2] && my >= h[1] && my < h[3]) {
                        int idx = h[4];
                        if (idx < mainOptions.size()) {
                            PacketDistributor.sendToServer(
                                    new DaotuMainDaoPayload(mainOptions.get(idx)));
                            closeMainPopup();
                            Minecraft.getInstance().getSoundManager().play(
                                    SimpleSoundInstance.forUI(
                                            SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        }
                        return true;
                    }
                }
                // 点在弹层外面 = 关掉，不当成一次选择
                closeMainPopup();
                return true;
            }
            // 页脚「主修」按钮：打开选择弹层
            if (mx >= mainX0 && mx < mainX1 && my >= mainY0 && my < mainY1) {
                openMainPopup();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            // 页脚开关：先翻本地（点了立刻有反馈），再交给服务端定夺
            if (mx >= toggleX0 && mx < toggleX1 && my >= toggleY0 && my < toggleY1) {
                autoPrompt = !autoPrompt;
                // 先写客户端偏好（立刻生效、且重进游戏还记得），再同步给服务端
                DaotuClientPrefs.setAutoPrompt(autoPrompt);
                PacketDistributor.sendToServer(new DaotuSettingPayload(autoPrompt));
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            // 候选行：点一下就提交，之后无法更改
            for (int[] h : offerHit) {
                if (mx >= h[0] && mx < h[2] && my >= h[1] && my < h[3]) {
                    PacketDistributor.sendToServer(new DaotuPickPayload(offerDraw, h[4]));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void openMainPopup() {
        mainOptions = new ArrayList<>();
        for (String[] kv : MAIN_DAOS) {
            mainOptions.add(kv[0]);
        }
        // 末尾加「清除」——已设定时才需要，但统一放着省得判断
        mainOptions.add("");
        layout();
    }

    private void closeMainPopup() {
        mainOptions = null;
        layout();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int visible = listBottom - listTop;
        int max = Math.max(0, contentHeight - visible);
        if (max > 0) {
            scroll = Mth.clamp(scroll - (int) (dy * ROW_H), 0, max);
            layout();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 主修弹层开着时，ESC 只关弹层，不关整个界面（否则玩家选个主修还要重开面板）
        if (keyCode == 256 && mainOptions != null) {
            closeMainPopup();
            return true;
        }
        // 道途键（改键后跟着变）或 ESC 关闭
        if (DaotuKeyMappings.VIEW.matches(keyCode, scanCode) || keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ================================================================ 工具

    /** 按像素宽度裁剪，超宽补 {@code …}。 */
    private String fit(String s, int maxW) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= maxW) {
            return s;
        }
        String ell = "…";
        int ellW = font.width(ell);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            int cw = font.width(c);
            if (w + cw + ellW > maxW) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        return sb + ell;
    }

    /** 画一个徽标，返回右边界。 */
    private int chip(GuiGraphics g, int x, int y, String text, int color) {
        int padX = 5;
        int w = font.width(text) + padX * 2;
        g.fill(x, y, x + w, y + 11, C_CHIP);
        g.fill(x, y, x + w, y + 1, C_CHIP_EDGE);
        g.fill(x, y + 10, x + w, y + 11, C_CHIP_EDGE);
        g.drawString(font, text, x + padX, y + 1, color, true);
        return x + w;
    }

    /**
     * 属性条归一化。
     *
     * <p>百分比按 100% 归一；平加类各有量级差异（幸运 0.5 已经很多，
     * 护甲韧性 1.5 才刚起步），统一用 1.0 归一会让「护甲韧性 1.50」
     * 直接顶满成金色，看起来像已到上限。因此按每个键的实际量级给分母。
     */
    private static double attrScale(String label, String value) {
        double v = parseOr(value.replace("%", "").replace("+", "").trim(), 0.0);
        double cap;
        if (value.contains("%")) {
            cap = 100.0;
        } else {
            cap = switch (label) {
                case "护甲韧性" -> 20.0;
                case "伤害吸收" -> 16.0;
                case "击退抗性" -> 1.0;
                case "幸运" -> 5.0;
                case "安全摔落" -> 10.0;
                case "攻击击退" -> 5.0;
                case "水下呼吸" -> 10.0;
                case "跨越高度" -> 2.0;
                default -> 5.0;
            };
        }
        return Math.min(1.0, Math.abs(v) / cap);
    }

    private static double parseOr(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 属性键 → 中文名。客户端独立一份，不引用服务端。 */
    private static String attrLabel(String key) {
        return switch (key) {
            case "attack_damage" -> "力量";
            case "max_health" -> "生命";
            case "movement_speed" -> "速度";
            case "attack_speed" -> "攻速";
            case "armor_toughness" -> "护甲韧性";
            case "max_absorption" -> "伤害吸收";
            case "knockback_resistance" -> "击退抗性";
            case "fall_damage_multiplier" -> "摔伤减免";
            case "luck" -> "幸运";
            case "safe_fall_distance" -> "安全摔落";
            case "attack_knockback" -> "攻击击退";
            case "oxygen_bonus" -> "水下呼吸";
            case "step_height" -> "跨越高度";
            case "scale" -> "体型";
            default -> key;
        };
    }

    private static String attrValueText(String key, double v) {
        return isPercentKey(key) ? fmtPct(v) : fmt2(v);
    }

    private static boolean isPercentKey(String key) {
        return switch (key) {
            case "attack_damage", "max_health", "movement_speed", "attack_speed",
                 "fall_damage_multiplier", "scale" -> true;
            default -> false;
        };
    }

    /** buff id → 中文名。 */
    private static String buffName(String id) {
        return switch (id) {
            case "shuidaozengfu" -> "水道增幅";
            case "yudaoguchongweilizengfu" -> "渔道蛊虫威力增幅";
            case "qidaoguchongweilizengfu" -> "气道蛊虫威力增幅";
            case "jiandaoshanghaizengfu" -> "剑道伤害增幅";
            case "lvdaozengfu" -> "律道增幅";
            case "jindaozengfu" -> "金道增幅";
            case "yandaozengfu" -> "炎道增幅";
            case "leidaozengshang" -> "雷道增伤";
            case "weilizengjia" -> "威力增加";
            case "weilijianbei" -> "威力减半";
            case "weilianfanbeicd" -> "威力翻倍冷却";
            default -> id;
        };
    }

    private static String zhuanText(double z) {
        int n = (int) Math.round(z);
        return switch (n) {
            case 1 -> "一转";
            case 2 -> "二转";
            case 3 -> "三转";
            case 4 -> "四转";
            case 5 -> "五转";
            default -> n + "转";
        };
    }

    /** 复刻服务端档位分段（客户端不能引用 DaotuDrawTable 的 Tier）。 */
    private static String tierName(int drawIndex) {
        if (drawIndex <= 5) {
            return "1";
        }
        if (drawIndex <= 15) {
            return "2";
        }
        if (drawIndex <= 30) {
            return "3";
        }
        if (drawIndex <= 60) {
            return "4";
        }
        return "5";
    }

    /** 复刻服务端转数缩放 1.4^(z-1)。 */
    private static double zhuanScale(double z) {
        double zz = Math.max(1.0, Math.min(5.0, z));
        return Math.pow(1.4, zz - 1.0);
    }

    private static String fmt1(double v) {
        return String.format("%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format("%.2f", v);
    }

    private static String fmtPct(double v) {
        return String.format("%+.1f%%", v * 100.0);
    }
}
