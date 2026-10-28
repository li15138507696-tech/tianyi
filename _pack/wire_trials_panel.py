"""P1：面板新增「试炼」页（旧 34 条，按章节分组）。"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi',
                 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()
n = 0


def rep(a, b, tag):
    global s, n
    if a in s:
        s = s.replace(a, b, 1)
        n += 1
        print('  +', tag)
    else:
        print('  ! 未匹配:', tag)


# 1. 页常量
rep('    private static final int PAGE_PAVILION = 5;',
    '    private static final int PAGE_PAVILION = 5;\n'
    '    /** 试炼：旧 34 条（1.x 5 章制），与谕令并存。 */\n'
    '    private static final int PAGE_TRIAL = 6;', 'PAGE_TRIAL 常量')

# 2. 分发
rep('            case PAGE_LIFE -> renderLife(g, rx0, ry0, rx1, ry1);',
    '            case PAGE_LIFE -> renderLife(g, rx0, ry0, rx1, ry1);\n'
    '            case PAGE_TRIAL -> renderTrials(g, rx0, ry0, rx1, ry1);', '渲染分发')

# 3. 页签
rep('        bx = tab(g, bx, y, "谕令", PAGE_QUEST);',
    '        bx = tab(g, bx, y, "谕令", PAGE_QUEST);\n'
    '        bx = tab(g, bx, y, "试炼", PAGE_TRIAL);', '底栏页签')

# 4. questRows 支持 TRIAL
rep('        boolean onlyAi = "AI".equals(filter);',
    '        boolean onlyAi = "AI".equals(filter);\n'
    '        boolean onlyTrial = "TRIAL".equals(filter);\n'
    '        boolean noTrial = "!TRIAL".equals(filter);', 'questRows 变量')
rep('''            boolean ai = "AI".equals(q.getString("kind"));
            if (onlyAi && !ai) {
                continue;
            }
            if (noAi && ai) {
                continue;
            }
            out.add(q);''',
    '''            boolean ai = "AI".equals(q.getString("kind"));
            boolean trial = "TRIAL".equals(q.getString("src"));
            if (onlyAi && !ai) {
                continue;
            }
            if (noAi && ai) {
                continue;
            }
            if (onlyTrial && !trial) {
                continue;
            }
            if (noTrial && trial) {
                continue;
            }
            out.add(q);''', 'questRows 过滤')

# 5. 谕令页只列非试炼
rep('        List<CompoundTag> rows = questRows(null);\n        sectionHeader(g, x0, top, x1, "谕 令"',
    '        List<CompoundTag> rows = questRows("!TRIAL");\n        sectionHeader(g, x0, top, x1, "谕 令"',
    '谕令页过滤试炼')

# 6. 标签按来源区分
rep('''    private String kindTag(String kind) {
        return switch (kind == null ? "" : kind) {
            case "MAIN" -> "§6[天谕] §r";
            case "EARTH" -> "§a[地谕] §r";
            case "DAILY" -> "§7[日课] §r";
            case "WEEKLY" -> "§c[禁谕] §r";
            case "AI" -> "§d[降谕] §r";
            default -> "§7[谕] §r";
        };
    }''',
    '''    /**
     * 行首标签。同一 {@code kind} 在两套表里意思不同：
     * 谕令页 WEEKLY 是「禁谕」，试炼页 WEEKLY 是旧表的「周课」，所以要看 {@code src}。
     */
    private String kindTag(String kind, String src) {
        if ("TRIAL".equals(src)) {
            return switch (kind == null ? "" : kind) {
                case "MAIN" -> "§6[章] §r";
                case "WEEKLY" -> "§e[周课] §r";
                default -> "§7[日课] §r";
            };
        }
        return switch (kind == null ? "" : kind) {
            case "MAIN" -> "§6[天谕] §r";
            case "EARTH" -> "§a[地谕] §r";
            case "DAILY" -> "§7[日课] §r";
            case "WEEKLY" -> "§c[禁谕] §r";
            case "AI" -> "§d[降谕] §r";
            default -> "§7[谕] §r";
        };
    }''', 'kindTag 带 src')
rep('kindTag(q.getString("kind")) + q.getString("name")',
    'kindTag(q.getString("kind"), q.getString("src")) + q.getString("name")', 'kindTag 调用')

# 7. 试炼页渲染（按章分组）
rep('    // ---------------------------------------------------------------- 天机阁',
    '''    // ---------------------------------------------------------------- 试炼（旧 34 条）

    /**
     * 旧试炼页：1.x 的 5 章章节制，按章分组显示。
     *
     * <p>与新谕令页的区别只在「叙事口径」：这里讲的是旧版「杀多少只妖兽、打到第几章」，
     * 谕令页讲的是知识库的四档（天谕/地谕/日课/禁谕）。两套进度靠 id 前缀隔离，
     * 面板上也各占一页，互不干扰。
     */
    private void renderTrials(GuiGraphics g, int x0, int top, int x1, int bottom) {
        List<CompoundTag> rows = questRows("TRIAL");
        sectionHeader(g, x0, top, x1, "试 炼", rows.size() + " 条 · 5 章");
        int ry = top + 22;
        int viewRows = Math.max(1, (bottom - ry) / ROW_H);
        int drawn = 0;
        int lastChapter = -1;
        for (int i = 0; i < rows.size() && drawn < viewRows - 1; i++) {
            CompoundTag q = rows.get(i);
            int chapter = q.getInt("chapter");
            if (chapter != lastChapter && chapter > 0) {
                // 章标题：一行暗金小字 + 分隔饰
                g.drawString(font, "第 " + chapter + " 章", x0 + PAD, ry, T_COPPER_TXT, true);
                divider(g, x0 + PAD + 34, x1 - PAD, ry + 4);
                ry += 11;
                lastChapter = chapter;
                drawn++;
            }
            boolean ready = q.getBoolean("ready");
            rowBack(g, x0 + 2, x1 - 2, ry, hoverY >= ry && hoverY < ry + ROW_H);
            g.drawString(font, kindTag(q.getString("kind"), q.getString("src")) + q.getString("name")
                            + "  " + q.getInt("p") + "/" + q.getInt("n"),
                    x0 + PAD, ry + 1, ready ? T_OK : T_BODY, true);
            drawWrapped(g, q.getString("desc"), x0 + PAD, ry + 10,
                    x1 - x0 - PAD * 2 - (ready ? 40 : 0), 1, T_DIM);
            if (ready) {
                goldButton(g, x1 - PAD - 34, ry + 2, x1 - PAD, ry + 14, "领取", "claim",
                        q.getString("id"));
            }
            ry += ROW_H;
            drawn++;
        }
        if (rows.isEmpty()) {
            g.drawString(font, "旧试炼已全部了结。新谕令在「谕令」页。", x0 + PAD, ry + 2, T_DIM, true);
        }
    }

    // ---------------------------------------------------------------- 天机阁''', '试炼页渲染')

open(P, 'w', encoding='utf-8', newline='\n').write(s)
print(f'面板共 {n} 处改动')
