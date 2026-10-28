package net.guzhenren.daotu;

import net.minecraft.world.entity.player.Player;

/**
 * 属性修正入口。
 *
 * <p><b>v1.4.0 大幅简化</b>：势力档位与流派节点的加成随功能一起删除后，
 * 属性成长<b>唯一</b>来源是 {@link DaotuRunAttributes} 从 {@code run/attrs} 重建的
 * transient {@code AttributeModifier}。
 *
 * <p>保留这个类而不是把调用点直接改成 {@code DaotuRunAttributes.apply}，
 * 是因为「什么时候重建属性」是一个独立的关注点（登录、转世、选完抽选、每 5 秒兜底），
 * 集中在一处比散落在十几个调用点好维护。
 *
 * <p><b>为什么用 modifier 而不是直接改字段</b>：本体每 tick 会重算自己的属性，
 * 直接改 {@code MAX_HEALTH} 之类的 base value 会被覆盖；modifier 是叠加层，不会被清掉。
 */
public final class DaotuAttributes {

    private DaotuAttributes() {
    }

    /** 按 {@code run/attrs} 全量重建本模组挂的全部属性修正。 */
    public static void sync(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        DaotuRunAttributes.apply(player);
    }
}
