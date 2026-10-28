// src/main/java/net/guzhenren/tianfu/TianfuCombat.java
package net.guzhenren.tianfu;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 附属战斗层（本体 {@code ShangHaiLiuCheng1Procedure} → {@code Entity.hurt()} 之后的额外乘区）。
 *
 * <p>本体伤害公式已经通过 A/B/C/D 把道痕算进出伤了（LiuPai1/daohen_zong 的专精加成/杂修惩罚），
 * 所以凡体的"流派伤害加成"不再在这里硬编码——附属只负责写入道痕本体自然吃。
 *
 * <p>附属在本体<b>完全没有的维度</b>上叠加新机制（暴击/反伤/闪避/非护甲减伤/条件阶梯），
 * 全部走 LivingIncomingDamageEvent 新乘区，不碰本体 A/B/C/D，不双计。
 *
 * <p>出伤侧顺序：条件阶梯倍率 × 暴击倍率 × 吸血。
 * 受击侧顺序：非护甲减伤 → 闪避过滤 → 反伤反弹。
 */
public final class TianfuCombat {
    private static final Map<String, TagKey<Item>> TAGS = new ConcurrentHashMap<>();
    /** 太虚化形的内置冷却（5 秒）。 */
    private static final long XUHUA_CD_TICKS = 100L;
    /** 暴击概率上限（50% 封顶，避免全暴击）。 */
    private static final double CRIT_CAP = 0.50;
    /** 反伤递归保护：事件内调用 {@code hurt()} 会重入本处理器。 */
    private static final ThreadLocal<Boolean> IN_REFLECT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // ---- 恶意联动（1.2.30）运行时状态 ----
    /** 恶意·反击：玩家上次被击中的 tick。 */
    private static final Map<java.util.UUID, Integer> COUNTER_TICK = new ConcurrentHashMap<>();
    /** 恶意·不死：冷却结束的世界时间（写进玩家 PersistentData，重登/换维度都不清）。 */
    private static final String TAG_UNDYING_READY = "tianfu_malice_undying_ready";
    /** 恶意·适应：玩家 → (伤害类型 msgId → 已叠层数)。 */
    private static final Map<java.util.UUID, Map<String, Integer>> ADAPT_STACK = new ConcurrentHashMap<>();

    private TianfuCombat() {
    }

    private static TagKey<Item> tag(String path) {
        return TAGS.computeIfAbsent(path,
                p -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("guzhenren", p)));
    }

    private static boolean holds(ServerPlayer player, String path) {
        ItemStack stack = player.getMainHandItem();
        return !stack.isEmpty() && stack.is(tag(path));
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // 递归保护：reflectDamage 里的 hurt() 会再次进本事件，此时直接跳过。
        if (IN_REFLECT.get()) return;
        Entity attacker = event.getSource().getEntity();

        // ---- 受击侧：非护甲减伤 → 闪避过滤 → 反伤反弹 ----
        if (event.getEntity() instanceof ServerPlayer victim && victim != attacker) {
            PlayerVariables v = victim.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            Set<String> ids = TianfuApply.effective(victim);

            double reduction = reduction(victim, v, ids, event);
            if (reduction > 0.0) {
                event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
            }

            // 恶意·月步：摔落伤害减免
            if (event.getSource().typeHolder().is(DamageTypeTags.IS_FALL)) {
                double fr = TianfuTalents.fallReduce(ids);
                if (fr > 0.0) {
                    event.setAmount((float) (event.getAmount() * (1.0 - Math.min(fr, 0.9))));
                }
            }

            // 恶意·适应：按「伤害类型」记忆层数，同类越打越不疼（上限见词条）
            applyAdaptive(victim, ids, event);

            if (rollDodge(victim, v, ids)) {
                event.setAmount(0.0F);
                return;
            }

            reflectDamage(victim, ids, event);   // 反伤：基于当前事件的最终伤害（减伤已生效）

            // 恶意·反击：记下被击中的时刻，出伤侧据此给短时加成
            if (TianfuTalents.counterBonus(ids) > 0.0) {
                COUNTER_TICK.put(victim.getUUID(), victim.tickCount);
            }

            // 恶意·不死：放在所有减伤/闪避之后，按最终伤害判定是否致命
            tryUndying(victim, ids, event);
        }

        // ---- 出伤侧：条件阶梯 × 暴击 × 吸血 ----
        ServerPlayer sp = attackerPlayer(event);
        if (sp != null && event.getEntity() != sp) {
            PlayerVariables v = sp.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            Set<String> ids = TianfuApply.effective(sp);

            double mult = outgoingMultiplier(sp, v, ids) * counterMultiplier(sp, ids);
            boolean crit = rollCrit(sp, ids);
            if (crit) {
                mult *= critDamageMultiplier(ids);
            }
            if (mult != 1.0) {
                event.setAmount((float) (event.getAmount() * mult));
            }

            // 「X烛」：流派体质在攻击命中时附加<b>自己流派</b>的烛
            // （剑道 → 剑烛、炎道 → 炎烛…），再次命中时按已侵蚀时长结算伤害。
            // v1.2.19 起取代原先「所有体质统一挂宙侵」的做法——
            // 那样无论什么流派打出来都是宙道伤害，流派没有区别。
            if (event.getEntity() instanceof LivingEntity living) {
                TianfuZhuCombat.onHit(sp, living);
                applyOnHit(sp, ids, living);   // 恶意·出伤附带（毒/凋零/缓慢/虚弱/致盲）
            }

            // 吸血：词条（吸血入魔/吸血如饕）+ 体质（血煞蜕变 5%）叠加
            double leech = leechAmount(v, ids);
            if (leech > 0.0) {
                sp.heal((float) (event.getAmount() * leech));
            }
        }
    }

    /**
     * 解析真正的「出伤玩家」。
     *
     * <p>蛊虫（{@code *Entity extends AbstractArrow}）命中时会调
     * {@code Shizhuotanshewu...Procedure.execute(level, x, y, z, target, getOwner())}，
     * 再由 {@code ShangHaiLiuCheng1Procedure} 用 {@code new DamageSource(holder, attacker)}
     * 结算——也就是说本体会把<b>发射者</b>放进伤害来源，正常情况下
     * {@code getSource().getEntity()} 已经是玩家。
     *
     * <p>仍保留 {@code Projectile.getOwner()} 回溯：部分蛊虫/附属的伤害来源可能直接
     * 归因到投射物实体（本体 395 个蛊虫实体里有 240 个是在飞行 procedure 里
     * {@code setOwner} 的，归因路径并不统一）。只认 {@code ServerPlayer} 会让这些路径整条跳过。
     */
    private static ServerPlayer attackerPlayer(LivingIncomingDamageEvent event) {
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer p) {
            return p;
        }
        if (src instanceof net.minecraft.world.entity.projectile.Projectile proj
                && proj.getOwner() instanceof ServerPlayer p) {
            return p;
        }
        if (src != null && src.getControllingPassenger() instanceof ServerPlayer p) {
            return p;
        }
        return null;
    }

    /** 条件阶梯倍率（不含暴击）。 */
    public static double outgoingMultiplier(ServerPlayer player, PlayerVariables v, Set<String> ids) {
        int tizhi = (int) Math.round(v.tizhi);
        double mult = 1.0;
        float hpRatio = player.getHealth() / player.getMaxHealth();

        // 满血加成（词条 战意盎然 + 扩展表「全盛」系）
        if (hpRatio >= 1.0f) {
            if (ids.contains("zhanyi_angran")) {
                mult *= 1.08;
            }
            mult *= 1.0 + TianfuTalents.outFull(ids);
        }
        // 残血加成（扩展表「残身」系，血量 <30%）
        if (hpRatio < 0.3F) {
            mult *= 1.0 + TianfuTalents.outLow(ids);
        }

        // 残血阶梯（从上到下，越残越狠）
        if (tizhi == 33) {
            // 修罗战体：每损 10% 血 +5%（最多 5 档 = +25%）
            double loss = 1.0 - hpRatio;
            int steps = (int) Math.floor(loss * 10);
            mult *= 1.0 + Math.min(steps, 5) * 0.05;
        }
        if (tizhi == 53 && hpRatio < 0.3F) {
            mult *= 1.12;                 // 血煞蜕变体：残血狂化
        }
        if (v.tizhi == 54.0) {
            mult *= TianfuSlay.multiplier(TianfuData.slay(player)); // 杀神杀戮成长
        }
        return mult;
    }

    private static double reduction(ServerPlayer player, PlayerVariables v, Set<String> ids, LivingIncomingDamageEvent event) {
        int tizhi = (int) Math.round(v.tizhi);
        double r = 0.0;
        if (tizhi == 22) {
            r += TianfuDaohen.scale(v.daohen_gudao, 0.10);                // 白玉铁骨体 · 骨道
        }
        if (tizhi == 19 && isCold(event)) {
            r += TianfuDaohen.scale(v.daohen_bingxuedao, 0.20);           // 寒渊素雪体 · 冰雪道
        }
        // 词条：游斗高手（移动时受击 −6%）
        if (ids.contains("youdou_gaoshou") && player.getDeltaMovement().lengthSqr() > 0.01) {
            r += 0.06;
        }
        // 神·不灭金身：受击减伤 +20%
        if (ids.contains("shen_bumie_jinshen")) {
            r += 0.20;
        }
        r += TianfuTalents.reduction(ids);   // 扩展词条（铁皮肉骨 / 入微定神 / 坚甲系）
        // 恶意·摄魂 / 破魔：按伤害是否吃护甲分流（护甲能减的算物理，穿护甲的算魔法/真伤）
        boolean armorApplies = !event.getSource().typeHolder().is(DamageTypeTags.BYPASSES_ARMOR);
        r += armorApplies ? TianfuTalents.physicalReduction(ids) : TianfuTalents.magicReduction(ids);
        return Math.min(r, 0.5);
    }

    private static boolean isCold(LivingIncomingDamageEvent event) {
        // 本体只有 1 个自定义伤害类型，冰雪道蛊的攻击多为原版伤害类型，
        // 所以「冰系」同时接受：原版冻结伤害标签，或攻击方主手持冰雪道蛊。
        if (event.getSource().typeHolder().is(DamageTypeTags.IS_FREEZING)) {
            return true;
        }
        return event.getSource().getEntity() instanceof ServerPlayer sp && holds(sp, "bing_xue_dao");
    }

    private static boolean rollDodge(ServerPlayer player, PlayerVariables v, Set<String> ids) {
        int tizhi = (int) Math.round(v.tizhi);
        double chance = 0.0;
        float hpRatio = player.getHealth() / player.getMaxHealth();

        if (tizhi == 39) {
            chance += 0.05;               // 如影随形体：常驻 +5%
        }
        if (tizhi == 17 && hpRatio < 0.1F) {
            chance += 0.25;               // 九命不灭体：濒死 +25%
        }
        if (tizhi == 37) {                // 太虚化形体：15% 虚化免伤，内置 5 秒 CD
            long now = player.level().getGameTime();
            long last = TianfuData.getLong(player, "xuhua_cd", player.tickCount - XUHUA_CD_TICKS);
            if (now - last >= XUHUA_CD_TICKS) {
                chance += 0.15;
                TianfuData.putLong(player, "xuhua_cd", now);
            }
        }
        if (ids.contains("shen_tianji_suan")) {
            chance += 0.24;               // 神·天机神算 +24%
        }
        chance += TianfuTalents.dodge(ids);   // 扩展词条（闪步随风 / 身法系）
        if (chance <= 0.0) {
            return false;
        }
        return player.getRandom().nextDouble() < Math.min(chance, 0.6);
    }

    // ========================================= 新增战斗机制 =========================================

    /** 暴击判定：原有词条（暴击精通 +5%）+ 扩展词条（暴击如裂 / 暴击如神）+ 神级（天机神算）叠加，50% 封顶。 */
    private static boolean rollCrit(ServerPlayer player, Set<String> ids) {
        double chance = 0.0;
        if (ids.contains("baoji_jingtong")) chance += 0.05;
        if (ids.contains("shen_tianji_suan")) chance += 0.50;   // 神·天机神算（恰好等于 CRIT_CAP → 必暴击）
        chance += TianfuTalents.critChance(ids);               // 扩展词条（暴击如裂 / 暴击如神）
        if (chance <= 0.0) return false;
        return player.getRandom().nextDouble() < Math.min(chance, CRIT_CAP);
    }

    /** 暴击伤害倍率。基础 2.0（普通暴击翻倍），原有词条 / 扩展词条 / 神级叠加。 */
    private static double critDamageMultiplier(Set<String> ids) {
        double mult = 2.0;                 // 基础：暴击 ×2 伤害
        if (ids.contains("baoji_jingtong")) mult += 0.20;
        if (ids.contains("jianbi_qingfeng")) mult += 0.30;
        if (ids.contains("shen_tianji_suan")) mult += 2.40;   // 神·天机神算：暴击 ×4.4
        mult += TianfuTalents.critDamage(ids);                // 扩展词条（暴伤如锋 / 暴击如神）
        return mult;
    }

    /** 吸血总比例（体质血煞蜕变 5% + 扩展词条 吸血入魔/吸血如饕 + 神级吞天噬地 12%，可叠加）。 */
    private static double leechAmount(PlayerVariables v, Set<String> ids) {
        double r = 0.0;
        if (Math.round(v.tizhi) == 53) r += 0.05;  // 血煞蜕变体
        if (ids.contains("shen_tuntian_shidi")) r += 0.12;  // 神·吞天噬地
        r += TianfuTalents.leech(ids);             // 扩展词条（吸血入魔 / 吸血如饕）
        return Math.min(r, 0.15);                  // 15% 封顶
    }

    /** 反伤：词条右守能铠 4% 概率反弹 10%；神级吞天噬地 25% 概率反弹 15%（取神级）。 */
    private static void reflectDamage(ServerPlayer victim, Set<String> ids, LivingIncomingDamageEvent event) {
        double chance = 0.0;
        double ratio = 0.0;
        if (ids.contains("youshou_nengkai")) { chance = 0.04; ratio = 0.10; }
        if (ids.contains("shen_tuntian_shidi")) { chance = 0.25; ratio = 0.15; }
        double[] talent = TianfuTalents.bestReflect(ids);
        if (talent != null && talent[0] * talent[1] > chance * ratio) {
            chance = talent[0];        // 扩展词条：取「概率 × 比例」最强者，避免多条词条数值失控
            ratio = talent[1];
        }
        if (chance <= 0.0) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (victim.getRandom().nextDouble() >= chance) return;
        float original = event.getAmount();
        float reflected = (float) (original * ratio);
        IN_REFLECT.set(Boolean.TRUE);
        try {
            attacker.hurt(victim.damageSources().thorns(victim), reflected);
        } finally {
            IN_REFLECT.set(Boolean.FALSE);
        }
    }

    // ------------------------------------------------------------------ 恶意联动（1.2.30）

    /** 恶意·适应：同类伤害叠层减伤。 */
    private static void applyAdaptive(ServerPlayer victim, Set<String> ids, LivingIncomingDamageEvent event) {
        double per = TianfuTalents.adaptivePer(ids);
        int cap = TianfuTalents.adaptiveCap(ids);
        if (per <= 0.0 || cap <= 0) {
            return;
        }
        String key = event.getSource().getMsgId();
        Map<String, Integer> stacks = ADAPT_STACK.computeIfAbsent(victim.getUUID(), k -> new ConcurrentHashMap<>());
        if (stacks.size() > 16) {
            stacks.clear();   // 防止记忆表随伤害类型无限膨胀
        }
        int cur = stacks.merge(key, 1, Integer::sum);
        if (cur > cap) {
            cur = cap;
            stacks.put(key, cap);
        }
        event.setAmount((float) (event.getAmount() * (1.0 - per * cur)));
    }

    /** 恶意·反击：被击中后的短时出伤加成。 */
    private static double counterMultiplier(ServerPlayer player, Set<String> ids) {
        double bonus = TianfuTalents.counterBonus(ids);
        if (bonus <= 0.0) {
            return 1.0;
        }
        Integer last = COUNTER_TICK.get(player.getUUID());
        if (last == null) {
            return 1.0;
        }
        int window = TianfuTalents.counterTicks(ids);
        return player.tickCount - last <= window ? 1.0 + bonus : 1.0;
    }

    /** 恶意·不死：致命伤害改为「留 1 血 + 回血」，带冷却。 */
    private static void tryUndying(ServerPlayer victim, Set<String> ids, LivingIncomingDamageEvent event) {
        double[] und = TianfuTalents.undying(ids);
        if (und == null || event.getAmount() < victim.getHealth()) {
            return;
        }
        long now = victim.level().getGameTime();
        var data = victim.getPersistentData();
        if (now < data.getLong(TAG_UNDYING_READY)) {
            return;
        }
        data.putLong(TAG_UNDYING_READY, now + (long) und[1]);
        event.setAmount(Math.max(0.0F, victim.getHealth() - 1.0F));
        victim.heal((float) (victim.getMaxHealth() * und[0]));
        victim.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§d[天赋] 不死之身：免于一死"), true);
    }

    /** Ordinary respawns preserve the cooldown; a wiped new life starts fresh. */
    public static void copyCooldownOnClone(ServerPlayer player, Entity original) {
        if (!TianfuData.isConfirmed(player)) {
            return;
        }
        var oldData = original.getPersistentData();
        if (oldData.contains(TAG_UNDYING_READY, net.minecraft.nbt.Tag.TAG_LONG)) {
            player.getPersistentData().putLong(TAG_UNDYING_READY, oldData.getLong(TAG_UNDYING_READY));
        }
    }

    /** 玩家下线：清掉恶意联动的内存状态，避免长期残留。 */
    public static void forget(java.util.UUID id) {
        COUNTER_TICK.remove(id);
        ADAPT_STACK.remove(id);
    }

    /** 恶意·出伤附带效果（毒 / 凋零 / 缓慢 / 虚弱 / 致盲）。 */
    private static void applyOnHit(ServerPlayer attacker, Set<String> ids, LivingEntity target) {
        var list = TianfuTalents.onHitEffects(ids);
        for (var fx : list) {
            var holder = TianfuTalents.effectHolder(fx.arg());
            if (holder == null) {
                continue;
            }
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    holder, (int) fx.a(), (int) fx.b()));
        }
    }

    /**
     * 只读验证V2战斗代价需要的约束，不能重复乘旧词条效果。
     * @param player 非null逻辑服务端玩家；未确认时不得应用新效果
     * @throws IllegalStateException 适配层字段失配时中止该次处理，不宣称已生效
     */
    public static void validateV2Constraints(ServerPlayer player) {
        if (player == null) {
            return;
        }
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        if (!snapshot.confirmed()) {
            return;
        }
        net.guzhenren.tianfu.v2.TianfuSelection.Rejection reason =
                new net.guzhenren.tianfu.v2.NeoForge1211Adapter().preflight(player, snapshot);
        if (reason != net.guzhenren.tianfu.v2.TianfuSelection.Rejection.NONE) {
            throw new IllegalStateException("V2 约束失配: " + reason);
        }    }
}
