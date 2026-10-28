// Path: src/main/java/net/guzhenren/daotu/DaotuMod.java
package net.guzhenren.daotu;

import net.guzhenren.daotu.net.DaotuMainDaoPayload;
import net.guzhenren.daotu.net.DaotuPickPayload;
import net.guzhenren.daotu.net.DaotuRequestPayload;
import net.guzhenren.daotu.net.DaotuSettingPayload;
import net.guzhenren.daotu.net.DaotuStatePayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 蛊真人附属「道途」入口。
 *
 * <p>与「天命」（tianfu）的分工：
 * 天命管<b>开局你是谁</b>（体质 + 天赋词条 + 机缘），道途管<b>后来你成了谁</b>
 * （三路历练 → 攒满抽选 → 5 选 1 永久锁定）。
 * 两者数据各自放在 PersistentData 的 {@code tianfu} / {@code daotu} 子标签里，
 * 本体字段的写入<b>并非完全不重叠</b>，可同装的前提是「双方都按加算语义写」：
 * <ul>
 *   <li>{@code daohen_*} / {@code dahen_*}：天命与道途都是 {@code +=}，兼容；</li>
 *   <li>{@code liupai_*}：道途写 {@code before + amount}（同 tick 内等价于 {@code +=}），
 *       且已加下限 0；</li>
 *   <li>{@code shenfen}：只有道途写，本体与天命都不写；</li>
 *   <li>{@code qiyun_shangxian}：天命「天弃之命」会每 tick 归零，道途发放前用
 *       {@link DaotuTianfuCompat#luckForfeit} 跳过，避免对冲；</li>
 *   <li>{@code kongqiao}：道途不写；天命「空窍桎梏」锁定时通过
 *       {@link DaotuTianfuCompat#aptitudeShackled} 只读探测。</li>
 * </ul>
 *
 * <p>弹窗时机：
 * <ul>
 *   <li><b>开窍时</b>不弹窗——天命已在 {@code UikongqiaoguanbiProcedure} 的 TAIL 注入并
 *       自动 {@code setScreen}，两个附属同时弹会互相覆盖；道途只发一条聊天提示。</li>
 *   <li><b>攒满历练时</b>按玩家开关决定（v1.4.1）：默认弹窗；关掉后只推状态 + 一条
 *       聊天提示，玩家按 M 自己打开。开关在界面页脚，值存 {@code daotu/autoPrompt}。</li>
 * </ul>
 */
@Mod(DaotuMod.MODID)
public class DaotuMod {
    public static final String MODID = "daotu";
    public static final Logger LOGGER = LoggerFactory.getLogger("daotu");

    public DaotuMod(IEventBus modBus, ModContainer container) {
        modBus.addListener(DaotuMod::registerPayloads);
        NeoForge.EVENT_BUS.register(DaotuEvents.class);
        var chronicleRepository = new net.guzhenren.daotu.chronicle.NbtChronicleRepository();
        var chronicle = new net.guzhenren.daotu.chronicle.ChronicleCoordinator(
                new net.guzhenren.daotu.chronicle.Core1020Adapter(), chronicleRepository,
                java.util.List.of(new net.guzhenren.daotu.chronicle.FactionJournalModule(),
                        new net.guzhenren.daotu.chronicle.MeritArchiveModule(),
                        new net.guzhenren.daotu.chronicle.ReputationJournalModule()));
        NeoForge.EVENT_BUS.register(new net.guzhenren.daotu.chronicle.ChronicleEvents(
                chronicle, chronicleRepository));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                        event.getDispatcher().register(net.minecraft.commands.Commands
                                .literal("daotu-chronicle")
                                .executes(context -> chronicle.show(context.getSource()))));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // v1.5.0：状态包加了 mainDao / drawCap 字段，协议号跟着升，
        // 避免新旧端混用时解码错位（v1.4.1 已因加 autoPrompt 升过一次）。
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(DaotuStatePayload.TYPE, DaotuStatePayload.STREAM_CODEC,
                DaotuStatePayload::handle);
        registrar.playToServer(DaotuRequestPayload.TYPE, DaotuRequestPayload.STREAM_CODEC,
                DaotuRequestPayload::handle);
        registrar.playToServer(DaotuPickPayload.TYPE, DaotuPickPayload.STREAM_CODEC,
                DaotuPickPayload::handle);
        registrar.playToServer(DaotuSettingPayload.TYPE, DaotuSettingPayload.STREAM_CODEC,
                DaotuSettingPayload::handle);
        // v1.5.0：设定主修流派（决定特殊奖励发什么）
        registrar.playToServer(DaotuMainDaoPayload.TYPE, DaotuMainDaoPayload.STREAM_CODEC,
                DaotuMainDaoPayload::handle);
    }
}
