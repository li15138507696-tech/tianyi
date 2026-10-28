"""修正取回文件里的两处问题（反编译残留 / 注册入口重写）。"""
import io
import os

ROOT = r'D:\daima\mcmod\天意'
OPEN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')

# ---- 1. TianfuTalents：反编译把 ArrayList 写成裸类型，toArray(new Field[0]) 被当成 Object[]
p = os.path.join(OPEN, 'TianfuTalents.java')
s = open(p, encoding='utf-8').read()
s = s.replace('         ArrayList var1 = new ArrayList();',
              '         ArrayList<Field> var1 = new ArrayList<>();')
s = s.replace('         daohenCache = var0 = var1.toArray(new Field[0]);',
              '         var0 = var1.toArray(new Field[0]);\n         daohenCache = var0;')
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('TianfuTalents 修正完成')

# ---- 2. TianfuMod：只注册机制必需的两样
MOD = '''package net.guzhenren.tianyi.open;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 天命「词条」层的注册入口（2.7.0 从 1.0.13 取回）。
 *
 * <h2>与 1.x 的差别</h2>
 * 1.x 的 {@code TianfuMod.register} 注册了五样：粒子类型、18 个 mob effect、8 个网络包、
 * 事件监听、命令。2.7.0 只保留<b>机制必需</b>的两样：
 * <ul>
 *   <li>{@link TianfuZhuEffects#REGISTRY} —— 词条/战斗用到的 18 个 {@code tianfu_zhu_*} 效果；</li>
 *   <li>{@link TianfuEvents} —— 乘区运行时（真元回复、暴击、CD、修炼倍率都靠它）。</li>
 * </ul>
 * 粒子（表现）、网络包与命令都不在这里：面板走 {@code tianyi:state} / {@code tianyi:action}，
 * 命令走 {@code /tianyi}。这样就不会再出现 1.0.9 那次「payload 撞名」。
 *
 * <p>明确<b>没有</b>恢复的：{@code TianfuOpening}（开窍注入）与它的 3 个 mixin、
 * {@code TianfuVfx} / {@code TianfuVfxEntity}（特效与实体，这里打了空实现桩）、
 * {@code TianfuRoll}（摇命）、以及 20 条体质（用户口径：只要词条）。
 */
public final class TianfuMod {
    public static final String MODID = "tianyi";
    public static final Logger LOGGER = LoggerFactory.getLogger("tianyi.open");

    private TianfuMod() {
    }

    public static void register(IEventBus modBus) {
        TianfuZhuEffects.REGISTRY.register(modBus);
        NeoForge.EVENT_BUS.register(TianfuEvents.class);
        LOGGER.info("[天意] 天命词条层已接入（{} 条词条；不含体质与开窍注入）",
                TianfuPool.entries().size());
    }
}
'''
open(os.path.join(OPEN, 'TianfuMod.java'), 'w', encoding='utf-8', newline='\n').write(MOD)
print('TianfuMod 重写完成')
