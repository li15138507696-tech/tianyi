package net.guzhenren.malice;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 蛊真人 × 莱特兰恶意 的适配附属。
 *
 * <p>本 mod 不做两件事之外的任何事情：
 * <ol>
 *   <li>把 {@code data/guzhenren/l2hostility_config/entity/guzhenren_malice.json}
 *       里的三档怪物配置交给恶意（等级下限、血量/攻击倍率、词条白名单）；</li>
 *   <li>在恶意分配完等级之后，按玩家选的难度档位再乘一次等级。</li>
 * </ol>
 * 不改本体任何基础属性，也不改本体任何一个类。
 */
@Mod(GuzhenrenMalice.MODID)
public class GuzhenrenMalice {

    public static final String MODID = "guzhenren_malice";
    public static final Logger LOGGER = LoggerFactory.getLogger("guzhenren_malice");

    public GuzhenrenMalice(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(MaliceEvents.class);
        NeoForge.EVENT_BUS.register(MaliceCommands.class);
        NeoForge.EVENT_BUS.register(MaliceDiagnostics.class);
        NeoForge.EVENT_BUS.register(MaliceWelcome.class);
        LOGGER.info("[蛊真人×恶意] 适配已加载：三档怪物 + 四档可切换难度");
    }
}
