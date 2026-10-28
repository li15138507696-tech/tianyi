# daotu 1.4.6 / tianfu 1.2.31 — 代码审查后的修复

> 审查方式：独立 review 子代理 + 作者复核（快照 08:49）。结论原为「不能交付」，本版修掉两个 Critical。

## Critical（已修）

### C1 重进/重生后否决权整段失效
`recentRequest()` 原写法 `tickCount - lastRequestTick <= 60` 没有下界：
玩家实体重建后 `tickCount` 从 0 起算，而 `lastRequestTick` 是静态字段，`0 - 20000 = -20000 <= 60`
直接成立 → 服务端强弹被当成「玩家按 M」放行。**每次重进/重生后的第一次攒满照样弹**。

修复：`d = tickCount - lastRequestTick; return d >= 0 && d <= 60;`，哨兵改 `-1000`（避免溢出），
并在 `ClientPlayerNetworkEvent.LoggingOut` 复位。

### C2 否决是纯静默的
服务端 `autoPrompt=true` 时只发 `open=true`、不发聊天提示；被客户端否决后玩家什么都收不到，
服务端也永远不收敛（把「关了还弹」换成「关了没反应」）。

修复：否决分支 ——
1. 本地补一条「历练已满，按 M 打开道途选择升级」；
2. 回发 `DaotuSettingPayload(false)`，把本地偏好推回服务端落库。

## Important（已修）

| 项 | 修复 |
|---|---|
| I2 强弹抢焦点（开着箱子被顶掉） | 强弹时若 `screen != null` 先挂起，客户端 tick 里等界面关掉再开；按 M 触发时仍直接丢弃 |
| I3 天命转世抹掉 vfx 偏好 | `JingJieBaoCunProcedureMixin` 在 `wipeAll` 前读、之后写回 |
| I4 偏好文件非原子写 | 临时文件 + `ATOMIC_MOVE`；解析失败时 `explicit=false`，交回服务端值兜底 |
| I5 关闭键硬编码 77 | 改用 `DaotuKeyMappings.VIEW.matches(keyCode, scanCode)`，改键后照样能关 |
| M8 控制台执行命令抛异常 | `/tianfu` 根节点加 `.requires(CommandSourceStack::isPlayer)` |
| M9 `forget()` 死分支 | 删除（1.2.27 起已无实体） |
| M10 注释夸大 | `TianfuData` 注释收窄为「体质形态 / 词条光环 / 购买爆发」 |

## 仍未处理（下一版）

- **I1 双权威不收敛**：客户端偏好 `explicit=true` 后不再采纳服务端值。现在只在「被否决」时反推一次，
  换机/换服仍可能不一致。
- **I6 待选期间每秒全量状态包** + 客户端每秒 `applyState`/`layout()`；`drawAll` 多空转一轮。
- 注释漂移若干（`DaotuStatePayload`、`DaotuClientHandler` 类注释等）。
- 天命「机缘确认后仍未开窍」的旧存档兼容分支仍未改（`legacy` 时跳过 JIYUAN）。

## 验证

- 两个模组 `javac` 均 exit 0（道途 31 源文件 / 天命 46 源文件）
- 道途 `pack_jar` 通过；天命 `pack_jar` + `verify_jar` + `check_bom` 通过
- 已安装 `daotu-1.4.6.jar` / `tianfu-1.2.31.jar`，两侧 SHA256 一致，旧版已删
- ❌ 游戏内未实测（否决、挂起、自愈、命令权限四处需实测）
