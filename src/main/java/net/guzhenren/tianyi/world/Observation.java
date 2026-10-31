package net.guzhenren.tianyi.world;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.Carrier;
import net.guzhenren.tianyi.will.Knowledge;
import net.guzhenren.tianyi.will.WillConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 观测层：把真实世界折算成天意<b>能看到</b>的样子。
 *
 * <h2>这一层的唯一职责：区分「世界里有」和「天意知道」</h2>
 * 服务器能读到每个玩家的全部变量，但天意只能通过载体触达范围内的接触获得情报。
 * 所以这里对每个玩家先算 {@link Carrier.Network#reachAt}：
 * <ul>
 *   <li>触达够（&gt;0.15）→ 天意真的看到，写入 {@link Knowledge.Store}；</li>
 *   <li>触达不到 / 该处载体被清 / 主体被遮蔽 → <b>什么都不写</b>。下半场决策器只能靠
 *       旧情报推断，且拿不到实时坐标。</li>
 * </ul>
 *
 * <h2>数值来源（全部本体原生字段，javap 核对过）</h2>
 * <pre>
 *   实力 power   = 1 + zhuanshu + Σ daohen ×0.02 + gongjili ×0.01
 *   资源 share   = 1 - 1/(1 + yuanshishuliang/1000)
 *   道德        = daode（原始值，可为负；不是 0..100 的评分类）
 *   气运        = qiyun，上限 qiyun_shangxian（可能为 0，也可能远大于 100）
 *   寿元/压力   = shouyuan / yali（用于仙道与灾劫条件）
 * </pre>
 */
public final class Observation {
    private Observation() {
    }

    /** 天意「看得到」的门槛：低于它就不写情报。 */
    private static final double SEE_FLOOR = 0.15;

    /** 参与实力合成的道痕字段（与本体变量名一致）。 */
    private static final String[] DAOHEN_KEYS = {
            "daohen_lidao", "daohen_jiandao", "daohen_hundao", "daohen_xuedao",
            "daohen_yuedao", "daohen_yudao", "dahen_zhoudao", "dahen_qidao",
            "dahen_liandao", "daohen_shuidao", "daohen_lvdao", "daohen_toudao",
            "dahen_zhidao", "daohen_jindao", "daohen_mudao", "daohen_yandao",
            "daohen_tudao", "daohen_leidao", "daohen_bianhuadao", "daohen_guangdao",
            "daohen_fengdao", "daohen_bingdao", "daohen_bingxuedao", "dahen_nudao",
            "dahen_xingdao", "dahen_tiandao", "daohen_dudao", "daohen_dandao",
    };

    /**
     * 3.14.1：解析过的反射字段（名字 → Field）。
     *
     * <p>{@code Class.getField} 每次调用都要搜索并拷贝一个 Field 对象；一次 {@link #probe} 要读 28 个
     * 道痕字段，决策轮里每个在线玩家、每条布局/灾劫都要 probe 一次。缓存后只剩 {@code Field.get}。
     * 字段集合对同一个类永远稳定，缓存不需要失效。
     */
    private static final Map<String, java.lang.reflect.Field> FIELDS = new HashMap<>();
    /** 确认不存在的字段名：同样只尝试一次，避免反复抛/接异常。 */
    private static final Set<String> MISSING = new HashSet<>();

    /** 一个玩家的实测画像（只读，不写入本体）。 */
    public record Probe(double power, double resources, double morality, double luck,
                        double luckCap, double lifespan, double pressure, int rank,
                        double daohenTotal, double heavenDaoMark, String dim, long pos) {
    }

    /** 读本体变量，算出一个玩家的画像。 */
    public static Probe probe(ServerPlayer player) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double daohen = 0.0;
        for (String key : DAOHEN_KEYS) {
            daohen += field(v, key);
        }
        double power = 1.0 + v.zhuanshu + daohen * 0.02 + Math.max(0.0, v.gongjili) * 0.01;
        double res = 1.0 - 1.0 / (1.0 + Math.max(0.0, v.yuanshishuliang) / 1000.0);
        String dim = player.level().dimension().location().toString();
        return new Probe(power, res, v.daode, v.qiyun, v.qiyun_shangxian,
                v.shouyuan, v.yali, (int) Math.round(v.zhuanshu), daohen,
                Math.max(0.0, v.dahen_tiandao), dim, player.blockPosition().asLong());
    }

    /**
     * 组装一轮决策用的世界快照，并把能看到的写进知识库。
     *
     * @param now 游戏时间（刻），一律 long
     */
    public static Assessment.Situation observe(MinecraftServer server, WillState state, long now) {
        Assessment.Situation s = new Assessment.Situation(now);
        s.channels.addAll(EnumSet.of(Assessment.Channel.RESOURCE, Assessment.Channel.HERITAGE,
                Assessment.Channel.RUMOR, Assessment.Channel.FACTION, Assessment.Channel.BEAST,
                Assessment.Channel.CALAMITY, Assessment.Channel.CARRIER));
        s.heavenControl = state.core().destiny().heavenControl();

        Knowledge.Store k = state.core().knowledge();
        double avgReach = 0.0;
        int samples = 0;

        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            for (ServerPlayer player : level.players()) {
                String id = player.getUUID().toString();
                Probe p = probe(player);
                BlockPos pos = player.blockPosition();
                double reach = state.carriers().reachAt(dim, pos.asLong(), now, 1024.0);
                boolean covered = k.isCovered(id, now);

                boolean observable = reach > SEE_FLOOR && !covered;

                // ---- B02：真实快照与「天意可知的画像」必须分开 ----
                // 以前无论看不看得见，都先把实时 power/resources/morality/luck 写进 Actor，
                // 于是「盲区里的玩家」仍然用真实数值参与份额与威胁计算 —— 那等于天意全知。
                Assessment.Actor actor;
                if (observable) {
                    actor = new Assessment.Actor(id, Assessment.ActorKind.PLAYER, "")
                            .power(p.power())
                            .resources(p.resources())
                            .morality(p.morality())
                            .luck(p.luck(), p.luckCap())
                            .dim(dim)
                            .heavenDaoMark(p.heavenDaoMark() > 0.0);
                } else {
                    // 看不到：只能用知识库里「有来源、有时间」的旧快照；拿不到就不把这个人算进来
                    Knowledge.Track tp = k.track(id, Knowledge.Kind.POWER, now, Double.NaN);
                    if (Double.isNaN(tp.value()) || tp.confidence() <= 0.0) {
                        avgReach += reach;
                        samples++;
                        continue; // 天意根本不知道这个人 → 不参与决策
                    }
                    Knowledge.Track tr = k.track(id, Knowledge.Kind.RESOURCE, now, 0.0);
                    Knowledge.Track tm = k.track(id, Knowledge.Kind.MORALITY, now, 0.0);
                    Knowledge.Track tl = k.track(id, Knowledge.Kind.LUCK, now, 0.0);
                    actor = new Assessment.Actor(id, Assessment.ActorKind.PLAYER, "")
                            .power(tp.value())
                            .resources(Math.max(0.0, tr.value()))
                            .morality(tm.value())
                            // 气运上限未知时按 0 处理 → 气运不参与走向（保守，不瞎猜）
                            .luck(tl.value(), 0.0)
                            .dim(tp.dim().isEmpty() ? "" : tp.dim())
                            .confidence(tp.confidence())
                            .hidden(covered);
                }

                // 增长趋势：与上次观测比较（天意只在看得到时更新基线）
                Double last = state.lastPower().get(id);
                double growth = 0.0;
                if (last != null) {
                    growth = Math.max(0.0, (p.power() - last) / 1.0);
                }

                if (observable) {
                    // 看得到 → 写情报，并更新增长基线
                    k.observe(Knowledge.Fact.observed(id, Knowledge.Kind.POWER, p.power(), now,
                            dim, pos.asLong(), Knowledge.Source.SELF_SPREAD));
                    k.observe(Knowledge.Fact.observed(id, Knowledge.Kind.RESOURCE, p.resources(),
                            now, dim, pos.asLong(), Knowledge.Source.SELF_SPREAD));
                    k.observe(Knowledge.Fact.observed(id, Knowledge.Kind.MORALITY, p.morality(),
                            now, dim, pos.asLong(), Knowledge.Source.CONTACT));
                    k.observe(Knowledge.Fact.observed(id, Knowledge.Kind.LUCK, p.luck(), now,
                            dim, pos.asLong(), Knowledge.Source.CONTACT));
                    k.observe(Knowledge.Fact.observed(id, Knowledge.Kind.POSITION,
                            pos.asLong(), now, dim, pos.asLong(), Knowledge.Source.SELF_SPREAD));
                    state.lastPower().put(id, p.power());
                    actor.growth(growth).confidence(1.0);
                } else {
                    actor.growth(0.0); // 看不到就不知道增长
                }

                // 反制手段（由 player persistent data 里的标记决定，见 WillServer）
                actor.counters.addAll(CounterFlags.read(player));
                state.core().observeCounterNoise(actor);

                // 炼蛊见证：只有「看得到 + 真的从进度不足走到恰好 100 + 背包差异能认出产物」才记证据
                // B02：这里以前只判 !covered，没有判载体触达，等于无载体也能记情报。
                if (observable) {
                    AlchemyWitness.observe(player, k, state.identity(), now, null);
                }

                // 天意实际知道的逆天行为（不是后台判定）
                actor.knownDefiance(k.defianceWeight(id, now, WillConfig.FACT_HARD_TTL));

                s.add(actor);
                avgReach += reach;
                samples++;
            }
        }
        if (samples > 0) {
            avgReach /= samples;
            state.carriers().regrow(now, Math.max(1, samples));
            // 世界的天意整体是否还活着：全灭时决策器只能收手
            if (!state.carriers().willAlive(now) && avgReach <= 0.02) {
                s.willBudget = 0.0;
            }
        }
        s.willBudget = Math.min(s.willBudget, 1.0);
        return s;
    }

    /** 给玩家挂一个反制标记（供管理命令做测试；效果由 {@link Knowledge} 与载体承担）。 */
    public static void grantCounter(ServerPlayer player,
                                    net.guzhenren.tianyi.will.Counter.Kind kind) {
        CounterFlags.grant(player, kind);
    }

    /** 记录一次「天意实际看到了的」逆天行为。 */
    public static void noteDefiance(Knowledge.Store k, ServerPlayer player,
                                    double weight, long now, String dim) {
        k.observe(Knowledge.Fact.observed(player.getUUID().toString(),
                Knowledge.Kind.DEFIANCE, weight, now, dim,
                player.blockPosition().asLong(), Knowledge.Source.EVENT));
    }

    /** 反射读本体字段。字段名由 javap 核对；取不到就按 0 处理，不猜语义。 */
    private static double field(PlayerVariables v, String name) {
        java.lang.reflect.Field f = FIELDS.get(name);
        if (f == null) {
            if (MISSING.contains(name)) {
                return 0.0;
            }
            try {
                f = PlayerVariables.class.getField(name);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // 3.14.1：以前这里静默返回 0，字段名打错就永远算成 0 道痕，没人知道。
                // 现在按名字只告警一次（最多 DAOHEN_KEYS.length 行），行为不变。
                MISSING.add(name);
                net.guzhenren.tianyi.TianyiMod.LOGGER.warn(
                        "[天意] 道痕字段 {} 在本体 PlayerVariables 里不存在，按 0 计；"
                                + "请用 javap 核对字段名（静默 0 会直接压低实力评估）", name);
                return 0.0;
            }
            FIELDS.put(name, f);
        }
        try {
            Object o = f.get(v);
            return o instanceof Number n ? n.doubleValue() : 0.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0;
        }
    }

    /** 供调试输出。 */
    public static String describe(Assessment.Situation s) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Situation{now=%d actors=%d channels=%d}",
                s.now, s.actors.size(), s.channels.size()));
        for (Assessment.Actor a : s.actors) {
            sb.append("\n  ").append(a.id, 0, 8)
                    .append(String.format(Locale.ROOT,
                            " pow=%.1f res=%.2f moral=%.0f luck=%.0f/%.0f conf=%.2f hidden=%s",
                            a.power, a.resources, a.morality, a.luck, a.luckCap,
                            a.confidence, a.hidden));
        }
        return sb.toString();
    }

    /** 反制标记的读取器（标记由玩家数据里的 {@code tianyi_counter} 字符串写入）。 */
    static final class CounterFlags {
        private CounterFlags() {
        }

        static java.util.EnumSet<net.guzhenren.tianyi.will.Counter.Kind> read(ServerPlayer player) {
            java.util.EnumSet<net.guzhenren.tianyi.will.Counter.Kind> out =
                    java.util.EnumSet.noneOf(net.guzhenren.tianyi.will.Counter.Kind.class);
            var pd = player.getPersistentData();
            if (!pd.contains("tianyi_counter", net.minecraft.nbt.Tag.TAG_LIST)) {
                return out;
            }
            var list = pd.getList("tianyi_counter", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    out.add(net.guzhenren.tianyi.will.Counter.Kind.valueOf(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return out;
        }

        static void grant(ServerPlayer player, net.guzhenren.tianyi.will.Counter.Kind kind) {
            var pd = player.getPersistentData();
            var list = pd.getList("tianyi_counter", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                if (list.getString(i).equals(kind.name())) {
                    return;
                }
            }
            list.add(net.minecraft.nbt.StringTag.valueOf(kind.name()));
            pd.put("tianyi_counter", list);
        }
    }

    /** 便于其他类按 UUID 找在线玩家。 */
    public static ServerPlayer playerOf(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    /** 供测试/日志：某个玩家的实力。 */
    public static double powerOf(ServerPlayer player) {
        return probe(player).power();
    }

    /** 未使用但保留：把地图键写入日志时的统一格式。 */
    public static String dimKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** 未使用画像字段的占位访问，避免"读了不用"的错觉。 */
    public static Map<String, Object> summary(Probe p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("power", p.power());
        m.put("rank", p.rank());
        m.put("daohen", p.daohenTotal());
        m.put("lifespan", p.lifespan());
        m.put("pressure", p.pressure());
        m.put("dim", p.dim());
        return m;
    }
}
