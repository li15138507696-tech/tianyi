package net.guzhenren.tianyi.item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 天意自己的物品注册（命名空间 {@code tianyi:}）。
 *
 * <h2>为什么单独开门，不用本体命名空间</h2>
 * 本体（{@code guzhenren}）jar 里没有宿命蛊的实物或类，所以由天意自建承载物。
 * 用 {@code tianyi:} 命名空间是明示「这是天意加的东西」，<b>不冒充本体注册项</b>。
 *
 * <h2>只注册一枚，且不进创造模式物品栏</h2>
 * 宿命蛊不是能随便拿的东西：它只能通过一次受控的世界级事务实体化
 * （{@code /tianyi destiny custody recover <note>}）。这里不挂 CreativeModeTab，
 * 免得「创造模式里有一格」变成事实上的自动发物品。
 */
public final class TianyiItems {
    /** 命名空间：与 {@code TianyiMod.MODID} 一致，不随本体走。 */
    public static final String NS = "tianyi";
    /** 注册 id（{@code tianyi:suminggu}）。改这个等于换物品，旧存档会认不出。 */
    public static final String SUMINGGU_ID = "suminggu";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NS);

    /** 宿命蛊：不可堆叠（{@code stacksTo(1)}）。 */
    public static final DeferredItem<net.minecraft.world.item.Item> SUMINGGU =
            ITEMS.register(SUMINGGU_ID, () -> new DestinyGuItem(DestinyGuItem.properties()));

    private TianyiItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** 新造一枚空壳（不带凭证）。凭证必须由服务端账本盖上去。 */
    public static net.minecraft.world.item.ItemStack newVessel() {
        return new net.minecraft.world.item.ItemStack(SUMINGGU.get());
    }
}
