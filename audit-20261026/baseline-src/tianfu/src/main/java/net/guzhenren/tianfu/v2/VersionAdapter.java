// src/main/java/net/guzhenren/tianfu/v2/VersionAdapter.java
package net.guzhenren.tianfu.v2;

import net.minecraft.server.level.ServerPlayer;

/** 隔离本体版本字段/收入来源/资质约束；本轮只支持 MC 1.21.1，不声称跨 MC 二进制兼容。 */
public interface VersionAdapter {
    /**
     * 预检所有将使用的字段及真实挂钩，不能以字段存在代替语义验证。
     * @param player 服务端非 null 玩家
     * @param state 待确认快照
     * @return 可安全完整兑现才为 NONE；未确认 API 返回 UNVERIFIED_EFFECT
     */
    TianfuSelection.Rejection preflight(ServerPlayer player, TianfuSelection.Snapshot state);
    /**
     * 确认时一次结算出身与硬代价，先原生体质/机缘再出身再硬约束。
     * @param player 服务端非 null 玩家
     * @param state 已预检的快照；原始生效键用于重入防护
     * @throws IllegalStateException 外部字段写入失败，保留可恢复结算计划，不能落成功锁
     */
    void settle(ServerPlayer player, TianfuSelection.Snapshot state);
    /**
     * 已确认后的约束维护：气运归零、资质锁定、寿元增益禁用以及新增周期效果。
     * @param player 非 null 服务端玩家；@param state 当前快照
     * @throws IllegalStateException 已验证字段发生运行时失配
     */
    void maintain(ServerPlayer player, TianfuSelection.Snapshot state);
    /**
     * 处理真实元石收入事件而非背包扫描；转移、丢拾、退款不得触发。
     * @param player 收入所属玩家
     * @param baseAmount 已确认来源的非负原始收入
     * @param sourceKey 去重来源键，同一收入只能结算一次
     * @return 应发的额外元石量，小数记余数，不创造负数；未验证来源抛异常
     */
    long extraIncome(ServerPlayer player, long baseAmount, String sourceKey);
}
