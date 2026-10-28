"""2.7.1：修天机阁三个交互/排版 bug。

## 三个 bug（全部是我 2.7.0 的疏漏）

1. **分类点不动**：页签往 `hits` 里塞了 `"pavtier"`，但 `mouseClicked` 的 switch
   里**没有这个 case**，点击直接落进 `default` 被丢掉。
2. **「买」按钮也点不动**：同一个原因——`"buy_talent"` 的 case 也没加。
3. **文字重叠**：页签宽度按 `font.width(labels[i])` 算，但实际画的是
   `labels[i] + " " + num`（更长），于是「全部 143」溢出压到「黄」上。
   行内标题同样没有任何裁剪，长名字会撞到右侧价格。
"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi',
                 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()

# ---- 1. 点击分发：补齐 pavtier 与 buy_talent
old_case = '                case "claim" -> TianyiClientHandler.action("claim", h.arg());'
new_case = ('                case "pavtier" -> pavTier = Integer.parseInt(h.arg());\n'
            '                case "buy_talent" -> TianyiClientHandler.action("buy_talent", h.arg());\n'
            + old_case)
if 'case "pavtier"' in s:
    print('  · pavtier 已存在，跳过')
else:
    s = s.replace(old_case, new_case)
    print('  + 点击分发：pavtier / buy_talent')

# ---- 2. 页签宽度按「实际绘制的字符串」算，并裁剪到可用宽度
old_tab = '''        String[] labels = {"全部", "黄", "玄", "地", "天", "极"};
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
        }'''
new_tab = '''        String[] labels = {"全部", "黄", "玄", "地", "天", "极"};
        for (int i = 0; i < labels.length; i++) {
            int num = i == 0 ? all : pav.getInt("n_" + labels[i]);
            // 宽度必须按「真正画出去的字符串」算，否则数字会把文字挤出页签、压到下一个上
            String txt = labels[i] + " " + num;
            int w = font.width(txt) + 10;
            boolean on = pavTier == i;
            g.fill(cx, cy, cx + w, cy + 12, on ? 0xFF3A2A10 : 0xFF1A1512);
            hline(g, cx, cx + w, cy, on ? GOLD_HI : GOLD_DK);
            hline(g, cx, cx + w, cy + 11, on ? GOLD : BLACK);
            g.drawString(font, txt, cx + 5, cy + 3, on ? T_GOLD : T_DIM, true);
            hits.add(new Hit(cx, cy, cx + w, cy + 12, "pavtier", String.valueOf(i)));
            cx += w + 3;
        }'''
if old_tab in s:
    s = s.replace(old_tab, new_tab)
    print('  + 页签宽度按实际字符串计算')
else:
    print('  ! 页签片段未匹配')

# ---- 3. 行内：标题裁剪 + 价格右对齐到按钮左侧，互不侵入
old_row = '''            g.drawString(font, "§7" + gt.getString("q") + "§r " + gt.getString("name"),
                    x0 + PAD, ry + 1, owned ? 0xFF7A7060 : T_BODY, true);
            g.drawString(font, owned ? "已有" : price + " 天机",
                    x1 - PAD - 34 - font.width(owned ? "已有" : price + " 天机"), ry + 1,
                    owned ? 0xFF6E6455 : (can ? T_GOLD : T_WARN), true);
            drawWrapped(g, gt.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - 44, 1, 0xFF9A9080);'''
new_row = '''            // 右侧给「价格 + 按钮」预留固定宽度，标题按剩余宽度裁剪，绝不重叠
            String priceTxt = owned ? "已有" : price + " 天机";
            int priceW = font.width(priceTxt);
            int reserve = priceW + 44;
            int titleW = Math.max(20, (x1 - PAD - reserve) - (x0 + PAD));
            String nameTxt = "§7" + gt.getString("q") + "§r " + gt.getString("name");
            g.drawString(font, font.plainSubstrByWidth(nameTxt, titleW),
                    x0 + PAD, ry + 1, owned ? 0xFF7A7060 : T_BODY, true);
            g.drawString(font, priceTxt, x1 - PAD - 38 - priceW, ry + 1,
                    owned ? 0xFF6E6455 : (can ? T_GOLD : T_WARN), true);
            drawWrapped(g, gt.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - reserve, 1, 0xFF9A9080);'''
if old_row in s:
    s = s.replace(old_row, new_row)
    print('  + 行内标题裁剪 / 价格右对齐')
else:
    print('  ! 行内片段未匹配')

open(P, 'w', encoding='utf-8', newline='\n').write(s)
