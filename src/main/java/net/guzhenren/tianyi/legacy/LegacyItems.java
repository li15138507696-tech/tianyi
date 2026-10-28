package net.guzhenren.tianyi.legacy;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 旧「神之商店」物品的**空壳注册**。
 *
 * <h2>为什么删了商店还要留两个物品</h2>
 * {@code shenshang:yuanshengu} / {@code shenshang:yuanshengu_gu_fang} 会出现在老存档的物品栏、
 * 蛊虫箱、元老蛊、汤姆存储里。直接把注册删掉，这些物品会变成「不存在的 id」——
 * 进存档时报 unknown item，严重时背包/容器操作抛异常（物品实体还会复制或消失）。
 *
 * <p>所以保留**同名同 id 的极简 Item**：老存档里它们还在、还能被拿起来放下，
 * 只是不再有「10 亿元石钱包 / 炼蛊柜蛊方槽」的功能。
 * 这比丢数据好，也比继续维护整套商店代码便宜。
 *
 * <h2>命名空间为什么是 shenshang 而不是 tianyi</h2>
 * 存档里的物品 id 是 {@code shenshang:yuanshengu}。改命名空间等于换物品，老存档照样丢东西。
 * 所以这里刻意保留旧命名空间（与 {@code tianfu:} 的 18 个 mob effect、{@code shenshang:} 的
 * 粒子一样，都属于「注册名不动」的兼容口径）。
 */
public final class LegacyItems {
    /** 旧商店的命名空间，故意不改。 */
    public static final String OLD_NS = "shenshang";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OLD_NS);

    /** 元神蛊（原大容量元石钱包，现为纪念品）。 */
    public static final DeferredItem<Item> YUAN_SHEN_GU =
            ITEMS.register("yuanshengu", () -> new Item(new Item.Properties().stacksTo(1)));

    /** 元神蛊-蛊方（原炼蛊柜蛊方槽用，现为纪念品）。 */
    public static final DeferredItem<Item> YUAN_SHEN_GU_GU_FANG =
            ITEMS.register("yuanshengu_gu_fang", () -> new Item(new Item.Properties().stacksTo(1)));

    private LegacyItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
