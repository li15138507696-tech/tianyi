package net.guzhenren.daotu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 与「天命」（tianfu）的<b>只读</b>兼容探测。
 *
 * <p>天命会把 {@code qiyun} / {@code qiyun_shangxian} 每 tick 强制归零（「天弃之命」），
 * 并把 {@code kongqiao} 压回锁定值（「空窍桎梏」）。道途若在这些状态下继续发放
 * 气运或改写资质，效果会被本体立刻抹掉，玩家只看到一条假的「+X」提示。
 *
 * <p>本类只读天命的 NBT（{@code PersistentData/tianfu}），<b>绝不写入</b>天命的任何键，
 * 也不依赖天命是否安装：未安装时 {@code contains} 为 false，一切返回 false。
 */
public final class DaotuTianfuCompat {
    private static final String ROOT = "tianfu";
    private static final String KEY_PURCHASED = "purchased";
    private static final String KEY_CONFIRMED = "confirmed";
    private static final String NEG_LUCK = "v2_luck_forfeit";
    private static final String NEG_APTITUDE = "v2_aptitude_shackled";

    private DaotuTianfuCompat() {
    }

    /** 该玩家是否已确认天命并持有指定词条。 */
    private static boolean owns(Player player, String id) {
        if (player == null) {
            return false;
        }
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag tag = pd.getCompound(ROOT);
        if (!tag.getBoolean(KEY_CONFIRMED)) {
            return false;
        }
        ListTag list = tag.getList(KEY_PURCHASED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /** 天命「天弃之命」：本世气运与气运上限恒为 0，任何气运增益都会被清零。 */
    public static boolean luckForfeit(Player player) {
        return owns(player, NEG_LUCK);
    }

    /** 天命「空窍桎梏」：本世空窍资质被锁定，不得再改写。 */
    public static boolean aptitudeShackled(Player player) {
        return owns(player, NEG_APTITUDE);
    }
}