package net.guzhenren.tianyi;

import net.guzhenren.tianyi.world.WillServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 蛊真人「天意」—— 唯一入口。
 *
 * <h2>3.0.0：天意是世界背后的意志，不是玩家面板</h2>
 * <ul>
 *   <li>{@code will/} —— 纯逻辑核心（不 import 任何 Minecraft 类型）：
 *       知识边界、格局评估、决策、布局、机缘、灾劫、仙道、宿命、载体、反制、回溯。
 *       可离线单测（{@code _pack/run_tests.py}）。</li>
 *   <li>{@code world/} —— Minecraft 接线：服务端权威调度、观测、执行、
 *       快照、旧数据归档。</li>
 * </ul>
 *
 * <h2>普通玩家看不到什么</h2>
 * 不公开后台好感度、威胁评分或干预清单。3.9.0 机缘独立显示明示奖惩，J 打开玩家见闻录。
 * 玩家只能通过世界本身感知天意：资源在野地里成熟、兽群迁徙、有人上门、
 * 天象异变、灾劫临头。天意的内部判定只进服务端日志与内部因果记录。
 */
@Mod(TianyiMod.MODID)
public class TianyiMod {
    public static final String MODID = "tianyi";
    public static final Logger LOGGER = LoggerFactory.getLogger("tianyi");

    /** 全模组唯一的服务端调度器。 */
    public static final WillServer SERVER = new WillServer(LOGGER);

    public TianyiMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                net.guzhenren.tianyi.world.EncounterSettings.SPEC, "tianyi-encounters-server.toml");
        // 有限资源点（3.11）：默认全 false，配置启用且模板运行时核验通过才会生成。
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                net.guzhenren.tianyi.world.SiteSettings.SPEC,
                net.guzhenren.tianyi.world.SiteSettings.CONFIG_FILE);
        // 天意强度（3.14）：只有一个 intensity（0 关闭 / 1 温和（默认）/ 2 严厉 / 3 天倾）。
        // 其余倍率与阈值都由纯逻辑 WillForce 按档位推导，不另存一份配置。
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                net.guzhenren.tianyi.world.WillForceSettings.SPEC,
                net.guzhenren.tianyi.world.WillForceSettings.CONFIG_FILE);
        // 3.13.0：旧商店的 shenshang:yuanshengu / yuanshengu_gu_fang 空壳注册已移除。
        // 原因：整合包里若装了旧 shenshang 附属，两边注册同一物品 ID 会在加载时崩溃
        // （Duplicate registration: shenshang:yuanshengu）。天意不再占用别人的命名空间。
        // LegacyItems 类保留但不再注册，旧代码引用它不会受影响。
        // 3.13.0 宿命蛊的承载物 tianyi:suminggu（不可堆叠；只装凭证，真伪问世界账本）
        net.guzhenren.tianyi.item.TianyiItems.register(modBus);

        // 世界级天意：事件总线 + 命令
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(TianyiEvents.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TianyiCommands::register);

        // 《见闻录》/《窥天录》：网络包（双端都要注册；客户端处理类只在客户端被实例化）
        modBus.addListener(net.guzhenren.tianyi.net.JournalNet::registerPayloads);

        // 纯客户端初始化：用 dist 挡住，独立服务器不会加载任何客户端类
        if (net.neoforged.fml.loading.FMLEnvironment.dist
                == net.neoforged.api.distmarker.Dist.CLIENT) {
            net.guzhenren.tianyi.client.JournalClientSetup.init(modBus);
        }

        // 旧附属残留检测：它们与本模组共用注册命名空间，留着只会互相顶掉
        for (String old : new String[]{"daotu", "tianfu", "shenshang"}) {
            if (net.neoforged.fml.ModList.get().isLoaded(old)) {
                LOGGER.error("[天意] 检测到旧附属「{}」仍在加载：请从 mods/ 删除对应的旧 jar"
                        + "（daotu-*.jar / tianfu-*.jar / shenshang-*.jar），"
                        + "否则按键与注册 id 会冲突，游戏可能连启动画面都进不去。", old);
            }
        }
        // 版本号从 mods.toml 读，不要硬编码（3.8.0 之前这里一直写着 3.0.0，日志对不上产物）
        String ver = net.neoforged.fml.ModList.get().getModContainerById(MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("[天意] {} 已加载：世界意志（感知·判断·布局·扶持·压制·灾劫·宿命·回溯）", ver);
        // 3.14：强度档位（0-3）。只打服务端日志，玩家侧没有任何面板/播报。
        LOGGER.info("[天意] 天意强度档位={}（{}）：0 关闭 / 1 温和 / 2 严厉 / 3 天倾；"
                        + "配置 tianyi-force-server.toml",
                net.guzhenren.tianyi.world.WillForceSettings.intensity(),
                net.guzhenren.tianyi.world.WillForceSettings.force().label());
        LOGGER.info("[天意] 见闻录面板：按键 {} → 《见闻录》/《窥天录》",
                "J（可在按键设置·天意 里改绑）");
    }
}
