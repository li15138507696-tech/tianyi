import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.guzhenren.tianyi.will.DestinyCustody;
import net.guzhenren.tianyi.will.DestinyVessel;
import net.guzhenren.tianyi.world.DestinyCustodyState;
import net.minecraft.nbt.CompoundTag;

/**
 * 宿命蛊世界账本在<b>真实 NBT 依赖</b>下的探针（离线，不启动游戏、不碰存档）。
 *
 * <p>与 {@code run_finite_persistence.py} 同款：只编译这一片的类 + 启动器的真实 NBT 依赖。
 * 它证明的是「落盘格式与失败关闭」；物品组件、事件顺序、容器写入仍只能在游戏里看到。
 */
public final class DestinyCustodyProbe {
    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        testFreshLedgerHasNothing();
        testWorldBinding();
        testIssueOnce();
        testTransferRevision();
        testDuplicateQuarantine();
        testCarrierLostIsOnlyCustody();
        testRoundTrip();
        testUnknownSchemaIsReadOnly();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void testFreshLedgerHasNothing() {
        DestinyCustodyState s = new DestinyCustodyState();
        check("新账本可改", s.mutationReady());
        check("新账本没有实物（不自动发物品）", !s.custody().exists()
                && s.custody().generation() == 0 && s.custody().custodyRevision() == 0L);
        check("新账本状态为 ABSENT", s.custody().state() == DestinyCustody.State.ABSENT);
        check("新账本没有绑定序号", s.boundSerial().isBlank());
        check("新账本没有隔离证据", s.isolation().isEmpty());
        CompoundTag empty = new CompoundTag();
        DestinyCustodyState loaded = DestinyCustodyState.load(empty, null);
        check("空档读成空账本、不发物品", !loaded.custody().exists() && loaded.mutationReady());
    }

    private static void testWorldBinding() {
        DestinyCustodyState s = new DestinyCustodyState();
        check("绑定世界身份成功", s.ensureWorld("world-1"));
        check("世界身份写入账本", s.custody().worldId().equals("world-1"));
        check("重复绑定同一世界幂等", s.ensureWorld("world-1"));
        check("已绑定后不许换世界身份", !s.ensureWorld("world-2")
                && s.custody().worldId().equals("world-1"));
        s.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        check("有事务之后不再接受新世界身份", !s.ensureWorld("world-3"));
    }

    private static void testIssueOnce() {
        DestinyCustodyState s = new DestinyCustodyState();
        s.ensureWorld("w");
        DestinyCustody.Result first = s.issue(DestinyCustody.Holder.entity("e-1"), "txn-i1", 5L);
        check("首次实体化成功", first.ok());
        check("实体化后代次与版本都是 1", s.custody().generation() == 1
                && s.custody().custodyRevision() == 1L);
        DestinyCustody.Result second = s.issue(DestinyCustody.Holder.entity("e-2"), "txn-i2", 6L);
        check("同一世界不得产生第二个正品", !second.ok()
                && second.reject() == DestinyCustody.Reject.ALREADY_EXISTS);
        check("被拒后账本不变", s.custody().generation() == 1
                && s.custody().holder().id().equals("e-1"));
    }

    private static void testTransferRevision() {
        DestinyCustodyState s = new DestinyCustodyState();
        s.ensureWorld("w");
        s.issue(DestinyCustody.Holder.player("p-1"), "txn-i1", 5L);
        DestinyCustody.Result stale = s.transfer("p-1", DestinyCustody.Holder.player("p-2"),
                s.custody().custodyRevision() + 9, "txn-t0");
        check("保管版本不符的转移被拒绝", !stale.ok()
                && stale.reject() == DestinyCustody.Reject.STALE_REVISION);
        DestinyCustody.Result wrong = s.transfer("p-9", DestinyCustody.Holder.player("p-2"),
                s.custody().custodyRevision(), "txn-t1");
        check("来源不符的转移被拒绝", !wrong.ok()
                && wrong.reject() == DestinyCustody.Reject.WRONG_SOURCE);
        DestinyCustody.Result ok = s.transfer("p-1", DestinyCustody.Holder.player("p-2"),
                s.custody().custodyRevision(), "txn-t2");
        check("合法转移成功并推进保管版本", ok.ok() && s.custody().custodyRevision() == 2L);
        check("转移后保管位置更新", s.custody().holder().id().equals("p-2"));
    }

    private static void testDuplicateQuarantine() {
        DestinyCustodyState s = new DestinyCustodyState();
        s.ensureWorld("w");
        s.issue(DestinyCustody.Holder.player("p-1"), "txn-i1", 5L);
        long before = s.custody().custodyRevision();
        DestinyCustody.Result dup = s.markDuplicate("ser-copy", "创造模式复制", 20L);
        check("复制证据让账本进隔离", !dup.ok()
                && dup.reject() == DestinyCustody.Reject.QUARANTINED);
        check("隔离后账本状态为 QUARANTINED", s.custody().quarantined());
        check("隔离不改保管版本（不把副本洗白）", s.custody().custodyRevision() == before);
        check("隔离证据落成一行", s.isolation().size() == 1
                && s.isolation().get(0).contains("ser-copy"));
        DestinyCustody.Result blocked = s.issue(DestinyCustody.Holder.player("p-9"), "txn-i2", 21L);
        check("隔离期间拒绝一切实体化", !blocked.ok()
                && blocked.reject() == DestinyCustody.Reject.QUARANTINED);
        check("解除隔离需要具名核验人", s.liftQuarantine("  ", "试图无痕解除") == false);
        check("具名核验后解除隔离", s.liftQuarantine("admin-uuid", "已核对实物"));
        check("解除后仍不认副本为真", s.custody().duplicates().size() == 1
                && !s.custody().quarantined());
    }

    /**
     * 载体丢失只动保管：这条断言用反射确认状态类里<b>根本没有阶段</b>，
     * 因此「火烧/岩浆/虚空把阶段也改了」在类型层面就不可能发生。
     */
    private static void testCarrierLostIsOnlyCustody() {
        DestinyCustodyState s = new DestinyCustodyState();
        s.ensureWorld("w");
        s.issue(DestinyCustody.Holder.player("p-1"), "txn-i1", 5L);
        String serial = "ser-1";
        s.bind(serial, s.custody().custodyRevision());
        s.carrierLost("lava", 30L);
        check("载体丢失后账本上不再有实物", !s.custody().exists());
        check("载体丢失只标待核验", s.custody().pendingVerification()
                && s.custody().state() == DestinyCustody.State.UNVERIFIED);
        check("载体丢失清掉绑定序号", s.boundSerial().isBlank());
        check("载体丢失不产生隔离", !s.custody().quarantined());
        check("载体丢失记下待核验原因", s.unverifiedReason().contains("lava"));
        long after = s.custody().custodyRevision();
        s.carrierLost("lava", 31L);
        check("重复丢失不刷版本号", s.custody().custodyRevision() == after);

        boolean stageField = false;
        for (Field f : DestinyCustodyState.class.getDeclaredFields()) {
            if (f.getName().toLowerCase(java.util.Locale.ROOT).contains("stage")) {
                stageField = true;
            }
        }
        check("状态类里没有阶段字段（丢失不可能改阶段）", !stageField);
        boolean advancement = false;
        for (Method m : DestinyCustodyState.class.getDeclaredMethods()) {
            String n = m.getName();
            if (n.contains("complete") || n.contains("damage") || n.contains("repair")) {
                advancement = true;
            }
        }
        check("状态类里没有推进阶段/数值的方法", !advancement);
    }

    private static void testRoundTrip() {
        DestinyCustodyState s = new DestinyCustodyState();
        s.ensureWorld("world-rt");
        s.issue(DestinyCustody.Holder.player("p-1"), "txn-i1", 5L);
        s.bind("ser-rt", s.custody().custodyRevision());
        s.markDuplicate("ser-x", "重放旧包", 40L);
        s.liftQuarantine("admin", "已核对");
        s.noteCheck("在位（登录时）", 50L, 1);
        String token = s.custody().token();
        int gen = s.custody().generation();
        long rev = s.custody().custodyRevision();

        CompoundTag tag = s.save(new CompoundTag(), null);
        check("落盘带 schemaVersion", tag.getInt("schemaVersion") == DestinyCustodyState.SCHEMA_VERSION);
        DestinyCustodyState back = DestinyCustodyState.load(tag, null);
        check("往返后账本可改", back.mutationReady());
        check("往返保留世界身份", back.custody().worldId().equals("world-rt"));
        check("往返保留代次", back.custody().generation() == gen);
        check("往返保留保管版本", back.custody().custodyRevision() == rev);
        check("往返保留凭证", back.custody().token().equals(token));
        check("往返保留在位", back.custody().exists());
        check("往返保留保管位置", back.custody().holder() != null
                && back.custody().holder().id().equals("p-1"));
        check("往返保留绑定序号", back.boundSerial().equals("ser-rt"));
        check("往返保留隔离证据", back.isolation().size() == 2);
        check("往返保留复制证据", back.custody().duplicates().size() == 1);
        check("往返保留最近核对", back.unverifiedReason().contains("登录时")
                && back.checkedVessels() == 1);
        check("往返后凭证仍可核对", back.custody().validate(token, gen, rev).valid());
        check("往返后旧序号仍是那一枚", DestinyVessel.serialOf(back.custody() == null ? null
                : new DestinyVessel.Vessel(token, gen, rev, back.boundSerial())).equals("ser-rt"));
    }

    private static void testUnknownSchemaIsReadOnly() {
        CompoundTag future = new CompoundTag();
        future.putInt("schemaVersion", 99);
        future.putString("somethingNew", "must survive");
        DestinyCustodyState s = DestinyCustodyState.load(future, null);
        check("未来 schema 只读保留", !s.mutationReady() && s.failureReason().contains("99"));
        check("未来 schema 不改账", !s.custody().exists());
        CompoundTag out = s.save(new CompoundTag(), null);
        check("未来 schema 原样写回（不丢证据）",
                out.getInt("schemaVersion") == 99
                        && out.getString("somethingNew").equals("must survive"));
        DestinyCustody.Result r = s.issue(DestinyCustody.Holder.player("p"), "txn", 1L);
        check("只读账本拒绝实体化", !r.ok()
                && r.reject() == DestinyCustody.Reject.QUARANTINED);
        check("只读账本拒绝解除隔离", !s.liftQuarantine("admin", "note"));

        CompoundTag noSchema = new CompoundTag();
        noSchema.putString("junk", "x");
        DestinyCustodyState legacy = DestinyCustodyState.load(noSchema, null);
        check("缺 schemaVersion 的文件不当账本", !legacy.mutationReady());
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }
}
