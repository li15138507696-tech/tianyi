"""收尾 5 处编译错误（全部是反编译残留或我这边的桩缺方法）。"""
import os

ROOT = r'D:\daima\mcmod\天意'
OPEN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')


def patch(name, pairs):
    p = os.path.join(OPEN, name)
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            print(f'  ! {name} 未找到: {a[:50]!r}')
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('  patched', name)


# 1. TianfuMod：TianfuZhuEffects 在 open.init 包下
patch('TianfuMod.java', [
    ('import net.neoforged.bus.api.IEventBus;',
     'import net.guzhenren.tianyi.open.init.TianfuZhuEffects;\nimport net.neoforged.bus.api.IEventBus;'),
])

# 2. 空桩补齐 forget(Player)
p = os.path.join(OPEN, 'TianfuVfxEntity.java')
s = open(p, encoding='utf-8').read()
if 'forget(' not in s:
    s = s.replace('    public static void tick(',
                  '''    /** 玩家死亡 / 下线 / 换维度时清特效状态（空桩什么都不清）。 */
    public static void forget(net.minecraft.world.entity.player.Player player) {
        // 空桩
    }

    public static void tick(''')
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('  patched TianfuVfxEntity.java（+forget）')

# 3. 开窍桩：onClone 第二个参数在 1.x 是 Player（death 前后的原实体）
patch('TianfuOpening.java', [
    ('public static void onClone(ServerPlayer now, ServerPlayer old) {',
     'public static void onClone(ServerPlayer now, net.minecraft.world.entity.player.Player old) {'),
])

# 4. ZhuCombat：裸 Optional 导致 map 的类型参数无效
patch('TianfuZhuCombat.java', [
    ('      Optional var1 = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("tianfu", var0.id()));\n'
     '      return var1.<Holder<MobEffect>>map(var0x -> (Holder<MobEffect>)var0x).orElse(null);',
     '      Optional<Holder<MobEffect>> var1 = BuiltInRegistries.MOB_EFFECT.getHolder(\n'
     '            ResourceLocation.fromNamespaceAndPath("tianfu", var0.id()));\n'
     '      return var1.orElse(null);'),
])
