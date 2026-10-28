package net.guzhenren.malice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /guzhenren_malice ...} 命令。
 *
 * <p>权限 0：单人档里玩家自己就能调，符合「玩家可以更改难度」的要求；
 * 服务器上不希望玩家乱改，把两处 {@code requires} 的值调成 2 即可。
 */
public final class MaliceCommands {

    /** 恶意给自己加的血量修饰符 id：{@code l2hostility:hostility_health}。 */
    private static final ResourceLocation L2_HEALTH_MOD =
            ResourceLocation.fromNamespaceAndPath("l2hostility", "hostility_health");

    private static final SuggestionProvider<CommandSourceStack> PRESETS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(Difficulty.names(), builder);

    private MaliceCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("guzhenren_malice")
                .then(Commands.literal("info").executes(MaliceCommands::info))
                .then(Commands.literal("choose").executes(MaliceCommands::choose))
                .then(Commands.literal("probe")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(MaliceCommands::probe)))
                .then(Commands.literal("difficulty")
                        .executes(MaliceCommands::info)
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(PRESETS)
                                .executes(MaliceCommands::set))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Difficulty d = DifficultyState.get(server).current();
        ctx.getSource().sendSuccess(() -> Component.literal("蛊真人 × 恶意：当前难度 ")
                .append(Component.literal("「" + d.cn() + "」").withStyle(d.color()))
                .append(Component.literal("（怪物恶意等级 ×" + d.levelMul() + "）")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("可切换：安逸 / 标准 / 凶险 / 噩梦 —— /guzhenren_malice difficulty <档位>"), false);
        GuzhenrenVersion ver = GuzhenrenVersion.detect();
        ctx.getSource().sendSuccess(() -> Component.literal("本体版本：")
                .append(Component.literal(ver.label()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" —— " + ver.note()).withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("查某只怪有没有吃到恶意：/guzhenren_malice probe <实体>"), false);
        return 1;
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        MinecraftServer server = ctx.getSource().getServer();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();

        String tier;
        double healthScale;
        if (target.getType().is(MaliceTags.BOSS)) {
            tier = "妖王";
            healthScale = 2.5;
        } else if (target.getType().is(MaliceTags.ELITE)) {
            tier = "精英";
            healthScale = 2.0;
        } else if (target.getType().is(MaliceTags.NORMAL)) {
            tier = "普通";
            healthScale = 1.5;
        } else if (target.getType().is(MaliceTags.EXCLUDE)) {
            tier = "排除（NPC/特效，恶意不会接管）";
            healthScale = 0;
        } else {
            tier = "未收录（按恶意默认规则走）";
            healthScale = 0;
        }

        boolean whitelisted = target.getType().is(MaliceTags.L2_WHITELIST);
        Difficulty diff = DifficultyState.get(server).current();

        ctx.getSource().sendSuccess(() -> Component.literal("—— " + id + " ——").withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("分档：" + tier
                + " ｜ 恶意白名单：" + (whitelisted ? "是" : "否")), false);

        if (!(target instanceof LivingEntity living)) {
            ctx.getSource().sendSuccess(() -> Component.literal("不是生物实体，恶意不会碰它。"), false);
            return 0;
        }

        double max = living.getMaxHealth();
        AttributeInstance inst = living.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier mod = inst == null ? null : inst.getModifier(L2_HEALTH_MOD);

        if (mod == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("恶意血量修饰符：无 —— 这只怪**没被恶意接管**（等级 0，头顶不显示）")
                    .withStyle(ChatFormatting.RED), false);
            ctx.getSource().sendSuccess(() -> Component.literal("最大生命 " + String.format("%.1f", max)
                    + "（本体原值；若它刚刷新还没 tick，过一秒再测一次）").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        // 恶意的血量加成 = 等级 × 0.03 × healthScale，反推等级只能给近似值
        double amount = mod.amount();
        double scale = healthScale > 0 ? healthScale : 1.0;
        int estLevel = (int) Math.round(amount / (0.03 * scale));

        ctx.getSource().sendSuccess(() -> Component.literal("恶意血量修饰符：+" + String.format("%.2f", amount)
                + "（×" + String.format("%.2f", 1 + amount) + "）")
                .withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("最大生命 " + String.format("%.1f", max)
                + " ｜ 反推恶意等级 ≈ Lv." + estLevel + "（难度倍率 ×" + diff.levelMul() + " 已计入）"), false);

        var data = target.getPersistentData();
        if (data.contains("GuzhenrenMaliceBase")) {
            int baseLv = data.getInt("GuzhenrenMaliceBase");
            int appliedLv = data.getInt("GuzhenrenMaliceApplied");
            ctx.getSource().sendSuccess(() -> Component.literal("记录：恶意原始等级 Lv." + baseLv
                    + " → 按难度 ×" + diff.levelMul() + " 结算为 Lv." + appliedLv)
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MaliceWelcome.sendChooser(player, DifficultyState.get(ctx.getSource().getServer()));
        return 1;
    }

    /**
     * 谁能改难度：单人存档任何人，专用服务器需要 OP（等级 2）。
     *
     * <p>难度是<b>整张存档共享</b>的，权限 0 会让任何玩家都能把别人的世界切成噩梦，
     * 所以这里按服务器类型分流。
     */
    private static boolean canChange(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        return server != null && (server.isSingleplayer() || src.hasPermission(2));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        if (!canChange(ctx.getSource())) {
            ctx.getSource().sendFailure(Component.literal("难度是全存档共享的，专用服务器上只有 OP 能改（权限等级 2）。"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "preset");
        Difficulty d = Difficulty.parse(raw);
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("未知档位：" + raw + "（可选 easy/normal/hard/nightmare 或 安逸/标准/凶险/噩梦）"));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        DifficultyState state = DifficultyState.get(server);
        state.set(d);
        server.getPlayerList().broadcastSystemMessage(Component.literal("难度已切换为 ")
                .append(Component.literal("「" + d.cn() + "」").withStyle(d.color()))
                .append(Component.literal("：新刷出的蛊界妖兽按等级 ×" + d.levelMul() + " 结算")), false);
        return 1;
    }
}
