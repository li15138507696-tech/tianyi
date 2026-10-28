# AI 降谕 · 设计与使用（tianyi-2.10.0）

> 一句话：**玩家拿自己的 AI，看自己的状态，给自己出题**；奖励由天道（服务端）按难度给。

---

## 〇、AI 通路（1.0.7 起四条）

| 后端 | 什么时候用 | 模型/key 从哪来 | 谁发起 |
|---|---|---|---|
| **Numen**（推荐） | 装了 Numen（`numen_api`） | Numen 自己的配置（同伴的模型） | 你对话的同伴，它主动调工具 |
| **DSH 本体**（1.0.7） | 本机跑着 DeepSeek Harness | 读 DSH 自己的 provider 凭据 | 面板按钮 → 服务端请求，**零配置** |
| **自建 HTTP** | 想用自己的 key | 玩家 `/tianyi ai setkey` 填 | 面板按钮 → 服务端请求 |
| **本地模板** | 以上都不可用 | 无 | 服务端按转数+游戏日轮转出题 |

`ai.mode = auto|numen|provider|dsh|http|local`，默认 `auto`：
Numen →（玩家自己的 key）→（**自定义接口**）→（DSH 本体凭据）→（服主兜底 key）→ 本地模板。

### 自定义模型接口（1.0.8）

想接**任何** HTTP 模型服务（不只 OpenAI 兼容），编辑 `config/tianyi-ai-providers.json`：

```json
{
  "default": "openai",
  "providers": [
    {
      "id": "openai",
      "name": "OpenAI 兼容",
      "endpoint": "https://api.deepseek.com/chat/completions",
      "model": "deepseek-chat",
      "key": "",
      "headers": { "Authorization": "Bearer {{key}}" },
      "body": {
        "model": "{{model}}",
        "messages": [
          { "role": "system", "content": "{{system}}" },
          { "role": "user", "content": "{{user}}" }
        ],
        "max_tokens": "{{maxTokens}}",
        "temperature": "{{temperature}}"
      },
      "replyPath": "choices[0].message.content"
    }
  ]
}
```

首次运行会自动生成一份**三协议示例**（OpenAI 兼容 / Anthropic Messages / Ollama 原生），照抄改就行。

**占位符**（body 与 headers 里都可用）：

| 占位符 | 含义 |
|---|---|
| `{{system}}` / `{{user}}` | 提示词，**已做 JSON 转义**，可直接塞进字符串 |
| `{{model}}` | 模型名 |
| `{{key}}` | 该接口的 key（也可用玩家自己配的 key 填空） |
| `{{maxTokens}}` / `{{temperature}}` | 数字 |

**`replyPath`** 是点号路径 + 数组下标，各家的回复位置差别都能表达：

| 服务 | replyPath |
|---|---|
| OpenAI 兼容 | `choices[0].message.content` |
| Anthropic Messages | `content[0].text` |
| Ollama `/api/chat` | `message.content` |
| Gemini `generateContent` | `candidates[0].content.parts[0].text` |
| 自己的网关 | `data.reply` 之类，随便写 |

命令：

| 命令 | 作用 |
|---|---|
| `/tianyi ai providers` | 列出所有接口（带当前选中标记） |
| `/tianyi ai provider <id>` | 选用某个接口（会先过 endpoint 白名单校验） |
| `/tianyi ai reload` | 改完 json 热加载，不必重启 |

规矩：
- 接口的 endpoint **同样要过** `ai.allowedHosts` 白名单 + 私网开关（防玩家把 URL 指向内网）。
- `key` 留空是合法的（本地 Ollama / vLLM），此时会尝试用玩家自己配的 key；
  两者都没有才视为不可用。
- body 模板渲染后如果不是合法 JSON，直接判失败并退回本地模板 —— **绝不把半成品发出去**。
- 接口 key 不进日志、不进网络包（审计强制）。

### DSH 本体凭据怎么来的（实测记录）

| 尝试 | 结果 |
|---|---|
| `GET 127.0.0.1:43120/v1/models` | **403** —— GUI 网关有 host/origin 拦截，走 `/api` + WebSocket RPC，不是给第三方 HTTP 的 |
| `127.0.0.1:18899`（`our-free-model.forward`） | 未监听（默认关闭） |
| `~/.dsh/.credentials.yaml` | 有 `refs: DEEPSEEK_API_KEY: sk-...` ← **能用的这条** |

所以实现是「**复用本体凭据**」而不是「调本体端口」：模组读 `~/.dsh/.credentials.yaml` 里的
`DEEPSEEK_API_KEY`，用 `ai.dshEndpoint`（默认 DeepSeek 官方）直接请求。装了 DSH 的机器上**零配置**。

- `ai.dshEnabled`（默认开）、`ai.dshKeyFile`（留空自动探测）、失败再退到 `~/.dsh/openai-gateway/api-key`。
- 若你把 DSH 的本地转发开起来，把 `ai.dshEndpoint` 改成 `http://127.0.0.1:18899/v1/chat/completions`
  即可（`127.0.0.1` 在默认 `allowedHosts` 内，`allowPrivateHosts` 默认 true）。
- key 只在内存：不落玩家数据、不进网络包、不进日志（`audit_ai.py` 三条强制）。
- 读不到文件 → 静默退回下一候选，不报错、不刷屏。

### Numen 通路怎么工作

```
你在游戏里对同伴说「给我降一道试炼」
   ↓ 同伴的 agent 看得到你的状态（contributeState 注入）
   ↓ 它调用工具 tianyi_destine(type, title, desc, tier, count, dim, reason)
   ↓ 我们服务端：同一个 TianyiAiValidator 校验 → 扣祈问额度 → 落库
   ↓ 工具回一句人话给 agent：「已降谕：「青狼试刃」 难度 2，完成可得 18 天机点」
```

也可以在面板点「祈问天意」：我们给同伴发一条 urgent 事件（`tianyi_ask`），让它立刻来降谕。

**关键：Numen 只负责「说什么」，不负责「给多少」。** 工具参数 schema 里没有奖励字段，
难度由服务端按目标推导、奖励查表。模型被提示词诱导也开不出天价奖励。

接入点（都是 Numen 官方扩展点）：

| Numen API | 用途 |
|---|---|
| `NumenPlugins.register("tianyi", plugin)` | 注册插件（构造期调用，内部排队到 Numen 就绪） |
| `api.contributeState(uuid -> text)` | 把转数/真元/寿元/气运/天机点/妖兽战绩/在库谕令塞进同伴上下文 |
| `api.registerEventType + api.emit` | 面板「祈问」→ 递事件给同伴 |
| `ToolRegistry.register(tool)` | 注册 `tianyi_destine`，schema 与服务端白名单同源 |

软依赖纪律（`audit_numen.py` 强制）：`com.dwinovo.numen.*` 的 import 只允许出现在
`ai/numen/` 一个类里；判断装没装只用不含 Numen 类型的 `TianyiNumenPresence.isLoaded()`；
调用点必须带这个守卫（`&&` 短路，没装时桥类根本不会被加载）。

---

## 一、它怎么运转（自建 HTTP 通路）

```
玩家按 J → 面板「降谕」页 → 点「祈问天意」（或 /tianyi ai ask）
   ↓ 服务端 · 游戏线程
① 配额    今日还剩几次？在库任务满没满？这一小时问太多了没？
   ↓
② 快照    转数 / 真元 / 寿元 / 气运 / 天机点 / 各阶妖兽击杀数 / 维度 …
           + 「可用菜单」：4 类目标、妖兽 1–5 阶、维度白名单、数量区间
   ↓ 扣 1 次额度
③ 线程池  HttpURLConnection → POST {你的 endpoint}（OpenAI 兼容）
   ↓ 15s 超时，失败不重试
④ 校验    类型白名单 / 目标映射 / 数量钳制 / 文案清洗 / 难度推导 / 查奖励表
   ↓ 失败 → 回滚额度 + 本地模板兜底
⑤ 落库    tianyi/ai/q.<id>，TTL 3 个游戏日
   ↓ server.execute 回主线程
⑥ 播报    面板刷新，打怪/修炼自动计数，达成后点「领取」进天机点
```

---

## 二、配置（`config/tianyi-ai-server.toml`）

```toml
# 文件名：config/tianyi-ai-server.toml（1.0.11 起显式指定；之前默认叫 tianyi-server.toml）
[ai]
enabled = true
mode = "auto"                 # auto / numen / provider / dsh / http / local
endpoint = "https://api.deepseek.com/chat/completions"
model = "deepseek-chat"
# 逗号分隔（1.0.11 起不是数组：数组型规格在「首次生成配置」时会 NPE）
allowedHosts = "api.openai.com,api.deepseek.com,open.bigmodel.cn,dashscope.aliyuncs.com,localhost,127.0.0.1"
allowPrivateHosts = true      # 本地 Ollama / LM Studio 需要 true
timeoutMs = 15000
maxConcurrent = 2
maxTokens = 400
temperature = 0.8
dailyAsks = 1
maxOpenQuests = 3
rewardByDifficulty = "10,18,28,40,55"
serverKey = ""                # 玩家没配 key 时的兜底（用服主的 key）
sendPlayerName = false
```

---

## 三、玩家侧命令

| 命令 | 作用 |
|---|---|
| `/tianyi ai setkey <key>` | 存自己的 key（**不回显**，连掩码都不回） |
| `/tianyi ai setendpoint <url>` | 自填 endpoint（必须命中 `allowedHosts`） |
| `/tianyi ai setmodel <model>` | 自填模型名 |
| `/tianyi ai status` | 看掩码 key、模型、endpoint host、今日剩余、在库任务 |
| `/tianyi ai clear` | 清掉自己填的三项，回到服务器默认 |
| `/tianyi ai ask` | 祈问（等于面板按钮） |

### 各家服务怎么填

| 服务 | endpoint | model |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/chat/completions` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `gpt-4o-mini` |
| 智谱 | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4-flash` |
| Ollama（本机） | `http://localhost:11434/v1/chat/completions` | `qwen2.5:7b` |
| LM Studio | `http://localhost:1234/v1/chat/completions` | 你加载的模型名 |

> Ollama 记得先 `ollama serve`，且 `allowPrivateHosts = true`（默认已开）。

---

## 四、AI 能决定什么，不能决定什么

| 维度 | AI | 服务端 |
|---|---|---|
| 题名 / 描述 / 降谕理由 | ✅ 自由写（限 24/120/80 字，去 `§`） | 清洗后采用 |
| 目标类型 | ✅ 从 4 类里选 | 不在白名单 → 整条作废 |
| 妖兽阶 / 数量 | ✅ 在区间内填 | **钳制**：阶 ≤ min(5, 转数+1)，数量 ≤ 30 |
| 修炼量 / 在线分钟 | ✅ 在区间内填 | 按转数压上限 |
| 维度 | ✅ 下界 / 末地 | 不在白名单 → 作废 |
| **奖励数值** | ❌ **无权** | 由推导难度查 `rewardByDifficulty` |

难度推导（服务端唯一口径）：

| 目标 | 难度 |
|---|---|
| KILL_RANK | `tier` 为基，数量 ≥10 / ≥20 各 +1，封顶 5 |
| CULTIVATE | `ceil(amount / 600)`，封顶 5 |
| SURVIVE_MIN | `ceil(minutes / 60)`，封顶 5 |
| DIMENSION | 下界 3 / 末地 4 |

---

## 五、状态快照长什么样

```json
{
  "schema": 1, "promptVersion": "tianyi-ai-1",
  "player": {
    "turn": 3, "stage": 1, "shouyuan": 412,
    "zy": 820, "zyMax": 1000, "qiyun": 7, "qiyunCap": 20,
    "yuanshi": 12500, "gongxian": 0, "tianji": 34,
    "talents": 12, "mainDone": 9, "aiDone": 0,
    "day": 42, "dimension": "minecraft:overworld",
    "beastsKilled": {"1": 30, "2": 5, "3": 0, "4": 0, "5": 0}
  },
  "menu": { "...": "4 类目标 / 阶 1-5 / 维度白名单 / 数量区间" }
}
```

**不含** UUID、玩家名（除非 `sendPlayerName=true`）、种子、坐标。

AI 要回的形状：

```json
{
  "title": "青狼试刃",
  "desc": "三日之内取十只一阶妖兽首级",
  "objective": {"type": "KILL_RANK", "faction": "beast", "tier": 1, "count": 10},
  "reason": "你三转未稳，宜以低阶妖兽养势"
}
```

---

## 六、安全设计（key 与 URL 都在玩家手里）

| 风险 | 处置 |
|---|---|
| key 明文落盘 | 存在玩家 `.dat` 的 `tianyi/ai.key`，**服主可读** —— 文档写明；提供 `ai.serverKey` 兜底与 `/tianyi ai clear` |
| key 外泄 | 只在 `TianyiAiKey` / `TianyiAiClient` 内使用；命令只回掩码；`setkey` 连掩码都不回；`audit_ai.py` 断言日志/包/客户端都不出现 key |
| SSRF | endpoint 必须过：协议 http/https、host 命中白名单、禁止 `user:pass@`、私网受 `allowPrivateHosts` 控制 |
| 提示注入 | AI 输出只当数据，严格 schema；非法即丢并退回本地模板 |
| 费用失控 | 日 1 次 + 每小时 ≤6 + 全局并发 ≤2 + 15s 超时 + 不自动重试 |
| 主线程阻塞 | 网络在守护线程池，结果 `server.execute` 回投 |
| 异步写玩家数据 | 回投后才改数据；玩家已下线则丢弃结果 |
| 刷奖励 | 奖励由难度表定，单条上限 55 点（≈ 两天日常量） |

---

## 七、失败时会发生什么

| 情况 | 结果 |
|---|---|
| 没配 key | 用**本地模板**出一道（`src=local`），**不扣配额** |
| AI 关闭 / endpoint 非法 | 本地模板兜底，不扣配额 |
| 超时 / 非 2xx / 非 JSON | 回滚配额 + 本地模板兜底 |
| 数量越界 / 类型不在白名单 | 整条作废 → 本地模板兜底 |
| 玩家请求途中下线 | 丢弃结果（配额已扣，无法回滚） |
| 在库满 3 条 | 拒绝祈问，提示先清任务 |

---

## 八、本地模板（兜底）出的题

按「转数 + 游戏日」轮转，5 种：

| 模板 | 目标 |
|---|---|
| 猎兽令 | 一阶（转数−1，至少 1 阶）妖兽 ×10 |
| 伏妖令 | 同阶妖兽 ×6 |
| 吐纳令 | 修炼 `min(3000, 600×转数)` |
| 坐忘令 | 在线 60 分钟 |
| 跨界令 / 立命令 | 转数 ≥2 时进下界或末地；否则在线 30 分钟 |

奖励同样走 `rewardByDifficulty`，**不会因为兜底就变便宜或变贵**。

---

## 九、未验证（必须在游戏里跑一遍）

1. 真实网络调用（各家 endpoint 的 `choices[0].message.content` 位置与格式）；
2. 面板「降谕」页交互（祈问按钮命中区、AI 任务与静态试炼混排时的滚动）；
3. 配额回滚（超时/非法 JSON 后当日次数是否真的退回来）；
4. 玩家请求途中下线（结果丢弃、不写半截数据）；
5. `gushi_xiulian_jindu` 的量级与 AI 出的 `CULTIVATE` 目标是否匹配；
6. 关服时线程池是否真被 `shutdownNow` 打断；
7. 本地模型（Ollama）在 `allowPrivateHosts=true` 下能否连通。

**服主自测一条命令（不经过游戏也能验通路）**：

```powershell
curl -s https://api.deepseek.com/chat/completions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $env:DEEPSEEK_KEY" `
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"回一个 {\"ok\":true}"}],"max_tokens":20}'
```

返回里有 `choices[0].message.content` 就说明协议对得上。
