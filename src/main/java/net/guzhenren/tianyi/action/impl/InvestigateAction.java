package net.guzhenren.tianyi.action.impl;

import net.guzhenren.tianyi.action.ActResult;
import net.guzhenren.tianyi.action.InterventionAction;
import net.guzhenren.tianyi.action.WorldView;
import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.fact.WorldFact;
import net.guzhenren.tianyi.world.LiveWorld;
import net.minecraft.core.BlockPos;

/**
 * 派一名居民去现场看一眼：<b>动身 + 在它自己的生活日志里留一条</b>。
 *
 * <h2>为什么只做这两件</h2>
 * 「调查」的价值在于把「听说」变成「看到」——而这恰恰只能由 NPC 自己走过去完成。
 * 这里不直接给 NPC 灌可信度：那样一来「谁亲眼见过」就无从追溯，
 * 天意也失去了「派谁去、走多久、有没有走到」这条真实链路。
 * 日志走本体自己的 {@code remember}，不另建一套生活记录。
 *
 * <h2>失败要分得清</h2>
 * 渠道不通 → {@code BLOCKED}（玩家看得到这条证据：他们切断了 NPC 的社会渠道）；
 * 居民档案里查不到执行者 → {@code FAILED}（桥接侧真的没这个人）；
 * 执行者已亡故、或没有可调查的地点 → {@code DENIED}（条件不成立，不是失败）。
 */
public final class InvestigateAction implements InterventionAction {
    /** 唯一 id，布局与日志都用它。 */
    public static final String ID = "npc.investigate";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String channel() {
        return "npc";
    }

    @Override
    public double cost() {
        return 0.05;
    }

    /** 能问的条件：至少有个活着的执行者，且 NPC 渠道通着。 */
    @Override
    public boolean applicable(WorldView view) {
        if (!(view instanceof LiveWorld world) || !world.channelOpen(channel())) {
            return false;
        }
        String executor = view.executorId();
        for (var resident : GuzhenrenBridge.allResidents(world.level())) {
            if (resident.npcId().toString().equals(executor)) {
                return !resident.deceased();
            }
        }
        return false;
    }

    @Override
    public ActResult execute(WorldView view) {
        long now = view.now();
        String executor = view.executorId();
        try {
            if (!(view instanceof LiveWorld world)) {
                return ActResult.failed(ID, cost(), "只支持服务端世界视图", now, executor);
            }
            if (!world.channelOpen(channel())) {
                return ActResult.blocked(ID, "npc 渠道不通", now, executor);
            }
            if (view.pos() == WorldFact.NO_POS) {
                return ActResult.denied(ID, "没有可调查的地点", now, executor);
            }
            BlockPos spot = BlockPos.of(view.pos());
            // 居民对象只能从桥接要：本类不 import 本体类型，也不自己造居民
            for (var resident : GuzhenrenBridge.allResidents(world.level())) {
                if (!resident.npcId().toString().equals(executor)) {
                    continue;
                }
                if (resident.deceased()) {
                    return ActResult.denied(ID, "执行者已亡故", now, executor);
                }
                double x = spot.getX() + 0.5;
                double y = spot.getY();
                double z = spot.getZ() + 0.5;
                boolean moved = GuzhenrenBridge.travelTo(resident, x, y, z);
                String line = "investigate " + world.dim() + " @" + spot.getX() + "," + spot.getZ();
                GuzhenrenBridge.remember(resident, line, now);
                if (!moved) {
                    return ActResult.failed(ID, cost(), "travelTo 被拒", now, executor);
                }
                return ActResult.ok(ID, cost(), line, now, executor);
            }
            return ActResult.failed(ID, cost(), "执行者不在居民档案", now, executor);
        } catch (RuntimeException e) {
            // 契约要求永不抛：任何意外都折成一次失败，让布局自己进冷却
            return ActResult.failed(ID, cost(), "异常：" + e, now, executor);
        }
    }
}
