"""从 1.0.13 反编译产物里取回「天命·词条」这一层，接到 2.6.0 上。

## 用户口径（本轮已确认）

- 恢复**只有词条（147 条）**，不含体质（20 条）、不含开窍注入（3 个 mixin）；
- 付款用**天机**（现在唯一的货币）；
- 旧 34 条试炼与新 38 条谕令**并存，分两页**。

## 取回策略：保留机制、砍掉表现

`TianfuEvents` 是词条乘区的运行时（真元回复、暴击、CD、修炼倍率都靠它），
不接它的话，买到的"真元回复 +15%"就是死数据。所以：
- **保留**：Pool（数据）/ Data（存档）/ Talents（效果）/ Attributes / Daohen /
  Events / Combat / Slay / ZhuCombat / Effects + init / Mod（注册入口）
- **砍掉**：Opening（开窍注入）、Vfx / VfxEntity（905 + 277 行特效与实体注册）、
  Roll（摇命）、client 面板与粒子、net payload
- **打桩**：`TianfuVfx` 只保留被调用的两个方法，空实现（无特效，但机制照旧）
"""
import os
import shutil

ROOT = r'D:\daima\mcmod\天意'
DEC = os.path.join(ROOT, '_pack', '_decomp_1013', 'out', 'net', 'guzhenren', 'tianyi', 'open')
DST = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')

KEEP = [
    'TianfuMod.java',
    'TianfuPool.java',
    'TianfuData.java',
    'TianfuTalents.java',
    'TianfuAttributes.java',
    'TianfuDaohen.java',
    'TianfuEvents.java',
    'TianfuCombat.java',
    'TianfuSlay.java',
    'TianfuZhuCombat.java',
    'TianfuEffects.java',
    'TianfuNative.java',
    'TianfuLiupai.java',
    'init/TianfuZhuEffects.java',
]

os.makedirs(DST, exist_ok=True)
os.makedirs(os.path.join(DST, 'init'), exist_ok=True)

copied, skipped = [], []
for rel in KEEP:
    src = os.path.join(DEC, rel)
    if not os.path.isfile(src):
        skipped.append(rel)
        continue
    shutil.copy2(src, os.path.join(DST, rel))
    copied.append(rel)

STUB = '''package net.guzhenren.tianyi.open;

import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 特效层的**空实现**（2.7.0）。
 *
 * <p>1.x 的 {@code TianfuVfx} 有 905 行 + 一个实体 {@code TianfuVfxEntity}（277 行 + 注册），
 * 负责购买/触发时的粒子表现。用户口径是「只恢复词条」，特效不属于词条本身，
 * 所以这里只保留被机制代码调用的两个方法签名，内部什么都不做。
 *
 * <p>好处：Combat / Slay / Apply 的逻辑一行不改即可编译运行；
 * 坏处：买词条不再有光效，只在聊天栏给一行提示（由天机阁自己发）。
 */
public final class TianfuVfx {
    private TianfuVfx() {
    }

    public static boolean has(int quality) {
        return false;
    }

    public static void play(ServerLevel level, ServerPlayer player, int quality, int tint) {
        // 空实现：特效层本轮不恢复
    }

    public static void burst(ServerLevel level, ServerPlayer player, int quality) {
        // 空实现
    }

    public static void buyBurst(ServerLevel level, ServerPlayer player, String id) {
        // 空实现
    }

    public static void buyBurstFor(ServerLevel level, ServerPlayer player, String id, String icon) {
        // 空实现
    }

    public static void playAura(ServerLevel level, ServerPlayer player, Set<String> owned, int ticks) {
        // 空实现
    }
}
'''
with open(os.path.join(DST, 'TianfuVfx.java'), 'w', encoding='utf-8', newline='\n') as f:
    f.write(STUB)

print(f'取回 {len(copied)} 个文件:')
for c in copied:
    print('   +', c)
if skipped:
    print('反编译产物里没有（可能已改名）:', skipped)
print('+ TianfuVfx.java（空实现）')
