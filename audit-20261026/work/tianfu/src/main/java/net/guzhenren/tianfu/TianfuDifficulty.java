// src/main/java/net/guzhenren/tianfu/TianfuDifficulty.java
package net.guzhenren.tianfu;

import java.util.List;

/** 四档开局基础点 8/6/4/2；老档已发放点数不重新结算。 */
public final class TianfuDifficulty {
    /** 未选择难度。 */
    public static final int NOT_CHOSEN = -1;

    /**
     * @param points 选择该难度时发放的自由点数
     */
    public record Option(String id, String name, int points, String desc) {
    }

    public static final List<Option> OPTIONS = List.of(
            new Option("easy", "顺天应命", 8, "基础点 8；负面补点硬上限 +24"),
            new Option("normal", "凡尘问道", 6, "基础点 6；负面补点硬上限 +24"),
            new Option("hard", "逆天争命", 4, "基础点 4；负面补点硬上限 +24"),
            new Option("hell", "万劫磨道", 2, "基础点 2；负面补点硬上限 +24"));

    private TianfuDifficulty() {
    }

    public static Option byId(String id) {
        for (Option o : OPTIONS) {
            if (o.id().equals(id)) {
                return o;
            }
        }
        return null;
    }

    public static Option byIndex(int index) {
        return index >= 0 && index < OPTIONS.size() ? OPTIONS.get(index) : null;
    }
}
