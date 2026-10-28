package net.guzhenren.tianyi.will;

import java.util.List;

/**
 * 宿命蛊<b>承载物凭证</b>的离线行为测试（纯 javac + java，不依赖 Minecraft）。
 *
 * <p>锁的是这几条：物品自称的凭证不是证明；同一凭证出现两枚必判复制；
 * 旧凭证不认定为真；载体丢失之后那枚实物不再「对得上」——
 * 而且以上任何一条都跟阶段无关（本套件根本拿不到阶段）。
 */
public final class DestinyVesselTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testNoLedger();
        testEmptyVessel();
        testAuthentic();
        testDuplicate();
        testStaleAfterTransfer();
        testQuarantined();
        testCarrierLostInvalidatesVessel();
        testWorldMismatch();
        testSerialPlaceholder();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：本套件只验凭证核对逻辑；物品组件读写、事件接线与命令权限需探针/实机验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static DestinyVessel.Vessel vesselOf(DestinyCustody c, String serial) {
        return new DestinyVessel.Vessel(c.token(), c.generation(), c.custodyRevision(), serial);
    }

    private static void testNoLedger() {
        DestinyVessel.Finding f = DestinyVessel.inspect(null, "w", List.of());
        check("没有账本就没有正品", f.look() == DestinyVessel.Look.ABSENT && !f.genuine());
        DestinyVessel.Finding f2 = DestinyVessel.inspect(null, "w",
                List.of(new DestinyVessel.Vessel("tok", 1, 1L, "s")));
        check("没有账本时不承认带凭证的实物", !f2.genuine());
    }

    private static void testEmptyVessel() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        DestinyVessel.Finding empty = DestinyVessel.inspect(c, "w", List.of());
        check("没看见实物记为不存在", empty.look() == DestinyVessel.Look.ABSENT);
        DestinyVessel.Finding shell = DestinyVessel.inspect(c, "w", List.of(DestinyVessel.Vessel.blank()));
        check("空壳记为没有凭证", shell.look() == DestinyVessel.Look.UNSTAMPED && !shell.genuine());
        check("空壳的 describe 不冒充凭证", DestinyVessel.Vessel.blank().describe().equals("无凭证"));
    }

    private static void testAuthentic() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Vessel v = vesselOf(c, "ser-1");
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "w", List.of(v));
        check("与账本一致的实物被认作正品", f.genuine() && f.look() == DestinyVessel.Look.AUTHENTIC);
        check("正品携带的序号被带回", f.authentic() != null && f.authentic().serial().equals("ser-1"));
        check("正品不触发隔离", !f.shouldQuarantine());
        check("同一凭证比较为真", DestinyVessel.sameCredential(v, vesselOf(c, "ser-2")));
        check("不同代次不算同一凭证", !DestinyVessel.sameCredential(v,
                new DestinyVessel.Vessel(c.token(), c.generation() + 1, c.custodyRevision(), "ser-1")));
    }

    private static void testDuplicate() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Vessel a = vesselOf(c, "ser-a");
        DestinyVessel.Vessel b = vesselOf(c, "ser-b");
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "w", List.of(a, b));
        check("同一凭证出现两枚判为复制", f.look() == DestinyVessel.Look.DUPLICATE);
        check("复制不得被认定为真", !f.genuine() && f.authentic() == null);
        check("复制必须进隔离", f.shouldQuarantine());
        check("复制证据带两个序号", f.duplicates().size() == 2
                && f.duplicates().contains("ser-a") && f.duplicates().contains("ser-b"));
    }

    private static void testStaleAfterTransfer() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Vessel old = vesselOf(c, "ser-1");
        c.transfer("p-1", DestinyCustody.Holder.player("p-2"), c.custodyRevision(), "txn-2");
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "w", List.of(old));
        check("转移后旧凭证不再认定为真", !f.genuine() && f.look() == DestinyVessel.Look.STALE);
        check("旧凭证不触发隔离（不是复制证据）", !f.shouldQuarantine());
        DestinyVessel.Finding now = DestinyVessel.inspect(c, "w", List.of(vesselOf(c, "ser-1")));
        check("用最新凭证核对仍为正品", now.genuine());
    }

    private static void testQuarantined() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Vessel v = vesselOf(c, "ser-1");
        c.markDuplicate("ser-x", "创造模式复制", 20L);
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "w", List.of(v));
        check("隔离中连正品也不认定", f.look() == DestinyVessel.Look.QUARANTINED && !f.genuine());
        check("隔离结论里带原因", f.note().contains("隔离"));
        c.liftQuarantine("admin", "已核对");
        DestinyVessel.Finding after = DestinyVessel.inspect(c, "w", List.of(vesselOf(c, "ser-1")));
        check("解除隔离后按账本重判", after.look() == DestinyVessel.Look.AUTHENTIC);
    }

    private static void testCarrierLostInvalidatesVessel() {
        DestinyCustody c = DestinyCustody.create("w", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Vessel v = vesselOf(c, "ser-1");
        check("丢失前是正品", DestinyVessel.inspect(c, "w", List.of(v)).genuine());
        c.onCarrierLost("lava", 30L);
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "w", List.of(v));
        check("载体丢失后同一枚不再对得上", !f.genuine() && f.look() == DestinyVessel.Look.STALE);
        check("载体丢失只记待核验（保管状态）", c.pendingVerification()
                && c.state() == DestinyCustody.State.UNVERIFIED);
        check("载体丢失不产生隔离", !c.quarantined());
        check("丢失后账本上没有实物", !c.exists());
        // 账本上没有实物时说「不存在」，不说「它是复制品」
        check("空视野在丢失后记为不存在", DestinyVessel.inspect(c, "w", List.of()).look()
                == DestinyVessel.Look.ABSENT);
    }

    private static void testWorldMismatch() {
        DestinyCustody c = DestinyCustody.create("world-A", "tianyi:suminggu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-1", 10L);
        DestinyVessel.Finding f = DestinyVessel.inspect(c, "world-B", List.of(vesselOf(c, "ser-1")));
        check("账本世界身份不符时一律不认", f.look() == DestinyVessel.Look.WORLD_MISMATCH
                && !f.genuine());
        check("世界不符的结论带两个身份", f.note().contains("world-A") && f.note().contains("world-B"));
    }

    private static void testSerialPlaceholder() {
        check("缺序号时给出稳定占位",
                DestinyVessel.serialOf(new DestinyVessel.Vessel("t", 1, 1L, "")).equals("unnamed"));
        check("空实物不崩", DestinyVessel.serialOf(null).equals("unnamed"));
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
