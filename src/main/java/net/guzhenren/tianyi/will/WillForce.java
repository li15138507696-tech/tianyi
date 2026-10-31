package net.guzhenren.tianyi.will;

/**
 * 天意强度（档位）—— 纯逻辑，不碰 Minecraft。
 *
 * <h2>为什么要一个强度档位</h2>
 * 3.13 之前，天意的压制、灾劫、兽群三条渠道都由一个默认关闭的旧开关
 * 单独控制 —— 结果是「天意」在实机里
 * 几乎不出手，玩家感觉不到世界在针对自己。3.14 把「出不出手、出多重的手」收拢成
 * 一个 0..3 的档位；本次清理又把那个旧开关整体删掉了，档位成为唯一入口。
 *
 * <p><b>档位只做乘数与阈值，不复制第二套系统</b>：决策、压制、布局、灾劫、机缘、兽群
 * 仍然是原来那一套类，档位只改它们读到的数。这样「强度」永远不会变成一个
 * 写着数字却没人读的枚举。
 *
 * <h2>四个档位（全部是 [平衡参数]，不是原著数值）</h2>
 * <table border="1">
 *   <tr><th>档位</th><th>实际会发生什么</th></tr>
 *   <tr><td>0 关闭</td><td>零干预：不决策、不施压、不推进灾劫、不落任何天象</td></tr>
 *   <tr><td>1 温和（默认）</td><td>只有世界现象：天象转阴、远雷、小规模兽群迁徙；
 *       不主动压制、不降灾劫</td></tr>
 *   <tr><td>2 严厉</td><td>放开既有压制渠道（SUPPRESS / HOLD_HEAVEN / 人劫 Ploy），
 *       灾劫真的会发生（仍是原有四等四阶段）</td></tr>
 *   <tr><td>3 天倾</td><td>压制更密、灾劫更重、允许兽潮 —— 兽潮只能从
 *       {@link BeastHerd} 的真实种群账里「赶」出来，种群不够就生不出来</td></tr>
 * </table>
 *
 * <h2>强度 1 必须与 3.13 完全一致</h2>
 * 所有倍率在档位 1 都等于 1.0、所有阈值都等于 {@link WillConfig} 里的原值，
 * 这样「默认档位」不会悄悄改掉任何既有行为（回归测试就是这么锁的）。
 *
 * <h2>为什么没有随机数</h2>
 * 本类只用<b>确定性</b>哈希（{@link #roll}）与窗口偏移（{@link #fires}）：
 * 同一世界同一刻永远得到同一结论，重启也不会重复触发同一件事 ——
 * 这就是「幂等键」在纯函数层的落点，而不是靠一张会丢的运行时状态表。
 */
public final class WillForce {
    /** 关闭：天意不出手。 */
    public static final int OFF = 0;
    /** 温和：只有世界现象（默认档位）。 */
    public static final int GENTLE = 1;
    /** 严厉：放开既有压制渠道，灾劫真的会发生。 */
    public static final int STRICT = 2;
    /** 天倾：压制更密、灾劫更重、允许兽潮。 */
    public static final int TILT = 3;
    /** 最大档位。 */
    public static final int MAX = TILT;

    /** 天象窗口（刻）：一个窗口内最多落一次天象。4 游戏小时。 */
    public static final long OMEN_WINDOW = 6_000L;
    /** 雷击窗口（刻）。 */
    public static final long BOLT_WINDOW = 6_000L;
    /**
     * 「产地枯损」窗口（刻）：一个游戏日内最多一次。
     *
     * <p>本次清理起旧产地系统已删除，这条现象暂时没有消费者；窗口与
     * {@code spoil*} 倍率仍留在档位表里（{@code ThrottleContractTest} 锁着它），
     * 但世界里不会再有东西被扣。要么接回有限资源点，要么下一次清理掉。
     */
    public static final long SPOIL_WINDOW = 24_000L;
    /** 兽潮窗口（刻）：半个游戏日。 */
    public static final long WAVE_WINDOW = 12_000L;
    /** 灾劫挑选窗口（刻）：同一目标在一个窗口内的取舍保持一致。 */
    public static final long CALAMITY_WINDOW = 1_200L;
    /** 见闻录条目去重窗口（刻）。 */
    public static final long JOURNAL_WINDOW = 6_000L;

    private static final WillForce[] CACHE = {
            new WillForce(OFF), new WillForce(GENTLE), new WillForce(STRICT), new WillForce(TILT)
    };

    private final int intensity;

    private WillForce(int intensity) {
        this.intensity = intensity;
    }

    /** 取档位实例；越界值夹到 0..3（配置写错不放大天意）。 */
    public static WillForce of(int intensity) {
        int i = intensity < 0 ? 0 : Math.min(intensity, MAX);
        return CACHE[i];
    }

    /** 默认档位：温和。 */
    public static WillForce gentle() {
        return CACHE[GENTLE];
    }

    public int intensity() {
        return intensity;
    }

    /** 档位 0 = 天意完全不出手。 */
    public boolean isOff() {
        return intensity <= OFF;
    }

    /** 中文档位名（只进服务端日志与管理员状态，不进玩家聊天）。 */
    public String label() {
        return switch (intensity) {
            case OFF -> "关闭";
            case GENTLE -> "温和";
            case STRICT -> "严厉";
            default -> "天倾";
        };
    }

    // ---------------------------------------------------------------- 开关

    /** 世界现象（天象/远雷/产地枯损/兽群迁徙）是否生效：档位 ≥ 1。 */
    public boolean phenomenaEnabled() {
        return intensity >= GENTLE;
    }

    /**
     * 原先被关掉的压制渠道是否放开：档位 ≥ 2。
     *
     * <p>这里说的「放开」指的是**既有**渠道（{@link Assessment.Channel#BEAST}、
     * {@link Assessment.Channel#RUMOR}、{@link Assessment.Channel#CALAMITY} 与
     * {@link Intent.Kind#HOLD_HEAVEN}）真的可以落到世界里，不是新开渠道。
     */
    public boolean pressureEnabled() {
        return intensity >= STRICT;
    }

    /** 灾劫是否真的会发生：档位 ≥ 2。 */
    public boolean calamityEnabled() {
        return intensity >= STRICT;
    }

    /** 产地枯损是否生效：档位 ≥ 1（温和档只有低概率的轻微枯损，严厉以上更常见）。 */
    public boolean spoilEnabled() {
        return intensity >= GENTLE;
    }

    /** 兽潮是否允许：档位 ≥ 3（且必须从真实种群扣减）。 */
    public boolean beastWaveEnabled() {
        return intensity >= TILT;
    }

    // ---------------------------------------------------------------- 调度

    /** 决策周期（刻）：档位越高越勤。档位 0/1 保持 {@link WillConfig#DECISION_PERIOD}。 */
    public int decisionPeriod() {
        return switch (intensity) {
            case STRICT -> 160;
            case TILT -> 120;
            default -> WillConfig.DECISION_PERIOD;
        };
    }

    /** 单轮最多执行几条意图（参与者预算）。 */
    public int decisionBatch() {
        return switch (intensity) {
            case OFF -> 0;
            case STRICT -> 24;
            case TILT -> 32;
            default -> WillConfig.DECISION_BATCH;
        };
    }

    /** 现象巡查周期（刻）。必须能整除所有窗口，保证「一个窗口恰有一次机会」。 */
    public int phenomenaPeriod() {
        return switch (intensity) {
            case STRICT -> 200;
            case TILT -> 100;
            default -> 400;
        };
    }

    // ---------------------------------------------------------------- 评估阈值

    /** 失衡份额阈值：档位越高，越小的份额也算「失衡」。 */
    public double balanceShare() {
        return switch (intensity) {
            case STRICT -> 0.32;
            case TILT -> 0.26;
            default -> WillConfig.IMBALANCE_SHARE;
        };
    }

    /** 失衡死区：档位越高，死区越窄（更容易动手）。 */
    public double balanceDeadband() {
        return switch (intensity) {
            case STRICT -> 0.04;
            case TILT -> 0.03;
            default -> WillConfig.BALANCE_DEADBAND;
        };
    }

    /** 压制所需的威胁下限：档位越高越容易过线。 */
    public double suppressThreshold() {
        return switch (intensity) {
            case STRICT -> 0.42;
            case TILT -> 0.32;
            default -> WillConfig.MIN_THREAT_TO_SUPPRESS;
        };
    }

    /** 扶持所需的棋子价值下限：天倾档连不太值钱的棋子也用。 */
    public double supportThreshold() {
        return intensity >= TILT ? 0.26 : WillConfig.MIN_UTILITY_TO_SUPPORT;
    }

    // ---------------------------------------------------------------- 动作配额

    /** 同一目标同一手段在一个时间窗口内的最大叠加次数。 */
    public int maxSameAction() {
        return switch (intensity) {
            case STRICT -> 3;
            case TILT -> 4;
            default -> WillConfig.MAX_SAME_ACTION;
        };
    }

    /** 单轮最多压制几个对象。 */
    public int maxSuppressPerRound() {
        return switch (intensity) {
            case OFF -> 0;
            case STRICT -> 3;
            case TILT -> 4;
            default -> 2;
        };
    }

    /** 单轮最多执行几个动作（压制 + 扶持）。 */
    public int maxActionPerRound() {
        return switch (intensity) {
            case OFF -> 0;
            case STRICT -> 4;
            case TILT -> 6;
            default -> 3;
        };
    }

    /** 一个动作的意志余量消耗倍率：档位越高，天意「出手更省」。 */
    public double budgetCostScale() {
        return switch (intensity) {
            case STRICT -> 0.85;
            case TILT -> 0.70;
            default -> 1.0;
        };
    }

    /** 同时存在的布局上限（人劫频次的上限侧）。 */
    public int maxActivePloys() {
        return switch (intensity) {
            case STRICT -> 8;
            case TILT -> 10;
            default -> WillConfig.MAX_ACTIVE_PLOYS;
        };
    }

    /** 人劫酝酿时长倍率：档位越高，酝酿越短（但不会短到「即时生效」）。 */
    public double renjieGestationScale() {
        return switch (intensity) {
            case STRICT -> 0.75;
            case TILT -> 0.50;
            default -> 1.0;
        };
    }

    /** 一次人劫额外卷入的 NPC 数（仍受 {@link WillConfig#RENJIE_MAX_NPCS} 夹住）。 */
    public int renjieExtraNpc() {
        return intensity >= TILT ? 1 : 0;
    }

    // ---------------------------------------------------------------- 灾劫

    /**
     * 灾劫被主动选中的概率（每个 {@link #CALAMITY_WINDOW} 窗口、对头号威胁）。
     *
     * <p>档位 0/1 恒为 0 —— 「温和」不降灾劫，这是本档位表的明确承诺。
     */
    public double calamityChance() {
        return switch (intensity) {
            case STRICT -> 0.35;
            case TILT -> 0.60;
            default -> 0.0;
        };
    }

    /** 灾劫规模倍率（乘在 {@link Calamity.Tier#scale} 上，最终仍夹在 0..1）。 */
    public double calamityScaleFactor() {
        return switch (intensity) {
            case STRICT -> 1.15;
            case TILT -> 1.35;
            default -> 1.0;
        };
    }

    /** 针对性倍率（最终仍受 {@link WillConfig#CALAMITY_TARGETING_CAP} 夹住）。 */
    public double calamityTargetingFactor() {
        return switch (intensity) {
            case STRICT -> 1.15;
            case TILT -> 1.40;
            default -> 1.0;
        };
    }

    /** 高阶灾劫的门限松紧：< 1 表示更容易出高阶（仍要求转数与天道权限）。 */
    public double calamityTierEase() {
        return switch (intensity) {
            case STRICT -> 0.85;
            case TILT -> 0.70;
            default -> 1.0;
        };
    }

    /** 同时存在的灾劫上限（档位 1 不额外限制，保持旧行为）。 */
    public int calamityMaxActive() {
        return switch (intensity) {
            case OFF -> 0;
            case STRICT -> 3;
            case TILT -> 4;
            default -> 64;
        };
    }

    /** 同一目标两场灾劫之间的最短间隔（刻）。档位 1 为 0（不额外限制）。 */
    public long calamityCooldown() {
        return switch (intensity) {
            case STRICT -> 12_000L;
            case TILT -> 6_000L;
            default -> 0L;
        };
    }

    // ---------------------------------------------------------------- 机缘诱饵

    /**
     * 诱饵判定的正弦门限：门限越低，诱饵越多。
     *
     * <p>档位 1 等于 {@link WillConfig#BAIT_SIN_THRESHOLD}（与 3.13 完全一致）；
     * 档位 0 为 2.0，而 |sin| 恒 ≤ 1，所以<b>永远不会</b>产生诱饵。
     * 诱饵占比仍受 {@link WillConfig#BAIT_RATIO_CAP} 硬上限约束。
     */
    public double baitSinThreshold() {
        return switch (intensity) {
            case OFF -> 2.0;
            case STRICT -> 0.80;
            case TILT -> 0.62;
            default -> WillConfig.BAIT_SIN_THRESHOLD;
        };
    }

    /** 诱饵占全部机缘的比例硬上限（沿用 {@link WillConfig#BAIT_RATIO_CAP}，不随档位放宽）。 */
    public double baitCap() {
        return WillConfig.BAIT_RATIO_CAP;
    }

    // ---------------------------------------------------------------- 兽群

    /** 压制时「借兽」规模的倍率（乘在原来的 3..6 只上）。 */
    public double beastDriveFactor() {
        return switch (intensity) {
            case STRICT -> 1.35;
            case TILT -> 2.20;
            default -> 1.0;
        };
    }

    /** 单次借兽/兽潮的数量硬上限（档位 1 沿用 3.13 的 7）。 */
    public int beastDriveMax() {
        return switch (intensity) {
            case OFF -> 0;
            case STRICT -> 12;
            case TILT -> WillConfig.BEAST_DRIVE_MAX;
            default -> 7;
        };
    }

    /** 兽潮一次想赶出多少只（真实种群，扣不出来就是 0）。 */
    public int beastWaveWant() {
        return intensity >= TILT ? 12 : 0;
    }

    /** 兽潮在一个 {@link #WAVE_WINDOW} 窗口内发生的概率。 */
    public double beastWaveChance() {
        return intensity >= TILT ? 0.45 : 0.0;
    }

    // ---------------------------------------------------------------- 世界现象

    /** 天象在一个 {@link #OMEN_WINDOW} 窗口内发生的概率。 */
    public double omenChance() {
        return switch (intensity) {
            case STRICT -> 0.30;
            case TILT -> 0.55;
            default -> 0.12;
        };
    }

    /** 天象持续刻数（只改天气，不改任何账）。 */
    public int omenTicks() {
        return switch (intensity) {
            case STRICT -> 8_000;
            case TILT -> 12_000;
            default -> 6_000;
        };
    }

    /** 天象是否带雷（档位 ≥ 2）。 */
    public boolean omenThunders() {
        return intensity >= STRICT;
    }

    /** 偏离雷击在一个 {@link #BOLT_WINDOW} 窗口内发生的概率。 */
    public double boltChance() {
        return switch (intensity) {
            case STRICT -> 0.20;
            case TILT -> 0.45;
            default -> 0.0;
        };
    }

    /** 产地枯损在一个 {@link #SPOIL_WINDOW} 窗口内发生的概率。 */
    public double spoilChance() {
        return switch (intensity) {
            case STRICT -> 0.25;
            case TILT -> 0.50;
            default -> 0.10;
        };
    }

    /** 枯损时被打散的储量比例。 */
    public double spoilRatio() {
        return switch (intensity) {
            case STRICT -> 0.35;
            case TILT -> 0.55;
            default -> 0.20;
        };
    }

    /** 枯损的影响半径（格）。 */
    public double spoilRadius() {
        return switch (intensity) {
            case STRICT -> 64.0;
            case TILT -> 96.0;
            default -> 48.0;
        };
    }

    // ---------------------------------------------------------------- 幂等 / 确定性

    /**
     * 确定性伪随机：同样的（种类, 主体, 窗口号）永远得到同一个 0..1 的数。
     *
     * <p>用 FNV-1a 而不是 {@code String.hashCode()}：后者 32 位且在
     * {@code audit_will} 里被禁作身份。这里也没有任何运行时随机状态，
     * 所以重启后同一窗口不会「再抽一次」。
     */
    public static double roll(String kind, String subject, long window) {
        String s = (kind == null ? "" : kind) + '|' + (subject == null ? "" : subject)
                + '|' + window;
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        long u = h >>> 11;          // 取高 53 位
        return u / (double) (1L << 53);
    }

    /**
     * 本刻是不是「这个窗口里唯一的那一次机会」。
     *
     * <p>做法：在窗口内取一个由 {@link #roll} 决定的确定性起点 {@code offset}，
     * 只有落在 {@code [offset, offset + phenomenaPeriod)} 内的刻才算命中。
     * 由于巡查间隔等于这个区间长度，一个窗口最多命中一次；
     * 又因为起点只由（窗口号, 主体, kind）决定，重启也不会重复命中。
     *
     * @param windowTicks 窗口长度（刻），建议用本类里的 {@code *_WINDOW}
     */
    public boolean fires(String kind, String subject, long now, long windowTicks) {
        if (isOff()) {
            return false;
        }
        long w = Math.max(1L, windowTicks);
        int period = Math.max(1, phenomenaPeriod());
        long window = now / w;
        // 偏移夹在 [0, w - period] 内，保证「起点 + 区间」不会跨过窗口边界 ——
        // 否则某个窗口会被整段跳过（这是本方法唯一的边界坑）。
        long span = Math.max(0L, w - Math.min((long) period, w));
        long offset = (long) (roll("slot|" + (kind == null ? "" : kind), subject, window)
                * (span + 1));
        long start = window * w + offset;
        return now >= start && now < start + period;
    }

    /** 幂等键：同一事件同一手段同一窗口，永远得到同一个字符串。 */
    public static String eventKey(String kind, String subject, long now, long windowTicks) {
        long w = Math.max(1L, windowTicks);
        return "force|" + (kind == null ? "" : kind) + '|'
                + (subject == null ? "" : subject) + '|' + (now / w);
    }

    @Override
    public String toString() {
        return "WillForce{" + intensity + ' ' + label() + '}';
    }
}
