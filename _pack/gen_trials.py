"""P1：从 1.0.13 反编译产物生成 `TianyiTrials`（旧 34 条试炼并存版）。

## 为什么用生成而不是手抄

34 条试炼的目标参数（阵营/转数/数量/章节/点数）手抄必错，而且错一个就悄悄改了数值。
这里直接读反编译产物的 `static { ... }` 块，只做一件事：给每条 id 加 `tr_` 前缀，
避免与新谕令的 id 撞车（新表里有 `t1_open`、`d_hunt` 这类短 id，容易混）。

## 生成结果

- 主线 25（5 章 × 5）+ 日课 6 + 周常 3 = 34 条
- 类名 `TianyiTrials`，包 `net.guzhenren.tianyi.quest`
- 工厂与旧版一致：`m(...)` = 主线、`d(...)` = 日课、`w(...)` = 周常
- 旧版 `TianyiQuest.weekly(...)` 在当前代码里没有，所以同步给 `TianyiQuest` 补一个
"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = open(os.path.join(ROOT, '_pack', '_decomp_1013', 'out', 'net', 'guzhenren', 'tianyi',
                        'quest', 'TianyiQuests.java'), encoding='utf-8').read()

# 取 static 块
m = re.search(r'static \{(.*?)\n   \}\n\}', SRC, re.S)
if not m:
    raise SystemExit('没找到 static 初始化块，反编译产物结构变了')
block = m.group(1).strip('\n')

# 给每条 id 加 tr_ 前缀：m(1, "ch1_hunt", ...) / d("d_x", ...) / w("w_x", ...)
def prefix(mo):
    return mo.group(1) + '"tr_' + mo.group(2) + '"'

lines = []
count = {'m': 0, 'd': 0, 'w': 0}
for line in block.split('\n'):
    s = line.strip()
    if not s:
        continue
    hit = re.match(r'(m\(\d+,\s*|d\(|w\()"([a-z0-9_]+)"', s)
    if hit:
        f = hit.group(1)[0]
        count[f] += 1
        s = re.sub(r'^(m\(\d+,\s*|d\(|w\()"([a-z0-9_]+)"',
                   lambda mo: mo.group(1) + '"tr_' + mo.group(2) + '"', s)
    else:
        print('  ! 非预期行（未加前缀）:', s[:70])
    lines.append('      ' + s)

OUT = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'quest',
                   'TianyiTrials.java')

HEADER = '''package net.guzhenren.tianyi.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧 34 条试炼（1.0.x 表，2.8.0 回归，与 38 条谕令并存）。
 *
 * <h2>为什么单独一个类</h2>
 * 新谕令（{@link TianyiQuests}）是知识库四档口径；这里是 1.x 的 5 章章节制，玩家已经熟悉，
 * 你要求「新旧并存分两页」。两套表各自独立：
 * <ul>
 *   <li>id 全部以 <b>{@code tr_}</b> 开头，与新表零交集（审计会校验）；</li>
 *   <li>进度仍用 {@link TianyiQuestState} 的 {@code p.<id>}/{@code c.<id>}，靠前缀天然隔离；</li>
 *   <li>面板「试炼」页按章节分组显示，「谕令」页显示 KB 四档。</li>
 * </ul>
 *
 * <p>本文件由 {@code _pack/gen_trials.py} 从 {@code tianyi-1.0.13} 的反编译产物生成，
 * <b>不要手改</b>：数值必须与 1.x 完全一致，手改一次就再也对不回去了。
 */
public final class TianyiTrials {

    /** 旧表只有 5 章（本体五转）。 */
    public static final int MAX_CHAPTER = 5;
    /** id 前缀：与新谕令隔离，也是 claim 的分流依据。 */
    public static final String ID_PREFIX = "tr_";

    private static final List<TianyiQuest> ALL = new ArrayList<>();
    private static final Map<String, TianyiQuest> BY_ID = new LinkedHashMap<>();
    private static final Map<TianyiObjective.Type, List<TianyiQuest>> BY_TYPE = new LinkedHashMap<>();

    static {
'''

FOOTER = '''   }

    private TianyiTrials() {
    }

    private static void m(int chapter, String id, String name, String desc,
                          TianyiObjective objective, int points) {
        add(TianyiQuest.main(chapter, id, name, desc, objective, points));
    }

    private static void d(String id, String name, String desc,
                          TianyiObjective objective, int points) {
        add(TianyiQuest.daily(id, name, desc, objective, points));
    }

    private static void w(String id, String name, String desc,
                          TianyiObjective objective, int points) {
        add(TianyiQuest.weekly(id, name, desc, objective, points));
    }

    private static void add(TianyiQuest q) {
        if (BY_ID.containsKey(q.id())) {
            throw new IllegalStateException("重复的试炼 id: " + q.id());
        }
        ALL.add(q);
        BY_ID.put(q.id(), q);
        BY_TYPE.computeIfAbsent(q.objective().type(), k -> new ArrayList<>()).add(q);
    }

    public static List<TianyiQuest> all() {
        return List.copyOf(ALL);
    }

    public static TianyiQuest byId(String id) {
        return BY_ID.get(id);
    }

    public static List<TianyiQuest> byType(TianyiObjective.Type type) {
        return BY_TYPE.getOrDefault(type, List.of());
    }

    public static int size() {
        return ALL.size();
    }
}
'''

with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write(HEADER + '\n'.join(lines) + '\n' + FOOTER)

print(f'生成 {OUT}')
print(f'  主线 {count["m"]} / 日课 {count["d"]} / 周常 {count["w"]} = 合计 {sum(count.values())}')
