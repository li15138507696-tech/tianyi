// src/main/java/net/guzhenren/tianfu/v2/TianfuV2Registration.java
package net.guzhenren.tianfu.v2;

import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.net.TianfuOriginPayload;
import net.guzhenren.tianfu.net.TianfuV2StatePayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 新增协议注册补充入口。旧主类不在修改授权列表，故保留其全部既有注册。
 * 此类仅完成MOD总线网络注册，不承载事件业务监听逻辑；旧通道版本1不改。
 */
@EventBusSubscriber(modid = TianfuMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TianfuV2Registration {
    private TianfuV2Registration() { }
    /** 将新增独立V2通道注册至NeoForge；不更改旧record构造器及其编码。 */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(TianfuOriginPayload.TYPE, TianfuOriginPayload.STREAM_CODEC, TianfuOriginPayload::handle);
        registrar.playToClient(TianfuV2StatePayload.TYPE, TianfuV2StatePayload.STREAM_CODEC, TianfuV2StatePayload::handle);
    }
}
