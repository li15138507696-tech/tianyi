package net.guzhenren.tianyi.action.impl;

import java.util.UUID;

import net.guzhenren.tianyi.action.ActResult;
import net.guzhenren.tianyi.action.InterventionAction;
import net.guzhenren.tianyi.action.WorldView;
import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.fact.WorldFact;
import net.guzhenren.tianyi.world.LiveWorld;
import net.minecraft.core.BlockPos;

/**
 * 派一名居民追捕某个身份：<b>结仇 + 动身</b>。
 *
 * <h2>为什么结仇与追捕必须一起做</h2>
 * 只有"动身"没有"结仇"，NPC 走到半路就会按本体自己的日程散掉——本体不认识这次差事。
 * 只有"结仇"没有"动身"，仇家永远站在原地看着地图。
 * 所以一次成功的追捕是两件真实改动：本体 feud 表里多一个 UUID，这个居民的下一个目的地是被追的人。
 *
 * <h2>目标身份</h2>
 * 追的是<b>身份 UUID</b>，不是玩家账号：一个人换身份作案，追兵该追的是那一世的脸。
 * 目标解析不出来就是条件不成立（{@code DENIED}），不能拿"没有目标"当成一次成功的追捕。
 */
public final class PursueAction implements InterventionAction {
    /** 唯一 id。 */
    public static final String ID = "npc.pursue";

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
        return 0.12;
    }

    @Override
    public boolean applicable(WorldView view) {
        if (!(view instanceof LiveWorld world) || !world.channelOpen(channel())) {
            return false;
        }
        if (parseId(view.targetId()) == null) {
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
            UUID wanted = parseId(view.targetId());
            if (wanted == null) {
                return ActResult.denied(ID, "目标身份缺失或非法", now, executor);
            }
            boolean positioned = view.pos() != WorldFact.NO_POS;
            BlockPos spot = positioned ? BlockPos.of(view.pos()) : null;
            for (var resident : GuzhenrenBridge.allResidents(world.level())) {
                if (!resident.npcId().toString().equals(executor)) {
                    continue;
                }
                if (resident.deceased()) {
                    return ActResult.denied(ID, "执行者已亡故", now, executor);
                }
                if (!GuzhenrenBridge.addFeud(resident, wanted)) {
                    return ActResult.failed(ID, cost(), "addFeud 被拒", now, executor);
                }
                String line = "pursue " + wanted + (positioned
                        ? " @" + spot.getX() + "," + spot.getZ() : "");
                if (positioned) {
                    GuzhenrenBridge.travelTo(resident, spot.getX() + 0.5,
                            spot.getY(), spot.getZ() + 0.5);
                }
                GuzhenrenBridge.remember(resident, line, now);
                return ActResult.ok(ID, cost(), line, now, executor);
            }
            return ActResult.failed(ID, cost(), "执行者不在居民档案", now, executor);
        } catch (RuntimeException e) {
            return ActResult.failed(ID, cost(), "异常：" + e, now, executor);
        }
    }

    /** 身份必须是完整 UUID：名字会被改，哈希会撞。 */
    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
