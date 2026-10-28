package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 抽选主流程。
 *
 * <p><b>v1.4.0 语义变更</b>：v1.3.0 是「攒满 → 随机自动发一个 → 事后可无限重选」。
 * 现在改成<b>「攒满 → 弹出 5 个候选 → 玩家选定 → 永久锁定」</b>。
 *
 * <p>为什么改：随机自动发让玩家没有参与感，而「事后无限重选」又让选择变得毫无分量——
 * 反正随时能改。改成一次性抉择后，每次攒满都是一个真正的决策点。
 *
 * <p><b>v1.5.0 两项新增</b>：
 * <ol>
 *   <li><b>每 10 抽的特殊奖励改为可选</b>——旧实现是系统随机发（3/5 蛊虫 buff、
 *       2/5 道痕）。现在 {@code drawIndex % 10 == 0} 时生成特殊候选
 *       （见 {@link DaotuSpecial#candidates}），玩家点选后才发奖，
 *       与普通抽选同一条流程。</li>
 *   <li><b>一转~五转每转最多 100 抽</b>——见 {@link #capOf}。五转之后无上限。
 *       攒满后历练停止累计（{@link DaotuLiLian} 里判），
 *       避免玩家攒着一池永远用不掉的历练还以为坏了。</li>
 * </ol>
 *
 * <p><b>流程</b>：
 * <ol>
 *   <li>{@link #tryDraw} 攒满时<b>不扣池、不发奖</b>，只生成候选存进 {@code run/offer}，
 *       然后 {@link DaotuNet#promptChoice} 提醒；弹不弹窗看玩家的「自动弹窗」开关
 *       （v1.4.1，默认开）；</li>
 *   <li>玩家点某个候选 → {@link #choose} 扣池、发奖、记入 {@code run/history}、清空 offer；</li>
 *   <li>玩家不选就关窗 → 候选保留，下次按 M 继续选（<b>不丢失</b>）。</li>
 * </ol>
 *
 * <p>这样设计的关键：<b>扣池发生在选择时而不是生成时</b>。
 * 若生成就扣池，玩家关窗不选，历练就白扣了。
 */
public final class DaotuDraw {

    /** 单次 {@code award} 最多连抽几次，防极端情况卡顿。 */
    private static final int MAX_CONSECUTIVE = 8;

    /** 一转~五转每转的抽选上限。 */
    public static final int PER_ZHUAN_CAP = 100;

    /** 特殊奖励的触发间隔（每 10 抽一次）。 */
    public static final int SPECIAL_EVERY = 10;

    private DaotuDraw() {
    }

    /**
     * 转数 → 本转抽选上限。
     *
     * <p>一转~五转各 {@value #PER_ZHUAN_CAP} 次；<b>五转之后无上限</b>
     * （返回 {@link Integer#MAX_VALUE}）。
     *
     * <p>为什么按转数而不是按局：本体 {@code zhuanshu} 每次转世 +1，
     * 所以「一转」与「一世」在这里是同一件事，{@code run/draws} 也是每世归零。
     */
    public static int capOf(double zhuanshu) {
        return zhuanshu <= 5.0 ? PER_ZHUAN_CAP : Integer.MAX_VALUE;
    }

    /** 当前玩家的抽选上限。 */
    public static int capOf(ServerPlayer player) {
        return capOf(DaotuRun.zhuanshu(player));
    }

    /** 是否还能再抽（用于界面提示与历练累计的提前拦截）。 */
    public static boolean atCap(ServerPlayer player) {
        return DaotuRun.draws(player) >= capOf(player);
    }

    /**
     * 检查是否攒满。攒满则生成候选并弹窗，返回 {@code 1}；否则返回 {@code 0}。
     *
     * <p><b>不扣池、不发奖</b>——真正的结算在 {@link #choose}。
     */
    public static int tryDraw(ServerPlayer player) {
        if (!DaotuOpening.hasOpenedQiao(player)) {
            return 0;
        }
        // 已达本转抽选上限：不再生成候选。池由 DaotuLiLian 停止累计，
        // 这里只兜底（防止上限是后来调小的，旧存档池子已经很高）。
        if (atCap(player)) {
            return 0;
        }
        // 已有待选候选就不重复生成（避免玩家反复触发把候选刷掉）
        if (!DaotuRun.offer(player).isEmpty()) {
            // v1.4.1：这里以前每次都 promptChoice（强制弹窗）。玩家关掉面板后，
            // 下一次击杀 / 走两步攒到历练又会把它顶出来，看起来像「关不掉」。
            // 现在只刷新已开着的界面，弹窗只在「刚攒满生成候选」那一下发生。
            DaotuNet.sendState(player);
            return 0;
        }
        double threshold = DaotuRun.thresholdOf(player);
        if (DaotuRun.pool(player) < threshold) {
            return 0;
        }
        int next = DaotuRun.draws(player) + 1;

        // 先生成候选；为空说明全部属性已到软上限，池原样保留。
        // 每 10 抽走特殊奖励候选（v1.5.0：可选，不再是系统随机发）。
        RandomSource rnd = player.getRandom();
        List<DaotuDrawTable.Candidate> cands = (next % SPECIAL_EVERY == 0)
                ? DaotuSpecial.candidates(player, next, rnd)
                : DaotuDrawTable.candidates(player, next, rnd);
        if (cands.isEmpty()) {
            return 0;
        }

        // 存候选 + 弹窗，不扣池
        DaotuRun.setOffer(player, next, encode(cands));
        DaotuNet.promptChoice(player);
        return 1;
    }

    /**
     * 玩家选定第 {@code index} 个候选。
     *
     * <p>扣池 → 发奖 → 记历史 → 清候选 → 刷新。选完即锁定，无法更改。
     *
     * @return 是否成功（候选不存在 / 池不够 / 下标越界都返回 false）
     */
    public static boolean choose(ServerPlayer player, int index) {
        if (!DaotuOpening.hasOpenedQiao(player)) {
            return false;
        }
        List<DaotuDrawTable.Candidate> cands = pendingOffer(player);
        if (cands.isEmpty()) {
            return false;
        }
        if (index < 0 || index >= cands.size()) {
            return false;
        }
        double threshold = DaotuRun.thresholdOf(player);
        double pool = DaotuRun.pool(player);
        if (pool < threshold) {
            return false;
        }
        int drawIndex = pendingDrawIndex(player);

        // 1. 扣池
        DaotuRun.setPool(player, pool - threshold);

        // 2. 发奖
        DaotuDrawTable.Candidate picked = cands.get(index);
        apply(player, picked);

        // 3. 记历史（锁定，不可改）
        DaotuRun.addHistory(player, drawIndex + "|" + picked.key() + "|"
                + picked.amount() + "|" + (picked.percent() ? "1" : "0"));

        // 4. 抽次 +1，清候选
        DaotuRun.setDraws(player, drawIndex);
        DaotuRun.clearOffer(player);

        // 5. 刷新
        DaotuAttributes.sync(player);
        DaotuNet.sendState(player);

        // 6. 播报
        player.displayClientMessage(Component.literal(
                "§6[道途] §e第 " + drawIndex + " 抽 §7· §f" + picked.label()
                        + " §7（已锁定）"), false);

        // 7. 如果还有余量，继续弹下一次
        tryDraw(player);
        return true;
    }

    /**
     * 把一个候选发出去。<b>玩家选择与旧存档迁移共用这一条路径</b>。
     *
     * <p>三类候选分开处理，绝不能图省事全塞给
     * {@link DaotuRunAttributes#grant}——那正是 v1.4.x 词条静默失效的原因：
     * 词条 key（{@code talent:xxx}）被当属性键用，挂了个不存在的 modifier，
     * 什么都不发生，玩家却以为抽到了。
     *
     * @param c 候选
     */
    private static void apply(ServerPlayer player, DaotuDrawTable.Candidate c) {
        if (c.isTalent()) {
            // 词条：整条效果一次性应用（可能同时给属性 + 道痕 + buff）
            String tid = c.talentId();
            DaotuTalent.Entry te = DaotuTalent.byId(tid);
            if (te != null) {
                DaotuTalentApply.apply(player, te,
                        DaotuDrawTable.zhuanScale(DaotuRun.zhuanshu(player)));
                DaotuTalentApply.markOwned(player, tid);
            } else {
                DaotuMod.LOGGER.warn("未知词条 id: {}", tid);
            }
        } else if (DaotuSpecial.isSpecial(c.key())) {
            // 特殊奖励（v1.5.0）：道痕 / 流派熟练度 / 主修蛊虫 / 气运上限
            DaotuSpecial.grant(player, c.key(), c.amount());
        } else {
            DaotuRunAttributes.grant(player, c.key(), c.amount(), c.percent());
        }
    }

    /** 当前待选候选（空 = 无待选）。 */
    public static List<DaotuDrawTable.Candidate> pendingOffer(ServerPlayer player) {
        String enc = DaotuRun.offer(player);
        return enc.isEmpty() ? List.of() : decode(enc);
    }

    /** 待选候选对应的抽次（0 = 无待选）。 */
    public static int pendingDrawIndex(ServerPlayer player) {
        return DaotuRun.offerDraw(player);
    }

    // ---------------------------------------------------------------- 编解码

    /**
     * 编码：{@code key=amount=percent,key=amount=percent,...}
     *
     * <p>v1.4.0 去掉了「已选下标」与「抽次前缀」——候选列表和抽次分开存，
     * 因为候选在玩家选定前一直是「未决」状态。
     *
     * <p><b>v1.5.0 分隔符从 {@code :} 改成 {@code =}</b>：特殊奖励的 key 本身
     * 含冒号（{@code special:daohen:jiandao}、{@code special:gu:YIZHUANJIANQI}）。
     * 用冒号分隔时 {@code split(":")} 会把 key 切碎，{@code f[0]} 只剩 {@code special}，
     * 解码后 key 丢失、发奖时对不上——这是<b>静默失效</b>，界面看着正常但点了没奖励。
     *
     * <p>属性键、词条键（{@code talent:xxx}）都不含 {@code =}，所以新分隔符安全。
     */
    private static String encode(List<DaotuDrawTable.Candidate> cands) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cands.size(); i++) {
            DaotuDrawTable.Candidate c = cands.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(c.key()).append('=').append(c.amount()).append('=')
                    .append(c.percent() ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * 解码。{@code split("=")} 后 key 可能仍含 {@code =}（防御性），
     * 所以用 {@code limit = -1} 再手工取首段为 key、末段为 percent。
     */
    private static List<DaotuDrawTable.Candidate> decode(String enc) {
        List<DaotuDrawTable.Candidate> out = new ArrayList<>();
        if (enc.isEmpty()) {
            return out;
        }
        for (String item : enc.split(",")) {
            if (item.isEmpty()) {
                continue;
            }
            // limit -1：保留尾部空段，key 里万一有 = 也不会被吞
            String[] f = item.split("=", -1);
            if (f.length < 3) {
                continue;
            }
            try {
                String key = f[0];
                // percent 恒为最后一段；amount 取倒数第二段。
                // key 中间若混入 = （目前不会），f[1] 之后都属于 amount 的位置，
                // 这里只认「恰好 3 段」的规整形式，不规则的一律跳过并记日志。
                if (f.length != 3) {
                    DaotuMod.LOGGER.warn("候选编码段数异常，已跳过: {}", item);
                    continue;
                }
                out.add(new DaotuDrawTable.Candidate(key, labelOf(key),
                        Double.parseDouble(f[1]), "1".equals(f[2])));
            } catch (NumberFormatException ex) {
                // 存档损坏时跳过该条，不让整个界面打不开
                DaotuMod.LOGGER.warn("候选编码数值异常，已跳过: {}", item);
            }
        }
        return out;
    }

    /** 键 → 中文名。客户端另有一份副本（不能引用服务端）。 */
    public static String labelOf(String key) {
        if (key.startsWith(DaotuDrawTable.TALENT_PREFIX)) {
            DaotuTalent.Entry entry = DaotuTalent.byId(
                    key.substring(DaotuDrawTable.TALENT_PREFIX.length()));
            if (entry != null) {
                String summary = entry.summary();
                return entry.name() + (summary.isEmpty() ? "" : " · " + summary);
            }
        }
        return switch (key) {
            case "attack_damage" -> "力量";
            case "max_health" -> "生命";
            case "movement_speed" -> "速度";
            case "attack_speed" -> "攻速";
            case "armor_toughness" -> "护甲韧性";
            case "max_absorption" -> "伤害吸收";
            case "knockback_resistance" -> "击退抗性";
            case "fall_damage_multiplier" -> "摔伤减免";
            case "luck" -> "幸运";
            case "safe_fall_distance" -> "安全摔落";
            case "attack_knockback" -> "攻击击退";
            case "oxygen_bonus" -> "水下呼吸";
            case "step_height" -> "跨越高度";
            case "scale" -> "体型";
            // 特殊奖励（v1.5.0）：key 形如 special:daohen:jiandao。
            // 历史记录与界面都要显示成人话，不能把裸 key 甩给玩家。
            default -> DaotuSpecial.isSpecial(key) ? DaotuSpecial.labelOf(key) : key;
        };
    }

    /**
     * 连抽到池不够为止，最多 {@link #MAX_CONSECUTIVE} 次。
     *
     * <p>v1.4.0 起一次只会生成一批候选（因为候选非空时 tryDraw 直接返回 0），
     * 保留循环是为了处理「池够很多次但玩家已经选完」的情况。
     */
    public static void drawAll(ServerPlayer player) {
        int n = 0;
        while (tryDraw(player) > 0) {
            n++;
            if (n >= MAX_CONSECUTIVE) {
                break;
            }
        }
    }

    /**
     * 旧存档迁移：替玩家补 {@code count} 次。
     *
     * <p>v1.4.0 起选择需要玩家参与，无法自动补。这里改成直接按档位随机发放，
     * 让老玩家的进度不至于凭空消失——播报里会说明这是迁移补发。
     *
     * <p><b>v1.5.0 修了两个静默失效</b>：
     * <ul>
     *   <li>原来无条件走 {@link DaotuRunAttributes#grant}，而候选里六成是<b>词条</b>
     *       （{@code talent:xxx}）——拿词条 key 当属性键挂，什么都不发生；</li>
     *   <li>每 10 次调 {@code DaotuSpecial.grant(player, next)}，那是 v1.5.0
     *       之前的旧签名，语义已变成「生成可选候选」，不能在这儿自动发。</li>
     * </ul>
     * 现在统一走 {@link #apply}，与玩家亲手选走同一条发奖路径。
     */
    public static void replayForMigration(ServerPlayer player, int count) {
        RandomSource rnd = player.getRandom();
        for (int i = 0; i < count; i++) {
            int next = DaotuRun.draws(player) + 1;
            // 每 10 次也按新规则生成特殊候选，让迁移补发的内容结构与正常游玩一致
            List<DaotuDrawTable.Candidate> cands = (next % SPECIAL_EVERY == 0)
                    ? DaotuSpecial.candidates(player, next, rnd)
                    : DaotuDrawTable.candidates(player, next, rnd);
            if (cands.isEmpty()) {
                break;
            }
            DaotuDrawTable.Candidate c = cands.get(rnd.nextInt(cands.size()));
            apply(player, c);
            DaotuRun.addHistory(player, next + "|" + c.key() + "|" + c.amount()
                    + "|" + (c.percent() ? "1" : "0"));
            DaotuRun.setDraws(player, next);
        }
        DaotuRun.setPool(player, 0.0);
        DaotuAttributes.sync(player);
        DaotuNet.sendState(player);
    }
}
