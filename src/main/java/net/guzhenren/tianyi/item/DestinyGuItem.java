package net.guzhenren.tianyi.item;

import java.util.List;

import net.guzhenren.tianyi.will.DestinyVessel;
import net.guzhenren.tianyi.world.DestinyCustodyService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/**
 * 宿命蛊的<b>承载物</b>（{@code tianyi:suminggu}）：黑白蜘蛛，不可堆叠。
 *
 * <h2>它只是壳，不是身份</h2>
 * 物品里只放四样<b>自称</b>的凭证（见 {@link DestinyVessel}），真伪由服务端世界账本
 * {@code DestinyCustody.validate} 判定。创造模式复制、整段 NBT 克隆、旧包重放
 * 都能造出一模一样的壳——所以「拿在手里的是不是正品」这件事，
 * <b>客户端永远不该、也不能自己回答</b>。
 *
 * <h2>刻意不做的事</h2>
 * <ul>
 *   <li>没有命败或通用右键技能；只有在铁砧处按修复仪式投入材料会推进修复进度。</li>
 *   <li><b>不伪造「已炼化仙蛊」</b>：没有「催动/发动/生效」这类方法，
 *       也从不向下发任何世界账本结论。</li>
 *   <li><b>不写玩家背包</b>：它被放在哪里由管理员命令落物决定（落进世界里）。</li>
 * </ul>
 *
 * <h2>受损外观</h2>
 * 受损/待核验/隔离时，服务端在核对时写一个 {@link DestinyVessel#K_SCARRED} 标记，
 * 提示文案随之出现「赤红伤痕」，物品模型也切换到对应的受损贴图。
 * 这只用于展示，不改任何规则，也不宣称本体状态。
 */
public class DestinyGuItem extends Item {
    public DestinyGuItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        DestinyVessel.Vessel v = DestinyCustodyService.read(stack);
        if (!v.stamped()) {
            tooltip.add(Component.translatable("item.tianyi.suminggu.tip.empty"));
            return;
        }
        // 只复述物品自己写的东西：代次与保管版本。不下发「它是不是正品」。
        tooltip.add(Component.translatable("item.tianyi.suminggu.tip.claim",
                v.generation(), v.revision()));
        if (isScarred(stack)) {
            tooltip.add(Component.translatable("item.tianyi.suminggu.tip.scarred"));
            tooltip.add(Component.translatable("item.tianyi.suminggu.tip.repair"));
        }
        String ledger = ledgerState(stack);
        if (!ledger.isBlank()) {
            tooltip.add(Component.translatable("item.tianyi.suminggu.tip.ledger", ledger));
        }
        tooltip.add(Component.translatable("item.tianyi.suminggu.tip.verify"));
    }

    /**
     * 掉在地上的载体遭遇火/岩浆/虚空时，只记「载体丢失待核验」。
     *
     * <p>返回 {@code false} = 不销毁、不拦住任何原版流程；本方法纯记录。
     * 是否需要记录由 {@link DestinyCustodyService#carrierLostFrom} 再核一次：
     * 只有<b>与账本一致</b>的那一枚烧掉了才算丢失，烧掉复制品不算。
     *
     * <p>这是 NeoForge 的物品扩展钩子，签名与 {@code ItemStack.onEntityItemUpdate}
     * 对应；<b>刻意不写 {@code @Override}</b>，这样即使扩展接口没被挂到 Item 上，
     * 也只是退化成「不记录」，不会让整个构建挂掉。
     */
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity == null || entity.level() == null || entity.level().isClientSide()) {
            return false;
        }
        String reason = null;
        if (entity.isInLava()) {
            reason = "lava";
        } else if (entity.isOnFire()) {
            reason = "fire";
        } else if (entity.getY() < entity.level().getMinBuildHeight()) {
            reason = "void";
        }
        if (reason != null) {
            DestinyCustodyService.carrierLostFrom(entity, reason);
        }
        return false;
    }

    /** 客户端模型与提示共用的只读受损标记；不代表账本真伪。 */
    public static boolean isScarred(ItemStack stack) {
        return customTag(stack).getBoolean(DestinyVessel.K_SCARRED);
    }

    private static String ledgerState(ItemStack stack) {
        return customTag(stack).getString(DestinyVessel.K_LEDGER);
    }

    private static net.minecraft.nbt.CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** 管理员命令/诊断用：这一枚壳是否「有凭证」。 */
    public static boolean stamped(ItemStack stack) {
        return DestinyCustodyService.read(stack).stamped();
    }

    /** 物品的默认属性：不可堆叠、稀有。放在这里，注册处只引用一次。 */
    public static Properties properties() {
        return new Properties().stacksTo(1).rarity(Rarity.RARE);
    }
}
