package net.guzhenren.tianfu;

import java.util.Set;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 属性类效果统一走 AttributeModifier：百分比用 ADD_MULTIPLIED_TOTAL 乘区，固定值用 ADD_VALUE。
 *
 * <p>刻意避开 {@code ARMOR}（本体把护甲钳在 0–30），减伤改由 {@link TianfuCombat} 自管乘区实现。
 * 使用 transient 修正：不写入玩家 NBT，登录 / 结算时用 {@link #sync} 幂等重建，避免重复叠加。
 */
public final class TianfuAttributes {
    private static final ResourceLocation HEALTH_MULT = id("body_health_mult");
    private static final ResourceLocation ATTACK_MULT = id("body_attack_mult");
    private static final ResourceLocation SPEED_MULT = id("body_speed_mult");
    private static final ResourceLocation ATTACK_SPEED_MULT = id("talent_attack_speed_mult");
    private static final ResourceLocation JUMP_FLAT = id("talent_jump_flat");
    private static final ResourceLocation KNOCKBACK_FLAT = id("talent_knockback_flat");
    private static final ResourceLocation FALL_FLAT = id("talent_fall_flat");

    private TianfuAttributes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, path);
    }

    public static void sync(Player player) {
        // 未最终确认 → 空集：属性修正不提前生效（与 TianfuEffects 同一道闸门）
        Set<String> ids = TianfuApply.effective(player);
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        int tizhi = (int) Math.round(v.tizhi);

        double health = 1.0;
        double attack = 1.0;
        double speed = 1.0;

        if (tizhi == 30) {                 // 苍木回春体：生命上限 +10%
            health *= 1.10;
        }
        if (tizhi == 28) {                 // 搬山撼岳体：攻击随力道道痕提升（每千 +12%）
            attack *= 1.0 + TianfuDaohen.scale(v.daohen_lidao, 0.12);
        }
        if (tizhi == 21 || tizhi == 39) {  // 长风破浪体 / 如影随形体：移速 +8%
            speed *= 1.08;
        }
        if (ids.contains("shen_bumie_jinshen")) {  // 神·不灭金身：生命上限 +100%（翻倍）
            health *= 2.0;
        }
        if (ids.contains("jingu_qiangjian")) {     // 筋骨强健：生命上限 +10%（原 +1 心，按基础 20 点折算）
            health *= 1.10;
        }

        // 扩展词条表（{@link TianfuTalents}）的属性类原语：与此处既有加成同乘区叠加
        TianfuTalents.Attrs talent = TianfuTalents.attrs(ids);
        health *= talent.healthMult();
        attack *= talent.attackMult();
        speed *= talent.speedMult();

        apply(player, Attributes.MAX_HEALTH, HEALTH_MULT, health - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, Attributes.ATTACK_DAMAGE, ATTACK_MULT, attack - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, Attributes.MOVEMENT_SPEED, SPEED_MULT, speed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MULT,
                talent.attackSpeedMult() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // 固定值类（ADD_VALUE）：跳跃强度 / 击退抗性 / 安全摔落距离
        apply(player, Attributes.JUMP_STRENGTH, JUMP_FLAT, talent.jumpFlat(), AttributeModifier.Operation.ADD_VALUE);
        apply(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_FLAT,
                talent.knockbackFlat(), AttributeModifier.Operation.ADD_VALUE);
        apply(player, Attributes.SAFE_FALL_DISTANCE, FALL_FLAT, talent.fallFlat(), AttributeModifier.Operation.ADD_VALUE);
    }

    private static void apply(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        AttributeModifier modifier = new AttributeModifier(id, amount, operation);
        if (existing == null) {
            instance.addTransientModifier(modifier);
        } else {
            instance.addOrUpdateTransientModifier(modifier);
        }
    }
}
