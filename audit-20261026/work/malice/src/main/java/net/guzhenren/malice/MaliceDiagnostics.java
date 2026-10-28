package net.guzhenren.malice;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 启动自检：把「数据包到底有没有加载」这件事写进日志。
 *
 * <p>标签是运行时数据，加载失败时 {@code type.is(tag)} 恒为 false，
 * 表现就是「怪头上没有等级、也没有词条」，跟没装 mod 一模一样。
 * 这里在服务器起来时把四个标签和白名单的真实条数打进日志，
 * 一眼就能看出是「配置没生效」还是「怪本来就该没等级」。
 */
public final class MaliceDiagnostics {

    private MaliceDiagnostics() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Registry<EntityType<?>> registry = server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        int normal = size(registry, MaliceTags.NORMAL);
        int elite = size(registry, MaliceTags.ELITE);
        int boss = size(registry, MaliceTags.BOSS);
        int exclude = size(registry, MaliceTags.EXCLUDE);
        int whitelist = size(registry, MaliceTags.L2_WHITELIST);
        Difficulty diff = DifficultyState.get(server).current();

        GuzhenrenVersion version = GuzhenrenVersion.detect();
        GuzhenrenMalice.LOGGER.info("[蛊真人×恶意] 数据检查: 普通={} 精英={} 妖王={} 排除={} ｜ l2hostility:whitelist={} ｜ 当前难度={}(×{})",
                normal, elite, boss, exclude, whitelist, diff.cn(), diff.levelMul());
        GuzhenrenMalice.LOGGER.info("[蛊真人×恶意] 识别到本体 {} —— {}", version.label(), version.note());

        String packed = packedVariant();
        if (packed != null && !packed.equals(version.key())) {
            GuzhenrenMalice.LOGGER.warn("[蛊真人×恶意] 装错包了：这个 jar 是给本体 {} 用的，实际本体是 {}。"
                    + "请换成 guzhenren_malice-<版本>-mc{} 那个包。", packed, version.label(), version.key());
        }

        if (normal + elite + boss == 0) {
            GuzhenrenMalice.LOGGER.error("[蛊真人×恶意] 三个分档标签一条都没解析出来：本体没装，或数据包没加载。");
        }
        if (whitelist == 0) {
            GuzhenrenMalice.LOGGER.error("[蛊真人×恶意] l2hostility:whitelist 为空：恶意的 MobTraitCap 不会挂到本体妖兽上，"
                    + "怪头顶不会显示等级。检查 data/l2hostility/tags/entity_type/whitelist.json 是否被别的数据包 replace 掉了。");
        }
    }

    /** 读 jar 内的版本标记（malice_variant.txt），没有则返回 null。 */
    private static String packedVariant() {
        try (var in = MaliceDiagnostics.class.getResourceAsStream("/malice_variant.txt")) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int size(Registry<EntityType<?>> registry, TagKey<EntityType<?>> tag) {
        return registry.getTag(tag).map(HolderSet.Named::size).orElse(-1);
    }
}
