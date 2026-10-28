# 3.11 守护兽共享魂魄过程审阅

静态证据：`finite-soul-procedures-main.javap.txt`（711 行）；未实机验证。

`ChushishengchenghunpoProcedure.execute(Entity)` 不生成实体。它把该实体的 `zuida_hunpo` 设为 LivingEntity 最大生命值（非 LivingEntity 为 -1），复制到 `hunpo` 并调用 `markSyncDirty`。

`HunposiwangProcedure.execute(LevelAccessor,double,double,double,Entity)` 仅在 `zuida_hunpo != 0` 且 `hunpo <= 0` 时进入。它查询膨胀 50 格 AABB 内实体、按距离排序，找首个 Player，将该 Player 作为 DamageSource 归因对象，对传入的实体调用两次 `hurt`（`guzhenren:hunpoxiaosuan` 和 `FELL_OUT_OF_WORLD`，均 1e8）。这里受伤对象是传入实体，不是扫描到的玩家。没有可用 Player 时跳过两次 hurt。

随后非玩家实体分支检查一个评分项（原始 dump 的中文评分名编码损坏），评分为零时按 zhuanshu 选择 FANRENHUNPO / YIZHUANHUNPO / ERZHUANHUNPO / SANZHUANHUNPO / SIZHUANHUNPO / WUZHUANHUNPO。循环条件每轮重新随机取 1–5，因此不能声称均匀固定数量；可执行循环至多 5 次。每次仅服务端创建 ItemEntity，拾取延迟 10。之后取得或新建上述评分目标，并将传入实体的对应评分设为 1，防止该非玩家分支再次掉魂魄；这里没有额外的末尾 hurt。

玩家分支另按 zhuanshu 掉魂魄，循环条件每轮重新随机取 5–15，最多 15 次。文件末尾辅助方法读取 scoreboard（目标缺失返回 0），排序 lambda 以 distanceToSqr 排序，筛选 lambda 恒 true。

过程没有召唤守护兽、克隆原实体、繁殖或资源桶补货。它确实有本体魂魄掉落及死亡伤害副作用，灰狼/土狼自己的死亡 procedure 还有独立本体掉落，故天意只能绑定原 UUID、记录已确认死亡，绝不能再叠加击杀奖励或以实体缺失判死。没有玩家在 50 格内时，不能据此推定本体死亡过程完全等同普通近战击杀。外部全局事件、数据包与特殊伤害的整体效果仍需实机验证。
