"""把天机阁接到网络层与面板上（含品阶分类）。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

# ---------------------------------------------------------------- 1. 网络层：发商品表 + 买
p = os.path.join(SRC, 'TianyiNet.java')
s = open(p, encoding='utf-8').read()
if 'tianjige' not in s:
    s = s.replace('''        // 试炼列表（静态试炼 + AI 降谕统一成同一形状）''',
'''        // 天机阁：品阶分类 + 商品（含价格与是否已有）
        CompoundTag pav = new CompoundTag();
        for (String tier : TianyiPavilion.TIERS) {
            pav.putInt("n_" + tier, TianyiPavilion.countOf(tier));
        }
        pav.putInt("n_all", TianyiPavilion.countOf(""));
        ListTag goods = new ListTag();
        for (TianyiPavilion.Offer o : TianyiPavilion.offers(player, "")) {
            CompoundTag gt = new CompoundTag();
            gt.putString("id", o.id());
            gt.putString("name", o.name());
            gt.putString("q", o.quality());
            gt.putString("desc", o.desc());
            gt.putInt("price", o.price());
            gt.putBoolean("owned", o.owned());
            goods.add(gt);
        }
        pav.put("goods", goods);
        t.put("tianjige", pav);

        // 试炼列表（静态试炼 + AI 降谕统一成同一形状）''')
if 'buy_talent' not in s:
    s = s.replace('            case "rebirth" -> LifeBook.settle(player);',
                  '            case "rebirth" -> LifeBook.settle(player);\n'
                  '            case "buy_talent" -> {\n'
                  '                String msg = TianyiPavilion.buy(player, arg);\n'
                  '                if (!msg.isEmpty()) {\n'
                  '                    TianyiVoice.say(player, "pav:" + arg, msg);\n'
                  '                }\n'
                  '            }')
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('Net: 天机阁状态 + buy_talent 已接')

# ---------------------------------------------------------------- 2. 命令：/tianyi pavilion
p = os.path.join(SRC, 'TianyiCommands.java')
s = open(p, encoding='utf-8').read()
if 'TianyiPavilion' not in s:
    s = s.replace('''                .then(Commands.literal("指瑕").executes(TianyiCommands::insight))''',
'''                .then(Commands.literal("pavilion")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::pavilion))
                .then(Commands.literal("天机阁").executes(TianyiCommands::pavilion))
                .then(Commands.literal("指瑕").executes(TianyiCommands::insight))''')
    s = s.replace('''    private static int insight(CommandContext<CommandSourceStack> ctx)''',
'''    private static int pavilion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        StringBuilder sb = new StringBuilder("§6[天机阁]§r 天机 " + TianyiEcon.points(p) + " · ");
        for (String tier : TianyiPavilion.TIERS) {
            sb.append(tier).append(" ").append(TianyiPavilion.countOf(tier)).append("  ");
        }
        p.displayClientMessage(Component.literal(sb.toString()), false);
        p.displayClientMessage(Component.literal("§7按 J 开面板 → 「天机阁」页，按品阶分类浏览"), false);
        return 1;
    }

    private static int insight(CommandContext<CommandSourceStack> ctx)''')
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('Commands: /tianyi pavilion + 天机阁 已加')

# ---------------------------------------------------------------- 3. 面板：新增「天机阁」页（含品阶页签）
p = os.path.join(SRC, 'client', 'TianyiPanelScreen.java')
s = open(p, encoding='utf-8').read()
if 'PAGE_PAVILION' not in s:
    s = s.replace('    private static final int PAGE_LIFE = 4;',
                  '    private static final int PAGE_LIFE = 4;\n'
                  '    /** 天机阁：词条兑换（按品阶分类）。 */\n'
                  '    private static final int PAGE_PAVILION = 5;')
    s = s.replace('            default -> renderLife(g, rx0, ry0, rx1, ry1);',
                  '            case PAGE_LIFE -> renderLife(g, rx0, ry0, rx1, ry1);\n'
                  '            default -> renderPavilion(g, rx0, ry0, rx1, ry1);')
    s = s.replace('    private int pavTier = 0;', '')  # 防重复
    s = s.replace('    private int hoverY = -1;',
                  '    private int hoverY = -1;\n'
                  '    /** 天机阁品阶筛选：0 = 全部，1..5 对应 黄/玄/地/天/极。 */\n'
                  '    private int pavTier;')
    # 页面方法
    s = s.replace('    // ---------------------------------------------------------------- 底栏',
'''    // ---------------------------------------------------------------- 天机阁

    /**
     * 天机阁：顶部品阶分类页签（全部 / 黄 / 玄 / 地 / 天 / 极），下面是该档的词条。
     *
     * <p>分类是用户明确要求的：147 条一次列完没法看，按品阶筛完每档只有十几到几十条。
     * 每行显示「名字 · 品阶 · 价格」+ 一行描述；已拥有的整行压暗并显示「已有」。
     */
    private void renderPavilion(GuiGraphics g, int x0, int top, int x1, int bottom) {
        CompoundTag pav = TianyiClientState.get().getCompound("tianjige");
        int all = pav.getInt("n_all");
        sectionHeader(g, x0, top, x1, "天 机 阁", "天机 " + TianyiClientState.i("tianji") + " · 共 " + all + " 条");

        // 品阶页签
        int cx = x0 + PAD;
        int cy = top + 20;
        String[] labels = {"全部", "黄", "玄", "地", "天", "极"};
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 12;
            boolean on = pavTier == i;
            g.fill(cx, cy, cx + w, cy + 12, on ? 0xFF3A2A10 : 0xFF1A1512);
            hline(g, cx, cx + w, cy, on ? GOLD_HI : GOLD_DK);
            hline(g, cx, cx + w, cy + 11, on ? GOLD : BLACK);
            int num = i == 0 ? all : pav.getInt("n_" + labels[i]);
            g.drawString(font, labels[i] + " " + num, cx + 4, cy + 3, on ? T_GOLD : T_DIM, true);
            hits.add(new Hit(cx, cy, cx + w, cy + 12, "pavtier", String.valueOf(i)));
            cx += w + 3;
        }

        String want = pavTier == 0 ? "" : labels[pavTier];
        ListTag goods = pav.getList("goods", 10);
        int ry = cy + 17;
        int shown = 0;
        int viewRows = Math.max(1, (bottom - ry) / ROW_H);
        for (int i = 0; i < goods.size() && shown < viewRows; i++) {
            CompoundTag gt = goods.getCompound(i);
            if (!want.isEmpty() && !want.equals(gt.getString("q"))) {
                continue;
            }
            boolean owned = gt.getBoolean("owned");
            int price = gt.getInt("price");
            boolean can = !owned && TianyiClientState.i("tianji") >= price;
            rowBack(g, x0 + 2, x1 - 2, ry, hoverY >= ry && hoverY < ry + ROW_H);
            g.drawString(font, "§7" + gt.getString("q") + "§r " + gt.getString("name"),
                    x0 + PAD, ry + 1, owned ? 0xFF7A7060 : T_BODY, true);
            g.drawString(font, owned ? "已有" : price + " 天机",
                    x1 - PAD - 34 - font.width(owned ? "已有" : price + " 天机"), ry + 1,
                    owned ? 0xFF6E6455 : (can ? T_GOLD : T_WARN), true);
            drawWrapped(g, gt.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - 44, 1, 0xFF9A9080);
            if (!owned) {
                goldButton(g, x1 - PAD - 34, ry + 2, x1 - PAD, ry + 14, "买", "buy_talent",
                        gt.getString("id"), can);
            }
            ry += ROW_H;
            shown++;
        }
        if (shown == 0) {
            g.drawString(font, "此品阶无货。换一档看看。", x0 + PAD, ry + 2, T_DIM, true);
        }
    }

    // ---------------------------------------------------------------- 底栏''')
    # 底栏加页签
    s = s.replace('        bx = tab(g, bx, y, "降谕", PAGE_ORACLE);',
                  '        bx = tab(g, bx, y, "天机阁", PAGE_PAVILION);\n'
                  '        bx = tab(g, bx, y, "降谕", PAGE_ORACLE);')
    # 点击分发
    s = s.replace('                case "buy_talent" -> {',
                  '                case "pavtier" -> pavTier = Integer.parseInt(h.arg());\n'
                  '                case "buy_talent" -> TianyiClientHandler.action("buy_talent", h.arg());\n'
                  '                case "buy_talent_unused" -> {')
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('Panel: 天机阁页 + 品阶分类页签 已加')
