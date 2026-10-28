package net.guzhenren.daotu;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 特殊奖励 buff 的维持。
 *
 * <p><b>为什么不能只在发放时加一次</b>：本体
 * {@code SiqibuffXiaoGuoChiXuShiMeiKeFaShengProcedure} 在「四气 buff」结束时会
 * 对 {@code LivingEntity} 调 {@code removeAllEffects()}——清空<b>全部</b>效果，
 * 不区分来源。玩家只要身上有四气 buff 到期，道途发的 buff 就会被一起抹掉，
 * 而且玩家完全看不出原因。
 *
 * <p>对策：<b>不依赖一次性发放</b>。每秒检查一次「按 {@code run/buffs} 本该有的效果」，
 * 缺了就补、快过期就续。即使被本体清空，下一秒也会自动恢复。
 *
 * <p><b>白名单只有 12 项，不可凭记忆扩展。</b>本体 348 个 MobEffect 的分类统计是
 * BENEFICIAL 171 / NEUTRAL 131 / HARMFUL 47，但 <b>category 不可靠</b>：
 * {@code lvdaozengfu}（律道增幅，明显正面）被标 NEUTRAL，
 * {@code weilijianbei}（威力减倍，实际是削弱）也是 NEUTRAL。
 * 因此本名单按语义 + 实际效果双重确认，宁可少不可错。
 *
 * <p><b>明确排除</b>：{@code weilijianbei}（威力减倍 = 削弱）、
 * {@code ya_li_2}（压力）、{@code jie_bi_ya_li}（界壁压力）。
 */
public final class DaotuBuffs {

    /** 剩余时长低于此值就续（tick）。避免每 tick 刷新导致图标一直闪。 */
    private static final int RENEW_BELOW = 200;

    /** 单次发放时长（tick，约 10 分钟）。够长，不必频繁补。 */
    private static final int DURATION = 12000;

    /** 本体 buff 的命名空间。 */
    private static final String BASE_NS = "guzhenren";

    /**
     * 正面效果白名单。每一项都已确认存在于本体的
     * {@code effect.guzhenren.*} 语言文件中（脚本核对）。
     *
     * <p>增删任何一项前必须重新核对 category 与语义——见类注释。
     */
    public static final List<String> WHITELIST = List.of(
            "shuidaozengfu",
            "yudaoguchongweilizengfu",
            "qidaoguchongweilizengfu",
            "jiandaoshanghaizengfu",
            "lvdaozengfu",
            "jindaozengfu",
            "yandaozengfu",
            "leidaozengshang",
            "weilizengjia",
            "daligubuff",
            "tiexue",
            "leishen");

    private DaotuBuffs() {
    }

    /** 按 {@code run/buffs} 维护。由 {@link DaotuRuntime} 每秒调用。 */
    public static void maintain(ServerPlayer player) {
        for (String id : DaotuRun.buffs(player)) {
            apply(player, id);
        }
    }

    private static void apply(ServerPlayer player, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        // 白名单里存的是不带命名空间的短名，本体效果都在 guzhenren 下。
        // 若调用方给了带冒号的全名，则原样使用（便于将来扩展）。
        ResourceLocation full = id.indexOf(':') >= 0
                ? ResourceLocation.tryParse(id)
                : ResourceLocation.fromNamespaceAndPath(BASE_NS, id);
        if (full == null) {
            return;
        }
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.getHolder(full).orElse(null);
        if (holder == null) {
            DaotuMod.LOGGER.debug("特殊奖励 buff 不存在，已跳过: {}", id);
            return;
        }

        MobEffectInstance cur = player.getEffect(holder);
        if (cur != null) {
            // 本体的 -1 表示无限；无限就不动它
            if (cur.getDuration() == -1) {
                return;
            }
            // 还够久就不打扰
            if (cur.getDuration() > RENEW_BELOW && cur.getAmplifier() >= 0) {
                return;
            }
            // 不覆盖玩家自己弄到的更长/更强的同名效果
            if (cur.getDuration() > DURATION) {
                return;
            }
        }
        player.addEffect(new MobEffectInstance(holder, DURATION, 0, false, false, true));
    }

    /** 立即补一次（刚抽到 buff 时用，不等下一秒的 maintain）。 */
    public static void grantNow(ServerPlayer player, String id) {
        apply(player, id);
    }
}
