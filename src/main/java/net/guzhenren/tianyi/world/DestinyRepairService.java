package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.guzhenren.tianyi.will.Destiny;
import net.guzhenren.tianyi.will.DestinyVessel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Server-authoritative anvil ritual for repairing the world's unique Destiny Gu. */
public final class DestinyRepairService {
    private static final ResourceLocation GU_MATERIAL_ID =
            ResourceLocation.fromNamespaceAndPath("guzhenren", "gucaiyuanshi");
    private static final int MATERIALS_PER_OFFERING = 4;
    private static final double PROGRESS_PER_OFFERING = 0.25;

    private DestinyRepairService() {
    }

    /**
     * Handles the interaction only when the main hand holds a Destiny Gu and the target is an anvil.
     * A handled attempt is consumed even when its requirements fail, so vanilla does not open an anvil UI.
     */
    public static boolean interact(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (player == null || pos == null || hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!DestinyCustodyService.isVessel(held) || !isAnvil(player.level().getBlockState(pos))) {
            return false;
        }
        contribute(player, held);
        return true;
    }

    private static void contribute(ServerPlayer player, ItemStack held) {
        WillState world = WillState.get(player.getServer());
        Destiny.DestinyGu destiny = world.core().destiny();
        if (destiny.stage() != Destiny.Stage.BODY_DAMAGED) {
            tell(player, destiny.stage() == Destiny.Stage.BODY_WHOLE
                    ? "宿命蛊已经修复完整。"
                    : "宿命蛊当前阶段不能进行修复。阶段：" + destiny.stage());
            return;
        }

        DestinyCustodyState ledger = DestinyCustodyService.state(player.getServer());
        if (!ledger.mutationReady()) {
            tell(player, "宿命蛊保管账本不可修改，修复已停止：" + ledger.failureReason());
            return;
        }
        if (ledger.custody().quarantined()) {
            tell(player, "宿命蛊正在隔离核验，不能投入修复材料。");
            return;
        }

        List<ItemStack> observed = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (DestinyCustodyService.isVessel(stack)) {
                observed.add(stack);
            }
        }
        DestinyVessel.Finding finding = DestinyCustodyService.reconcile(
                player, ledger, observed, "铁砧修复");
        if (finding.look() != DestinyVessel.Look.AUTHENTIC || finding.authentic() == null
                || !DestinyVessel.sameCredential(DestinyCustodyService.read(held), finding.authentic())) {
            tell(player, "手持载体未通过世界账本核验，未扣除材料：" + finding.describe());
            return;
        }

        if (destiny.repairProgress() >= 1.0) {
            tell(player, "修复进度已满但阶段尚未完成，请管理员检查世界数据；材料未扣除。");
            return;
        }

        Item guMaterial = BuiltInRegistries.ITEM.get(GU_MATERIAL_ID);
        if (guMaterial == Items.AIR) {
            tell(player, "蛊材元石未注册，无法开始修复；材料未扣除。");
            return;
        }

        boolean creative = player.getAbilities().instabuild;
        if (!creative && (!has(player, guMaterial, MATERIALS_PER_OFFERING)
                || !has(player, Items.AMETHYST_SHARD, MATERIALS_PER_OFFERING))) {
            tell(player, "材料不足：每次需要蛊材元石 ×4、紫水晶碎片 ×4；材料未扣除。");
            return;
        }

        if (!creative) {
            consume(player, guMaterial, MATERIALS_PER_OFFERING);
            consume(player, Items.AMETHYST_SHARD, MATERIALS_PER_OFFERING);
        }

        long now = player.serverLevel().getGameTime();
        double next = Math.min(1.0, destiny.repairProgress() + PROGRESS_PER_OFFERING);
        destiny.setRepairProgress(next, now, "anvil-repair:" + player.getGameProfile().getName());
        if (next >= 1.0) {
            String txnId = "anvil-repair:" + UUID.randomUUID();
            Destiny.Stage completed = destiny.completeRepair(txnId, now,
                    "anvil-repair:" + player.getGameProfile().getName());
            if (completed != Destiny.Stage.BODY_WHOLE) {
                world.setDirty();
                tell(player, "材料已投入，但完成事务未能推进阶段；请管理员检查世界数据。");
                return;
            }
            DestinyCustodyService.onPickup(player);
            tell(player, "修复仪式完成：宿命蛊已恢复完整，赤痕消散。");
        } else {
            int percent = (int) Math.round(next * 100.0);
            int remaining = (int) Math.ceil((1.0 - next) / PROGRESS_PER_OFFERING);
            tell(player, "修复供料完成：" + percent + "%；还需 " + remaining + " 次。"
                    + (creative ? "（创造模式未扣除材料）" : ""));
        }
        world.setDirty();
    }

    private static boolean isAnvil(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL);
    }

    private static boolean has(ServerPlayer player, Item item, int count) {
        int found = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
                if (found >= count) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Called only after both ingredient counts have already been checked. */
    private static void consume(ServerPlayer player, Item item, int count) {
        var inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
    }

    /**
     * 世界层不回话：聊天栏消息只允许由命令层发出（audit_will 的既有硬约束）。
     * 这里写服务端日志，保证修复过程可追溯；玩家可见的结果由命令层回执。
     */
    private static void tell(ServerPlayer player, String text) {
        net.guzhenren.tianyi.TianyiMod.LOGGER.info("[天意·修复] {} {}",
                player == null ? "console" : player.getGameProfile().getName(), text);
    }
}
