// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleRepository.java
package net.guzhenren.daotu.chronicle;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;

/** 仓储边界；所有方法仅服务端主线程使用，不实现 SQLite/YAML。 */
public interface ChronicleRepository {
    /**
     * 读取独立根 daotu_chronicle，禁止读取时创建标签。
     * @param player 非空所有者
     * @return 不可变状态；缺失为空；损坏或未来 schema 应拒绝且保留原数据
     * @throws IllegalStateException 线程错误、数据损坏或不支持 schema
     */
    Optional<ChronicleState> load(ServerPlayer player);

    /**
     * 原子替换独立根，禁止合并或清理 daotu/tianfu。
     * @param player 非空所有者
     * @param expectedRevision 预期旧版本；首次建档为 -1
     * @param next 非空、同 UUID；revision 必须为 expectedRevision + 1
     * @return 提交成功为 true；版本冲突为 false 且不写入
     * @throws IllegalArgumentException 所有者、版本或集合长度非法
     * @throws IllegalStateException 非服务端线程或原数据损坏
     */
    boolean save(ServerPlayer player, long expectedRevision, ChronicleState next);

    /**
     * 克隆独立根，深拷贝且重复调用幂等，不依赖默认 PersistentData 复制。
     * @param original 原玩家实体，附件可访问的生命周期内
     * @param replacement 同 UUID 的新玩家；保留有效的更新版本
     * @throws IllegalArgumentException UUID 不同
     * @throws IllegalStateException 线程或存档错误
     */
    void copyForClone(ServerPlayer original, ServerPlayer replacement);
}
