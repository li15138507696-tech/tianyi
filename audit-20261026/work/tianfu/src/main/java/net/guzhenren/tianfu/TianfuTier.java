package net.guzhenren.tianfu;

/**
 * 空窍档位 → 显示名。
 *
 * <p>本体在 {@code UikongqiaoguanbiProcedure} 里按 kongqiao 分流：
 * 2=甲等、1/4=乙等、5–7=丙等、8–10=丁等、3=十绝体。
 */
public final class TianfuTier {
    private TianfuTier() {
    }

    public static String tierName(double kongqiao) {
        if (kongqiao == 2.0) {
            return "甲";
        }
        if (kongqiao == 1.0 || kongqiao == 4.0) {
            return "乙";
        }
        if (kongqiao == 3.0) {
            return "十绝体";
        }
        if (kongqiao >= 5.0 && kongqiao <= 7.0) {
            return "丙";
        }
        if (kongqiao >= 8.0 && kongqiao <= 10.0) {
            return "丁";
        }
        return kongqiao <= 0.0 ? "未开窍" : "无";
    }
}
