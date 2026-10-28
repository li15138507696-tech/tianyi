"""2.5.0：把所有贴图绘制换成几何绘制（fill），彻底消除"糊"。

## 为什么放弃贴图

2.4.x 用了 PNG 纹理 + blit。两个问题都出在"纹理采样不受我们控制"：
1. `blit(..., w, h, texW, texH)` 的 w 同时是屏幕宽与采样宽 → 拉宽即平铺（2.4.0 网格事故）；
2. 即便分块贴对了，纹理仍受 GPU 采样过滤影响，在大 GUI 缩放下会被插值成"滤镜感"。

`GuiGraphics.fill` 是**纯几何填充**：整数坐标、1px 边、无采样，任何缩放下都是硬的。
所以界面全部改成 fill 绘制——用「多层描边 + 阶梯渐变 + 角饰菱形」做出金铜质感，
而不是靠贴图。

本脚本只替换绘制辅助方法区（从「// ---- 纹理」到「// ---- 交互」之前），保持业务逻辑不动。
"""
import re

P = r'D:\daima\mcmod\天意\src\main\java\net\guzhenren\tianyi\client\TianyiPanelScreen.java'
s = open(P, encoding='utf-8').read()

start = s.index('    // ---------------------------------------------------------------- 纹理')
end = s.index('    // ---------------------------------------------------------------- 交互')

new = '''    // ---------------------------------------------------------------- 配色（几何绘制用的实色）
    private static final int INK = 0xFF14100E;        // 墨底
    private static final int INK_LIGHT = 0xFF1F1915;  // 墨底高光（左上倒角）
    private static final int INK_DARK = 0xFF0B0806;   // 墨底压暗（右下倒角）
    private static final int BLACK = 0xFF050302;      // 最外描边
    private static final int GOLD = 0xFFC9A227;       // 金线
    private static final int GOLD_HI = 0xFFE8C86A;    // 高光金
    private static final int GOLD_DK = 0xFF6B4F1D;    // 暗金
    private static final int GOLD_DEEP = 0xFF3A2A10;  // 深金（渐变底）
    private static final int CINNABAR = 0xFFB03A2E;   // 朱砂（印）
    private static final int TROUGH = 0xFF0A0705;     // 进度条槽

    // ---------------------------------------------------------------- 文字
    private static final int T_GOLD = 0xFFE8C86A;
    private static final int T_BODY = 0xFFE6E0D2;
    private static final int T_DIM = 0xFFB3A98F;
    private static final int T_OK = 0xFF8FD48A;
    private static final int T_WARN = 0xFFE0695A;

    private static final int ROW_H = 18;
    private static final int PAD = 7;

    private static final int PAGE_QUEST = 0;
    private static final int PAGE_BODY = 1;
    private static final int PAGE_STATE = 2;
    private static final int PAGE_ORACLE = 3;
    private static final int PAGE_LIFE = 4;

    private int page = PAGE_QUEST;
    private int scroll;
    private int maxScroll;
    private int hoverY = -1;

    private record Hit(int x0, int y0, int x1, int y1, String action, String arg) {
        boolean inside(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public TianyiPanelScreen() {
        super(Component.literal("天意"));
    }

    public void refresh() {
        scroll = Math.min(scroll, maxScroll);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 背景：不透明墨底，不用原版那套「模糊世界 + 暗化渐变」。
     *
     * <p>MC 1.21 会按 {@code menuBackgroundBlurriness} 把世界画面模糊一遍。
     * 面板四周与分栏之间一有缝，透出来的就是糊掉的世界——玩家看到的就是
     * 「像加了层滤镜」。直接盖不透明墨色，模糊层不会出现在画面里。
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0B0806);
    }

    // ---------------------------------------------------------------- 绘制原语（全 fill，永不被采样）

    /** 1px 横线。 */
    private void hline(GuiGraphics g, int x0, int x1, int y, int color) {
        g.fill(x0, y, x1, y + 1, color);
    }

    /** 1px 竖线。 */
    private void vline(GuiGraphics g, int x, int y0, int y1, int color) {
        g.fill(x, y0, x + 1, y1, color);
    }

    /** 角饰：3x3 金点 + 十字，代替贴图云纹（整数填充，任何缩放都硬）。 */
    private void cornerMark(GuiGraphics g, int cx, int cy, int dx, int dy) {
        g.fill(cx, cy, cx + 3, cy + 1, GOLD_HI);
        g.fill(cx, cy, cx + 1, cy + 3, GOLD_HI);
        g.fill(cx + dx * 2, cy + dy * 2, cx + dx * 2 + 1, cy + dy * 2 + 1, GOLD_DK);
    }

    /**
     * 容器：墨底 + 四层描边 + 左上/右下倒角 + 四角金饰。
     *
     * <p>层次（由外向内）：黑边 → 暗金 → 墨底倒角 → 内金线。
     * 靠「亮度差」而不是贴图噪点制造金铜质感，所以缩放到 4x 也不会糊。
     */
    private void body(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, INK);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, INK_LIGHT);      // 上倒角
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, INK_LIGHT);      // 左倒角
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, INK_DARK);       // 下压暗
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, INK_DARK);       // 右压暗
        // 外描边
        hline(g, x0, x1, y0, BLACK);
        hline(g, x0, x1, y1 - 1, BLACK);
        vline(g, x0, y0, y1, BLACK);
        vline(g, x1 - 1, y0, y1, BLACK);
        // 暗金内框
        hline(g, x0 + 1, x1 - 1, y0 + 1, GOLD_DK);
        hline(g, x0 + 1, x1 - 1, y1 - 2, GOLD_DK);
        vline(g, x0 + 1, y0 + 1, y1 - 1, GOLD_DK);
        vline(g, x1 - 2, y0 + 1, y1 - 1, GOLD_DK);
        // 四角金饰
        cornerMark(g, x0 + 3, y0 + 3, 1, 1);
        cornerMark(g, x1 - 6, y0 + 3, -1, 1);
        cornerMark(g, x0 + 3, y1 - 6, 1, -1);
        cornerMark(g, x1 - 6, y1 - 6, -1, -1);
    }

    /** 金色横带（标题条 / 表头）：四段阶梯渐变 + 上下金线。 */
    private void goldBand(GuiGraphics g, int x0, int x1, int y, int h) {
        int band = Math.max(1, h / 4);
        for (int i = 0; i < 4; i++) {
            int yy0 = y + i * band;
            int yy1 = (i == 3) ? y + h : yy0 + band;
            int color = switch (i) {
                case 0 -> 0xFF4A3414;
                case 1 -> 0xFF3A2A10;
                case 2 -> 0xFF2C2010;
                default -> 0xFF20180C;
            };
            g.fill(x0, yy0, x1, yy1, color);
        }
        hline(g, x0, x1, y, GOLD);
        hline(g, x0, x1, y + h - 1, GOLD_DK);
    }

    private void titleBar(GuiGraphics g, int x0, int y, int x1) {
        goldBand(g, x0 + 1, x1 - 1, y, 14);
        // 朱砂印（几何）：红底 + 金框 + 中竖
        g.fill(x0 + 3, y + 1, x0 + 15, y + 13, CINNABAR);
        hline(g, x0 + 3, x0 + 15, y + 1, GOLD);
        hline(g, x0 + 3, x0 + 15, y + 12, GOLD_DK);
        vline(g, x0 + 3, y + 1, y + 13, GOLD);
        vline(g, x0 + 14, y + 1, y + 13, GOLD_DK);
        vline(g, x0 + 9, y + 4, y + 10, 0xFFE8C86A);
        hline(g, x0 + 6, x0 + 12, y + 6, 0xFFE8C86A);
        g.drawString(font, "天 意", x0 + 19, y + 3, T_GOLD, true);
        String right = "第 " + TianyiClientState.i("life") + " 世 · 天机 "
                + TianyiClientState.i("tianji") + " · 第 " + TianyiClientState.i("tier")
                + " 阶 " + TianyiClientState.s("tierName");
        g.drawString(font, right, x1 - 6 - font.width(right), y + 3, T_DIM, true);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int maxLines, int color) {
        if (text == null || text.isEmpty() || maxLines <= 0) {
            return y;
        }
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(text), Math.max(20, width));
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) {
            g.drawString(font, lines.get(i), x, y, color, true);
            y += 9;
        }
        return y;
    }

    /** 分隔饰：中间 5x5 菱形金点 + 两侧暗金细线。 */
    private void divider(GuiGraphics g, int x0, int x1, int y) {
        int cx = (x0 + x1) / 2;
        hline(g, x0, cx - 5, y + 1, GOLD_DK);
        hline(g, cx + 5, x1, y + 1, GOLD_DK);
        g.fill(cx - 1, y, cx + 2, y + 1, GOLD);
        g.fill(cx, y - 1, cx + 1, y + 2, GOLD);
        g.fill(cx, y, cx + 1, y + 1, GOLD_HI);
    }

    // ---------------------------------------------------------------- 左栏

    private void renderState(GuiGraphics g, int x0, int top, int x1, int bottom) {
        int x = x0 + PAD;
        int w = x1 - x0 - PAD * 2;
        int y = top + PAD;

        g.drawString(font, "修 为", x, y, T_GOLD, true);
        y += 11;
        g.drawString(font, TianyiClientState.i("zhuan") + " 转 · "
                + stageName(TianyiClientState.i("jieduan")), x, y, T_BODY, true);
        y += 10;
        g.drawString(font, "空窍 " + TianyiClientState.i("kongqiao")
                + " · 修炼 " + fmt(TianyiClientState.d("xiulian")), x, y, T_DIM, true);
        y += 13;

        y = resBar(g, x, y, w, "真元", TianyiClientState.d("zy"), TianyiClientState.d("zyMax"));
        y = resBar(g, x, y, w, "精力", TianyiClientState.d("jl"), TianyiClientState.d("jlMax"));
        y = resBar(g, x, y, w, "念头", TianyiClientState.d("nt"), TianyiClientState.d("ntMax"));

        divider(g, x, x + w, y + 2);
        y += 10;

        g.drawString(font, "天机", x, y, T_DIM, true);
        g.drawString(font, String.valueOf(TianyiClientState.i("tianji")), x + 30, y, T_GOLD, true);
        y += 11;
        y = stat(g, x, y, "阶位", "第 " + TianyiClientState.i("tier") + " 阶 · "
                + TianyiClientState.s("tierName"));
        y = stat(g, x, y, "下阶", TianyiClientState.i("tierNext") <= 0 ? "已至顶"
                : "还需 " + TianyiClientState.i("tierNext"));
        y = stat(g, x, y, "注视", TianyiClientState.i("attention") + " · "
                + TianyiClientState.s("attentionLabel"));

        int wy = bottom - 44;
        divider(g, x, x + w, wy - 7);
        g.drawString(font, "天意低语", x, wy, T_DIM, true);
        drawWrapped(g, whisper(), x, wy + 10, w, 3, T_BODY);
    }

    /** 资源条：槽 + 三段阶梯金填充（无采样，缩放也硬）。 */
    private int resBar(GuiGraphics g, int x, int y, int w, String name, double cur, double max) {
        g.drawString(font, name, x, y, T_DIM, true);
        g.drawString(font, fmt(cur) + (max > 0 ? " / " + fmt(max) : ""), x + 26, y, T_BODY, true);
        y += 10;
        g.fill(x, y, x + w, y + 5, TROUGH);
        hline(g, x, x + w, y, GOLD_DK);
        hline(g, x, x + w, y + 4, 0xFF050302);
        int fillW = max > 0 ? (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, cur / max))) : 0;
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 4, GOLD_DK);
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 3, GOLD);
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 2, GOLD_HI);
        }
        return y + 9;
    }

    private String whisper() {
        if (TianyiClientState.i("zhuan") <= 0) {
            return "空窍未开。你还不在局中。";
        }
        if (TianyiClientState.i("tianji") <= 0) {
            return "你什么也没做。天意在看。";
        }
        if (readyCount() > 0) {
            return "有 " + readyCount() + " 道谕令等着你去领。别让它等太久。";
        }
        return "活下去。这就够了。";
    }

    private int stat(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, label, x, y, T_DIM, true);
        g.drawString(font, value, x + 30, y, T_BODY, true);
        return y + 10;
    }

    // ---------------------------------------------------------------- 谕令

    private void renderQuests(GuiGraphics g, int x0, int top, int x1, int bottom) {
        List<CompoundTag> rows = questRows(null);
        sectionHeader(g, x0, top, x1, "谕 令", rows.size() + " 道");
        int viewRows = Math.max(1, (bottom - top - 30) / ROW_H);
        maxScroll = Math.max(0, rows.size() - viewRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int ry = top + 24;
        for (int i = scroll; i < rows.size() && i < scroll + viewRows; i++) {
            CompoundTag q = rows.get(i);
            boolean ready = q.getBoolean("ready");
            rowBack(g, x0 + 2, x1 - 2, ry, hoverY >= ry && hoverY < ry + ROW_H);
            g.drawString(font, kindTag(q.getString("kind")) + q.getString("name")
                            + "  " + q.getInt("p") + "/" + q.getInt("n"),
                    x0 + PAD, ry + 1, ready ? T_OK : T_BODY, true);
            drawWrapped(g, q.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - (ready ? 40 : 0), 1, T_DIM);
            if (ready) {
                goldButton(g, x1 - PAD - 34, ry + 2, x1 - PAD, ry + 14, "领取", "claim",
                        q.getString("id"));
            }
            ry += ROW_H;
        }
        if (rows.isEmpty()) {
            g.drawString(font, "无谕令。以希望蛊开窍后，天意才会降下第一道。",
                    x0 + PAD, ry + 2, T_DIM, true);
        }
    }

    /** 行底：常态只一条发丝线；悬停加左侧金条与浅金带。 */
    private void rowBack(GuiGraphics g, int x0, int x1, int y, boolean hover) {
        if (hover) {
            g.fill(x0, y, x1, y + ROW_H - 1, 0xFF22190F);
            g.fill(x0, y, x0 + 2, y + ROW_H - 1, GOLD);
        }
        hline(g, x0, x1, y + ROW_H - 1, 0xFF2C2010);
    }

    private void sectionHeader(GuiGraphics g, int x0, int top, int x1, String title, String note) {
        g.drawString(font, title, x0 + PAD, top + 3, T_GOLD, true);
        if (note != null && !note.isEmpty()) {
            g.drawString(font, note, x1 - PAD - font.width(note), top + 3, T_DIM, true);
        }
        divider(g, x0 + PAD, x1 - PAD, top + 15);
    }

    // ---------------------------------------------------------------- 身

    private void renderBody(GuiGraphics g, int x0, int top, int x1, int bottom) {
        int x = x0 + PAD;
        int y = top + PAD + 18;
        int w = x1 - x0 - PAD * 2;
        sectionHeader(g, x0, top, x1, "身 · 本体属性", "天机 " + TianyiClientState.i("tianji"));

        g.drawString(font, "修为", x, y, T_GOLD, true);
        y += 10;
        y = stat(g, x, y, "转数", TianyiClientState.i("zhuan") + " 转 · "
                + stageName(TianyiClientState.i("jieduan")) + "   修炼 " + fmt(TianyiClientState.d("xiulian")));
        y = stat(g, x, y, "空窍", TianyiClientState.i("kongqiao") + " 窍   资质 "
                + fmt(TianyiClientState.d("tizhi")));
        y = stat(g, x, y, "寿元", fmt(TianyiClientState.d("shouyuan")) + "   十绝压力 "
                + fmt(TianyiClientState.d("yali")));
        y = stat(g, x, y, "道德", signed(TianyiClientState.d("daode")) + "   人气 "
                + fmt(TianyiClientState.d("renqi")));

        y += 3;
        g.drawString(font, "战与魂", x, y, T_GOLD, true);
        y += 10;
        y = stat(g, x, y, "生命", fmt(TianyiClientState.d("hp")) + " / " + fmt(TianyiClientState.d("hpMax"))
                + "   攻 " + fmt(TianyiClientState.d("atk")) + "  防 " + fmt(TianyiClientState.d("def")));
        y = stat(g, x, y, "魂魄", fmt(TianyiClientState.d("hunpo")) + " / " + fmt(TianyiClientState.d("hunpoMax"))
                + "（承载 " + fmt(TianyiClientState.d("hunpoCap")) + "）");
        y = stat(g, x, y, "资源", "真元 " + fmt(TianyiClientState.d("zy"))
                + "  精力 " + fmt(TianyiClientState.d("jl")) + "  念头 " + fmt(TianyiClientState.d("nt")));

        y += 3;
        g.drawString(font, "气运与道痕", x, y, T_GOLD, true);
        y += 10;
        y = stat(g, x, y, "气运", fmt(TianyiClientState.d("qiyun")) + " / "
                + fmt(TianyiClientState.d("qiyunCap")) + " · " + TianyiClientState.s("qiyunName"));
        String dhen = TianyiClientState.s("daohenName0").isEmpty() ? "尚未凝聚道痕"
                : TianyiClientState.s("daohenName0") + " " + fmt(TianyiClientState.d("daohenVal0"))
                + " · " + TianyiClientState.s("daohenName1") + " " + fmt(TianyiClientState.d("daohenVal1"))
                + " · " + TianyiClientState.s("daohenName2") + " " + fmt(TianyiClientState.d("daohenVal2"));
        g.drawString(font, "道痕", x, y, T_DIM, true);
        drawWrapped(g, dhen, x + 26, y, w - 26, 1, T_BODY);
        y += 11;
        y = stat(g, x, y, "", "共 " + TianyiClientState.i("daohenCount") + " 道 · 合计 "
                + fmt(TianyiClientState.d("daohenSum")));

        y += 3;
        g.drawString(font, "经济", x, y, T_GOLD, true);
        y += 10;
        y = stat(g, x, y, "元石", fmt(TianyiClientState.d("yuanshi")) + "   贡献 "
                + fmt(TianyiClientState.d("gongxian")));
        stat(g, x, y, "签到", TianyiClientState.i("streak") + " 日"
                + (TianyiClientState.b("signed") ? " · 已签" : " · 未签")
                + "   保底 " + TianyiClientState.i("pity") + "/" + TianyiLuck.PITY);
    }

    private String signed(double v) {
        long n = Math.round(v);
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    // ---------------------------------------------------------------- 系统

    private void renderSystem(GuiGraphics g, int x0, int top, int x1, int bottom) {
        int x = x0 + PAD;
        int y = top + PAD + 18;
        int w = x1 - x0 - PAD * 2;
        sectionHeader(g, x0, top, x1, "系 统", "第 " + TianyiClientState.i("life") + " 世");

        y = stat(g, x, y, "天机", String.valueOf(TianyiClientState.i("tianji")));
        y = stat(g, x, y, "累计", String.valueOf(TianyiClientState.i("lifetime")));
        y = stat(g, x, y, "阶位", "第 " + TianyiClientState.i("tier") + " 阶 · "
                + TianyiClientState.s("tierName")
                + (TianyiClientState.i("tierNext") <= 0 ? "（顶）"
                : "（还需 " + TianyiClientState.i("tierNext") + "）"));
        y = stat(g, x, y, "注视", TianyiClientState.i("attention") + " · "
                + TianyiClientState.s("attentionLabel"));
        y += 5;

        drawWrapped(g, "天机只涨阶位、只付系统功能，不换物品、不与元石互通。", x, y, w, 1, T_DIM);
        y += 11;
        drawWrapped(g, "用系统会被注视：演算 / 违背 / 禁谕都会累积注视度。", x, y, w, 1, T_DIM);
        y += 17;

        boolean canFlaw = TianyiClientState.i("tianji") >= TianyiInsight.COST_FLAW;
        goldButton(g, x, y, x + 58, y + 14, "指瑕 " + TianyiInsight.COST_FLAW, "improvise", "", canFlaw);
        boolean freeSim = TianyiClientState.b("freeSim");
        boolean canSim = freeSim || TianyiClientState.i("tianji") >= TianyiInsight.COST_SIM;
        goldButton(g, x + 64, y, x + 128, y + 14,
                freeSim ? "演算 · 免费" : "演算 " + TianyiInsight.COST_SIM, "simulate", "", canSim);
        y += 21;
        drawWrapped(g, "指瑕：读你的念头，告诉你这一推会不会卡（不改物品）。", x, y, w, 1, T_DIM);
        y += 11;
        drawWrapped(g, "演算：预演与最近之敌的胜负，三档结论。算一次就被看见一次。", x, y, w, 1, T_DIM);
    }

    // ---------------------------------------------------------------- 降谕

    private void renderOracle(GuiGraphics g, int x0, int top, int x1, int bottom) {
        CompoundTag ai = TianyiClientState.get().getCompound("ai");
        boolean enabled = ai.getBoolean("enabled");
        boolean hasKey = ai.getBoolean("hasKey");
        int remain = ai.getInt("remain");
        String tag = switch (ai.getString("backend")) {
            case "numen" -> "Numen";
            case "provider" -> ai.getString("provider");
            case "dsh" -> "DSH";
            case "http" -> "自建AI";
            default -> "本地";
        };
        sectionHeader(g, x0, top, x1, "降 谕", "今日剩 " + remain + " 次 · " + tag);

        boolean can = enabled && remain > 0;
        goldButton(g, x1 - PAD - 62, top + 17, x1 - PAD, top + 31, "祈问天意", "ai_ask", "", can);

        List<CompoundTag> rows = questRows("AI");
        int ry = top + 36;
        if (rows.isEmpty()) {
            g.drawString(font, hasKey ? "尚无谕令。按右上「祈问天意」。"
                    : "未配置 AI：/tianyi ai setkey <key>，或装 Numen。", x0 + PAD, ry, T_DIM, true);
            return;
        }
        int viewRows = Math.max(1, (bottom - ry) / ROW_H);
        for (int i = 0; i < rows.size() && i < viewRows; i++) {
            CompoundTag q = rows.get(i);
            boolean ready = q.getBoolean("ready");
            rowBack(g, x0 + 2, x1 - 2, ry, hoverY >= ry && hoverY < ry + ROW_H);
            g.drawString(font, q.getString("name") + "  " + q.getInt("p") + "/" + q.getInt("n"),
                    x0 + PAD, ry + 1, ready ? T_OK : T_BODY, true);
            drawWrapped(g, q.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - (ready ? 40 : 0), 1, T_DIM);
            if (ready) {
                goldButton(g, x1 - PAD - 34, ry + 2, x1 - PAD, ry + 14, "领取", "claim", q.getString("id"));
            }
            ry += ROW_H;
        }
    }

    // ---------------------------------------------------------------- 前世

    private void renderLife(GuiGraphics g, int x0, int top, int x1, int bottom) {
        int x = x0 + PAD;
        int y = top + PAD + 18;
        int w = x1 - x0 - PAD * 2;
        sectionHeader(g, x0, top, x1, "前 世 · 百世书", null);

        y = stat(g, x, y, "本世", "第 " + TianyiClientState.i("life") + " 世");
        y = stat(g, x, y, "峰值", TianyiClientState.i("bestTianji") + " 天机");
        y = stat(g, x, y, "垂青", TianyiClientState.i("blessings") + " / " + LifeBook.MAX_BLESSINGS + " 次");
        y += 8;
        divider(g, x, x + w, y);
        y += 9;
        drawWrapped(g, "一世终了，带走本世天机 " + LifeBook.CARRY_PERCENT
                + "%，并得一次垂青（上限 " + LifeBook.MAX_BLESSINGS + " 次）。", x, y, w, 2, T_BODY);
        y += 20;
        drawWrapped(g, "不带走：转数、流派熟练度、道痕、蛊虫 —— 那些归本体。", x, y, w, 2, T_DIM);
        y += 20;
        drawWrapped(g, "结算会清空本世谕令进度与 AI 降谕；天机按比例返还。", x, y, w, 2, T_DIM);
        y += 24;
        goldButton(g, x, y, x + 86, y + 14, "结算这一世", "rebirth", "", true);
    }

    // ---------------------------------------------------------------- 底栏

    private void renderFooter(GuiGraphics g, int x0, int y, int x1) {
        int bx = x0 + PAD;
        bx = tab(g, bx, y, "谕令", PAGE_QUEST);
        bx = tab(g, bx, y, "身", PAGE_BODY);
        bx = tab(g, bx, y, "系统", PAGE_STATE);
        bx = tab(g, bx, y, "降谕", PAGE_ORACLE);
        bx = tab(g, bx, y, "前世", PAGE_LIFE);

        int ax = x1 - PAD;
        String[] acts = {"摇命", "签到"};
        String[] ids = {"luck", "sign"};
        for (int i = 0; i < acts.length; i++) {
            int w = font.width(acts[i]) + 16;
            goldButton(g, ax - w, y, ax, y + 13, acts[i], ids[i], "");
            ax -= w + 4;
        }
    }

    private int tab(GuiGraphics g, int x, int y, String label, int target) {
        int w = font.width(label) + 14;
        boolean on = page == target;
        int bg = on ? 0xFF3A2A10 : 0xFF1A1512;
        g.fill(x, y, x + w, y + 13, bg);
        hline(g, x, x + w, y, on ? GOLD_HI : GOLD_DK);
        hline(g, x, x + w, y + 12, on ? GOLD : BLACK);
        g.drawString(font, label, x + 7, y + 3, on ? T_GOLD : T_DIM, true);
        hits.add(new Hit(x, y, x + w, y + 13, "page", String.valueOf(target)));
        return x + w + 3;
    }

    /** 金铜按钮：三段填充 + 上下金线；未激活压暗且不响应点击。 */
    private void goldButton(GuiGraphics g, int x0, int y0, int x1, int y1, String label,
                            String action, String arg) {
        goldButton(g, x0, y0, x1, y1, label, action, arg, true);
    }

    private void goldButton(GuiGraphics g, int x0, int y0, int x1, int y1, String label,
                            String action, String arg, boolean active) {
        boolean hover = active && hoverY >= y0 && hoverY < y1;
        if (!active) {
            g.fill(x0, y0, x1, y1, 0xFF1A1512);
            hline(g, x0, x1, y0, 0xFF3A2A10);
        } else {
            g.fill(x0, y0, x1, y1, hover ? 0xFF4A3414 : 0xFF30240F);
            g.fill(x0, y0, x1, y0 + 1, hover ? GOLD_HI : GOLD);
            g.fill(x0, y1 - 1, x1, y1, GOLD_DK);
            vline(g, x0, y0, y1, GOLD_DK);
            vline(g, x1 - 1, y0, y1, GOLD_DK);
        }
        int color = active ? (hover ? 0xFFFFF4D8 : T_GOLD) : 0xFF6E6455;
        g.drawString(font, label, x0 + (x1 - x0 - font.width(label)) / 2, y0 + 3, color, true);
        if (active) {
            hits.add(new Hit(x0, y0, x1, y1, action, arg));
        }
    }

'''

s = s[:start] + new + s[end:]
# 关掉旧常量（若残留）避免重复定义
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('help 区已替换；残留 blit 调用:', s.count('blit('), ' nine:', s.count('nine('))
