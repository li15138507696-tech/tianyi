package net.guzhenren.tianyi;

import net.guzhenren.tianyi.will.BeastCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * 妖兽志：判断一只本体生物「是不是蛊兽、几阶、属哪一道」。
 *
 * <h2>数据来源</h2>
 * 阶数与流派<b>全部</b>来自 {@link BeastCatalog} —— 那张表按整合包知识库
 * 第 6.2 节「蛊兽名录（流派 × 规模）」编目，并由 {@code _pack/audit_bestiary.py}
 * 对本体 jar 的 {@code assets/guzhenren/lang/en_us.json} 逐条核对
 * （id 必须存在、名字必须对得上）。
 *
 * <h2>为什么不用血量</h2>
 * 反查本体实体类的 {@code MAX_HEALTH} 后发现血量完全不能代表强弱
 * （魂兽与魂兽·万兽王同为 1000 血、木魅树精 760 血跨两档），
 * 本体也没有任何「妖兽等级」字段。所以只认名录。
 *
 * <h2>本类只做 MC 侧判定</h2>
 * 纯数据与查询在 {@code will/BeastCatalog}（无 Minecraft 依赖，可离线测试）。
 */
public final class TianyiBestiary {

    /** 阵营（修士暂未用于试炼，保留接口）。 */
    public enum Faction {
        /** 妖兽。 */
        BEAST,
        /** 修士。 */
        CULTIVATOR
    }

    /** 最大阶（与名录一致）。 */
    public static final int MAX_TIER = BeastCatalog.MAX_TIER;

    /** 判为修士的 id 片段（不参与试炼）。 */
    private static final String[] CULTIVATOR_HINTS = {
            "gu_xiu", "guxiu", "xiu_shi", "san_xiu", "npc", "renwu", "ren_wu", "nanmin",
    };

    private TianyiBestiary() {
    }

    /** 实体注册 id（如 {@code guzhenren:hunshou_2}）；取不到返回空串。 */
    public static String idOf(LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? "" : key.toString();
    }

    /** 是否是本体生物（妖兽 / 修士的候选）。 */
    public static boolean isBengu(LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null || !"guzhenren".equals(key.getNamespace())) {
            return false;
        }
        String path = key.getPath();
        return !path.contains("texiao") && !path.contains("shiti");
    }

    /** 是否妖兽（本体生物里排除修士）。 */
    public static boolean isBeast(LivingEntity entity) {
        return isBengu(entity) && factionOf(entity) == Faction.BEAST;
    }

    /** 阵营判定（修士按注册 id 拼音，启发式）。 */
    public static Faction factionOf(LivingEntity entity) {
        if (entity instanceof AbstractVillager
                || entity instanceof net.minecraft.world.entity.raid.Raider) {
            return Faction.CULTIVATOR;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path = key == null ? "" : key.getPath();
        for (String hint : CULTIVATOR_HINTS) {
            if (path.contains(hint)) {
                return Faction.CULTIVATOR;
            }
        }
        return Faction.BEAST;
    }

    /**
     * 妖兽阶（1–5）。
     *
     * <p>名录里的按名录；名录里没有的本体妖兽算 1 阶；非本体 / 修士返回 0。
     */
    public static int tierOf(LivingEntity entity) {
        if (!isBeast(entity)) {
            return 0;
        }
        int t = BeastCatalog.tierOf(idOf(entity));
        return t == 0 ? 1 : t;
    }

    /** 妖兽所属流派（不在名录里返回「无」）。 */
    public static BeastCatalog.Dao daoOf(LivingEntity entity) {
        return BeastCatalog.daoOf(idOf(entity));
    }

    /** 阶名（1 普通蛊兽 / 2 百兽 / 3 千兽 / 4 万兽 / 5 兽皇）。 */
    public static String tierName(int tier) {
        return BeastCatalog.tierName(tier);
    }

    /** 名录里的中文名（不在名录里返回空串）。 */
    public static String nameOf(String id) {
        return BeastCatalog.nameOf(id);
    }

    /** 是否畜生（用于文档展示，不参与判定）。 */
    public static boolean animalLike(LivingEntity entity) {
        return entity instanceof Animal || entity instanceof Enemy;
    }
}
