// Path: src/main/java/net/guzhenren/daotu/chronicle/CoreVersionAdapter.java
package net.guzhenren.daotu.chronicle;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;

/** 本体版本边界；不暴露 PlayerVariables 可变引用，不提供任何写接口。 */
public interface CoreVersionAdapter {
    /**
     * 在服务端主线程读取玩家同 tick 快照，不创建 scoreboard 分数。
     * @param player 非空、在线的服务端玩家
     * @return 完整快照；版本不支持、附件缺失或数值非法时为空；禁止部分成功
     * @throws IllegalStateException 调用线程不是服务端主线程
     */
    Optional<CoreSnapshot> read(ServerPlayer player);
}
