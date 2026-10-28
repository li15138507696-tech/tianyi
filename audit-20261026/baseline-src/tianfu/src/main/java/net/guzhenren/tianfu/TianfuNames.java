package net.guzhenren.tianfu;

/**
 * 附属自建的体质名表（17–54）。
 *
 * <p>本体 {@code UishuxingshijuetiProcedure} 只认 1–16，{@code tizhi >= 17} 一律显示「体质:无」，
 * 因此凡体与杀神必须由附属自建名称，并通过 Mixin 把本体那段的返回值替换掉。
 */
public final class TianfuNames {
    private static final String[] NAMES = new String[55];

    static {
        NAMES[17] = "九命不灭体";
        NAMES[18] = "百战金戈体";
        NAMES[19] = "寒渊素雪体";
        NAMES[21] = "长风破浪体";
        NAMES[22] = "白玉铁骨体";
        NAMES[24] = "幽魂凝识体";
        NAMES[25] = "断岳剑心体";
        NAMES[26] = "百炼庚金体";
        NAMES[28] = "搬山撼岳体";
        NAMES[30] = "苍木回春体";
        NAMES[31] = "太息养元体";
        NAMES[33] = "修罗战体";
        NAMES[37] = "太虚化形体";
        NAMES[39] = "如影随形体";
        NAMES[40] = "明月入怀体";
        NAMES[42] = "慧心通明体";
        NAMES[43] = "光阴逆转体";
        NAMES[45] = "御空踏云体";
        NAMES[48] = "南柯一梦体";
        NAMES[53] = "血煞蜕变体";
        NAMES[54] = "杀神";
    }

    private TianfuNames() {
    }

    /** 返回附属体质名；非附属体质返回 null。 */
    public static String bodyName(double tizhi) {
        int i = (int) Math.round(tizhi);
        if (i < 17 || i >= NAMES.length) {
            return null;
        }
        return NAMES[i];
    }
}
