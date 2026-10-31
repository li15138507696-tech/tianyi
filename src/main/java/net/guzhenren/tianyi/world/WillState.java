package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.guzhenren.tianyi.will.Carrier;
import net.guzhenren.tianyi.will.DestinyMigration;
import net.guzhenren.tianyi.will.History;
import net.guzhenren.tianyi.will.Immortal;
import net.guzhenren.tianyi.will.Intent;
import net.guzhenren.tianyi.will.Knowledge;
import net.guzhenren.tianyi.will.WillConfig;
import net.guzhenren.tianyi.will.WillCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * 世界天意的持久状态（{@code SavedData}）。
 *
 * <h2>为什么是 SavedData 而不是玩家 NBT</h2>
 * 天意是<b>世界</b>的意志，不是某个玩家的附属品：它的知识、布局、载体、
 * 宿命状态和回溯快照必须随世界存档一起走。挂在玩家身上会导出两个问题：
 * 玩家换号/重生时天意记忆丢失，且多人世界里会出现多份互相冲突的「天意」。
 *
 * <h2>存什么</h2>
 * <ul>
 *   <li>决策器状态：意志余量、姿态、叠加计数、冷却（{@link WillCore#importState}）；</li>
 *   <li>知识库：情报（含来源/时间/位置/可信度）与遮蔽记录 —— 这样重启后盲区不会凭空消失；</li>
 *   <li>载体网络与区域盲区；</li>
 *   <li>宿命蛊（唯一正品）与星宿意志；</li>
 *   <li>每个玩家的仙窍；</li>
 *   <li>历史快照的<b>元数据</b>（真实的区块/实体数据在磁盘快照目录里）；</li>
 *   <li>幂等结算键：同一次世界事件不会被重复结算。</li>
 * </ul>
 */
public final class WillState extends net.minecraft.world.level.saveddata.SavedData {
    /** 存档里的数据名。 */
    public static final String NAME = "tianyi_will";
    /** 当前 schema。改动结构时 +1 并写迁移。 */
    public static final int SCHEMA = 1;

    private WillCore core;
    private final Carrier.Network carriers = new Carrier.Network();
    private final Map<UUID, Immortal.Aperture> apertures = new LinkedHashMap<>();
    private final History.Store history = new History.Store();
    private final Map<String, Boolean> settled = new HashMap<>();
    /** 上次观测到的实力（算增长趋势用）。 */
    private final Map<String, Double> lastPower = new HashMap<>();
    /** 寿蛊自然供给（3.1.x：真正接到世界里，不再是纯模型）。 */
    private Immortal.LifespanSupply shouGu;
    // 本次清理：旧产地簿（sites）与物流批次簿（cargo）已删。存档里残留的 "sites" /
    // "cargo" / "cargoSettled" 键在 load 时被**静默忽略**（不解析、不清档、不抛异常），
    // 写回时也不再写这些键。世界里真实的资源只由有限资源点（FiniteSiteState）承担。
    /** 野生兽群账（3.4.0：栖息地 / 种群 / 承载力 / 捕猎 / 迁徙）。 */
    private final net.guzhenren.tianyi.will.BeastHerd.Book herds =
            new net.guzhenren.tianyi.will.BeastHerd.Book();
    /** 身份证据链（3.6.0：多份证据互证/矛盾）。 */
    private final net.guzhenren.tianyi.will.IdentityCase.Ledger identity =
            new net.guzhenren.tianyi.will.IdentityCase.Ledger();

    public WillState() {
        this.core = new WillCore(new Knowledge.Store(), "world");
    }

    // ---------------------------------------------------------------- 取用

    /** 取世界唯一实例（挂主世界的数据存储，保证全维度共用一份）。 */
    public static WillState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        WillState::new, WillState::load, null), NAME);
    }

    public WillCore core() {
        return core;
    }

    public Carrier.Network carriers() {
        return carriers;
    }

    public History.Store history() {
        return history;
    }

    public Map<String, Double> lastPower() {
        return lastPower;
    }

    public Immortal.Aperture aperture(UUID player) {
        return apertures.computeIfAbsent(player, k -> new Immortal.Aperture(k.toString()));
    }

    public Immortal.Aperture apertureIfPresent(UUID player) {
        return apertures.get(player);
    }

    public List<Immortal.Aperture> allApertures() {
        return new ArrayList<>(apertures.values());
    }

    /** 取寿蛊供给模型（懒初始化：首次用当前时刻起算一个周期）。 */
    public Immortal.LifespanSupply shouGuSupply(long now) {
        if (shouGu == null) {
            shouGu = new Immortal.LifespanSupply(now);
        }
        return shouGu;
    }

    /** 野生兽群账。 */
    public net.guzhenren.tianyi.will.BeastHerd.Book herds() {
        return herds;
    }

    /** 身份证据链。 */
    public net.guzhenren.tianyi.will.IdentityCase.Ledger identity() {
        return identity;
    }

    // ---------------------------------------------------------------- 幂等结算

    /**
     * 同一个世界事件只能结算一次。
     *
     * @return {@code true} 表示这次是第一次（可以结算）
     */
    public boolean settleOnce(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (settled.containsKey(key)) {
            return false;
        }
        settled.put(key, Boolean.TRUE);
        setDirty();
        return true;
    }

    public int settledCount() {
        return settled.size();
    }

    // ---------------------------------------------------------------- 序列化

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", SCHEMA);
        tag.putLong("savedAt", System.currentTimeMillis());

        // 决策器
        tag.putDouble("budget", core.budget());
        tag.putLong("lastBudgetAt", core.lastBudgetAt());
        tag.putString("worldId", core.destiny().worldId());

        CompoundTag d = new CompoundTag();
        d.putString("stage", core.destiny().stage().name());
        d.putDouble("integrity", core.destiny().repairProgress());
        d.putBoolean("starAligned", core.destiny().starAligned());
        d.putString("cause", core.destiny().lastCause());
        d.putLong("at", core.destiny().lastChangeAt());
        // 3.13.0 新增键：最近一次被接受的阶段事务 id（旧档没有此键时读作空串）
        d.putString("lastTxn", core.destiny().lastTxnId());
        tag.put("destiny", d);

        CompoundTag st = new CompoundTag();
        st.putDouble("share", core.star().share());
        st.putDouble("exhaust", core.star().exhaustion());
        st.putInt("wins", core.star().contestWins());
        st.putInt("losses", core.star().contestLosses());
        tag.put("star", st);

        ListTag postures = new ListTag();
        core.postures().forEach((id, p) -> {
            CompoundTag c = new CompoundTag();
            c.putString("id", id);
            c.putString("p", p.name());
            postures.add(c);
        });
        tag.put("posture", postures);

        ListTag counts = new ListTag();
        core.actionCounts().forEach((id, n) -> {
            CompoundTag c = new CompoundTag();
            c.putString("id", id);
            c.putInt("n", n);
            counts.add(c);
        });
        tag.put("counts", counts);

        ListTag cooldowns = new ListTag();
        core.cooldowns().forEach((id, t) -> {
            CompoundTag c = new CompoundTag();
            c.putString("id", id);
            c.putLong("t", t);
            cooldowns.add(c);
        });
        tag.put("cooldowns", cooldowns);

        // 知识库
        ListTag facts = new ListTag();
        core.knowledge().snapshotFacts().forEach((subject, list) -> {
            for (Knowledge.Fact f : list) {
                CompoundTag c = new CompoundTag();
                c.putString("s", f.subject);
                c.putString("k", f.kind.name());
                c.putDouble("v", f.value);
                c.putLong("at", f.observedAt);
                c.putString("d", f.dim);
                c.putLong("p", f.pos);
                c.putDouble("c", f.confidence);
                c.putString("src", f.source.name());
                facts.add(c);
            }
        });
        tag.put("facts", facts);

        ListTag covers = new ListTag();
        core.knowledge().snapshotCovers().forEach((subject, until) -> {
            CompoundTag c = new CompoundTag();
            c.putString("s", subject);
            c.putLong("u", until);
            covers.add(c);
        });
        tag.put("covers", covers);

        // 载体
        ListTag nodes = new ListTag();
        for (Carrier.Node n : carriers.all()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", n.id);
            c.putString("kind", n.kind.name());
            c.putString("dim", n.dim);
            c.putLong("pos", n.pos);
            c.putDouble("reach", n.reach);
            c.putDouble("integ", n.integrity);
            c.putLong("blind", n.blindUntil);
            c.putLong("regrow", n.regrowTicks);
            nodes.add(c);
        }
        tag.put("carriers", nodes);

        ListTag regionBlind = new ListTag();
        carriers.snapshotRegionBlind().forEach((dim, untils) -> {
            CompoundTag c = new CompoundTag();
            c.putString("dim", dim);
            ListTag us = new ListTag();
            for (Long u : untils) {
                CompoundTag one = new CompoundTag();
                one.putLong("u", u);
                us.add(one);
            }
            c.put("untils", us);
            regionBlind.add(c);
        });
        tag.put("regionBlind", regionBlind);

        // 仙窍
        ListTag aps = new ListTag();
        for (Immortal.Aperture a : apertures.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("owner", a.owner);
            c.putInt("grade", a.grade);
            c.putDouble("stab", a.stability);
            c.putDouble("ess", a.essence);
            c.putDouble("cap", a.capacity);
            c.putDouble("ratio", a.innerTimeRatio);
            c.putLong("inner", a.innerTicks);
            c.putBoolean("indep", a.independent);
            ListTag res = new ListTag();
            a.resources.forEach((k, v) -> {
                CompoundTag r = new CompoundTag();
                r.putString("k", k);
                r.putDouble("v", v);
                res.add(r);
            });
            c.put("res", res);
            aps.add(c);
        }
        tag.put("apertures", aps);

        // 快照元数据
        ListTag snaps = new ListTag();
        for (String branch : history.branches()) {
            for (History.Snapshot s : history.onBranch(branch)) {
                CompoundTag c = new CompoundTag();
                c.putString("id", s.id);
                c.putString("branch", s.branch);
                c.putString("parent", s.parentBranch);
                c.putLong("at", s.takenAt);
                c.putString("scope", s.scope);
                c.putString("digest", s.digest);
                c.putBoolean("verified", s.verified);
                c.putString("note", s.note);
                ListTag parts = new ListTag();
                for (History.Part p : s.parts) {
                    CompoundTag pc = new CompoundTag();
                    pc.putString("sec", p.section().name());
                    pc.putInt("entries", p.entries());
                    pc.putLong("bytes", p.bytes());
                    pc.putString("digest", p.digest());
                    parts.add(pc);
                }
                c.put("parts", parts);
                snaps.add(c);
            }
        }
        tag.put("snapshots", snaps);

        // 幂等结算键
        ListTag set = new ListTag();
        for (String k : settled.keySet()) {
            set.add(StringTag.valueOf(k));
        }
        tag.put("settled", set);

        // 增长趋势基线
        CompoundTag lp = new CompoundTag();
        lastPower.forEach(lp::putDouble);
        tag.put("lastPower", lp);

        // 寿蛊自然供给（周期是 30 游戏日，必须过重启；ID 白名单每次启动重新审计）
        if (shouGu != null) {
            CompoundTag sg = new CompoundTag();
            sg.putLong("next", shouGu.nextSupplyAt);
            sg.putDouble("rate", shouGu.rate);
            sg.putDouble("drift", shouGu.drift);
            sg.putInt("produced", shouGu.produced);
            tag.put("shougu", sg);
        }

        // 本次清理：旧产地（"sites"）与物流（"cargo" / "cargoSettled"）不再写回。
        // 老存档里的这三个键保持原样躺在 NBT 里，本模组既不读也不再覆盖它。

        // 野生兽群账
        ListTag herdList = new ListTag();
        for (net.guzhenren.tianyi.will.BeastHerd h : herds.all()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", h.id);
            c.putString("beast", h.beastId);
            c.putString("dim", h.dim);
            c.putLong("anchor", h.anchorPos());
            c.putDouble("pop", h.population());
            c.putDouble("cap", h.capacity());
            c.putDouble("habitat", h.habitatQuality());
            c.putInt("harvested", h.harvested());
            c.putInt("driven", h.driven());
            c.putString("state", h.state().name());
            c.putString("cause", h.lastCause());
            c.putLong("changed", h.lastChangeAt());
            c.putInt("migrations", h.migrations());
            herdList.add(c);
        }
        tag.put("herds", herdList);

        // 身份证据链（证据一旦写下就留在当时那个 UUID 上）
        ListTag evList = new ListTag();
        for (var c0 : identity.all()) {
            for (var e : c0.all()) {
                CompoundTag ec = new CompoundTag();
                ec.putString("id", e.id());
                ec.putString("subj", e.subjectUuid());
                ec.putString("kind", e.kind().name());
                ec.putDouble("w", e.weight());
                ec.putDouble("rel", e.reliability());
                ec.putString("tag", e.identityTag());
                ec.putString("dim", e.dim());
                ec.putLong("pos", e.pos());
                ec.putLong("at", e.at());
                ec.putString("note", e.note());
                evList.add(ec);
            }
        }
        tag.put("identity", evList);

        // 机缘（B01：申请与兑现状态必须过重启，否则一重启旧的成熟机缘就丢了）
        ListTag offerList = new ListTag();
        for (var o : core.opportunities().active()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", o.id);
            c.putString("kind", o.kind.name());
            c.putString("intended", o.intended);
            c.putString("dim", o.dim);
            c.putLong("pos", o.pos);
            c.putString("origin", o.origin);
            c.putLong("created", o.createdAt);
            c.putLong("mature", o.matureAt);
            c.putLong("expires", o.expiresAt);
            c.putBoolean("bait", o.bait);
            c.putString("ploy", o.ployId);
            c.putBoolean("settled", o.settled);
            c.putLong("settledAt", o.settledAt);
            offerList.add(c);
        }
        tag.put("offers", offerList);

        // B04：压制配额的时间窗口起点（不存就等于重启清零、无上限）
        CompoundTag ws = new CompoundTag();
        core.windowStarts().forEach(ws::putLong);
        tag.put("windowStart", ws);

        return tag;
    }

    public static WillState load(CompoundTag tag, HolderLookup.Provider registries) {
        WillState s = new WillState();
        String worldId = tag.contains("worldId") ? tag.getString("worldId") : "world";

        // 决策器
        Map<String, Intent.Posture> postures = new LinkedHashMap<>();
        for (Tag t : tag.getList("posture", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                postures.put(c.getString("id"), Intent.Posture.valueOf(c.getString("p")));
            } catch (IllegalArgumentException ignored) {
                // 旧数据里的未知姿态直接丢弃，不阻断加载
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Tag t : tag.getList("counts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            counts.put(c.getString("id"), c.getInt("n"));
        }
        Map<String, Long> cooldowns = new LinkedHashMap<>();
        for (Tag t : tag.getList("cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            cooldowns.put(c.getString("id"), c.getLong("t"));
        }

        Knowledge.Store store = new Knowledge.Store();
        s.core = new WillCore(store, worldId);
        s.core.importState(tag.getDouble("budget"),
                postures, counts, cooldowns, tag.getLong("lastBudgetAt"));

        // 宿命（3.13.0：旧档经 DestinyMigration 迁移，旧键名与旧数值原样保留）
        if (tag.contains("destiny", Tag.TAG_COMPOUND)) {
            CompoundTag d = tag.getCompound("destiny");
            // destiny 子标签从来没有自己的版本字段 → 传 0（无版本旧档）
            DestinyMigration.Outcome mig = DestinyMigration.migrate(
                    new DestinyMigration.Legacy(d.getString("stage"), d.getDouble("integrity"),
                            d.contains("integrity"), 0, false, null));
            s.core.destiny().importState(mig.stage(), d.getDouble("integrity"),
                    !d.contains("starAligned") || d.getBoolean("starAligned"),
                    d.getString("cause"), d.getLong("at"), d.getString("lastTxn"));
            if (mig.migrated() || mig.quarantined()) {
                s.core.note("destiny.migrate " + mig.management() + " " + mig.note());
            }
        }
        if (tag.contains("star", Tag.TAG_COMPOUND)) {
            CompoundTag st = tag.getCompound("star");
            s.core.star().importState(st.getDouble("share"), st.getDouble("exhaust"),
                    st.getInt("wins"), st.getInt("losses"));
        }

        // 知识库
        Map<String, List<Knowledge.Fact>> facts = new LinkedHashMap<>();
        for (Tag t : tag.getList("facts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                Knowledge.Kind kind = Knowledge.Kind.valueOf(c.getString("k"));
                Knowledge.Source src = Knowledge.Source.valueOf(c.getString("src"));
                Knowledge.Fact f = new Knowledge.Fact(c.getString("s"), kind, c.getDouble("v"),
                        c.getLong("at"), c.getString("d"), c.getLong("p"),
                        c.getDouble("c"), src);
                facts.computeIfAbsent(f.subject, k -> new ArrayList<>()).add(f);
            } catch (IllegalArgumentException ignored) {
                // 未知枚举（跨版本旧档）跳过
            }
        }
        Map<String, Long> covers = new HashMap<>();
        for (Tag t : tag.getList("covers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            covers.put(c.getString("s"), c.getLong("u"));
        }
        store.importState(facts, covers);

        // 载体
        for (Tag t : tag.getList("carriers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                Carrier.Kind kind = Carrier.Kind.valueOf(c.getString("kind"));
                s.carriers.restore(c.getString("id"), kind, c.getString("dim"), c.getLong("pos"),
                        c.getDouble("reach"), c.getDouble("integ"), c.getLong("blind"),
                        c.getLong("regrow"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Tag t : tag.getList("regionBlind", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<Long> untils = new ArrayList<>();
            for (Tag u : c.getList("untils", Tag.TAG_COMPOUND)) {
                untils.add(((CompoundTag) u).getLong("u"));
            }
            s.carriers.restoreRegionBlind(c.getString("dim"), untils);
        }

        // 仙窍
        for (Tag t : tag.getList("apertures", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                UUID owner = UUID.fromString(c.getString("owner"));
                Immortal.Aperture a = new Immortal.Aperture(c.getString("owner"));
                a.grade = c.getInt("grade");
                a.stability = c.getDouble("stab");
                a.essence = c.getDouble("ess");
                a.capacity = c.getDouble("cap");
                a.innerTimeRatio = c.getDouble("ratio");
                a.innerTicks = c.getLong("inner");
                a.independent = c.getBoolean("indep");
                for (Tag r : c.getList("res", Tag.TAG_COMPOUND)) {
                    CompoundTag rc = (CompoundTag) r;
                    a.resources.put(rc.getString("k"), rc.getDouble("v"));
                }
                s.apertures.put(owner, a);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 快照
        for (Tag t : tag.getList("snapshots", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<History.Part> parts = new ArrayList<>();
            for (Tag p : c.getList("parts", Tag.TAG_COMPOUND)) {
                CompoundTag pc = (CompoundTag) p;
                try {
                    parts.add(new History.Part(History.Section.valueOf(pc.getString("sec")),
                            pc.getInt("entries"), pc.getLong("bytes"), pc.getString("digest")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            s.history.add(new History.Snapshot(c.getString("id"), c.getString("branch"),
                    c.getString("parent"), c.getLong("at"), c.getString("scope"), parts,
                    c.getString("digest"), c.getBoolean("verified"), c.getString("note")));
        }

        // 幂等键
        for (Tag t : tag.getList("settled", Tag.TAG_STRING)) {
            s.settled.put(((StringTag) t).getAsString(), Boolean.TRUE);
        }

        // 增长基线
        CompoundTag lp = tag.getCompound("lastPower");
        for (String k : lp.getAllKeys()) {
            s.lastPower.put(k, lp.getDouble(k));
        }

        // 寿蛊自然供给（ID 白名单不存盘：每次启动按本体 NpcStock.longevityIds() 重新审计）
        if (tag.contains("shougu", Tag.TAG_COMPOUND)) {
            CompoundTag sg = tag.getCompound("shougu");
            Immortal.LifespanSupply sup = new Immortal.LifespanSupply(0L);
            sup.nextSupplyAt = sg.getLong("next");
            sup.rate = sg.getDouble("rate");
            sup.drift = sg.getDouble("drift");
            sup.produced = sg.getInt("produced");
            s.shouGu = sup;
        }
        // 本次清理：旧产地 / 物流的读取分支已删除。
        // 老存档里若还有 "sites" / "cargo" / "cargoSettled" 这三个键，这里**根本不解析**：
        // 缺键与坏条目都不会抛异常，也不会把整档隔离；写回时也不再写它们。
        // 想查历史存量请用外部 NBT 工具，模组内不再提供入口。

        // 野生兽群账
        for (Tag t : tag.getList("herds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                net.guzhenren.tianyi.will.BeastHerd h = new net.guzhenren.tianyi.will.BeastHerd(
                        c.getString("id"), c.getString("beast"), c.getString("dim"),
                        c.getLong("anchor"), c.getDouble("pop"), c.getDouble("cap"),
                        c.getDouble("habitat"), net.guzhenren.tianyi.will.BeastHerd.Book.DEFAULT_GROWTH,
                        c.getLong("changed"));
                net.guzhenren.tianyi.will.BeastHerd.State st =
                        net.guzhenren.tianyi.will.BeastHerd.State.valueOf(c.getString("state"));
                h.restore(c.getDouble("pop"), c.getDouble("habitat"), c.getInt("harvested"),
                        c.getInt("driven"), st, c.getString("cause"), c.getLong("changed"),
                        c.getInt("migrations"), c.getLong("anchor"));
                s.herds.add(h);
            } catch (IllegalArgumentException ignored) {
                // 坏条目跳过
            }
        }

        // 身份证据链
        for (Tag t : tag.getList("identity", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                net.guzhenren.tianyi.will.IdentityCase.Kind kind =
                        net.guzhenren.tianyi.will.IdentityCase.Kind.valueOf(c.getString("kind"));
                s.identity.restore(new net.guzhenren.tianyi.will.IdentityCase.Evidence(
                        c.getString("id"), c.getString("subj"), kind, c.getDouble("w"),
                        c.getDouble("rel"), c.getString("tag"), c.getString("dim"),
                        c.getLong("pos"), c.getLong("at"), c.getString("note")));
            } catch (IllegalArgumentException ignored) {
                // 跨版本/损坏条目跳过
            }
        }

        // 机缘
        for (Tag t : tag.getList("offers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                var kind = net.guzhenren.tianyi.will.Opportunity.Kind
                        .valueOf(c.getString("kind"));
                var o = new net.guzhenren.tianyi.will.Opportunity.Offer(
                        c.getString("id"), kind, c.getString("intended"), c.getString("dim"),
                        c.getLong("pos"), c.getString("origin"), c.getLong("created"),
                        c.getLong("mature"), c.getLong("expires"),
                        c.getBoolean("bait"), c.getString("ploy"));
                if (c.getBoolean("settled")) {
                    o.markSettled(c.getLong("settledAt"));
                }
                s.core.opportunities().restore(o);
            } catch (IllegalArgumentException ignored) {
                // 坏条目跳过
            }
        }

        // B04：压制配额窗口起点
        CompoundTag ws = tag.getCompound("windowStart");
        java.util.Map<String, Long> starts = new java.util.LinkedHashMap<>();
        for (String k : ws.getAllKeys()) {
            starts.put(k, ws.getLong(k));
        }
        s.core.importWindowStarts(starts);

        return s;
    }
    public void ensureCarriers(MinecraftServer server) {
        if (carriers.count() > 0) {
            return;
        }
        for (var level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            // 每个维度的原点处放一处区域载体；天道道痕随世界加载自行扩散
            carriers.add("region:" + dim, Carrier.Kind.REGION, dim, 0L,
                    Math.max(WillConfig.CARRIER_REACH, 512.0));
        }
        setDirty();
    }
}
