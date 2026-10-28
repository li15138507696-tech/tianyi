// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleTables.java
package net.guzhenren.daotu.chronicle;

import java.util.List;
import net.guzhenren.daotu.chronicle.ChronicleModels.Milestone;

/** 纯设计数据，未实现发放逻辑；门槛不是本体常量，不代表贡献来源。 */
public final class ChronicleTables {
    public static final List<Milestone> MERIT = List.of(
            new Milestone("merit_first", 10.0, "初积功绩"),
            new Milestone("merit_record", 40.0, "功绩成册"),
            new Milestone("merit_legacy", 70.0, "功绩留名"));
    public static final List<Milestone> REPUTATION = List.of(
            new Milestone("reputation_known", 100.0, "初闻其名"),
            new Milestone("reputation_renowned", 1000.0, "声名远播"),
            new Milestone("reputation_legend", 5000.0, "传闻成篇"));
    public static final int MAX_FACTION_PAIRS = 128;
    public static final int SAMPLE_PERIOD_TICKS = 20;
    private ChronicleTables() {}
}
