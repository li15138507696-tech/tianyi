package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.List;

/**
 * 宿命蛊<b>承载物</b>的凭证模型（纯逻辑，不 import 任何 Minecraft 类型）。
 *
 * <h2>物品只保存「凭证」，不保存「身份」</h2>
 * 物品上写的四样东西全部是<b>自称</b>：{@code token}（凭证串）、{@code generation}
 * （实体化代次）、{@code custodyRevision}（保管版本）、{@code serial}（这一枚的序号）。
 * 它们能被创造模式复制、被整段 NBT 克隆、被旧包重放——所以<b>物品 UUID/序号不构成唯一性证明</b>。
 * 真伪只有一个判据：拿这三项去问服务端世界账本 {@link DestinyCustody#validate}。
 *
 * <h2>这一层负责什么</h2>
 * <ol>
 *   <li>把「一次核对里看见的若干枚实物」翻译成 {@link Look}；
 *       账本说谁是真品，就只有一个真品，多出来的必是复制；</li>
 *   <li>区分「旧凭证」与「复制品」：两者都<b>不</b>认定为真，
 *       但只有复制品会触发隔离（{@link DestinyCustody#markDuplicate}）；</li>
 *   <li>保持「实物对不上账本」与「阶段」完全无关——本类根本不认识 {@link Destiny.Stage}。</li>
 * </ol>
 *
 * <p>键名是物品 CUSTOM_DATA 里的键，<b>改了等于让存档里的旧实物失去凭证</b>，
 * 因此一律常量化，不许在别处写字符串字面量。
 */
public final class DestinyVessel {
    /** 凭证串（世界＋逻辑身份＋代次＋保管版本）。 */
    public static final String K_TOKEN = "tianyi_destiny_token";
    /** 实体化代次。 */
    public static final String K_GENERATION = "tianyi_destiny_generation";
    /** 保管版本。 */
    public static final String K_REVISION = "tianyi_destiny_revision";
    /** 这一枚实物的序号（只作证据用，不构成唯一性证明）。 */
    public static final String K_SERIAL = "tianyi_destiny_serial";
    /** 受损外观开关（服务端在核对时写入；客户端只用于贴图和提示，不据此判断真伪）。 */
    public static final String K_SCARRED = "tianyi_destiny_scarred";
    /** 服务端最近一次写下的账本状态名（仅展示）。 */
    public static final String K_LEDGER = "tianyi_destiny_ledger";

    private DestinyVessel() {
    }

    /** 一枚实物自称的凭证。{@code token} 为空即「没有凭证」。 */
    public record Vessel(String token, int generation, long revision, String serial) {
        public static Vessel blank() {
            return new Vessel("", 0, 0L, "");
        }

        /** 带上凭证（不是空壳）。 */
        public boolean stamped() {
            return token != null && !token.isBlank();
        }

        /** 可读描述（命令/日志用，不含任何账本结论）。 */
        public String describe() {
            if (!stamped()) {
                return "无凭证";
            }
            String s = serial == null || serial.isBlank() ? "" : " 序号=" + serial;
            return "代次=" + generation + " 保管版本=" + revision + s;
        }
    }

    /** 一次核对的结论。 */
    public enum Look {
        /** 这次核对没有看见任何带凭证的实物。 */
        ABSENT,
        /** 看见了实物，但它没有凭证（空壳）。 */
        UNSTAMPED,
        /** 与当前有效正品一致。 */
        AUTHENTIC,
        /** 有凭证但对不上：旧凭证、已作废、或复制品。**不认定为真。** */
        STALE,
        /** 账本隔离中，任何实物都不认定为真。 */
        QUARANTINED,
        /** 同一凭证同时出现两枚以上：判为复制，进隔离。 */
        DUPLICATE,
        /** 账本的世界身份与这个世界不符（旧档/被塞进来的账本）。 */
        WORLD_MISMATCH
    }

    /**
     * 核对结论。
     *
     * @param authentic  与账本一致的那一枚（仅 {@link Look#AUTHENTIC} 时非空）
     * @param duplicates 多出来的实物序号（证据用，不据此认定任何一枚为真）
     */
    public record Finding(Look look, Vessel authentic, List<String> duplicates, String note) {
        public boolean genuine() {
            return look == Look.AUTHENTIC;
        }

        /** 是否应当把账本送进隔离（只对「同一凭证多枚」成立）。 */
        public boolean shouldQuarantine() {
            return look == Look.DUPLICATE;
        }

        public String describe() {
            return look.name() + "：" + (note == null ? "" : note);
        }
    }

    /** 两枚实物自称的凭证是否完全相同（复制品与原品会相同）。 */
    public static boolean sameCredential(Vessel a, Vessel b) {
        if (a == null || b == null) {
            return false;
        }
        return a.stamped() && b.stamped() && a.generation() == b.generation()
                && a.revision() == b.revision() && a.token().equals(b.token());
    }

    /**
     * 核对「一次视野里看见的实物」与账本。
     *
     * <p>调用方只许传<b>已经拿到手上的</b>实物（某个玩家背包、某个正在被打开且已加载的容器、
     * 某个已经存在的掉落物）。本方法不查世界、不加载区块、也不猜测没看见的东西。
     *
     * @param custody   服务端世界账本；{@code null} 视为账本缺失
     * @param worldId   这个存档的世界身份（来自宿命本体）
     * @param observed  这次核对看见的实物（可含空壳）
     */
    public static Finding inspect(DestinyCustody custody, String worldId, List<Vessel> observed) {
        if (custody == null) {
            return new Finding(Look.ABSENT, null, List.of(), "账本缺失（没有世界账本就没有正品）");
        }
        String mine = custody.worldId();
        if (worldId != null && !worldId.isBlank() && mine != null && !mine.isBlank()
                && !mine.equals(worldId)) {
            return new Finding(Look.WORLD_MISMATCH, null, List.of(),
                    "账本世界身份不符：账本=" + mine + " 世界=" + worldId);
        }
        if (custody.quarantined()) {
            return new Finding(Look.QUARANTINED, null, List.of(),
                    "账本隔离中：" + custody.quarantineReason());
        }

        List<Vessel> stamped = new ArrayList<>();
        if (observed != null) {
            for (Vessel v : observed) {
                if (v != null && v.stamped()) {
                    stamped.add(v);
                }
            }
        }
        if (stamped.isEmpty()) {
            String note = custody.exists()
                    ? "账本上有一枚正品，但这次核对范围内没有它（待核验）"
                    : "账本上没有实物";
            return new Finding(observed != null && !observed.isEmpty() ? Look.UNSTAMPED : Look.ABSENT,
                    null, List.of(), note);
        }

        List<Vessel> genuine = new ArrayList<>();
        for (Vessel v : stamped) {
            DestinyCustody.Check c = custody.validate(v.token(), v.generation(), v.revision());
            if (c.valid()) {
                genuine.add(v);
            }
        }
        if (genuine.size() > 1) {
            List<String> serials = new ArrayList<>();
            for (Vessel v : genuine) {
                serials.add(serialOf(v));
            }
            return new Finding(Look.DUPLICATE, null, serials,
                    "同一凭证同时出现 " + genuine.size() + " 枚：判为复制，不认定副本为真");
        }
        if (genuine.size() == 1) {
            Vessel v = genuine.get(0);
            return new Finding(Look.AUTHENTIC, v, List.of(), "与当前有效正品一致：" + v.describe());
        }
        return new Finding(Look.STALE, null, List.of(),
                "有凭证但对不上账本（旧凭证/已作废/复制品）：" + stamped.get(0).describe());
    }

    /** 序号为空时给一个稳定占位（证据里不出现空串）。 */
    public static String serialOf(Vessel v) {
        if (v == null || v.serial() == null || v.serial().isBlank()) {
            return "unnamed";
        }
        return v.serial();
    }
}
