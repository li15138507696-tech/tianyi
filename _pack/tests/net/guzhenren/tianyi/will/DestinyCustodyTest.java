package net.guzhenren.tianyi.will;

import java.util.Map;

/**
 * 宿命蛊保管账本的离线行为测试（纯 javac + java，不依赖 Minecraft）。
 *
 * <p>锁的是「唯一性只能来自服务端账本」这条纪律：物品 UUID 不是证明，
 * 复制品不得自动认正，载体丢失不得改阶段。
 */
public final class DestinyCustodyTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testFreshLedger();
        testUniqueIssue();
        testTransferRevision();
        testDuplicateTxnIdempotent();
        testCarrierLostKeepsStage();
        testDuplicateQuarantine();
        testValidate();
        testPendingTxnBlocksIssue();
        testSnapshotRoundTrip();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：本套件只验账本逻辑；NBT 写入、物品校验接线由切片 2 与实机验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void testFreshLedger() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        check("新账本没有实物", !c.exists());
        check("新账本状态为不存在", c.state() == DestinyCustody.State.ABSENT);
        check("新账本代次为 0", c.generation() == 0);
        check("新账本保管版本为 0", c.custodyRevision() == 0L);
        check("新账本没有承载者", c.holder() == null);
        check("新账本没有待核验", !c.pendingVerification());
        check("新账本未隔离", !c.quarantined());
    }

    private static void testUniqueIssue() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        DestinyCustody.Result first = c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-1", 10L);
        check("首次实体化成功", first.ok() && !first.replayed());
        check("实体化后代次为 1", c.generation() == 1);
        check("实体化后存在", c.exists() && c.state() == DestinyCustody.State.PRESENT);
        check("实体化后凭证非空", !first.token().isBlank() && first.token().equals(c.token()));

        DestinyCustody.Result second = c.issue(DestinyCustody.Holder.player("p-2"), "txn-issue-2", 20L);
        check("同一世界不得产生第二个正品", !second.ok()
                && second.reject() == DestinyCustody.Reject.ALREADY_EXISTS);
        check("被拒后账本不变", c.generation() == 1 && c.holder().id().equals("p-1"));

        DestinyCustody.Result replay = c.issue(DestinyCustody.Holder.player("p-2"), "txn-issue-1", 30L);
        check("重放同一实体化事务返回原凭证", replay.ok() && replay.replayed()
                && replay.token().equals(first.token()));
        check("重放不改代次", c.generation() == 1);

        DestinyCustody.Result noTxn = DestinyCustody.create("w", "l")
                .issue(DestinyCustody.Holder.player("p"), "  ", 1L);
        check("没有事务 id 不能实体化", !noTxn.ok() && noTxn.reject() == DestinyCustody.Reject.BAD_TXN);
    }

    private static void testTransferRevision() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-1", 10L);
        long rev = c.custodyRevision();

        DestinyCustody.Result stale = c.transfer("p-1", DestinyCustody.Holder.player("p-2"),
                rev + 5, "txn-t-1");
        check("保管版本不符被拒", !stale.ok()
                && stale.reject() == DestinyCustody.Reject.STALE_REVISION);

        DestinyCustody.Result wrongSource = c.transfer("p-9", DestinyCustody.Holder.player("p-2"),
                rev, "txn-t-2");
        check("来源不符被拒", !wrongSource.ok()
                && wrongSource.reject() == DestinyCustody.Reject.WRONG_SOURCE);

        DestinyCustody.Result self = c.transfer("p-1", DestinyCustody.Holder.player("p-1"),
                rev, "txn-t-3");
        check("目标与来源相同被拒", !self.ok() && self.reject() == DestinyCustody.Reject.BAD_TARGET);

        DestinyCustody.Result ok = c.transfer("p-1", DestinyCustody.Holder.player("p-2"),
                rev, "txn-t-4");
        check("合法转移成功", ok.ok() && !ok.replayed());
        check("转移后保管版本 +1", c.custodyRevision() == rev + 1);
        check("转移后承载者更新", c.holder().id().equals("p-2"));
        check("转移后凭证随之变化", ok.token().equals(c.token()));

        DestinyCustody.Result oldAgain = c.transfer("p-1", DestinyCustody.Holder.player("p-3"),
                rev, "txn-t-5");
        check("用过期的版本再转移被拒", !oldAgain.ok()
                && oldAgain.reject() == DestinyCustody.Reject.STALE_REVISION);
    }

    private static void testDuplicateTxnIdempotent() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-1", 10L);
        long rev = c.custodyRevision();
        c.transfer("p-1", DestinyCustody.Holder.player("p-2"), rev, "txn-move");
        long after = c.custodyRevision();

        DestinyCustody.Result replay = c.transfer("p-1", DestinyCustody.Holder.player("p-3"),
                rev, "txn-move");
        check("重复 txnId 按幂等成功处理", replay.ok() && replay.replayed());
        check("重复 txnId 不改保管版本", c.custodyRevision() == after);
        check("重复 txnId 不改承载者", c.holder().id().equals("p-2"));
    }

    private static void testCarrierLostKeepsStage() {
        Destiny.DestinyGu gu = new Destiny.DestinyGu("world-1");
        Destiny.Stage before = gu.stage();
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.block("minecraft:overworld", 123L), "txn-issue-1", 10L);

        c.onCarrierLost("lava", 20L);
        check("载体丢失后账本无实物", !c.exists());
        check("载体丢失记为待核验", c.pendingVerification()
                && c.state() == DestinyCustody.State.UNVERIFIED);
        check("载体丢失清空承载者", c.holder() == null);
        check("载体丢失不改宿命阶段", gu.stage() == before);
        check("载体丢失后转移被拒", c.transfer("block@123", DestinyCustody.Holder.player("p"),
                c.custodyRevision(), "txn-after-loss").reject() == DestinyCustody.Reject.ABSENT);
        check("载体丢失后旧凭证失效", !c.validate("world-1:tianyi:destiny_gu:1:1", 1, 1L).valid());

        DestinyCustody.Result reissue = c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-2", 30L);
        check("核验后可以重新实体化", reissue.ok() && c.generation() == 2);
        check("重新实体化后不再待核验", !c.pendingVerification());
    }

    private static void testDuplicateQuarantine() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-1", 10L);
        int gen = c.generation();
        String tokenBefore = c.token();

        DestinyCustody.Result dup = c.markDuplicate("item-uuid-666", "创造模式取物", 20L);
        check("复制检测进入隔离", c.quarantined() && !dup.ok());
        check("隔离保留证据", c.duplicates().size() == 1
                && c.duplicates().get(0).serial().equals("item-uuid-666"));
        check("隔离不自动认定副本为真", c.holder().id().equals("p-1")
                && c.generation() == gen && c.token().equals(tokenBefore));
        check("隔离期间不能转移", c.transfer("p-1", DestinyCustody.Holder.player("p-2"),
                c.custodyRevision(), "txn-t").reject() == DestinyCustody.Reject.QUARANTINED);
        check("隔离期间不能实体化", c.issue(DestinyCustody.Holder.player("p-9"), "txn-i2", 30L)
                .reject() == DestinyCustody.Reject.QUARANTINED);
        check("隔离期间凭证不通过", !c.validate(tokenBefore, gen, c.custodyRevision()).valid());

        check("具名核验人可解除隔离", c.liftQuarantine("admin", "确认为误报"));
        check("解除后恢复在位", c.state() == DestinyCustody.State.PRESENT);
        check("解除后证据仍在", c.duplicates().size() == 1);
        check("解除后凭证重新有效", c.validate(tokenBefore, gen, c.custodyRevision()).valid());
        check("空核验人不能解除隔离",
                !DestinyCustody.create("w", "l").liftQuarantine("", "x"));
    }

    private static void testValidate() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.player("p-1"), "txn-issue-1", 10L);
        int gen = c.generation();
        long rev = c.custodyRevision();

        check("凭证一致即通过", c.validate(c.token(), gen, rev).valid());
        check("代次不符被拒", !c.validate(c.token(), gen + 1, rev).valid());
        check("保管版本不符被拒", !c.validate(c.token(), gen, rev + 1).valid());
        check("凭证串不符被拒", !c.validate("forged-token", gen, rev).valid());
        check("空凭证被拒", !c.validate("", gen, rev).valid());
        check("拒绝原因可读", !c.validate("", gen, rev).reason().isBlank());
    }

    private static void testPendingTxnBlocksIssue() {
        DestinyCustody c = DestinyCustody.create("world-1", "tianyi:destiny_gu");
        check("可以登记未决事务", c.beginPendingTxn("txn-long-1"));
        check("未决事务期间不能实体化", c.issue(DestinyCustody.Holder.player("p-1"),
                "txn-other", 10L).reject() == DestinyCustody.Reject.PENDING_TXN);
        check("不能重复登记未决事务", !c.beginPendingTxn("txn-long-2"));
        check("结束未决事务", c.endPendingTxn("txn-long-1"));
        check("结束后可以实体化", c.issue(DestinyCustody.Holder.player("p-1"),
                "txn-issue-1", 20L).ok());
    }

    private static void testSnapshotRoundTrip() {
        DestinyCustody c = DestinyCustody.create("world-9", "tianyi:destiny_gu");
        c.issue(DestinyCustody.Holder.block("minecraft:the_nether", 777L), "txn-issue-1", 10L);
        c.markDuplicate("dup-1", "NBT 副本", 20L);
        Map<String, String> snap = c.snapshot();

        DestinyCustody back = DestinyCustody.restore(snap);
        check("快照往返保留世界身份", back.worldId().equals("world-9"));
        check("快照往返保留逻辑身份", back.logicalId().equals("tianyi:destiny_gu"));
        check("快照往返保留代次", back.generation() == c.generation());
        check("快照往返保留保管版本", back.custodyRevision() == c.custodyRevision());
        check("快照往返保留承载者", back.holder() != null && back.holder().sameAs(c.holder()));
        check("快照往返保留隔离状态", back.quarantined() && back.quarantineReason().contains("dup-1"));
        check("快照往返保留复制证据", back.duplicates().size() == 1);
        check("坏快照按保守默认处理",
                !DestinyCustody.restore(Map.of("generation", "x", "custodyRevision", "-"))
                        .exists());
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
