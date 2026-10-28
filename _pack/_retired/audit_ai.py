"""AI 降谕的安全审计（1.0.5）。

这个功能的特殊之处：**key 与 endpoint 都在玩家手里**。所以除了常规结构检查，
这里重点锁「不该出现的东西不出现在不该出现的地方」：

1. key 明文不得出现在日志 / 聊天回显 / 网络包 / 客户端代码里；
2. 只用 `HttpURLConnection`（`java.net.http` 是独立模块，模组环境可能没解析）；
3. 网络调用只在 `ai/TianyiAiClient` 里，且结果必须经 `server.execute` 回投主线程；
4. 关服必须 `shutdownNow`；
5. 目标白名单 ⊆ `TianyiObjective.Type`，数量区间与文档一致；
6. 奖励表单调递增且封顶；
7. 难度由服务端推导（`deriveDifficulty`），AI 输出的 difficulty 不参与奖励；
8. AI 任务 id 前缀恒为 `ai_`；
9. endpoint 校验含 协议 / host 白名单 / 禁止 user:pass@；
10. `client/**` 不出现 HTTP、不出现 key。
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
BASE = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
AI = os.path.join(BASE, 'ai')

problems = []


def read(p):
    return open(p, encoding='utf-8').read()


def code_only(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


files = {f: os.path.join(AI, f) for f in os.listdir(AI) if f.endswith('.java')}
if len(files) < 8:
    problems.append(f'ai 包只有 {len(files)} 个文件，预期 8 个（config/key/snapshot/prompt/client/validator/quests/templates/oracle）')

# ---------------------------------------------------------------- 1. key 不得外泄
LEAK_OK = {'TianyiAiKey.java', 'TianyiAiClient.java'}
for name, path in files.items():
    src = code_only(read(path))
    if name in LEAK_OK:
        continue
    if re.search(r'\b(ownKey|effectiveKey)\s*\(', src) or 'SERVER_KEY' in src:
        # 允许读「有没有 key」，不允许取明文
        if re.search(r'effectiveKey\s*\(\s*player\s*\)', src):
            problems.append(f'{name} 直接取用了 key 明文（只允许 TianyiAiKey/TianyiAiClient 使用）')

logs = re.findall(r'LOGGER\.\w+\(([^;]*)\)', read(os.path.join(AI, 'TianyiAiClient.java')))
for call in logs:
    if re.search(r'\b(key|authorization)\b', call, re.I):
        problems.append('TianyiAiClient 的日志里出现了 key：' + call.strip()[:60])

for name in os.listdir(os.path.join(BASE, 'net')):
    if name.endswith('Payload.java'):
        # 只看代码：注释里出现「key」这个词不算泄露（例如「按 key 取字段」）
        src = code_only(read(os.path.join(BASE, 'net', name)))
        if re.search(r'\bkey\b', src, re.I) and 'masked' not in src:
            problems.append(f'网络包里出现了 key 相关字段：{name}')

client_dir = os.path.join(BASE, 'client')
for name in os.listdir(client_dir):
    if not name.endswith('.java'):
        continue
    src = code_only(read(os.path.join(client_dir, name)))
    if re.search(r'HttpURLConnection|HttpClient|openConnection|Bearer', src):
        problems.append(f'客户端代码里出现网络调用：{name}')
    if re.search(r'\.setKey\(|effectiveKey\(', src):
        problems.append(f'客户端代码里碰了 key：{name}')

# ---------------------------------------------------------------- 2. HTTP 选型
all_src = {n: code_only(read(p)) for n, p in files.items()}
if 'java.net.http' in ''.join(all_src.values()):
    problems.append('出现了 java.net.http（模块可能未解析，改用 HttpURLConnection）')
if 'HttpURLConnection' not in all_src.get('TianyiAiClient.java', ''):
    problems.append('TianyiAiClient 没有使用 HttpURLConnection')

# ---------------------------------------------------------------- 3/4. 线程与关停
client_src = all_src.get('TianyiAiClient.java', '')
if 'server.execute' not in client_src:
    problems.append('TianyiAiClient 没有把结果回投 server.execute（会异步写玩家数据）')
if 'Executors.newFixedThreadPool' not in client_src or 'setDaemon' not in client_src:
    problems.append('TianyiAiClient 的线程池不是固定大小 + 守护线程')
if 'shutdownNow' not in client_src:
    problems.append('TianyiAiClient 缺少 shutdownNow')

events = read(os.path.join(BASE, 'TianyiEvents.java'))
if 'ServerStoppedEvent' not in events or 'TianyiAiClient.shutdown' not in events:
    problems.append('TianyiEvents 没有在 ServerStoppedEvent 里关停 AI 线程池')

# 网络调用只允许出现在 TianyiAiClient
for base, _, fs in os.walk(BASE):
    for f in fs:
        if not f.endswith('.java') or f == 'TianyiAiClient.java':
            continue
        src = code_only(read(os.path.join(base, f)))
        if re.search(r'openConnection\s*\(', src):
            problems.append(f'{f} 里出现了网络调用（只允许 TianyiAiClient）')

# ---------------------------------------------------------------- 5. 白名单与区间
validator = all_src.get('TianyiAiValidator.java', '')
snapshot = all_src.get('TianyiAiSnapshot.java', '')
objective_src = code_only(read(os.path.join(BASE, 'quest', 'TianyiObjective.java')))
op_enum = re.search(r'enum Type\s*\{(.*?)\}', objective_src, re.S)
types = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\b', op_enum.group(1))) if op_enum else set()

allowed = re.search(r'OBJECTIVES\s*=\s*List\.of\(([^)]*)\)', snapshot, re.S)
allowed_list = re.findall(r'"([A-Z_]+)"', allowed.group(1)) if allowed else []
print('AI 可用目标：', allowed_list)
if not allowed_list:
    problems.append('没解析到 TianyiAiSnapshot.OBJECTIVES')
for t in allowed_list:
    if t not in types:
        problems.append(f'白名单里的 {t} 不是 TianyiObjective.Type 的成员')

if 'KILL_RANK' in validator and 'clamp(intOf(o, "count", 5), 1, 30)' not in validator:
    problems.append('KILL_RANK 的 count 没按 1–30 钳制')
if 'maxTokens' not in all_src.get('TianyiAiConfig.java', '') and False:
    pass

# ---------------------------------------------------------------- 6. 奖励表
cfg = all_src.get('TianyiAiConfig.java', '')
# 1.0.11 起改成逗号分隔的字符串（不用 defineList，避免新建配置时 NPE）
m = re.search(r'\.define\("rewardByDifficulty",\s*"([^"]*)"\)', cfg)
if not m:
    problems.append('没解析到 rewardByDifficulty 默认值')
else:
    vals = [int(x) for x in re.findall(r'\d+', m.group(1))]
    print('奖励表：', vals)
    if len(vals) != 5:
        problems.append(f'rewardByDifficulty 应是 5 档，实际 {len(vals)}')
    if any(b <= a for a, b in zip(vals, vals[1:])):
        problems.append('rewardByDifficulty 不是单调递增：' + str(vals))
    if max(vals) > 60:
        problems.append(f'单条 AI 任务奖励上限 {max(vals)} 超过 60（会破坏天机点经济）')

# ---------------------------------------------------------------- 7. 难度由服务端推导
if 'deriveDifficulty' not in validator:
    problems.append('TianyiAiValidator 缺少 deriveDifficulty（难度必须服务端算）')
if re.search(r'points\s*=\s*intOf\(o,\s*"points"', validator):
    problems.append('校验层采用了 AI 给的奖励字段')
if 'rewardFor' not in validator:
    problems.append('校验层没有用 TianyiAiConfig.rewardFor 定奖')
if not re.search(r'clamp\(d, 1, 5\)', validator):
    problems.append('难度没有 clamp 到 1–5')

# ---------------------------------------------------------------- 8. id 前缀
quests = all_src.get('TianyiAiQuests.java', '')
if 'ID_PREFIX = "ai_"' not in quests:
    problems.append('AI 任务 id 前缀不是 ai_')
engine = read(os.path.join(BASE, 'quest', 'TianyiQuestEngine.java'))
if 'TianyiAiQuests.ID_PREFIX' not in engine:
    problems.append('引擎领取分支没有按 ai_ 前缀分流')

# ---------------------------------------------------------------- 9. endpoint 校验
key_src = all_src.get('TianyiAiKey.java', '')
for need, why in (
        ('http', '协议白名单'),
        ('allowedHosts()', 'host 白名单'),
        ('getUserInfo', '禁止 user:pass@'),
        ('ALLOW_PRIVATE_HOSTS', '私网开关'),
):
    if need not in key_src:
        problems.append(f'endpoint 校验缺少 {why}（{need}）')

# ---------------------------------------------------------------- 9b. 配置声明方式（1.0.11 的崩溃）
# NeoForge 21.1.259 的 ModConfigSpec.correct() 在**新建**配置文件时会对尚未写入的键
# 调用 ValueSpec.test(null)；defineInList 的实现是 acceptableValues.contains(value)，
# 而 List.of 对 null 抛 NPE → 首次生成配置时服务器崩在 tick loop。
all_cfg_src = '\n'.join(all_src.values())
for bad_spec in ('defineInList', 'defineList'):
    if bad_spec in all_cfg_src:
        problems.append(f'用了 {bad_spec}：新建配置文件时 correct() 会把 null 喂给 spec.test，'
                        '可能 NPE（改用 define + 自己在代码里切分/归一化）')
if 'TianyiAiConfig.mode()' not in all_cfg_src:
    problems.append('没有 mode() 归一化入口（defineInList 的替代品）')
# 只在配置类内部允许直接读 MODE.get()（mode() 里要读它），别处必须走 mode()
for _name, _src in all_src.items():
    if _name == 'TianyiAiConfig.java':
        continue
    if 'MODE.get()' in _src:
        problems.append(f'{_name} 直接读 MODE.get()：可能拿到 null，必须走 TianyiAiConfig.mode()')

# 配置文件名必须显式指定，且与文档一致
mod_src = code_only(read(os.path.join(BASE, 'TianyiMod.java')))
if 'tianyi-ai-server.toml' not in mod_src:
    problems.append('registerConfig 没有显式指定 tianyi-ai-server.toml（默认会叫 tianyi-server.toml）')

# ---------------------------------------------------------------- 10. DSH 本体凭据
dsh = all_src.get('TianyiDshKey.java', '')
if not dsh:
    problems.append('缺 ai/TianyiDshKey.java（DSH 本体凭据复用）')
else:
    if 'DSH_ENABLED' not in dsh:
        problems.append('TianyiDshKey 没有受 ai.dshEnabled 开关控制')
    if 'Files.readString' not in dsh:
        problems.append('TianyiDshKey 没读凭据文件（实现缺失）')
    # 日志里不得插值 key 内容（允许 found.isEmpty() 这种「有没有」判断）
    for call in re.findall(r'LOGGER\.\w+\(([^;]*)\)', dsh):
        sane = re.sub(r'found\s*\.\s*isEmpty\(\s*\)', '_HAS_', call)
        if re.search(r'\bcachedKey\b|\bfound\b', sane):
            problems.append('TianyiDshKey 的日志里插值了 key 内容：' + call.strip()[:60])
    # 只允许在白名单类里读文件系统（DshKey 读凭据、Providers 读自己的接口表）
    FILE_READERS = {'TianyiDshKey.java', 'TianyiAiProviders.java'}
    for name, src in all_src.items():
        if name in FILE_READERS:
            continue
        if 'Files.readString' in src or 'Paths.get(System' in src:
            problems.append(f'{name} 也在读文件系统（只允许 TianyiDshKey / TianyiAiProviders）')

# ---------------------------------------------------------------- 10b. 自定义模型接口
prov = all_src.get('TianyiAiProviders.java', '')
if not prov:
    problems.append('缺 ai/TianyiAiProviders.java（自定义模型接口）')
else:
    for need, why in (
            ('renderBody', '请求体模板渲染'),
            ('replyPath', '回复路径'),
            ('{{system}}', 'system 占位符'),
            ('{{user}}', 'user 占位符'),
            ('validateEndpoint', 'endpoint 校验（自身不校验，至少调用方要）'),
    ):
        if need not in prov and why not in ('endpoint 校验（自身不校验，至少调用方要）',):
            problems.append(f'TianyiAiProviders 缺少 {why}（{need}）')
    for call in re.findall(r'LOGGER\.\w+\(([^;]*)\)', prov):
        if re.search(r'\bkey\b|provider\.key\(\)|\.key\(\)', call):
            problems.append('TianyiAiProviders 的日志里插值了 key：' + call.strip()[:60])
    # 模板渲染失败必须返回 null 而不是把模板原样发出去
    if 'return null;' not in prov:
        problems.append('renderBody 渲染失败时没有返回 null（会把坏 body 发出去）')

cmds = code_only(read(os.path.join(BASE, 'TianyiCommands.java')))
if 'validateEndpoint(pr.endpoint())' not in cmds:
    problems.append('/tianyi ai provider 选择时没有校验 endpoint（会绕过 SSRF 白名单）')
if 'TianyiAiProviders.reload' not in cmds:
    problems.append('缺少 /tianyi ai reload（改完接口表无法热加载）')

# DSH 凭据路径不得下发给客户端
state_payload = code_only(read(os.path.join(BASE, 'net', 'TianyiStatePayload.java'))) if os.path.isfile(
    os.path.join(BASE, 'net', 'TianyiStatePayload.java')) else ''
net_src = code_only(read(os.path.join(BASE, 'TianyiNet.java')))
if 'dshKeyFile' in net_src or 'DSH_KEY_FILE' in net_src:
    problems.append('TianyiNet 引用了 dshKeyFile（配置路径不该出现在网络层）')

# ---------------------------------------------------------------- 11. 配置项齐备
for k in ('enabled', 'mode', 'endpoint', 'model', 'allowedHosts', 'allowPrivateHosts', 'timeoutMs',
          'maxConcurrent', 'maxTokens', 'temperature', 'dailyAsks', 'maxOpenQuests',
          'rewardByDifficulty', 'serverKey', 'sendPlayerName',
          'dshEnabled', 'dshKeyFile', 'dshEndpoint', 'dshModel'):
    if f'"{k}"' not in cfg:
        problems.append(f'配置缺少 {k}')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('AI 降谕 OK（key 不出域 / HttpURLConnection / 异步回投 / 关停 / 白名单与钳制 / 奖励单调 / 难度服务端算）')
