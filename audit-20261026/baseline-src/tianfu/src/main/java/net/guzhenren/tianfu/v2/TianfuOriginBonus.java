// src/main/java/net/guzhenren/tianfu/v2/TianfuOriginBonus.java
package net.guzhenren.tianfu.v2;

/**
 * 出身效果的只读取值器：给现有周期通道（炼蛊、元石）提供已确认的出身加成。
 *
 * <p>未确认或老档无出身一律返回 0，绝不凭空补效果。
 */
public final class TianfuOriginBonus {
    private TianfuOriginBonus() { }

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

    /** 炼蛊额外进度倍率：地域 + 出身两部分相加。 */
    public static double liangu(TianfuSelection.Snapshot state) {
        if (state == null || !state.confirmed()) {
            return 0.0;
        }
        TianfuSelection.Origin origin = state.origin();
        if (origin == null) {
            return 0.0;
        }
        double bonus = 0.0;
        TianfuOriginTable.Region r = origin.regionId() == null ? null : region(origin.regionId());
        if (r != null) {
            bonus += r.lianguBonus();
        }
        TianfuOriginTable.Background b =
                origin.backgroundId() == null ? null : background(origin.backgroundId());
        if (b != null) {
            bonus += b.lianguBonus();
        }
        return bonus;
    }

    /** 商贾的元石比例（仅确认时一次性发放，不参与周期收入）。 */
    public static double yuanshi(TianfuSelection.Snapshot state) {
        if (state == null || !state.confirmed()) {
            return 0.0;
        }
        TianfuSelection.Origin origin = state.origin();
        if (origin == null || origin.backgroundId() == null) {
            return 0.0;
        }
        TianfuOriginTable.Background b = background(origin.backgroundId());
        return b == null ? 0.0 : b.yuanshiBonus();
    }
}