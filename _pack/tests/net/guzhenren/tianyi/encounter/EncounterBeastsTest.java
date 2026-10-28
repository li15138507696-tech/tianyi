package net.guzhenren.tianyi.encounter;

import java.util.HashSet;
import java.util.Set;

import net.guzhenren.tianyi.will.BeastCatalog;

/**
 * 奇遇蛊兽池的离线测试。
 *
 * <p>3.10.1 起池子不再是手写 6 只，而是「名录 85 只 + 抽出来的原生属性」。
 * 这里锁定：成员关系、默认兽、数值有效性、门槛、确定性、可达性、不可变性。
 */
public final class EncounterBeastsTest {
    public static void main(String[] args) {
        var all = EncounterBeasts.all();

        require(all.size() == BeastStats.size(), "池子与属性表条数不一致");
        require(all.size() >= 80, "名录没接进来（仍是旧的小名单？）");
        require(all.getFirst().id().equals(EncounterBeasts.DEFAULT_ID),
                "默认兽必须排在最前");

        Set<String> ids = new HashSet<>();
        for (var beast : all) {
            require(ids.add(beast.id()), "重复的兽：" + beast.id());
            // 成员必须来自名录
            require(BeastCatalog.get(beast.id()) != null,
                    "池子里出现了名录外的兽：" + beast.id());
            // 原生属性必须有效
            require(beast.health() > 0 && beast.armor() >= 0 && beast.attack() > 0,
                    "原生属性非法：" + beast.id());
            require(!beast.name().isBlank() && !beast.warning().isBlank(),
                    "缺少名字或说明：" + beast.id());
            // 说明里必须写出真实数值（不允许空泛劝告）
            require(beast.warning().contains("原生生命"),
                    "说明里没有原生数值：" + beast.id());
            // 门槛必须与「阶/血取高」一致
            int expect = EncounterBeasts.minRankOf(
                    BeastCatalog.tierOf(beast.id()), beast.health());
            require(beast.minRank() == expect,
                    "门槛不符：" + beast.id() + " 期望 " + expect + " 实际 " + beast.minRank());
        }

        // 新手/未知门槛恒为默认兽
        for (long seed : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            require(EncounterBeasts.select(0, seed).equals(all.getFirst()), "新手门槛失效");
            require(EncounterBeasts.select(-99, seed).equals(all.getFirst()), "未知门槛失效");
        }

        // 每个 rank：只出符合条件的、结果确定、且符合条件的都能被抽到
        for (int rank = 1; rank <= 9; rank++) {
            Set<String> seen = new HashSet<>();
            for (long seed = -1500; seed < 1500; seed++) {
                var picked = EncounterBeasts.select(rank, seed);
                require(all.contains(picked), "选出了池子外的兽");
                require(picked.minRank() <= rank, "门槛失效：" + picked.id());
                require(picked.equals(EncounterBeasts.select(rank, seed)), "选择不确定");
                seen.add(picked.id());
            }
            for (var beast : all) {
                if (beast.minRank() <= rank) {
                    require(seen.contains(beast.id()),
                            "符合条件的兽抽不到：" + beast.id() + "（rank=" + rank + "）");
                }
            }
        }

        // 门槛确实分档，而不是一律 0
        long rank0 = all.stream().filter(b -> b.minRank() == 0).count();
        long rankHigh = all.stream().filter(b -> b.minRank() >= 3).count();
        require(rank0 > 0 && rankHigh > 0, "门槛没有分档");
        require(rank0 < all.size(), "所有兽都是 0 门槛 —— 门槛没起作用");

        // 不可变
        try {
            all.clear();
            throw new AssertionError("池子可被外部修改");
        } catch (UnsupportedOperationException expected) {
            // 正常
        }

        // 按 id 查得到，查不到返回 null
        require(EncounterBeasts.byId("guzhenren:dian_lang") != null, "按 id 查不到电狼");
        require(EncounterBeasts.byId("guzhenren:no_such") == null, "查不到的应返回 null");

        System.out.println("EncounterBeastsTest passed ("
                + all.size() + " catalog beasts, rank0=" + rank0
                + ", rank>=3=" + rankHigh + ", deterministic gates)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
