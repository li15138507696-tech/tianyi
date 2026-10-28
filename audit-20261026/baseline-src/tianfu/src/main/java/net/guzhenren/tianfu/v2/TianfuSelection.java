// src/main/java/net/guzhenren/tianfu/v2/TianfuSelection.java
package net.guzhenren.tianfu.v2;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 无 Minecraft 依赖的数据边界；集合由构造方提供不可变副本，禁止原地修改。 */
public final class TianfuSelection {
    public static final int SCHEMA = 2;
    public static final int NEGATIVE_CAP = 24;
    private TianfuSelection() { }

    /** @param regionId 空字符串=老档无地域；@param backgroundId 空字符串=无类型。 */
    public record Origin(String regionId, String backgroundId) { }
    /**
     * 成交快照；legacy=true 表示从冻结的旧成本表补入，只建账不改余额。
     * @param paidCost 有符号成交成本；退款只认此值，不认当前目录价
     * @param grant 补点占用量=max(0,-paidCost)，历史已超限保持原值不裁剪
     */
    public record Receipt(String id, int paidCost, int grant, boolean legacy) { }
    /**
     * @param schema 0=缺失 V2，2=已迁移；缺失不意味新玩家
     * @param revision 服务端事务序号，任何成功变更加一；重复/过时请求不落库
     * @param originReceipt 出身类型原成交价，独立于天赋成交账；无出身时为null，不得推算目录价
     * @param originEffectKeys 本世已结算效果的幂等键，转世随 tianfu 根一起清除
     */
    public record Snapshot(int schema, long revision, int points, int difficulty, boolean confirmed,
                           Origin origin, Receipt originReceipt, List<String> purchased, Map<String, Receipt> receipts,
                           int negativeGrant, Set<String> originEffectKeys) { }
    /** 业务拒绝不抛异常；不认识的 ID、锁定、预算、补点限额均返回失败。 */
    public enum Rejection { NONE, UNKNOWN_ID, LOCKED, NOT_OPENED, NO_DIFFICULTY, INSUFFICIENT_POINTS,
        NEGATIVE_CAP, CONFLICT, STALE_REVISION, UNVERIFIED_EFFECT, UNSAFE_LIFESPAN, LEGACY_PRICE_UNKNOWN }
    /** ok=false 时 points/revision/购买记录/本体字段必须完全不变。 */
    public record Result(boolean ok, Rejection reason, Snapshot snapshot) { }
    /** 事务拟稿；只交给 Store 提交，预览阶段不触碰 PlayerVariables。 */
    public record Change(long expectedRevision, Snapshot next) { }
}
