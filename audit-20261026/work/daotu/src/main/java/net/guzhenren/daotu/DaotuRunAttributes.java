package net.guzhenren.daotu;

import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 把 {@code run/attrs} 里的累加值挂成原版属性 modifier。
 *
 * <p><b>每次全量重建</b>：先移除本模组全部 modifier，再按当前值重新挂。
 * 这样重选、转世、离线登录都只需要读一次 {@code run/attrs}，
 * 不需要重放抽选历史，也不怕中途改过数值表。
 *
 * <p><b>绝不 {@code setBaseValue}</b>：那会钳当前血量。modifier 是叠加层，只影响上限计算。
 *
 * <p><b>不用 {@code ARMOR}</b>：本体有 46 个 procedure 用命令
 * {@code /attribute ... generic.armor base set N} 控制护甲（铁皮蛊、铜皮蛊等）。
 * {@code base set} 只改 base、不移除 modifier，我们的加成会与本体蛊虫叠加，
 * 玩家开蛊时白赚护甲，本体卸甲时加成还在。防御改走 {@code ARMOR_TOUGHNESS}
 * 与 {@code MAX_ABSORPTION}，本体对这两个<b>零引用</b>（已扫描确认）。
 */
public final class DaotuRunAttributes {

    private static final String NS = DaotuMod.MODID;

    /** 全部 14 个键，顺序固定（清理时按此遍历）。 */
    public static final String[] ALL_KEYS = {
        "attack_damage", "max_health", "movement_speed", "attack_speed",
        "armor_toughness", "max_absorption", "knockback_resistance",
        "fall_damage_multiplier", "luck", "safe_fall_distance",
        "attack_knockback", "oxygen_bonus", "step_height", "scale",
    };

    private DaotuRunAttributes() {
    }

    /** 键 → 属性。未知键返回 {@code null}。 */
    private static Holder<Attribute> attributeOf(String key) {
        return switch (key) {
            case "attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "max_health" -> Attributes.MAX_HEALTH;
            case "movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "attack_speed" -> Attributes.ATTACK_SPEED;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "max_absorption" -> Attributes.MAX_ABSORPTION;
            case "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "fall_damage_multiplier" -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case "luck" -> Attributes.LUCK;
            case "safe_fall_distance" -> Attributes.SAFE_FALL_DISTANCE;
            case "attack_knockback" -> Attributes.ATTACK_KNOCKBACK;
            case "oxygen_bonus" -> Attributes.OXYGEN_BONUS;
            case "step_height" -> Attributes.STEP_HEIGHT;
            case "scale" -> Attributes.SCALE;
            default -> null;
        };
    }

    /** 哪些键是百分比语义。其余是平加。 */
    public static boolean isPercent(String key) {
        return switch (key) {
            case "attack_damage", "max_health", "movement_speed", "attack_speed", "scale" -> true;
            default -> false;
        };
    }

    /**
     * 累加一个抽选结果。
     *
     * <p>重选时传负 {@code amount} 即可撤销。所有值都受 {@link #capOf} 钳制，
     * 且除 {@code fall_damage_multiplier}（语义上就该是负数）外不低于 0。
     */
    public static void grant(Player player, String key, double amount, boolean percent) {
        if (amount == 0.0) {
            return;
        }
        double cur = DaotuRun.attr(player, key);
        double next = cur + amount;
        double cap = capOf(key);
        if (cap > 0.0 && next > cap) {
            next = cap;
        }
        if (next < 0.0 && !"fall_damage_multiplier".equals(key)) {
            next = 0.0;
        }
        DaotuRun.setAttr(player, key, next);
    }

    /** 软上限；{@code 0} 表示无上限。 */
    public static double capOf(String key) {
        return switch (key) {
            case "knockback_resistance" -> DaotuDrawTable.KNOCKBACK_CAP;
            case "movement_speed" -> DaotuDrawTable.SPEED_CAP;
            case "attack_speed" -> DaotuDrawTable.ATTACK_SPEED_CAP;
            case "max_health" -> DaotuDrawTable.HEALTH_CAP;
            case "scale" -> DaotuDrawTable.SCALE_CAP;
            case "step_height" -> DaotuDrawTable.STEP_CAP;
            default -> 0.0;
        };
    }

    /** 全量重建：先清掉本模组挂过的一切，再按 {@code run/attrs} 重挂。 */
    public static void apply(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        for (String key : ALL_KEYS) {
            Holder<Attribute> attr = attributeOf(key);
            if (attr == null) {
                continue;
            }
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            ResourceLocation rid = id(key);
            if (inst.getModifier(rid) != null) {
                inst.removeModifier(rid);
            }
        }

        Map<String, Double> attrs = DaotuRun.attrs(player);
        for (Map.Entry<String, Double> e : attrs.entrySet()) {
            String key = e.getKey();
            double value = e.getValue();
            // c_ 开头是战斗/回复乘区，由 DaotuCombatEffects 读取，不挂 Attribute
            if (key.startsWith("c_")) {
                continue;
            }
            Holder<Attribute> attr = attributeOf(key);
            if (attr == null) {
                DaotuMod.LOGGER.warn("未知属性键: {}", key);
                continue;
            }
            if (Math.abs(value) <= 1.0e-9) {
                continue;
            }
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            AttributeModifier.Operation op = isPercent(key)
                    ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    : AttributeModifier.Operation.ADD_VALUE;
            inst.addOrUpdateTransientModifier(new AttributeModifier(id(key), value, op));
        }
    }

    private static ResourceLocation id(String key) {
        return ResourceLocation.fromNamespaceAndPath(NS, "run_" + key);
    }
}
