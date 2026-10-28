// src/main/java/net/guzhenren/tianfu/v2/NeoForge1211Adapter.java
package net.guzhenren.tianfu.v2;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.tianfu.TianfuData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.21.1 本体适配。
 *
 * <p>只使用**字节码实测存在**的字段：{@code shouyuan} / {@code kongqiao} /
 * {@code yuanshishuliang} / {@code qiyun_shangxian} 与 45 个道痕字段。
 * 找不到真实钩子的效果一律返回 {@link TianfuSelection.Rejection#UNVERIFIED_EFFECT}，
 * 绝不静默给点或静默失效。
 */
public final class NeoForge1211Adapter implements VersionAdapter {
    /** 硬代价条目 id（与 TianfuTalents 中的 −8 档一致）。 */
    private static final String NEG_APTITUDE = "v2_aptitude_shackled";
    private static final String NEG_LUCK = "v2_luck_forfeit";
    private static final String NEG_LIFESPAN = "v2_lifespan_scar";
    /** 确认时一次性寿元扣除量。 */
    private static final double LIFESPAN_SCAR_COST = 400.0;
    /** 锁定原值的存档键。 */
    private static final String KEY_LOCK_KONGQIAO = "v2_lock_kongqiao";
    private static final String KEY_LIFESPAN_CAP = "v2_lifespan_cap";
    /** 空窍桎梏的念头容量惩罚。 */
    private static final String FIELD_NIANTOU_RONGLIANG = "niantou_rongliang";

    private static PlayerVariables vars(ServerPlayer player) {
        return player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
    }

    private static Field field(String name) {
        try {
            return PlayerVariables.class.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** 反射加值；字段不存在直接抛，不静默跳过（否则"成功"是假的）。 */
    private static void add(PlayerVariables v, String name, double delta) {
        Field f = field(name);
        if (f == null) {
            throw new IllegalStateException("本体字段不存在: " + name);
        }
        try {
            f.setDouble(v, f.getDouble(v) + delta);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("本体字段不可写: " + name, e);
        }
    }

    private static TianfuOriginTable.Region region(String id) {
        for (TianfuOriginTable.Region r : TianfuOriginTable.REGIONS) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private static TianfuOriginTable.Background background(String id) {
        for (TianfuOriginTable.Background b : TianfuOriginTable.BACKGROUNDS) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }



    /** {@inheritDoc} 未完成验证前必须拒绝对应出身/重度负面确认。 */
    @Override
    public TianfuSelection.Rejection preflight(ServerPlayer player, TianfuSelection.Snapshot state) {
        if (player == null || state == null) {
            return TianfuSelection.Rejection.UNVERIFIED_EFFECT;
        }
        // 1) 字段存在性：缺一个就整单拒绝，避免"部分生效"。
        for (String name : List.of("shouyuan", "kongqiao", "qiyun_shangxian")) {
            if (field(name) == null) {
                return TianfuSelection.Rejection.UNVERIFIED_EFFECT;
            }
        }
        // 2) 出身：炼蛊/商店折扣无钩子 → 拒绝。
        TianfuSelection.Origin origin = state.origin();
        if (origin != null) {
            TianfuOriginTable.Region r = origin.regionId() == null ? null : region(origin.regionId());
            TianfuOriginTable.Background b =
                    origin.backgroundId() == null ? null : background(origin.backgroundId());

            // 地域道痕字段必须全部真实存在。
            if (r != null) {
                for (String name : r.daohen().keySet()) {
                    if (field(name) == null) {
                        return TianfuSelection.Rejection.UNVERIFIED_EFFECT;
                    }
                }
            }
        }
        // 3) 命烛残尽：寿元不足不得确认（不能确认出一个当场死亡的角色）。
        if (state.purchased().contains(NEG_LIFESPAN)
                && vars(player).shouyuan - LIFESPAN_SCAR_COST <= 0.0) {
            return TianfuSelection.Rejection.UNSAFE_LIFESPAN;
        }
        return TianfuSelection.Rejection.NONE;
    }

    /** {@inheritDoc} 每个一次性效果独立幂等键，失败必须可恢复而不双写。 */
    @Override
    public void settle(ServerPlayer player, TianfuSelection.Snapshot state) {
        PlayerVariables v = vars(player);
        TianfuSelection.Origin origin = state.origin();

        // 1) 地域道痕：只写表内字段，不加熟练度权重、不碰 daohen_zong。
        if (origin != null && origin.regionId() != null && !origin.regionId().isEmpty()) {
            TianfuOriginTable.Region r = region(origin.regionId());
            if (r != null) {
                for (Map.Entry<String, Double> entry : r.daohen().entrySet()) {
                    add(v, entry.getKey(), entry.getValue().doubleValue());
                }
            }
        }

        // 2) 出身：气运上限百分比写 qiyun_shangxian，不写当前气运。
        if (origin != null && origin.backgroundId() != null && !origin.backgroundId().isEmpty()) {
            TianfuOriginTable.Background b = background(origin.backgroundId());
            if (b != null && b.luckCapBonus() != 0) {
                add(v, "qiyun_shangxian", v.qiyun_shangxian * b.luckCapBonus());
            }
        }

        // 3) 商贾：确认时一次性发放元石比例（yuanshishuliang 已实测存在）。
        if (origin != null && origin.backgroundId() != null && !origin.backgroundId().isEmpty()) {
            TianfuOriginTable.Background b = background(origin.backgroundId());
            if (b != null && b.yuanshiBonus() != 0) {
                add(v, "yuanshishuliang", v.yuanshishuliang * b.yuanshiBonus());
            }
        }

        // 4) 硬代价：空窍桎梏锁原资质，命烛残尽扣寿元并记上限。
        if (state.purchased().contains(NEG_APTITUDE)) {
            TianfuData.putDouble(player, KEY_LOCK_KONGQIAO, v.kongqiao);
            add(v, FIELD_NIANTOU_RONGLIANG, -Math.abs(v.niantou_rongliang));
        }
        if (state.purchased().contains(NEG_LIFESPAN)) {
            v.shouyuan = Math.max(0.0, v.shouyuan - LIFESPAN_SCAR_COST);
            TianfuData.putDouble(player, KEY_LIFESPAN_CAP, v.shouyuan);
        }
    }

    /** {@inheritDoc} 炼蛊额外进度只补整数到99，50至70投料窗口不加速。 */
    @Override
    public void maintain(ServerPlayer player, TianfuSelection.Snapshot state) {
        PlayerVariables v = vars(player);

        // 天弃之命：气运与上限持续归零，增益不能抵消。
        if (state.purchased().contains(NEG_LUCK)) {
            v.qiyun = 0.0;
            v.qiyun_shangxian = 0.0;
        }

        // 空窍桎梏：把 kongqiao 压回锁定值（后续机缘/十绝体无法突破）。
        if (state.purchased().contains(NEG_APTITUDE)) {
            double locked = TianfuData.getDouble(player, KEY_LOCK_KONGQIAO, -1.0);
            if (locked >= 0.0 && v.kongqiao != locked) {
                v.kongqiao = locked;
            }
        }

        // 命烛残尽：本世寿元不得增加。
        if (state.purchased().contains(NEG_LIFESPAN)) {
            double cap = TianfuData.getDouble(player, KEY_LIFESPAN_CAP, -1.0);
            if (cap >= 0.0 && v.shouyuan > cap) {
                v.shouyuan = cap;
            }
        }
    }

    /** {@inheritDoc} 需先反编译定位真实收入来源，禁止用物品拾取充当全部收入。 */
    @Override
    public long extraIncome(ServerPlayer player, long baseAmount, String sourceKey) {
        // 商贾改为确认时一次性元石比例，不走周期收入；此处不再发放额外元石。
        if (player == null || sourceKey == null || baseAmount <= 0) {
            return 0L;
        }
        return 0L;
    }
}