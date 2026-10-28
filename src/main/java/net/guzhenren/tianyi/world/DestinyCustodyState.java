package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.guzhenren.tianyi.will.DestinyCustody;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 宿命蛊的<b>世界账本</b>（{@code SavedData}）。
 *
 * <h2>为什么必须挂在世界上，而不是玩家身上</h2>
 * 「谁是正品」是世界级事实：换号、重生、跨维度、重连都不能改变它。
 * 挂在玩家身上，多人世界里就会出现两份互相矛盾的「宿命」——
 * 那正好是复制品想要的结果。
 *
 * <h2>账本里到底有什么（与 {@link DestinyCustody} 的分工）</h2>
 * <ul>
 *   <li>{@link DestinyCustody}：身份、代次、保管版本、在位与否、隔离与证据（纯逻辑）；</li>
 *   <li>本类：把那份纯逻辑落盘，并额外记下<b>只有世界层才知道</b>的东西——
 *       事务号序列、当前绑定的是哪一枚实物序号、以及隔离证据行；</li>
 *   <li>环境侧「待核验」原因：不改账本身份，也不是阶段，只是「为什么这次没核实上」。</li>
 * </ul>
 *
 * <h2>三条硬纪律</h2>
 * <ol>
 *   <li><b>读旧档缺失时不发物品</b>：账本缺失就是「没有实物」，不猜测、不补发。
 *       首次实体化必须是一次显式的世界级事务（管理员命令），且会失败关闭而不是自动执行。</li>
 *   <li><b>阶段不在这里</b>：本类没有、也不许有推进 {@code Destiny.Stage} 的调用。
 *       载体丢失、复制、隔离、解除隔离全都是<b>保管状态</b>。</li>
 *   <li><b>未知 schema 只读保留</b>：整份 NBT 原样留着、拒绝改账，
 *       这样新版本存档被旧版本打开过之后证据还在，不会被写回覆盖。</li>
 * </ol>
 */
public final class DestinyCustodyState extends SavedData {
    /** 存档文件名（{@code <world>/data/tianyi_destiny_custody.dat}）。 */
    public static final String STORAGE_KEY = "tianyi_destiny_custody";
    /** 账本 schema。改动结构时 +1 并写迁移。 */
    public static final int SCHEMA_VERSION = 1;
    /**
     * 逻辑身份：整个存档只有一套宿命蛊，复制品<b>沿用同一逻辑身份</b>，
     * 所以「同一逻辑身份出现两枚」一定会在账本里对不上。
     */
    public static final String LOGICAL_ID = "tianyi:suminggu";

    /**
     * {@link DestinyCustody#snapshot()} 的键。刻意写死成白名单：
     * 1.21.1 的 {@link CompoundTag} 迭代 API 在离线链上无法核对，白名单更稳；
     * 2. 白名单让「存档里多出来的键」不会被静默吞掉又被写回。
     * 若 DestinyCustody 的 snapshot 增加键，**必须**同步这里，否则新键不会落盘。
     */
    private static final String[] CUSTODY_KEYS = {
            "worldId", "logicalId", "generation", "custodyRevision", "exists", "state",
            "holderKind", "holderId", "holderDim", "holderPos", "lastTxnId", "pendingTxnId",
            "pendingVerification", "quarantineReason", "lastReason", "duplicates", "evidence",
    };

    private DestinyCustody custody = DestinyCustody.create("", LOGICAL_ID);
    /** 事务号序列：同一次世界事件只会有一个事务 id。 */
    private long txnSeq;
    /** 当前绑定的实物序号与其保管版本（证据用，不构成唯一性证明）。 */
    private String boundSerial = "";
    private long boundRevision = -1L;
    /** 隔离/复制证据（人可读行，一行一条）。 */
    private final List<String> isolation = new ArrayList<>();
    /** 环境侧「这次为什么没核实上」。不是阶段，也不是账本状态。 */
    private String unverifiedReason = "";
    private long lastCheckedAt;
    private int checkedVessels;
    /** 未知/损坏的整份存档：只读保留，拒绝改账。 */
    private CompoundTag preserved;
    private String failure = "";

    public DestinyCustodyState() {
    }

    /** 取世界唯一实例（挂主世界的数据存储，保证全维度共用一份）。 */
    public static DestinyCustodyState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DestinyCustodyState::new, DestinyCustodyState::load, null),
                STORAGE_KEY);
    }

    // ---------------------------------------------------------------- 读

    public DestinyCustody custody() {
        return custody;
    }

    /** 能不能改账。未知 schema / 损坏存档一律拒绝。 */
    public boolean mutationReady() {
        return preserved == null;
    }

    public String failureReason() {
        return failure;
    }

    public String boundSerial() {
        return boundSerial;
    }

    public long boundRevision() {
        return boundRevision;
    }

    /** 隔离/复制证据行（副本不可变）。 */
    public List<String> isolation() {
        return List.copyOf(isolation);
    }

    public String unverifiedReason() {
        return unverifiedReason;
    }

    public long lastCheckedAt() {
        return lastCheckedAt;
    }

    public int checkedVessels() {
        return checkedVessels;
    }

    public long txnSeq() {
        return txnSeq;
    }

    // ---------------------------------------------------------------- 世界身份

    /**
     * 让账本与这个存档绑定。
     *
     * <p>只在账本还是「全新」（没代次、没版本、没实物、没隔离）时写入世界身份；
     * 一旦有过任何事务就不再改，避免「换个世界 id 就把旧正品洗白」。
     *
     * @return 绑定成功（或早已绑定一致）返回 {@code true}
     */
    public boolean ensureWorld(String worldId) {
        if (!mutationReady() || worldId == null || worldId.isBlank()) {
            return false;
        }
        String mine = custody.worldId();
        if (mine != null && !mine.isBlank()) {
            return mine.equals(worldId);
        }
        if (custody.generation() != 0 || custody.custodyRevision() != 0
                || custody.exists() || custody.quarantined()) {
            return false;
        }
        custody = DestinyCustody.create(worldId, LOGICAL_ID);
        setDirty();
        return true;
    }

    // ---------------------------------------------------------------- 写：事务

    /** 下一枚事务 id（事务号序列随存档落盘，重启后不会重复）。 */
    public String nextTxnId(String prefix, long now) {
        txnSeq++;
        setDirty();
        return (prefix == null || prefix.isBlank() ? "txn" : prefix) + '-' + txnSeq + '-' + now;
    }

    /**
     * 实体化一次（唯一的世界级入口）。
     *
     * <p>账本上已经有正品时，这里必然失败（{@code ALREADY_EXISTS}）——
     * 这就是「同一世界不得产生第二个有效正品」的落点。
     */
    public DestinyCustody.Result issue(DestinyCustody.Holder to, String txnId, long now) {
        if (!mutationReady()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.QUARANTINED,
                    "账本存档不可改（" + failure + "）");
        }
        DestinyCustody.Result r = custody.issue(to, txnId, now);
        if (r.ok()) {
            setDirty();
        }
        return r;
    }

    /** 合法转移。版本不符/来源不符一律拒绝，重放同一事务幂等。 */
    public DestinyCustody.Result transfer(String fromId, DestinyCustody.Holder to,
                                          long expectedRevision, String txnId) {
        if (!mutationReady()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.QUARANTINED,
                    "账本存档不可改（" + failure + "）");
        }
        DestinyCustody.Result r = custody.transfer(fromId, to, expectedRevision, txnId);
        if (r.ok()) {
            setDirty();
        }
        return r;
    }

    /**
     * 载体丢失：只记「待核验」，<b>绝不改阶段</b>。
     *
     * <p>账本上已经没有实物时重复调用是空操作，避免一次火烧刷出一串版本号。
     */
    public void carrierLost(String reason, long now) {
        if (!mutationReady() || !custody.exists()) {
            return;
        }
        custody.onCarrierLost(reason, now);
        boundSerial = "";
        boundRevision = -1L;
        unverifiedReason = "载体丢失待核验：" + (reason == null ? "unknown" : reason);
        setDirty();
    }

    /** 发现复制证据：进隔离并保留证据，不认定副本为真。 */
    public DestinyCustody.Result markDuplicate(String serial, String note, long now) {
        if (!mutationReady()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.QUARANTINED,
                    "账本存档不可改（" + failure + "）");
        }
        DestinyCustody.Result r = custody.markDuplicate(serial, note, now);
        addIsolation("duplicate@" + now + " serial=" + serial + " " + note);
        unverifiedReason = "复制证据待核验";
        setDirty();
        return r;
    }

    /** 解除隔离：必须有具名核验人，且不认任何副本为真。 */
    public boolean liftQuarantine(String reviewer, String note) {
        if (!mutationReady()) {
            return false;
        }
        boolean ok = custody.liftQuarantine(reviewer, note);
        if (ok) {
            addIsolation("lift@" + reviewer + " " + (note == null ? "" : note));
            setDirty();
        }
        return ok;
    }

    /** 记下「当前绑定的是哪一枚实物」。只在核对通过后调用。 */
    public void bind(String serial, long revision) {
        String s = serial == null ? "" : serial;
        if (s.equals(boundSerial) && revision == boundRevision) {
            return;
        }
        boundSerial = s;
        boundRevision = revision;
        setDirty();
    }

    /**
     * 记一次核对结果（环境侧）。
     *
     * <p>值没变就不写盘：这个方法会被登录/切维度/拾取反复调用，
     * 每 tick 都 setDirty 会让存档一直在写。
     */
    public void noteCheck(String reason, long now, int vessels) {
        String r = reason == null ? "" : reason;
        if (r.equals(unverifiedReason) && vessels == checkedVessels && now == lastCheckedAt) {
            return;
        }
        boolean material = !r.equals(unverifiedReason) || vessels != checkedVessels;
        unverifiedReason = r;
        checkedVessels = vessels;
        lastCheckedAt = now;
        if (material) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------- 工具

    private void addIsolation(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        isolation.add(line.replace('\n', ' ').replace('\r', ' '));
        while (isolation.size() > 64) {
            isolation.remove(0);
        }
    }

    /** 命令/日志用的一行摘要。 */
    public String describe() {
        if (!mutationReady()) {
            return "账本存档不可改：" + failure;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("状态=").append(custody.state().name())
                .append(" 在位=").append(custody.exists())
                .append(" 代次=").append(custody.generation())
                .append(" 保管版本=").append(custody.custodyRevision());
        DestinyCustody.Holder h = custody.holder();
        sb.append(" 保管位置=").append(h == null ? "（无）" : h.kind() + ':' + h.id());
        if (custody.pendingVerification()) {
            sb.append(" 待核验=是");
        }
        if (custody.quarantined()) {
            sb.append(" 隔离=").append(custody.quarantineReason());
        }
        sb.append(" 绑定序号=").append(boundSerial.isBlank() ? "（未绑定）" : boundSerial);
        return sb.toString();
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    public CompoundTag save(CompoundTag destination, HolderLookup.Provider registries) {
        if (preserved != null) {
            // 未知 schema/损坏档：原样写回，一个字节都不改
            return preserved.copy();
        }
        CompoundTag out = new CompoundTag();
        out.putInt("schemaVersion", SCHEMA_VERSION);
        CompoundTag c = new CompoundTag();
        Map<String, String> snap = custody.snapshot();
        for (String k : CUSTODY_KEYS) {
            c.putString(k, snap.getOrDefault(k, ""));
        }
        out.put("custody", c);
        out.putLong("txnSeq", txnSeq);
        out.putString("boundSerial", boundSerial);
        out.putLong("boundRevision", boundRevision);
        out.putString("isolation", String.join("\n", isolation));
        out.putString("unverifiedReason", unverifiedReason);
        out.putLong("lastCheckedAt", lastCheckedAt);
        out.putInt("checkedVessels", checkedVessels);
        return out;
    }

    /** 读档。任何异常都退化成「只读保留 + 明确原因」，绝不静默丢证据。 */
    public static DestinyCustodyState load(CompoundTag tag, HolderLookup.Provider registries) {
        DestinyCustodyState s = new DestinyCustodyState();
        if (tag == null || tag.isEmpty()) {
            return s;
        }
        try {
            if (!tag.contains("schemaVersion", Tag.TAG_INT)) {
                s.preserved = tag.copy();
                s.failure = "缺少 schemaVersion（不作为账本使用，原样保留）";
                return s;
            }
            int schema = tag.getInt("schemaVersion");
            if (schema != SCHEMA_VERSION) {
                s.preserved = tag.copy();
                s.failure = "schema=" + schema + "（本版本只认 " + SCHEMA_VERSION
                        + "，原样保留、拒绝改账）";
                return s;
            }
            Map<String, String> m = new LinkedHashMap<>();
            if (tag.contains("custody", Tag.TAG_COMPOUND)) {
                CompoundTag c = tag.getCompound("custody");
                for (String k : CUSTODY_KEYS) {
                    if (c.contains(k, Tag.TAG_STRING)) {
                        m.put(k, c.getString(k));
                    }
                }
            }
            if (!m.isEmpty()) {
                s.custody = DestinyCustody.restore(m);
                if (s.custody == null) {
                    s.custody = DestinyCustody.create("", LOGICAL_ID);
                }
            }
            s.txnSeq = tag.getLong("txnSeq");
            s.boundSerial = tag.getString("boundSerial");
            s.boundRevision = tag.contains("boundRevision", Tag.TAG_LONG)
                    ? tag.getLong("boundRevision") : -1L;
            String iso = tag.getString("isolation");
            if (!iso.isBlank()) {
                for (String line : iso.split("\n")) {
                    if (!line.isBlank()) {
                        s.isolation.add(line.trim());
                    }
                }
                while (s.isolation.size() > 64) {
                    s.isolation.remove(0);
                }
            }
            s.unverifiedReason = tag.getString("unverifiedReason");
            s.lastCheckedAt = tag.getLong("lastCheckedAt");
            s.checkedVessels = tag.getInt("checkedVessels");
            return s;
        } catch (RuntimeException e) {
            s.preserved = tag.copy();
            s.failure = "读档失败，原样保留：" + e;
            return s;
        }
    }
}
