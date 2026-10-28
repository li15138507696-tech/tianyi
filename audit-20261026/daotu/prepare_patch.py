from pathlib import Path
import difflib

ORIGINAL = Path(r'D:\daima\mcmod\道途附属')
OUT = Path(__file__).resolve().parent
ROOT = 'src/main/java/net/guzhenren/daotu/'
CHANGES = {
    'DaotuOpening.java': [
        ('/** 死亡克隆：PersistentData 默认会复制，这里只需重挂属性修正。 */',
         '/** 克隆时只深拷贝道途自己的数据根，再重挂属性修正。 */'),
        ('public static void onClone(ServerPlayer player, ServerPlayer original) {\n        try {\n            DaotuAttributes.sync(player);',
         'public static void onClone(ServerPlayer player, ServerPlayer original) {\n        try {\n            // NeoForge 只自动复制 PlayerPersisted，普通根标签须由模组自己迁移。\n            var source = original.getPersistentData();\n            if (source.contains(DaotuData.ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) {\n                player.getPersistentData().put(DaotuData.ROOT,\n                        source.getCompound(DaotuData.ROOT).copy());\n            }\n            DaotuAttributes.sync(player);'),
    ],
    'DaotuLiLian.java': [
        ('double gain = combat + travel + cultivate;',
         'if (!DaotuOpening.hasOpenedQiao(player)) {\n            return;\n        }\n        double gain = combat + travel + cultivate;'),
    ],
    'DaotuDraw.java': [
        ('return zhuanshu < 5.0 ? PER_ZHUAN_CAP : Integer.MAX_VALUE;',
         'return zhuanshu <= 5.0 ? PER_ZHUAN_CAP : Integer.MAX_VALUE;'),
        ('public static int tryDraw(ServerPlayer player) {',
         'public static int tryDraw(ServerPlayer player) {\n        if (!DaotuOpening.hasOpenedQiao(player)) {\n            return 0;\n        }'),
        ('public static boolean choose(ServerPlayer player, int index) {',
         'public static boolean choose(ServerPlayer player, int index) {\n        if (!DaotuOpening.hasOpenedQiao(player)) {\n            return false;\n        }'),
        ('public static String labelOf(String key) {\n        return switch (key) {',
         'public static String labelOf(String key) {\n        if (key.startsWith(DaotuDrawTable.TALENT_PREFIX)) {\n            DaotuTalent.Entry entry = DaotuTalent.byId(\n                    key.substring(DaotuDrawTable.TALENT_PREFIX.length()));\n            if (entry != null) {\n                String summary = entry.summary();\n                return entry.name() + (summary.isEmpty() ? "" : " · " + summary);\n            }\n        }\n        return switch (key) {'),
    ],
    'DaotuMigrate.java': [
        ('// 折算的抽选次数直接灌满池，让 drawAll 一次抽完\n        DaotuRun.setDraws(player, draws);\n        DaotuDraw.replayForMigration(player, draws);',
         '// 补发流程自己逐次递增 draws；不能在补发前预加一次。\n        DaotuDraw.replayForMigration(player, draws);'),
    ],
    'chronicle/NbtChronicleRepository.java': [
        ('new MeritArchive(-1.0, -1.0, Set.of(), 0L)',
         'new MeritArchive(-1.0, -1.0, Set.of(), -1L)'),
        ('new ReputationJournal(-1.0, -1.0, Set.of(), 0L)',
         'new ReputationJournal(-1.0, -1.0, Set.of(), -1L)'),
        ('private static Set<String> readPairs(CompoundTag parent) {\n        Set<String> out = new LinkedHashSet<>();\n        ListTag list = parent.getList("pairs", Tag.TAG_STRING);',
         'private static Set<String> readStrings(CompoundTag parent, String key) {\n        Set<String> out = new LinkedHashSet<>();\n        ListTag list = parent.getList(key, Tag.TAG_STRING);'),
        ('Set.copyOf(readPairs(tag))', 'Set.copyOf(readStrings(tag, "unlocked"))'),
        ('Set.copyOf(readPairs(factionTag))', 'Set.copyOf(readStrings(factionTag, "pairs"))'),
    ],
    'chronicle/FactionJournalModule.java': [
        ('state.revision(), snapshot.runId(),', 'state.revision(), state.runId(),'),
    ],
    'chronicle/MeritArchiveModule.java': [
        ('boolean noBaseline = previous.lastObserved() < 0.0;',
         'boolean noBaseline = previous.lastObservedTick() < 0L;'),
        ('Math.max(current, previous.peakObserved()), previous.unlockedEntries(),',
         'noBaseline ? current : Math.max(current, previous.peakObserved()), previous.unlockedEntries(),'),
        ('if (peak >= milestone.threshold() && unlocked.add(milestone.id())) {',
         'if (previous.peakObserved() < milestone.threshold()\n                        && peak >= milestone.threshold() && unlocked.add(milestone.id())) {'),
        ('state.revision(), snapshot.runId(),', 'state.revision(), state.runId(),'),
    ],
    'chronicle/ReputationJournalModule.java': [
        ('boolean noBaseline = previous.lastObserved() < 0.0;',
         'boolean noBaseline = previous.lastObservedTick() < 0L;'),
        ('Math.max(current, previous.peakObserved()), previous.unlockedEntries(),',
         'noBaseline ? current : Math.max(current, previous.peakObserved()), previous.unlockedEntries(),'),
        ('if (peak >= milestone.threshold() && unlocked.add(milestone.id())) {',
         'if (previous.peakObserved() < milestone.threshold()\n                    && peak >= milestone.threshold() && unlocked.add(milestone.id())) {'),
        ('state.revision(), snapshot.runId(),', 'state.revision(), state.runId(),'),
    ],
    'chronicle/ChronicleCoordinator.java': [
        ('/** 首次建档的空状态：merit/reputation 的 lastObserved 用 -1 表示"尚无基线"。 */',
         '/** 首次建档的空状态：采样时间 -1 表示尚无基线，负声望仍是有效观测值。 */'),
        ('new MeritArchive(-1.0, -1.0, Set.of(), 0L)',
         'new MeritArchive(-1.0, -1.0, Set.of(), -1L)'),
        ('new ReputationJournal(-1.0, -1.0, Set.of(), 0L)',
         'new ReputationJournal(-1.0, -1.0, Set.of(), -1L)'),
        ('|| working.runId() != loaded.get().runId()',
         '|| snapshot.runId() != loaded.get().runId()'),
        ('expectedRevision + 1L,\n                working.runId(),',
         'expectedRevision + 1L,\n                snapshot.runId(),'),
        ('// 整批一次 revision：任何模块都不能自己递增版本。',
         '// 整批统一提交 revision 与 runId；模块合并期间保留旧 runId，\n        // 使每个模块都能识别同一次转世，不能由前一个模块提前消费。'),
    ],
}

patch = []
for rel, replacements in CHANGES.items():
    path = ROOT + rel
    old = (ORIGINAL / path).read_text(encoding='utf-8-sig')
    new = old
    for before, after in replacements:
        if before not in new:
            raise RuntimeError(f'Expected text missing in {path}: {before}')
        new = new.replace(before, after)
    dest = OUT / 'patched-src' / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(new, encoding='utf-8', newline='\n')
    patch.extend(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
                                    fromfile='a/' + path, tofile='b/' + path))
(OUT / 'daotu-fixes.patch').write_text(''.join(patch), encoding='utf-8', newline='\n')
print(f'Prepared {len(CHANGES)} source overlays and daotu-fixes.patch')
