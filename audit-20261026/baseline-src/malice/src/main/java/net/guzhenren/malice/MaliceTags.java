package net.guzhenren.malice;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 本体怪物的分档标签。
 *
 * <p>全部挂在 {@code guzhenren} 命名空间下，文件位于
 * {@code data/guzhenren/tags/entity_type/malice_*.json}，跟本体自带的实体标签同域，
 * 因此本体更新时这些标签不会因为命名空间变化而失效。
 *
 * <p>刻意<b>不</b>使用 {@code Monster} 判类：本体 450 个实体里只有 7 个继承
 * {@code net.minecraft.world.entity.monster.Monster}，绝大多数妖兽是
 * {@code TamableAnimal}/{@code PathfinderMob}，按类判会漏掉 90% 的怪。
 */
public final class MaliceTags {

    /** 普通妖兽。 */
    public static final TagKey<EntityType<?>> NORMAL = key("malice_normal");
    /** 精英妖兽。 */
    public static final TagKey<EntityType<?>> ELITE = key("malice_elite");
    /** 妖王 / BOSS。 */
    public static final TagKey<EntityType<?>> BOSS = key("malice_boss");
    /** 剧情 NPC、商人、术法特效实体：完全不让恶意接管。 */
    public static final TagKey<EntityType<?>> EXCLUDE = key("malice_exclude");

    /**
     * 恶意自己的接入白名单（{@code data/l2hostility/tags/entity_type/whitelist.json}）。
     *
     * <p><b>这是「怪头上没有等级」的真正原因</b>。恶意 3.0.18 的挂载条件是：
     * <pre>
     * isProper(e) = type.is(l2hostility:whitelist)
     *            || (e instanceof net.minecraft.world.entity.monster.Enemy &amp;&amp; !type.is(l2hostility:blacklist))
     * </pre>
     * 而本体 450 个实体里只有 7 个继承 {@code Monster}（也就是 {@code Enemy}），
     * 其余妖兽全是 {@code TamableAnimal}/{@code PathfinderMob} —— 它们既不在白名单、
     * 也不是 Enemy，于是恶意<b>根本不挂 MobTraitCap</b>：不分配等级、不抽词条、
     * 头顶自然什么都不显示。把本体的 91 只妖兽写进这个标签，恶意才会接管它们。
     */
    public static final TagKey<EntityType<?>> L2_WHITELIST =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("l2hostility", "whitelist"));

    private MaliceTags() {
    }

    private static TagKey<EntityType<?>> key(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("guzhenren", path));
    }

    /** 是否是被我们接管的三档怪物之一。 */
    public static boolean isTiered(EntityType<?> type) {
        return type.is(NORMAL) || type.is(ELITE) || type.is(BOSS);
    }
}
