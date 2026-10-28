package net.guzhenren.malice;

import net.minecraft.ChatFormatting;

/**
 * 四档难度。玩家用命令切换，值存在存档里（{@link DifficultyState}）。
 *
 * <p>难度只作用在 <b>恶意给的等级</b> 上（等级 → 血量 ADD_MULTIPLIED_TOTAL，
 * 命中时按等级放大伤害），不改本体的基础属性，所以：
 * <ul>
 *   <li>本体怪物自己的技能、召唤、术法伤害不受影响；</li>
 *   <li>卸载本 mod 后存档里的怪立刻回到「只有恶意」的状态，不会留下属性残留。</li>
 * </ul>
 */
public enum Difficulty {

    /** 安逸：适合刚开窍、还在南疆捡蛊材的阶段。 */
    EASY("安逸", 0.6, ChatFormatting.GREEN),
    /** 标准：默认档，和四档数值表 1:1。 */
    NORMAL("标准", 1.0, ChatFormatting.YELLOW),
    /** 凶险：怪等级 ×1.5，妖兽开始成群压过来。 */
    HARD("凶险", 1.5, ChatFormatting.GOLD),
    /** 噩梦：怪等级 ×2.2，BOSS 会顶到 300 级以上。 */
    NIGHTMARE("噩梦", 2.2, ChatFormatting.RED);

    private final String cn;
    private final double levelMul;
    private final ChatFormatting color;

    Difficulty(String cn, double levelMul, ChatFormatting color) {
        this.cn = cn;
        this.levelMul = levelMul;
        this.color = color;
    }

    public String cn() {
        return cn;
    }

    /** 恶意等级倍率。 */
    public double levelMul() {
        return levelMul;
    }

    public ChatFormatting color() {
        return color;
    }

    /** 解析命令参数，接受 EASY / easy / 安逸。 */
    public static Difficulty parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(s) || d.cn.equals(s)) {
                return d;
            }
        }
        return null;
    }

    public static String[] names() {
        Difficulty[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].name().toLowerCase(java.util.Locale.ROOT);
        }
        return out;
    }
}
