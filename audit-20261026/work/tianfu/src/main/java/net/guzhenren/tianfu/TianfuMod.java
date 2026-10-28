package net.guzhenren.tianfu;

import net.guzhenren.tianfu.init.TianfuParticleTypes;
import net.guzhenren.tianfu.init.TianfuZhuEffects;
import net.guzhenren.tianfu.net.TianfuBuyPayload;
import net.guzhenren.tianfu.net.TianfuConfirmPayload;
import net.guzhenren.tianfu.net.TianfuDifficultyPayload;
import net.guzhenren.tianfu.net.TianfuRequestPayload;
import net.guzhenren.tianfu.net.TianfuRevokePayload;
import net.guzhenren.tianfu.net.TianfuRollAcceptPayload;
import net.guzhenren.tianfu.net.TianfuRollPayload;
import net.guzhenren.tianfu.net.TianfuStatePayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 蛊真人附属「天赋」入口。
 *
 * <p>自由点来自开局难度（四档），点数与已购天赋永久保留。
 * 本体的十绝体与甲乙丙丁机缘入池，由玩家用点数购买（见 {@link TianfuNative}）。
 * 所有状态存放在玩家 PersistentData 的 {@code tianfu} 子标签中，不占用本体 PlayerVariables 字段。
 */
@Mod(TianfuMod.MODID)
public class TianfuMod {
    public static final String MODID = "tianfu";
    public static final Logger LOGGER = LoggerFactory.getLogger("tianfu");

    public TianfuMod(IEventBus modBus, ModContainer container) {
        TianfuParticleTypes.REGISTRY.register(modBus);
        // 19 个「X烛」效果（每个凡体一个专属流派侵蚀）
        TianfuZhuEffects.REGISTRY.register(modBus);
        modBus.addListener(TianfuMod::registerPayloads);
        NeoForge.EVENT_BUS.register(TianfuEvents.class);
        // 命令注册用方法引用，避免依赖 register(Class) 的静态方法扫描
        NeoForge.EVENT_BUS.addListener(TianfuCommands::register);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TianfuStatePayload.TYPE, TianfuStatePayload.STREAM_CODEC, TianfuStatePayload::handle);
        registrar.playToClient(net.guzhenren.tianfu.net.TianfuVfxPayload.TYPE,
                net.guzhenren.tianfu.net.TianfuVfxPayload.STREAM_CODEC,
                net.guzhenren.tianfu.net.TianfuVfxPayload::handle);
        registrar.playToServer(TianfuBuyPayload.TYPE, TianfuBuyPayload.STREAM_CODEC, TianfuBuyPayload::handle);
        registrar.playToServer(TianfuConfirmPayload.TYPE, TianfuConfirmPayload.STREAM_CODEC, TianfuConfirmPayload::handle);
        registrar.playToServer(TianfuDifficultyPayload.TYPE, TianfuDifficultyPayload.STREAM_CODEC, TianfuDifficultyPayload::handle);
        registrar.playToServer(TianfuRequestPayload.TYPE, TianfuRequestPayload.STREAM_CODEC, TianfuRequestPayload::handle);
        registrar.playToServer(TianfuRevokePayload.TYPE, TianfuRevokePayload.STREAM_CODEC, TianfuRevokePayload::handle);
        // 「命运由天」随机天赋：摇一次 / 接受并锁定
        registrar.playToServer(TianfuRollPayload.TYPE, TianfuRollPayload.STREAM_CODEC, TianfuRollPayload::handle);
        registrar.playToServer(TianfuRollAcceptPayload.TYPE, TianfuRollAcceptPayload.STREAM_CODEC,
                TianfuRollAcceptPayload::handle);
    }
}
