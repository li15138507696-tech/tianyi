"""修完反编译残留（裸泛型 / 缺类），让「词条」层编译通过。

原样取回的 1.0.13 代码里，Vineflower 把若干泛型擦成了裸类型，导致：
- `for (String x : rawSet)` → Object 无法转 String（TianfuApply）
- `rawList.toArray(new Field[0])` → Object[] 无法转 Field[]（TianfuEffects）
- `rawMap.merge(k, 1, Integer::sum)` → 类型参数无效（TianfuCombat）
另外两处引用了未取回的类：TianfuOpening（开窍注入）、TianfuVfxEntity（特效实体）——
用户口径「不含开窍注入」，所以按方案 A 给它们打**空桩**：签名在、内部不做事，
这样机制代码一行不改即可运行，本体开窍流程完全不受影响。
"""
import os

ROOT = r'D:\daima\mcmod\天意'
OPEN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')


def patch(name, pairs):
    p = os.path.join(OPEN, name)
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            print(f'  ! {name}: 未找到片段 {a[:40]!r}')
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print(f'  patched {name}')


# 1. TianfuApply：裸 Set 的增强 for → 用 Object 接、再转 String
patch('TianfuApply.java', [
    ('for (String var6 : var3) {',
     'for (Object var6o : (java.util.Set<?>) var3) {\n                  String var6 = String.valueOf(var6o);'),
    ('for (String var13 : var3) {',
     'for (Object var13o : (java.util.Set<?>) var3) {\n                     String var13 = String.valueOf(var13o);'),
])

# 2. TianfuEffects：裸 ArrayList.toArray → Object[]；顺手把类型写全
patch('TianfuEffects.java', [
    ('      liupaiCache = var0 = var1.toArray(new Field[0]);',
     '      var0 = var1.toArray(new Field[0]);\n      liupaiCache = var0;'),
    ('ArrayList var1 = new ArrayList();',
     'ArrayList<Field> var1 = new ArrayList<>();'),
])

# 3. TianfuCombat：raw Map.merge 的方法引用不接受 Integer::sum
patch('TianfuCombat.java', [
    ('int var8 = var7.merge(var6, 1, Integer::sum);',
     'Object var8o = var7.get(var6);\n         int var8 = var8o instanceof Integer i0 ? i0 + 1 : 1;\n'
     '         var7.put(var6, var8);'),
])

# 4. 空桩：开窍注入（用户明确不要）
OPENING = '''package net.guzhenren.tianyi.open;

import net.minecraft.server.level.ServerPlayer;

/**
 * 开窍注入的**空桩**（2.7.0）。
 *
 * <p>1.x 的 {@code TianfuOpening} 会在本体开窍结算时往玩家身上写天赋数据。
 * 用户口径是「只要词条，不含开窍注入」，所以这里保留 {@code TianfuEvents} 需要的两个
 * 方法签名，内部什么都不做——<b>本体开窍流程一点都不碰</b>。
 */
public final class TianfuOpening {
    private TianfuOpening() {
    }

    public static void onLogin(ServerPlayer player) {
        // 空桩：不注入、不结算、不改本体数据
    }

    public static void onClone(ServerPlayer now, ServerPlayer old) {
        // 空桩：跨维度/重生时不做任何继承
    }
}
'''
open(os.path.join(OPEN, 'TianfuOpening.java'), 'w', encoding='utf-8', newline='\n').write(OPENING)
print('  + TianfuOpening.java（空桩）')

# 5. 空桩：特效实体
VFX_ENTITY = '''package net.guzhenren.tianyi.open;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 特效实体的**空桩**（2.7.0）。
 *
 * <p>1.x 有一个 {@code TianfuVfxEntity}（277 行 + 实体注册）负责购买光效的逐 tick 推进。
 * 本轮不恢复表现层，保留 {@code TianfuEffects} 调用的 {@code tick} 签名即可。
 */
public final class TianfuVfxEntity {
    private TianfuVfxEntity() {
    }

    public static void tick(ServerLevel level, ServerPlayer player, int quality, int age) {
        // 空桩：不生成实体、不推进特效
    }
}
'''
open(os.path.join(OPEN, 'TianfuVfxEntity.java'), 'w', encoding='utf-8', newline='\n').write(VFX_ENTITY)
print('  + TianfuVfxEntity.java（空桩）')
