"""2.4.1 修 2.4.0 的平铺花屏。

根因：MC 的 `blit(tex, x, y, u, v, w, h, texW, texH)` 把 `w` 同时当作屏幕宽度与采样宽度，
把 64px 纹理拉宽 → UV 超出 1.0 → 纹理横向平铺（截图里的 19 列 = 1200/64）。

本脚本把所有「拉伸」画法换成 `strip()`（分块，每块 ≤ texW，末块裁切）或纯色 `fill`。
"""
import re

P = r'D:\daima\mcmod\天意\src\main\java\net\guzhenren\tianyi\client\TianyiPanelScreen.java'
s = open(P, encoding='utf-8').read()

# 1. 三个 nine() → body()
s = s.replace(
    "        nine(g, TEX_PANEL, left, top, right, bottom, 8, 64, 64);\n"
    "        nine(g, TEX_PANEL, left + 4, top + 18, left + leftW, bottom - 20, 8, 64, 64);\n"
    "        nine(g, TEX_PANEL, left + leftW + 4, top + 18, right - 4, bottom - 20, 8, 64, 64);",
    "        body(g, left, top, right, bottom);\n"
    "        body(g, left + 4, top + 18, left + leftW, bottom - 20);\n"
    "        body(g, left + leftW + 4, top + 18, right - 4, bottom - 20);")

# 2. 分隔线
s = s.replace("        g.blit(TEX_DIV, x0, y, 0f, 0f, x1 - x0, 5, 48, 5);",
              "        strip(g, TEX_DIV, x0, x1, y, 5, 48, 5);")

# 3. 进度条
s = s.replace("        g.blit(TEX_BAR_BG, x, y, 0f, 0f, w, 5, 64, 6);",
              "        strip(g, TEX_BAR_BG, x, x + w, y, 5, 64, 6);")
s = s.replace("            g.blit(TEX_BAR_FILL, x + 1, y + 1, 0f, 0f, fillW, 3, 64, 6);",
              "            strip(g, TEX_BAR_FILL, x + 1, x + 1 + fillW, y + 1, 3, 64, 6);")

# 4. 谕令行 / 降谕行
s = s.replace("            g.blit(hover ? TEX_ROW_HL : TEX_ROW, x0 + 2, ry, 0f, 0f, x1 - x0 - 4, ROW_H, 64, 18);",
              "            strip(g, hover ? TEX_ROW_HL : TEX_ROW, x0 + 2, x1 - 2, ry, ROW_H, 64, 18);")
s = s.replace("            g.blit(TEX_ROW, x0 + 2, ry, 0f, 0f, x1 - x0 - 4, ROW_H, 64, 18);",
              "            strip(g, TEX_ROW, x0 + 2, x1 - 2, ry, ROW_H, 64, 18);")

# 5. 页签
s = s.replace("        g.blit(on ? TEX_TAB_ON : TEX_TAB_OFF, x, y, 0f, 0f, w, 13, 40, 13);",
              "        strip(g, on ? TEX_TAB_ON : TEX_TAB_OFF, x, x + w, y, 13, 40, 13);")

# 6. 按钮
s = s.replace("        g.blit(hover ? TEX_BTN_HL : TEX_BTN, x0, y0, 0f, 0f, x1 - x0, y1 - y0, 48, 14);",
              "        strip(g, hover ? TEX_BTN_HL : TEX_BTN, x0, x1, y0, y1 - y0, 48, 14);")

# 7. 颜色常量（body() 用到）
anchor = "    private static final int T_GOLD = 0xFFE8C86A;"
if 'C_INK' not in s:
    s = s.replace(anchor,
                  "    private static final int C_INK = 0xFF14100E;\n"
                  "    private static final int C_BLACK = 0xFF0A0705;\n"
                  "    private static final int C_GOLD_DK = 0xFF6B4F1D;\n" + anchor)

open(P, 'w', encoding='utf-8', newline='\n').write(s)

# 自检：还有没有会被拉宽的 blit
stretched = [m.group(0)[:90] for m in re.finditer(r'g\.blit\([^;]*?\)', s)
             if re.search(r'blit\((TEX_[A-Z_]+|\w+),', m.group(0))
             and 'strip(' not in m.group(0)]
print('剩余裸 blit 调用:', len(stretched))
for x in stretched:
    print('   ', x)
print('nine( 残留:', s.count('nine('), ' strip( 次数:', s.count('strip('))
