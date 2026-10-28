// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleModule.java
package net.guzhenren.daotu.chronicle;

import java.util.Optional;
import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;
import net.guzhenren.daotu.chronicle.ChronicleModels.ModuleDecision;

/** 所有玩法纯计算，不访问世界、仓储、本体 procedure 或可变附件。 */
public abstract class ChronicleModule {
    /**
     * 生成一个模块的候选变更；禁止修改其他模块状态或递增 revision。
     * @param snapshot 非空、已验证、同所有者的本体快照
     * @param state 非空、已验证的不可变状态；snapshot.runId 可与之不同
     * @return 有效变化或新基线；无变化时为空；同一输入必须得到同一结果
     * @throws IllegalArgumentException 所有者不匹配或输入状态非法
     */
    public abstract Optional<ModuleDecision> evaluate(CoreSnapshot snapshot, ChronicleState state);
}
