"""Create minimal patched source copies and a unified diff, never change the input tree."""
from pathlib import Path
import difflib

HERE = Path(__file__).resolve().parent
SOURCE = Path(r'D:\daima\mcmod\蛊真人附属\src\main\java')
PATCHED = HERE / 'patched-src'

changes = {
    'net/guzhenren/tianfu/TianfuEffects.java': [
        ('if (guCd > 0.0 && v.cd > 0.0)',
         'if (guCd != 0.0 && v.cd > 0.0)'),
        ('                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(\n'
         '                        GuzhenrenModMobEffects.CHENMO, target,\n'
         '                        chenmo.getAmplifier(), chenmo.isAmbient(), chenmo.isVisible()));',
         '                // Same-amplifier addEffect only extends duration; remove before shortening.\n'
         '                player.removeEffect(GuzhenrenModMobEffects.CHENMO);\n'
         '                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(\n'
         '                        GuzhenrenModMobEffects.CHENMO, target,\n'
         '                        chenmo.getAmplifier(), chenmo.isAmbient(), chenmo.isVisible(), chenmo.showIcon()));'),
        ('        if (touched) {\n            v.markSyncDirty();\n        }\n    }',
         '        maintainV2(player);\n\n        if (touched) {\n            v.markSyncDirty();\n        }\n    }'),
    ],
    'net/guzhenren/tianfu/TianfuSlay.java': [
        ('        boolean tuntian = slay || TianfuApply.effective(sp).contains("shen_tuntian_shidi");\n'
         '        if (!slay && !tuntian) {',
         '        var ids = TianfuApply.effective(sp);\n'
         '        boolean tuntian = slay || ids.contains("shen_tuntian_shidi");\n'
         '        boolean shiyuan = ids.contains("shiyuan_zhixing");\n'
         '        if (!slay && !tuntian && !shiyuan) {'),
        ('if (TianfuApply.effective(sp).contains("shiyuan_zhixing") && victim.getType().is(TEXIAO))',
         'if (shiyuan && victim.getType().is(TEXIAO))'),
    ],
    'net/guzhenren/tianfu/v2/NeoForge1211Adapter.java': [
        ('    public void maintain(ServerPlayer player, TianfuSelection.Snapshot state) {\n'
         '        PlayerVariables v = vars(player);',
         '    public void maintain(ServerPlayer player, TianfuSelection.Snapshot state) {\n'
         '        PlayerVariables v = vars(player);\n'
         '        double oldLuck = v.qiyun;\n'
         '        double oldLuckCap = v.qiyun_shangxian;\n'
         '        double oldAptitude = v.kongqiao;\n'
         '        double oldLifespan = v.shouyuan;'),
        ('            if (cap >= 0.0 && v.shouyuan > cap) {\n'
         '                v.shouyuan = cap;\n'
         '            }\n'
         '        }\n'
         '    }\n\n'
         '    /** {@inheritDoc} 需先反编译',
         '            if (cap >= 0.0) {\n'
         '                v.shouyuan = Math.min(v.shouyuan, cap);\n'
         '                if (v.shouyuan < cap) {\n'
         '                    TianfuData.putDouble(player, KEY_LIFESPAN_CAP, v.shouyuan);\n'
         '                }\n'
         '            }\n'
         '        }\n'
         '        if (v.qiyun != oldLuck || v.qiyun_shangxian != oldLuckCap\n'
         '                || v.kongqiao != oldAptitude || v.shouyuan != oldLifespan) {\n'
         '            v.markSyncDirty();\n'
         '        }\n'
         '    }\n\n'
         '    /** {@inheritDoc} 需先反编译'),
    ],
    'net/guzhenren/tianfu/TianfuCombat.java': [
        ('    /** 玩家下线：清掉恶意联动的内存状态，避免长期残留。 */',
         '    /** Ordinary respawns preserve the cooldown; a wiped new life starts fresh. */\n'
         '    public static void copyCooldownOnClone(ServerPlayer player, Entity original) {\n'
         '        if (!TianfuData.isConfirmed(player)) {\n'
         '            return;\n'
         '        }\n'
         '        var oldData = original.getPersistentData();\n'
         '        if (oldData.contains(TAG_UNDYING_READY, net.minecraft.nbt.Tag.TAG_LONG)) {\n'
         '            player.getPersistentData().putLong(TAG_UNDYING_READY, oldData.getLong(TAG_UNDYING_READY));\n'
         '        }\n'
         '    }\n\n'
         '    /** 玩家下线：清掉恶意联动的内存状态，避免长期残留。 */'),
    ],
    'net/guzhenren/tianfu/TianfuEvents.java': [
        ('            TianfuOpening.onClone(player, event.getOriginal());',
         '            TianfuOpening.onClone(player, event.getOriginal());\n'
         '            TianfuCombat.copyCooldownOnClone(player, event.getOriginal());\n'
         '            TianfuCombat.forget(player.getUUID());'),
        ('            TianfuVfxEntity.forget(sp);',
         '            TianfuVfxEntity.forget(sp);\n'
         '            TianfuCombat.forget(sp.getUUID());'),
    ],
}

diffs = []
lifecycle_diffs = []
for rel, replacements in changes.items():
    original = (SOURCE / rel).read_text(encoding='utf-8')
    revised = original
    for old, new in replacements:
        assert revised.count(old) == 1, f'Expected one occurrence: {rel}: {old[:70]}'
        revised = revised.replace(old, new)
    dest = PATCHED / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(revised, encoding='utf-8', newline='\n')
    delta = list(difflib.unified_diff(original.splitlines(True), revised.splitlines(True),
                                    'a/src/main/java/' + rel, 'b/src/main/java/' + rel))
    diffs.extend(delta)
    if rel.endswith(('TianfuCombat.java', 'TianfuEvents.java')):
        lifecycle_diffs.extend(delta)
(HERE / 'runtime-fixes.patch').write_text(''.join(diffs), encoding='utf-8', newline='\n')
(HERE / 'lifecycle-fixes.patch').write_text(''.join(lifecycle_diffs), encoding='utf-8', newline='\n')
print(f'Created {len(changes)} patched source copies and runtime-fixes.patch')
