// src/main/java/net/guzhenren/tianfu/v2/TianfuMindTable.java
package net.guzhenren.tianfu.v2;

import java.util.Map;

/** 新悟性层级元数据；效果与成本见TianfuTalents，旧条目不纳入新互斥组。 */
public final class TianfuMindTable {
    private TianfuMindTable() { }
    /** 同family最多一个新层级；重选按旧成交价退款再扣新价，不能删除历史不同ID。 */
    public record Level(String family, int tier) { }
    public static final Map<String, Level> LEVELS = Map.ofEntries(
            Map.entry("v2_mind_xiulian_1", new Level("xiulian", 1)),
            Map.entry("v2_mind_xiulian_2", new Level("xiulian", 2)),
            Map.entry("v2_mind_xiulian_3", new Level("xiulian", 3)),
            Map.entry("v2_mind_capacity_1", new Level("capacity", 1)),
            Map.entry("v2_mind_capacity_2", new Level("capacity", 2)),
            Map.entry("v2_mind_capacity_3", new Level("capacity", 3)),
            Map.entry("v2_mind_recovery_1", new Level("recovery", 1)),
            Map.entry("v2_mind_recovery_2", new Level("recovery", 2)),
            Map.entry("v2_mind_liangu_1", new Level("liangu", 1)),
            Map.entry("v2_mind_liangu_2", new Level("liangu", 2)),
            Map.entry("v2_mind_cooldown_1", new Level("cooldown", 1)),
            Map.entry("v2_mind_cooldown_2", new Level("cooldown", 2)));
}
