package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.guzhenren.tianyi.item.DestinyGuItem;
import net.guzhenren.tianyi.item.TianyiItems;
import net.guzhenren.tianyi.journal.PlayerJournal;
import net.guzhenren.tianyi.net.JournalNet;
import net.guzhenren.tianyi.will.Destiny;
import net.guzhenren.tianyi.will.DestinyCustody;
import net.guzhenren.tianyi.will.DestinyVessel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 宿命蛊「真实保管」的接线层。
 *
 * <h2>只核对已经拿在手上的东西</h2>
 * 每一次核对都必须先有一个<b>看得见的实物来源</b>：某个玩家的背包、某个正被打开的容器、
 * 某个已经存在的掉落物。本类<b>不扫全世界</b>、<b>不强制加载区块</b>、
 * 也不根据「谁应该拥有」去猜——猜出来的保管位置正好是复制品最想要的东西。
 *
 * <h2>能验证才更新持有者</h2>
 * <ul>
 *   <li>背包与「玩家正在打开的容器方块」＝可核验视图；</li>
 *   <li>其它位置（别人身上的、远方的箱子）＝不核验，只记「待核验」原因；</li>
 *   <li>转移只在<b>观测到的时刻</b>发生（拾取、登录、切维度、开容器）；
 *       只有「账本上那个人<b>此刻仍拿着一枚同凭证的实物</b>」才判为复制，
 *       这样正常交接（丢下被捡、放进箱子被取）不会被误判，而创造模式复制会。</li>
 * </ul>
 *
 * <h2>三种事绝不能做</h2>
 * <ol>
 *   <li><b>载体丢失不改阶段</b>：火/岩浆/虚空只调 {@code carrierLost}（记「待核验」），
 *       本类不出现任何推进 {@link Destiny.DestinyGu} 阶段的方法；</li>
 *   <li><b>普通死亡与杀怪无关宿命</b>：死亡不掉凭证、不损宿命，本类根本没有死亡钩子；</li>
 *   <li><b>不塞背包</b>：实体化只把实物<b>落进世界</b>（掉落物），由玩家自己捡。</li>
 * </ol>
 */
public final class DestinyCustodyService {
    private DestinyCustodyService() {
    }

    // ---------------------------------------------------------------- 账本

    /** 取账本，并让它与这个世界绑定（只在账本全新时写入，之后不可改）。 */
    public static DestinyCustodyState state(MinecraftServer server) {
        DestinyCustodyState s = DestinyCustodyState.get(server);
        if (server != null) {
            s.ensureWorld(worldId(server));
        }
        return s;
    }

    /** 这个存档的世界身份（来自宿命本体，跟存档走）。 */
    public static String worldId(MinecraftServer server) {
        return server == null ? "" : WillState.get(server).core().destiny().worldId();
    }

    // ---------------------------------------------------------------- 物品

    public static boolean isVessel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof DestinyGuItem;
    }

    /** 读物品自称的凭证（不做任何判定）。 */
    public static DestinyVessel.Vessel read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DestinyVessel.Vessel.blank();
        }
        CompoundTag t = customTag(stack);
        return new DestinyVessel.Vessel(t.getString(DestinyVessel.K_TOKEN),
                t.getInt(DestinyVessel.K_GENERATION),
                t.getLong(DestinyVessel.K_REVISION),
                t.getString(DestinyVessel.K_SERIAL));
    }

    /**
     * 把账本凭证盖到一枚实物上。
     *
     * <p>只写组件、不重排背包、不复制物品：调用方传进来的就是那一枚的引用。
     */
    public static void stamp(ItemStack stack, DestinyVessel.Vessel v, boolean scarred,
                             String ledgerState) {
        if (!isVessel(stack) || v == null) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(DestinyVessel.K_TOKEN, v.token() == null ? "" : v.token());
            tag.putInt(DestinyVessel.K_GENERATION, v.generation());
            tag.putLong(DestinyVessel.K_REVISION, v.revision());
            tag.putString(DestinyVessel.K_SERIAL, v.serial() == null ? "" : v.serial());
            tag.putBoolean(DestinyVessel.K_SCARRED, scarred);
            tag.putString(DestinyVessel.K_LEDGER, ledgerState == null ? "" : ledgerState);
        });
    }

    private static CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** 新序号：只作证据，不构成唯一性证明（可以被复制）。 */
    public static String newSerial() {
        return UUID.randomUUID().toString();
    }

    // ---------------------------------------------------------------- 核对

    /** 登录：只核对自己背包里的东西，不碰世界。 */
    public static void onLogin(ServerPlayer player) {
        checkOwnInventory(player, "登录时");
    }

    /** 退出：下线前再核一次，把保管位置落到账本上。 */
    public static void onLogout(ServerPlayer player) {
        checkOwnInventory(player, "下线前");
    }

    /** 跨维度：只核对自己背包，不扫目的地。 */
    public static void onDimensionChanged(ServerPlayer player) {
        checkOwnInventory(player, "跨维度后");
    }

    /** 拾取之后：实物此刻在背包里，这是最可靠的一次核验时刻。 */
    public static void onPickup(ServerPlayer player) {
        checkOwnInventory(player, "拾取后");
    }

    /**
     * 玩家动了一个容器方块：只读<b>这一个</b>方块实体。
     *
     * <p>它就在玩家面前（区块必然已加载），所以既不需要扫世界，也不需要强制加载。
     * 容器里没有载体就什么都不记，避免给账本刷噪声。
     */
    public static void onContainerUsed(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof Container container)) {
            return;
        }
        List<ItemStack> found = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (isVessel(s)) {
                found.add(s);
            }
        }
        if (found.isEmpty()) {
            return;
        }
        // 位于容器里的载体：保管位置记成这个方块（可验证位置），不是「谁看了一眼」
        DestinyCustody.Holder place = DestinyCustody.Holder.block(
                player.level().dimension().location().toString(), pos.asLong());
        reconcile(player, state(player.getServer()), found, "容器", place);
    }

    private static void checkOwnInventory(ServerPlayer player, String where) {
        if (player == null || player.getServer() == null) {
            return;
        }
        var inv = player.getInventory();
        List<ItemStack> found = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isVessel(s)) {
                found.add(s);
            }
        }
        DestinyCustodyState st = state(player.getServer());
        // 没看见实物、账本也没记在这个人身上：什么都不写（拾取事件很频繁，别刷账）
        if (found.isEmpty() && !heldByPlayer(st, player)) {
            return;
        }
        reconcile(player, st, found, where);
    }

    /** 账本是不是把保管位置记在这个玩家身上。 */
    private static boolean heldByPlayer(DestinyCustodyState st, ServerPlayer player) {
        DestinyCustody.Holder h = st.custody().holder();
        return st.custody().exists() && h != null && "player".equals(h.kind())
                && h.id().equals(player.getUUID().toString());
    }

    /**
     * 核对「这一次视野里的实物」与账本，并按结论做最小的账本动作。
     *
     * <p>默认把保管位置记成核对者本人（背包场景）。
     *
     * @param found 调用方已经拿到引用的实物（可含空壳／旧凭证／复制品）
     */
    public static DestinyVessel.Finding reconcile(ServerPlayer player, DestinyCustodyState st,
                                                  List<ItemStack> found, String where) {
        return reconcile(player, st, found, where,
                DestinyCustody.Holder.player(player.getUUID().toString()));
    }

    /**
     * 核对并决定保管位置。
     *
     * @param target 这次核对看到的实物所在位置（玩家背包＝player，容器＝block）
     */
    public static DestinyVessel.Finding reconcile(ServerPlayer player, DestinyCustodyState st,
                                                  List<ItemStack> found, String where,
                                                  DestinyCustody.Holder target) {
        long now = player.serverLevel().getGameTime();
        List<DestinyVessel.Vessel> vessels = new ArrayList<>();
        for (ItemStack s : found) {
            vessels.add(read(s));
        }
        DestinyCustody custody = st.custody();
        // 拿「这个存档的世界身份」与账本自报的身份对一遍：账本被塞进别的存档时一律不认
        DestinyVessel.Finding f = DestinyVessel.inspect(custody, worldId(player.getServer()), vessels);

        switch (f.look()) {
            case DUPLICATE -> {
                // 同一凭证同时出现两枚：进隔离、留证据，不认定任何一枚为真
                String serial = f.duplicates().isEmpty() ? "unnamed" : f.duplicates().get(0);
                st.markDuplicate(serial, "同一凭证同时出现在" + where + "的核对范围内", now);
                return f;
            }
            case AUTHENTIC -> {
                if (!settleHolder(player, st, f, where, now, target)) {
                    // 账本说在另一处，而那里还拿着一枚「对得上」的：这就是复制
                    return new DestinyVessel.Finding(DestinyVessel.Look.DUPLICATE, null,
                            List.of(DestinyVessel.serialOf(f.authentic())),
                            "同一凭证同时出现在两处：判为复制，已进隔离");
                }
                refreshStamps(player, st, found, f.authentic());
                st.bind(DestinyVessel.serialOf(f.authentic()), custody.custodyRevision());
                st.noteCheck("在位（" + where + "）", now, found.size());
                noteSight(player, st, where, now);
            }
            case QUARANTINED -> st.noteCheck("账本隔离中：" + custody.quarantineReason(), now,
                    found.size());
            case WORLD_MISMATCH -> st.noteCheck("账本世界身份不符（疑似别档账本）", now, found.size());
            case STALE -> {
                st.noteCheck("手上这枚凭证与账本不符（旧凭证/复制品）：" + where, now, found.size());
                reflectScarred(player.getServer(), st, found, null);
            }
            case UNSTAMPED -> st.noteCheck("有宿命蛊的空壳（没有凭证）：" + where, now, found.size());
            default -> st.noteCheck(custody.exists()
                    ? "账本上有一枚正品，但" + where + "的核对范围里没有它（待核验）"
                    : "账本上没有实物", now, found.size());
        }
        return f;
    }

    /**
     * 决定保管位置。
     *
     * <p>只有三种情况：
     * <ol>
     *   <li>账本记的就是我 —— 不动；</li>
     *   <li>账本记的是另一名在线玩家，<b>而且他手上确实还有一枚</b> ——
     *       同一凭证此刻存在两枚，判为复制；</li>
     *   <li>其余（那个人下线了/已经交出去了/放进箱子了）—— 这是一次观测到的真实转移。</li>
     * </ol>
     *
     * @return {@code false} 表示这次核对其实是复制（账本已进隔离），调用方必须停手，
     *         不能再盖戳、不能再写「在位」
     */
    private static boolean settleHolder(ServerPlayer player, DestinyCustodyState st,
                                        DestinyVessel.Finding f, String where, long now,
                                        DestinyCustody.Holder target) {
        DestinyCustody custody = st.custody();
        DestinyCustody.Holder holder = custody.holder();
        if (holder != null && holder.sameAs(target)) {
            return true;
        }
        ServerPlayer other = onlineOwner(player.getServer(), holder);
        if (other != null && other != player && carriesVessel(other, f.authentic())) {
            // 账本说在另一个人身上，而那个人手上还拿着一枚同凭证的：这正是复制
            st.markDuplicate(DestinyVessel.serialOf(f.authentic()),
                    "账本记在在线玩家 " + other.getName().getString() + " 处（他仍持有一枚），"
                            + where + " 又出现同一凭证", now);
            return false;
        }
        String from = holder == null ? "" : holder.id();
        String txn = st.nextTxnId("bind", now);
        DestinyCustody.Result r = st.transfer(from, target, custody.custodyRevision(), txn);
        if (!r.ok()) {
            // 转移被拒（版本不符/来源不符）：只记待核验，不认副本、不改阶段
            st.noteCheck("保管转移被拒绝：" + r.detail(), now, 1);
            return false;
        }
        return true;
    }

    /**
     * 这个玩家的背包里是不是还拿着一枚凭证与 {@code authentic} 相同的实物。
     *
     * <p>读的是在线玩家内存里的背包（已核验视图），不扫世界、不加载区块。
     */
    private static boolean carriesVessel(ServerPlayer player, DestinyVessel.Vessel authentic) {
        if (player == null || authentic == null) {
            return false;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isVessel(s) && DestinyVessel.sameCredential(read(s), authentic)) {
                return true;
            }
        }
        return false;
    }

    /** 把当前凭证盖回「确实是那一枚」的实物上；旧凭证/复制品不动，留着当证据。 */
    private static void refreshStamps(ServerPlayer player, DestinyCustodyState st,
                                      List<ItemStack> found, DestinyVessel.Vessel authentic) {
        if (authentic == null) {
            return;
        }
        DestinyCustody custody = st.custody();
        String serial = DestinyVessel.serialOf(authentic);
        DestinyVessel.Vessel fresh = new DestinyVessel.Vessel(custody.token(),
                custody.generation(), custody.custodyRevision(), serial);
        boolean scarred = isScarred(player.getServer(), st);
        for (ItemStack s : found) {
            if (DestinyVessel.sameCredential(read(s), authentic)) {
                stamp(s, fresh, scarred, custody.state().name());
            }
        }
    }

    /** 只更新展示用的受损标记（不改凭证、不改账本）。 */
    private static void reflectScarred(MinecraftServer server, DestinyCustodyState st,
                                       List<ItemStack> found, String ledger) {
        if (found == null || found.isEmpty()) {
            return;
        }
        boolean scarred = isScarred(server, st);
        String state = ledger == null ? st.custody().state().name() : ledger;
        for (ItemStack s : found) {
            DestinyVessel.Vessel v = read(s);
            if (v.stamped()) {
                stamp(s, v, scarred, state);
            }
        }
    }

    /**
     * 受损文案开关：本体不完整、或保管待核验、或账本隔离。
     * <b>只影响名称/提示的「赤红伤痕」语义</b>，不驱动任何规则。
     */
    public static boolean isScarred(MinecraftServer server, DestinyCustodyState st) {
        Destiny.Stage stage = server == null ? null
                : WillState.get(server).core().destiny().stage();
        return stage != Destiny.Stage.BODY_WHOLE
                || st.custody().pendingVerification()
                || st.custody().quarantined();
    }

    /** 见闻：亲手取得或亲眼核对到正品时，写<b>本人</b>的条目（不新增 payload）。 */
    private static void noteSight(ServerPlayer player, DestinyCustodyState st, String where,
                                  long now) {
        boolean added = JournalStore.note(player, new PlayerJournal.Entry(
                "destiny-vessel-" + now,
                "destiny|vessel|" + st.custody().generation(),
                PlayerJournal.Kind.KNOWLEDGE, PlayerJournal.Source.WITNESSED,
                "宿命蛊",
                "你在" + where + "亲手核对了这枚蛊：它自称宿命。真伪不由它自己说了算。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            JournalNet.sync(player);
        }
    }

    // ---------------------------------------------------------------- 载体丢失

    /**
     * 掉在地上的载体遭遇火/岩浆/虚空：只记「载体丢失待核验」，<b>绝不改阶段</b>。
     *
     * <p>先确认这一枚<b>确实是账本上的正品</b>才记：烧掉复制品不算正品丢失，
     * 否则一次「烧掉假货」就能把真品从账本上抹掉。
     */
    public static void carrierLostFrom(ItemEntity entity, String reason) {
        if (entity == null || entity.level() == null) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (!isVessel(stack)) {
            return;
        }
        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }
        DestinyCustodyState st = state(server);
        DestinyVessel.Vessel v = read(stack);
        DestinyCustody.Check c = st.custody().validate(v.token(), v.generation(), v.revision());
        if (!c.valid()) {
            return;
        }
        st.carrierLost(reason, entity.level().getGameTime());
    }

    // ---------------------------------------------------------------- 实体化（唯一入口）

    /**
     * 把载体实体化一次：落进世界（掉落物），凭证由账本新发。
     *
     * <p>这是「单一世界级初始化事务」：账本上已经有正品时必定失败
     * （{@code ALREADY_EXISTS}）；旧凭证在实体化时随代次/保管版本一起作废。
     * 默认<b>不</b>自动执行——只有管理员命令（{@code custody recover}）与取得路径
     * （{@link #materializeAt}）会走到这里。落点是执行者脚下。
     */
    public static DestinyCustody.Result materialize(ServerPlayer player, String note) {
        if (player == null || player.getServer() == null) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.BAD_TARGET,
                    "实体化需要一名在场的执行者（载体要落进世界）");
        }
        return materializeAt(player, note, player.serverLevel(),
                player.getX(), player.getY() + 0.2, player.getZ());
    }

    /**
     * 3.14.2：把载体实体化到<b>指定的世界位置</b>（仍然是掉落物，仍然只走账本这一条事务）。
     *
     * <p>唯一的新用途是「取得路径」（{@code world/DestinySeekService}）：实物必须落在该资源点的容器上方，
     * 而不是执行者脚下——否则「必须在地点取得」就成了摆设。除了落点，判定顺序与
     * {@link #materialize(ServerPlayer, String)} 完全一致：账本不可写、隔离中、已有正品，一律拒绝，
     * 且拒绝时世界里不会多出任何实体。
     *
     * <p>本方法<b>不</b>决定寿命/转数/阶段，也不推进任何阶段。
     */
    public static DestinyCustody.Result materializeAt(ServerPlayer player, String note,
            ServerLevel level, double x, double y, double z) {
        if (player == null || player.getServer() == null) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.BAD_TARGET,
                    "实体化需要一名在场的执行者（载体要落进世界）");
        }
        if (level == null) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.BAD_TARGET,
                    "实体化需要一个已加载的世界来落点");
        }
        DestinyCustodyState st = state(player.getServer());
        if (!st.mutationReady()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.QUARANTINED,
                    "账本存档不可改：" + st.failureReason());
        }
        if (st.custody().quarantined()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.QUARANTINED,
                    "账本隔离中，先解除隔离：" + st.custody().quarantineReason());
        }
        if (st.custody().exists()) {
            return DestinyCustody.Result.fail(DestinyCustody.Reject.ALREADY_EXISTS,
                    "账本上已有有效正品（代次=" + st.custody().generation() + "），不产生第二个");
        }
        long now = player.serverLevel().getGameTime();
        ItemStack stack = TianyiItems.newVessel();
        // 落点由调用方给定（管理员命令＝执行者脚下；取得路径＝该资源点容器上方），
        // 但「先算实体 UUID、再让账本决定」的顺序不变：账本拒绝时世界里不会多出任何东西。
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        // 先用实体 UUID 当保管位置，再决定实体化：账本拒绝时世界里不会多出任何东西
        DestinyCustody.Holder holder = DestinyCustody.Holder.entity(entity.getUUID().toString());
        String txn = st.nextTxnId("materialize", now);
        DestinyCustody.Result r = st.issue(holder, txn, now);
        if (!r.ok()) {
            return r;
        }
        String serial = newSerial();
        stamp(stack, new DestinyVessel.Vessel(st.custody().token(), st.custody().generation(),
                st.custody().custodyRevision(), serial), isScarred(player.getServer(), st),
                st.custody().state().name());
        level.addFreshEntity(entity);
        st.bind(serial, st.custody().custodyRevision());
        st.noteCheck("刚实体化（" + (note == null || note.isBlank() ? "未注明" : note)
                + "），等待拾取", now, 1);
        return r;
    }

    // ---------------------------------------------------------------- 工具

    private static ServerPlayer onlineOwner(MinecraftServer server, DestinyCustody.Holder holder) {
        if (server == null || holder == null || !"player".equals(holder.kind())) {
            return null;
        }
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(holder.id()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
