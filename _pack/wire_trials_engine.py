"""P1 接线：让旧 34 条试炼与新 38 条谕令并存（引擎 / 网络 / 面板）。"""
import os

ROOT = r'D:\daima\mcmod\天意'
Q = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')


def edit(path, pairs, label):
    s = open(path, encoding='utf-8').read()
    ok = 0
    for a, b in pairs:
        if a in s:
            s = s.replace(a, b, 1)
            ok += 1
        else:
            print(f'  ! {label}: 未匹配 {a[:60]!r}')
    open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print(f'  {label}: {ok}/{len(pairs)} 处已改')


# ---------------------------------------------------------------- 1. TianyiQuest：补 weekly 工厂
edit(os.path.join(Q, 'quest', 'TianyiQuest.java'), [(
    '''    public static TianyiQuest earth(String id, String name, String desc,''',
    '''    /**
     * 周常（旧试炼表的口径）。与 {@link #ban} 同为 {@link Kind#WEEKLY}，
     * 区别只在面板上的标签：试炼页显示「周课」，谕令页显示「禁谕」。
     */
    public static TianyiQuest weekly(String id, String name, String desc,
                                     TianyiObjective objective, int points) {
        return new TianyiQuest(id, name, desc, Kind.WEEKLY, 0, 1, objective, points);
    }

    public static TianyiQuest earth(String id, String name, String desc,''')],
     'TianyiQuest +weekly')

# ---------------------------------------------------------------- 2. 引擎：两套表统一推进 / 分流领取
ENG = os.path.join(Q, 'quest', 'TianyiQuestEngine.java')
edit(ENG, [
    # byType → poolOf（推进时两套表都要看）
    ('        for (TianyiQuest q : TianyiQuests.byType(type)) {',
     '        for (TianyiQuest q : poolOf(type)) {'),
    ('        for (TianyiQuest q : TianyiQuests.byType(TianyiObjective.Type.MORAL)) {',
     '        for (TianyiQuest q : poolOf(TianyiObjective.Type.MORAL)) {'),
    ('        for (TianyiQuest q : TianyiQuests.byType(TianyiObjective.Type.PROFICIENCY)) {',
     '        for (TianyiQuest q : poolOf(TianyiObjective.Type.PROFICIENCY)) {'),
    # visible：两套表都列
    ('        for (TianyiQuest q : TianyiQuests.all()) {\n            if (q.minZhuan() > zhuan) {\n                continue;\n            }\n            if (TianyiQuestState.claimed(player, q.id())) {\n                continue;\n            }\n            out.add(q);',
     '        for (TianyiQuest q : pool()) {\n            if (q.minZhuan() > zhuan) {\n                continue;\n            }\n            if (TianyiQuestState.claimed(player, q.id())) {\n                continue;\n            }\n            out.add(q);'),
    # claim：tr_ 分流
    ('''        TianyiQuest q = TianyiQuests.byId(questId);''',
     '''        // 旧试炼（tr_ 前缀）走自己的表，与 38 条谕令互不干扰
        TianyiQuest q = questId != null && questId.startsWith(TianyiTrials.ID_PREFIX)
                ? TianyiTrials.byId(questId)
                : TianyiQuests.byId(questId);'''),
    # Row 加 src
    ('''    public record Row(String id, String name, String desc, String kind, int chapter,
                      int progress, int count, int points, boolean ready) {
    }''',
     '''    public record Row(String id, String name, String desc, String kind, String src, int chapter,
                      int progress, int count, int points, boolean ready) {
    }'''),
    # AI 行
    ('''            out.add(new Row(aq.id(), aq.title(), aq.desc(), "AI", 0,
                    p, aq.objective().count(), aq.points(), p >= aq.objective().count()));''',
     '''            out.add(new Row(aq.id(), aq.title(), aq.desc(), "AI", "AI", 0,
                    p, aq.objective().count(), aq.points(), p >= aq.objective().count()));'''),
    # 谕令行 + 新增试炼行
    ('''            out.add(new Row(q.id(), q.name(), q.desc(), q.kind().name(), q.chapter(),
                    p, q.objective().count(), q.points(), TianyiQuestState.readyToClaim(player, q)));
        }
        return out;
    }''',
     '''            out.add(new Row(q.id(), q.name(), q.desc(), q.kind().name(), "YULING", q.chapter(),
                    p, q.objective().count(), q.points(), TianyiQuestState.readyToClaim(player, q)));
        }
        // 旧 34 条试炼：独立 src，面板「试炼」页据此分组
        for (TianyiQuest q : TianyiTrials.all()) {
            if (q.minZhuan() > zhuan) {
                continue;
            }
            if (TianyiQuestState.claimed(player, q.id())) {
                continue;
            }
            int p = TianyiQuestState.progress(player, q.id());
            out.add(new Row(q.id(), q.name(), q.desc(), q.kind().name(), "TRIAL", q.chapter(),
                    p, q.objective().count(), q.points(), TianyiQuestState.readyToClaim(player, q)));
        }
        return out;
    }

    /** 两套表的合并视图（推进 / 可见性用）。 */
    private static java.util.List<TianyiQuest> pool() {
        java.util.List<TianyiQuest> out = new java.util.ArrayList<>(TianyiQuests.all());
        out.addAll(TianyiTrials.all());
        return out;
    }

    /** 按目标类型取两套表的合并列表。 */
    private static java.util.List<TianyiQuest> poolOf(TianyiObjective.Type type) {
        java.util.List<TianyiQuest> out = new java.util.ArrayList<>(TianyiQuests.byType(type));
        out.addAll(TianyiTrials.byType(type));
        return out;
    }'''),
], 'TianyiQuestEngine')

# ---------------------------------------------------------------- 3. 网络：补 src 字段
edit(os.path.join(Q, 'TianyiNet.java'), [(
    '            qt.putString("kind", r.kind());',
    '            qt.putString("kind", r.kind());\n            qt.putString("src", r.src());')],
    'TianyiNet +src')
