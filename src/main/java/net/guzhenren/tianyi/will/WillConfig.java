package net.guzhenren.tianyi.will;

/**
 * 天意的全部数值参数 —— <b>原创平衡参数，不来自原著</b>。
 *
 * <h2>为什么集中在一个类</h2>
 * 原著只给出「天意维持天地平衡、制约威胁、利用棋子」这类<b>定性</b>描述，
 * 没有任何一条数值。凡是本模组自己定的数，全部收在这里，并在注释里标注
 * {@code [平衡参数]}，便于审核时区分「原著事实」与「实现取舍」。
 * 违反这条纪律（把自定数值写进能力注释冒充原著）会导致考据与实现无法对账。
 *
 * <p>本类不 import 任何 Minecraft 类型，因此可被离线测试直接实例化。
 */
public final class WillConfig {
    private WillConfig() {
    }

    // ---------------------------------------------------------------- 知识边界

    /** [平衡参数] 一条情报在多少游戏刻后开始被视为「旧情报」。1 游戏日 = 24000 刻。 */
    public static final long FACT_SOFT_TTL = 24_000L;

    /** [平衡参数] 旧情报的硬过期上限：超过此值直接丢弃，只能靠推断。 */
    public static final long FACT_HARD_TTL = 24_000L * 8L;

    /** [平衡参数] 每个主体的情报条数上限（防内存无界增长）。 */
    public static final int FACTS_PER_SUBJECT = 24;

    /** [平衡参数] 单个主体被遮蔽的最短时长。 */
    public static final long BLIND_MIN_TICKS = 1_200L;

    /** [平衡参数] 遮蔽到期后，天意重新「看到」所需的重新接触时间。 */
    public static final long BLIND_RECOVER_TICKS = 2_400L;

    // ---------------------------------------------------------------- 判断与布局

    /** [平衡参数] 判定「严重失衡」的实力份额阈值（单一主体占世界的比例）。 */
    public static final double IMBALANCE_SHARE = 0.38;

    /** [平衡参数] 低于此份额不算失衡（避免一有波动就干预）。 */
    public static final double BALANCE_DEADBAND = 0.05;

    /** [平衡参数] 威胁评分里「已知逆天行为」的权重。 */
    public static final double THREAT_DEFIANCE_WEIGHT = 1.25;

    /** [平衡参数] 威胁评分里「增长趋势」的权重。 */
    public static final double THREAT_GROWTH_WEIGHT = 0.85;

    /** [平衡参数] 威胁评分里「实力」的权重。 */
    public static final double THREAT_POWER_WEIGHT = 1.0;

    /** [平衡参数] 情报可信度不足时，评分按此系数打折（不确定性惩罚）。 */
    public static final double UNCERTAINTY_PENALTY = 0.55;

    /** [平衡参数] 低于此置信度时天意不改动布局，改为「观测」（先派探） */
    public static final double CONFIDENCE_ACT_FLOOR = 0.35;

    /** [平衡参数] 同一目标同一手段的最大叠加次数（防止无限叠加惩罚）。
     *  注意：这是「一个时间窗口内」的上限，<b>不是终身配额</b>。 */
    public static final int MAX_SAME_ACTION = 2;

    /** [平衡参数] 压制配额的时间窗口（刻）。窗口一过配额自动归零（3 游戏日）。 */
    public static final long ACTION_WINDOW = 24_000L * 3L;

    /** [平衡参数] 布局失败后的冷却（刻），期间不对同一目标重开同类布局。 */
    public static final long PLOY_FAIL_COOLDOWN = 24_000L * 3L;

    /** [平衡参数] 一个布局从建立到必须结算的最长时间。 */
    public static final long PLOY_MAX_LIFETIME = 24_000L * 20L;

    /** [平衡参数] 同时存在的布局数量上限。 */
    public static final int MAX_ACTIVE_PLOYS = 6;

    // ---------------------------------------------------------------- 扶持 / 压制

    /** [平衡参数] 扶持对象的最低棋子价值（低于此值不值得扶持）。 */
    public static final double MIN_UTILITY_TO_SUPPORT = 0.30;

    /** [平衡参数] 被压制对象的威胁下限（低于此值不压制，避免打压弱小）。 */
    public static final double MIN_THREAT_TO_SUPPRESS = 0.55;

    /** [平衡参数] 高道德强者的「社会局面」加权：道德越高，其被压制的社会代价越大，
     *  但<b>不构成豁免</b>——只是让天意更倾向借人施劫而非直接降灾。 */
    public static final double MORAL_SOCIAL_COST = 0.45;

    /** [平衡参数] 扶持低道德者时，天意承受的额外不确定性（魔头反噬风险）。 */
    public static final double VILLAIN_BACKFIRE_RISK = 0.30;

    // ---------------------------------------------------------------- 机缘

    /** [平衡参数] 机缘从酝酿到成熟的时长范围（刻）。 */
    public static final long OPPORTUNITY_MIN_GESTATION = 2_400L;
    public static final long OPPORTUNITY_MAX_GESTATION = 24_000L;

    /** [平衡参数] 机缘无人领取的存续上限。 */
    public static final long OPPORTUNITY_TTL = 24_000L * 12L;

    /** [平衡参数] 作为诱饵的机缘占比上限（不能让所有机会都是陷阱）。
     *  <b>这是硬上限，3.14 的强度档位也不放宽它</b>。 */
    public static final double BAIT_RATIO_CAP = 0.34;

    /** [平衡参数] 诱饵判定的正弦门限：{@code |sin(...)| > 门限} 才算诱饵。
     *  门限越大诱饵越少；1.0 以上等于永不产生诱饵（3.14 档位 0 就是用它关掉的）。
     *  这是 3.13 及更早的取值，保持默认档位行为不变。 */
    public static final double BAIT_SIN_THRESHOLD = 0.93;

    // ---------------------------------------------------------------- 人劫 / 兽群

    /** [平衡参数] 人劫布局的酝酿最短时长：原著强调「酝酿」，不能即时生效。 */
    public static final long RENJIE_MIN_GESTATION = 6_000L;

    /** [平衡参数] 一次人劫最多卷入的 NPC 数（不做无限仇恨傀儡）。 */
    public static final int RENJIE_MAX_NPCS = 3;

    /** [平衡参数] 兽群迁徙的规模上限（只按现有生物群系实际刷怪，不凭空生成）。 */
    public static final int BEAST_DRIVE_MAX = 24;

    /** [平衡参数] 玩家快速移动超过此距离（格），酝酿中的针对性布局失效。 */
    public static final double RENJIE_BREAK_DISTANCE = 512.0;

    // ---------------------------------------------------------------- 灾劫

    /** [平衡参数] 灾劫的「针对性」上限：最多只能把这么多比例的火力对准弱点，
     *  其余必须是无差别部分。原著允许天意在规则内针对弱点，但不是必杀。 */
    public static final double CALAMITY_TARGETING_CAP = 0.45;

    /** [平衡参数] 四等灾劫的基础酝酿时长（刻）。 */
    public static final long CALAMITY_GESTATION_DI = 6_000L;
    public static final long CALAMITY_GESTATION_TIAN = 12_000L;
    public static final long CALAMITY_GESTATION_HAO = 24_000L;
    public static final long CALAMITY_GESTATION_WAN = 48_000L;

    /** [平衡参数] 灾劫的渡劫失败惩罚上限（不直接抹杀，只削道痕/寿元）。 */
    public static final double CALAMITY_MAX_FAIL_PENALTY = 0.35;

    // ---------------------------------------------------------------- 仙窍

    /** [平衡参数] 仙窍内部时间倍率（原著有内外时间差，具体比例未量化）。 */
    public static final double APERTURE_TIME_RATIO_MIN = 1.0;
    public static final double APERTURE_TIME_RATIO_MAX = 12.0;

    /** [平衡参数] 升仙三气调和的最低人气要求（原著「升仙人气」的语义见考据文档）。 */
    public static final double ASCENSION_MIN_RENQI = 0.0;

    /** [平衡参数] 师法自然带来的调和削减比例上限。 */
    public static final double SHIFA_ZIRAN_BONUS_CAP = 0.40;

    // ---------------------------------------------------------------- 寿蛊 / 资源

    /** [平衡参数] 自然寿蛊的产出周期（刻）：不是取消掉落，而是改变未来供给。 */
    public static final long SHOU_GU_SUPPLY_PERIOD = 24_000L * 30L;

    /** [平衡参数] 天意影响资源演化时，单区块每次最多改动的方块数。 */
    public static final int RESOURCE_EDIT_BUDGET = 64;

    // ---------------------------------------------------------------- 宿命

    /** [平衡参数] 宿命修复/损坏的推进量。 */
    public static final double DESTINY_STEP = 0.12;

    /** [平衡参数] 星宿意志干涉天意的成功率基数（两者目标不同，不是同一意志）。 */
    public static final double STAR_INTERFERE_BASE = 0.35;

    /** [平衡参数] 天意反过来压制星宿干涉的概率。 */
    public static final double WILL_COUNTER_STAR = 0.25;

    // ---------------------------------------------------------------- 载体

    /** [平衡参数] 载体的最大传播距离（格）：超出范围即失去该载体的感知。 */
    public static final double CARRIER_REACH = 256.0;

    /** [平衡参数] 局部载体被清除后，该区域的盲区时长。 */
    public static final long CARRIER_PURGE_BLIND = 24_000L * 2L;

    /** [平衡参数] 载体自愈所需时长（天意会重新渗入，但不是瞬间）。 */
    public static final long CARRIER_REGROW = 24_000L * 5L;

    // ---------------------------------------------------------------- 调度

    /** [平衡参数] 世界决策器的基础周期（刻）：不是每 tick 扫全世界。 */
    public static final int DECISION_PERIOD = 200;

    /** [平衡参数] 单次决策最多处理的参与者数（分区调度）。 */
    public static final int DECISION_BATCH = 16;

    /** [平衡参数] 每 tick 最多推进的灾劫/布局数。 */
    public static final int ACTIVE_JOBS_PER_TICK = 4;
}
