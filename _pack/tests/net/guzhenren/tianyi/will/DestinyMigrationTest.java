package net.guzhenren.tianyi.will;

/**
 * 宿命旧档迁移的离线行为测试（纯 javac + java，不依赖 Minecraft）。
 *
 * <p>锁三件事：旧三阶段到新四阶段的映射不许「升级成更强」；
 * 未知/损坏记录只隔离不猜；重复迁移幂等。
 */
public final class DestinyMigrationTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testDamagedMapping();
        testRepairedNeedsVerification();
        testDestroyedIsNotDistributed();
        testCorruptQuarantine();
        testVersionGuard();
        testNewFormatPassThrough();
        testIdempotent();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：迁移只算逻辑映射；写回 NBT 与旧档副本演练由切片 2/实机验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void testDamagedMapping() {
        DestinyMigration.Outcome o = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("DAMAGED", 0.62));
        check("旧 DAMAGED → 本体受损",
                o.stage() == Destiny.Stage.BODY_DAMAGED);
        check("旧 DAMAGED 迁移成功", o.migrated());
        check("旧 DAMAGED 不需要待核验", o.management() == DestinyMigration.Management.NORMAL);
        check("旧数值作为历史数据保留", o.preserved().get("legacy.value").equals("0.62"));
        check("旧阶段名保留", o.preserved().get("legacy.stage").equals("DAMAGED"));
        check("迁移说明可读", o.note().contains("历史"));
    }

    private static void testRepairedNeedsVerification() {
        DestinyMigration.Outcome o = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("REPAIRED", 0.95));
        check("旧 REPAIRED 不自动认定完整",
                o.stage() != Destiny.Stage.BODY_WHOLE);
        check("旧 REPAIRED 落到本体受损（保守）", o.stage() == Destiny.Stage.BODY_DAMAGED);
        check("旧 REPAIRED 进入待核验管理状态", o.pendingVerification());
        check("待核验不是第五阶段",
                o.management() == DestinyMigration.Management.PENDING_VERIFICATION
                        && Destiny.Stage.values().length == 4);
        check("待核验说明提到完整须由事务产生", o.note().contains("事务"));
    }

    private static void testDestroyedIsNotDistributed() {
        DestinyMigration.Outcome o = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("DESTROYED", 0.0));
        check("旧 DESTROYED → 本体被毁仍可重现",
                o.stage() == Destiny.Stage.BODY_DESTROYED_REVIVABLE);
        check("旧 DESTROYED 绝不映射为拆分分发",
                o.stage() != Destiny.Stage.SPLIT_AND_DISTRIBUTED);
        check("旧 DESTROYED 说明不会自动分发", o.note().contains("不会自动"));
        check("旧 DESTROYED 迁移成功", o.migrated()
                && o.management() == DestinyMigration.Management.NORMAL);
    }

    private static void testCorruptQuarantine() {
        DestinyMigration.Outcome unknown = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("WHAT_IS_THIS", 0.5));
        check("未知阶段名进入隔离", unknown.quarantined());
        check("隔离保留原始数据", unknown.preserved().get("legacy.stage").equals("WHAT_IS_THIS")
                && unknown.preserved().get("legacy.value").equals("0.5"));
        check("隔离不改规则（默认保守阶段）", unknown.stage() == Destiny.Stage.BODY_DAMAGED);
        check("隔离不算迁移成功", !unknown.migrated());

        DestinyMigration.Outcome empty = DestinyMigration.migrate(
                DestinyMigration.Legacy.bare(""));
        check("缺阶段名进入隔离", empty.quarantined());
        check("空记录进入隔离", DestinyMigration.migrate(null).quarantined());
    }

    private static void testVersionGuard() {
        DestinyMigration.Outcome future = DestinyMigration.migrate(
                new DestinyMigration.Legacy("DESTROYED", 0.0, true, 99, false, null));
        check("未来版本只隔离不覆盖", future.quarantined());
        check("未来版本保留版本号", future.preserved().get("legacy.version").equals("99"));

        DestinyMigration.Outcome negative = DestinyMigration.migrate(
                new DestinyMigration.Legacy("DAMAGED", 0.1, true, -3, false, null));
        check("非法版本隔离", negative.quarantined());

        DestinyMigration.Outcome v1 = DestinyMigration.migrate(
                DestinyMigration.Legacy.of("DAMAGED", 0.4, DestinyMigration.LEGACY_VERSION));
        check("明确旧版本可迁移", v1.migrated()
                && v1.stage() == Destiny.Stage.BODY_DAMAGED);
        check("当前版本号高于旧版本",
                DestinyMigration.CURRENT_VERSION > DestinyMigration.LEGACY_VERSION);
    }

    private static void testNewFormatPassThrough() {
        for (Destiny.Stage s : Destiny.Stage.values()) {
            DestinyMigration.Outcome o = DestinyMigration.migrate(
                    DestinyMigration.Legacy.unversioned(s.name(), 0.0));
            if (o.stage() != s || o.migrated()) {
                check("新格式阶段原样通过：" + s.name(), false);
                return;
            }
        }
        check("新格式四阶段全部原样通过", true);
        check("isNewStageName 认得新阶段",
                DestinyMigration.isNewStageName("BODY_WHOLE")
                        && !DestinyMigration.isNewStageName("REPAIRED"));
    }

    private static void testIdempotent() {
        DestinyMigration.Outcome first = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("REPAIRED", 0.95));
        DestinyMigration.Outcome second = DestinyMigration.migrate(
                DestinyMigration.Legacy.alreadyMigrated(first.stage().name(), first.management()));
        check("重复迁移不再标记迁移", !second.migrated());
        check("重复迁移阶段不变", second.stage() == first.stage());
        check("重复迁移保留待核验标记", second.pendingVerification());
        check("重复迁移不改规则说明", second.note().contains("幂等"));

        DestinyMigration.Outcome third = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("DAMAGED", 0.62));
        DestinyMigration.Outcome fourth = DestinyMigration.migrate(
                DestinyMigration.Legacy.unversioned("DAMAGED", 0.62));
        check("同一旧记录两次迁移结果一致",
                third.stage() == fourth.stage()
                        && third.management() == fourth.management()
                        && third.migrated() == fourth.migrated());
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
