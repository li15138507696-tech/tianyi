"""世界级天意的硬约束审计（3.0.0）。

这份审计不核对「玩法数值」，只核对**设计纪律**——那些一旦被顺手改坏，
整套东西就会退化成「随机事件 + 面板数字」的红线：

1.  **核心与接线分离**：`will/` 不得 import 任何 `net.minecraft` / `net.neoforged` /
    `java.net`，否则离线单测跑不起来，判断逻辑也会被游戏接线绑死。
2.  **隐性存在**：`world/` 与 `will/` 不得向玩家 `displayClientMessage`/`sendSystemMessage`
    任何天意判定；旧的天意播报器（TianyiVoice）不得被这两层引用。
3.  **不往背包塞东西**：`world/` 不得调用 `addItem` / `setItem` / 直接写 InventorySlot；
    扶持只能通过世界里的实物与过程。
4.  **寿蛊只动供给**：`Immortal.LifespanSupply` 不得存在 remove/take/deduct/consume 方法。
5.  **针对性有上限**：`WillConfig.CALAMITY_TARGETING_CAP` 必须存在且 < 1.0，
    `Calamity.Spec` 必须对它做 clamp。
6.  **天外之魔不免疫**：`Counter.immuneToCalamity` 必须恒返回 false。
7.  **回溯不携带背包、不因日志而全知**：`History.carriesInventory` 与
    `History.willLearnsFromLogs` 必须恒返回 false。
8.  **运行期不写回区块**：`SnapshotService` 不得在 capture 路径之外复制文件到世界目录。
9.  **旧系统退场**：天机/签到/摇命/商城/百世书 的类不得存在于源码。
10. **身份规范**：`Knowledge.requireIdentity` 存在；`world/` 里不得用 `hashCode()` 当身份。
11. **AI 不参与核心**：`will/` 不得出现 http/socket/api key 之类的东西。
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
WILL = os.path.join(SRC, 'will')
WORLD = os.path.join(SRC, 'world')

problems = []


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def code_only(src):
    """剥掉注释再做文本断言，避免注释里提到旧写法被误判。"""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    out = []
    for line in src.splitlines():
        i = line.find('//')
        out.append(line[:i] if i >= 0 else line)
    return '\n'.join(out)


def java_files(base):
    out = []
    for root, _, files in os.walk(base):
        for f in files:
            if f.endswith('.java'):
                out.append(os.path.join(root, f))
    return out


# ---------------------------------------------------------------- 1. 核心与接线分离

CORE_CLASSES = [
    'WillConfig.java', 'Knowledge.java', 'Assessment.java', 'Intent.java', 'WillCore.java',
    'Ploy.java', 'Opportunity.java', 'Calamity.java', 'Immortal.java', 'Destiny.java',
    'Carrier.java', 'Counter.java', 'History.java',
    # 3.13.0 宿命蛊重做：账本/规则/迁移必须同样与 Minecraft 分离，否则离线测试跑不起来
    'DestinyCustody.java', 'DestinyRules.java', 'DestinyMigration.java',
    # 3.13.0 切片 2：承载物凭证模型（物品自称的凭证 vs 世界账本）
    'DestinyVessel.java',
    # 3.14 天意强度：档位只能是乘数/阈值，必须与 Minecraft 分离才能离线测
    'WillForce.java',
]
for name in CORE_CLASSES:
    p = os.path.join(WILL, name)
    if not os.path.isfile(p):
        problems.append(f'will/ 缺少核心类 {name}')
        continue
    txt = read(p)
    for bad in ('net.minecraft', 'net.neoforged', 'import java.net'):
        if bad in txt:
            problems.append(f'{name} 引用了 {bad}（核心必须与 Minecraft 分离，才能离线单测）')

# ---------------------------------------------------------------- 2. 隐性存在

for f in java_files(WILL) + java_files(WORLD):
    txt = read(f)
    rel = os.path.relpath(f, SRC)
    if re.search(r'displayClientMessage\s*\(', txt):
        problems.append(f'{rel} 向玩家发了聊天消息（天意必须隐性）')
    if 'TianyiVoice' in txt:
        problems.append(f'{rel} 引用了 TianyiVoice（天意播报已退场）')
# 3.9.0 用户批准的自愿机缘直接播报与发奖仅限 EncounterService；旧后台仍须隐性。
for f in java_files(WORLD):
    txt = read(f)
    if 'sendSystemMessage' in txt and os.path.basename(f) != 'EncounterService.java':
        problems.append(f'{os.path.relpath(f, SRC)} 使用 sendSystemMessage（应只在命令层回话）')

# ---------------------------------------------------------------- 3. 不往背包塞东西

#: 只读快照的例外：AlchemyWitness 必须数一遍背包才能推断炼蛊产物（只读，不写）。
READ_ONLY_INVENTORY = {'AlchemyWitness.java'}

for f in java_files(WORLD):
    txt = read(f)
    rel = os.path.relpath(f, SRC)
    read_only = os.path.basename(f) in READ_ONLY_INVENTORY
    # 只禁「写玩家背包」，不禁「写容器」，也不禁「把槽位清空」。
    # 3.11 起有限资源点把蛊材放进木桶，barrel.setItem(0, …) 是资源点的**唯一**库存来源；
    # 3.13 起修复链要真的消耗材料，inventory.setItem(slot, ItemStack.EMPTY) 是**消耗**而非发放。
    # 旧规则把三者一起禁掉，两次都误报 FAIL。这里先把「置空消耗」摘出去再判断。
    probe = re.sub(r'\.setItem\([^;()]*ItemStack\.EMPTY\)', '/*consume*/', txt)
    banned = ['.addItem(', 'getInventory().setItem(', 'inventory.setItem(']
    if not read_only:
        banned.append('getInventory().items')
    for bad in banned:
        if bad in probe:
            problems.append(f'{rel} 直接写了玩家背包（{bad}）——扶持必须是世界里的实物')
    # 出现 setItem 时必须能认出接收方是容器（方块实体），否则视为可疑的背包写入。
    if '.setItem(' in probe and not any(marker in probe for marker in
                                       ('BarrelBlockEntity', 'Container', 'BlockEntity')):
        problems.append(f'{rel} 出现 setItem 但看不出是容器写入——请确认不是玩家背包')
    # 例外文件也要额外确认没写
    if read_only:
        for bad in ('getInventory().setItem(', '.setCount(', 'inventory.add('):
            if bad in txt:
                problems.append(f'{rel} 在只读例外里写了背包（{bad}）')

# ---------------------------------------------------------------- 4. 寿蛊只动供给

imm = read(os.path.join(WILL, 'Immortal.java'))
if 'class LifespanSupply' not in imm:
    problems.append('Immortal 里没有 LifespanSupply')
else:
    block = imm.split('class LifespanSupply', 1)[1]
    for bad in ('void remove', 'int take', 'void deduct', 'void consume'):
        if bad in block:
            problems.append(f'LifespanSupply 出现了扣除库存的方法（{bad}）')
if 'neverTouchesInventory' not in imm:
    problems.append('LifespanSupply 缺少 neverTouchesInventory 声明')

# ---------------------------------------------------------------- 5. 针对性有上限

cfg = read(os.path.join(WILL, 'WillConfig.java'))
m = re.search(r'CALAMITY_TARGETING_CAP\s*=\s*([0-9.]+)', cfg)
if not m:
    problems.append('WillConfig 里没有 CALAMITY_TARGETING_CAP')
elif float(m.group(1)) >= 1.0:
    problems.append(f'CALAMITY_TARGETING_CAP = {m.group(1)}，必须 < 1.0（否则等于必杀）')
cal = read(os.path.join(WILL, 'Calamity.java'))
if 'CALAMITY_TARGETING_CAP' not in cal:
    problems.append('Calamity 没有对针对性做上限截断')
if 'chaosInfluenced' not in cal:
    problems.append('Calamity 没有声明混沌不受天意支配')

# ---------------------------------------------------------------- 6. 天外之魔不免疫

counter = read(os.path.join(WILL, 'Counter.java'))
m = re.search(r'public static boolean immuneToCalamity\([^)]*\)\s*\{\s*return\s+(true|false)\s*;',
              counter, re.S)
if not m:
    problems.append('Counter.immuneToCalamity 结构异常（必须是一个直接返回常量的方法）')
elif m.group(1) != 'false':
    problems.append('Counter.immuneToCalamity 返回了 true —— 任何手段都不等于免疫攻击')
if 'HEAVEN_CONTROL' not in counter:
    problems.append('Counter 没有区分「天道控制」这一环')

# ---------------------------------------------------------------- 7. 回溯边界

hist = read(os.path.join(WILL, 'History.java'))
for name, want in (('carriesInventory', 'false'), ('willLearnsFromLogs', 'false')):
    m = re.search(rf'public static boolean {name}\(\)\s*\{{\s*return\s+(true|false)\s*;', hist, re.S)
    if not m:
        problems.append(f'History.{name} 结构异常')
    elif m.group(1) != want:
        problems.append(f'History.{name} 返回 {m.group(1)}，应为 {want}')

# ---------------------------------------------------------------- 8. 运行期不写回世界

snap = read(os.path.join(WORLD, 'SnapshotService.java'))
if re.search(r'Files\.(copy|move)\([^;]*getWorldPath', snap):
    problems.append('SnapshotService 在运行期把文件写回世界目录（会毁档）')
if 'saveEverything' not in snap:
    problems.append('SnapshotService 没有先落盘再复制')
if 'pending_rewind' not in snap and 'PENDING' not in snap:
    problems.append('SnapshotService 没有写待恢复标记（恢复必须两段式）')

# ---------------------------------------------------------------- 9. 旧系统退场

RETIRED = ['TianyiEcon.java', 'TianyiSign.java', 'TianyiLuck.java', 'TianyiPavilion.java',
           'LifeBook.java', 'TianyiInsight.java', 'TianyiCausality.java', 'TianyiFate.java',
           'TianyiBoard.java', 'TianyiNet.java']
for name in RETIRED:
    if os.path.isfile(os.path.join(SRC, name)):
        problems.append(f'旧系统 {name} 仍在源码里（应已退场）')
# 旧目录必须退场，但允许「只为《窥天录》重新引入」的 client/ 与 net/：
#   - client/ 只允许见闻录相关类（按键、界面、客户端状态），不得出现旧面板类
#   - net/    只允许见闻录的两个包（同步 / 动作），不得出现旧的整包同步
RETIRED_DIRS = ('quest', 'open', 'contract')
for d in RETIRED_DIRS:
    if os.path.isdir(os.path.join(SRC, d)):
        problems.append(f'旧系统目录 {d}/ 仍存在')
ALLOWED_CLIENT = {'JournalKeys.java', 'JournalScreen.java', 'JournalButton.java', 'JournalClientState.java',
                  'JournalClientEvents.java', 'JournalClientSetup.java',
                  'JournalClientHandler.java'}
if os.path.isdir(os.path.join(SRC, 'client')):
    for n in os.listdir(os.path.join(SRC, 'client')):
        if n.endswith('.java') and n not in ALLOWED_CLIENT:
            problems.append(f'client/ 出现非见闻录类 {n}（旧面板不得回潮）')
ALLOWED_NET = {'JournalSyncPayload.java', 'JournalActionPayload.java', 'JournalNet.java',
               'EncounterSyncPayload.java', 'EncounterActionPayload.java'}
if os.path.isdir(os.path.join(SRC, 'net')):
    for n in os.listdir(os.path.join(SRC, 'net')):
        if n.endswith('.java') and n not in ALLOWED_NET:
            problems.append(f'net/ 出现非见闻录包 {n}（旧整包同步不得回潮）')
for f in java_files(SRC):
    txt = read(f)
    for name in RETIRED:
        cls = name[:-5]
        if cls in txt and os.path.basename(f) != name:
            problems.append(f'{os.path.relpath(f, SRC)} 仍引用已退场的 {cls}')

# 命令树里不得出现旧入口
cmds = read(os.path.join(SRC, 'TianyiCommands.java'))
for bad in ('literal("shop")', 'literal("sign")', 'literal("luck")', 'literal("pavilion")',
            'literal("rebirth")', 'literal("fate")'):
    if bad in cmds:
        problems.append(f'命令树仍有旧入口 {bad}')

# ---------------------------------------------------------------- 10. 身份规范

if 'requireIdentity' not in read(os.path.join(WILL, 'Knowledge.java')):
    problems.append('Knowledge 缺少 requireIdentity（身份必须用完整 UUID/注册 ID）')
for f in java_files(WORLD):
    txt = read(f)
    if re.search(r'\.hashCode\(\)\s*[;)]', txt) and 'UUID' not in txt:
        problems.append(f'{os.path.relpath(f, SRC)} 用 hashCode 当身份')

# ---------------------------------------------------------------- 11. AI 不参与核心

for f in java_files(WILL):
    txt = read(f)
    for bad in ('http', 'Socket', 'apiKey', 'api_key', 'llm', 'openai'):
        if bad in txt:
            problems.append(f'{os.path.relpath(f, SRC)} 出现 AI/网络痕迹（{bad}）')

# ---------------------------------------------------------------- 12. 资源产地五本账

site_path = os.path.join(WILL, 'ResourceSite.java')
if not os.path.isfile(site_path):
    problems.append('will/ 缺少 ResourceSite（资源产地的储量/恢复/上限/归属/破坏）')
else:
    site = read(site_path)
    for need in ('public int harvest(', 'public double regen(', 'public boolean destroy(',
                 'maxStock', 'owner', 'DEPLETED', 'DESTROYED'):
        if need not in site:
            problems.append(f'ResourceSite 缺少「{need}」')
    # 采集必须夹在储量以内（防刷物品的算法锁）
    if 'Math.min(can, want)' not in site:
        problems.append('ResourceSite.harvest 没有把采集量夹在储量以内（可能被刷物品）')
    # 已毁的产地不能再恢复
    if 'state == State.DESTROYED || regenPerDay <= 0.0' not in site:
        problems.append('ResourceSite.regen 没有拦住已毁的产地')

sites_world = os.path.join(WORLD, 'WorldSites.java')
if not os.path.isfile(sites_world):
    problems.append('world/ 缺少 WorldSites（产地没有接到世界里）')
else:
    sw = read(sites_world)
    for need in ('harvest(', 'scrambleOwnedBy(', 'isLoaded('):
        if need not in sw:
            problems.append(f'WorldSites 缺少「{need}」')
    if 'addItem(' in sw or 'setItem(' in sw:
        problems.append('WorldSites 直接写玩家背包（产出必须只落地）')

state_txt = read(os.path.join(WORLD, 'WillState.java'))
if '"sites"' not in state_txt:
    problems.append('WillState 没有持久化资源产地（重启会丢）')

# ---------------------------------------------------------------- 13. 物流幂等结算

cargo_path = os.path.join(WILL, 'CargoBatch.java')
if not os.path.isfile(cargo_path):
    problems.append('will/ 缺少 CargoBatch（物流与幂等结算）')
else:
    cargo = read(cargo_path)
    for need in ('public Map<String, Integer> settle(', 'public Map<String, Integer> block(',
                 'public Map<String, Integer> lose(', 'markSettled(', 'alreadySettled('):
        if need not in cargo:
            problems.append(f'CargoBatch 缺少「{need}」')
    # 幂等闸门：只有「在途且在点」才允许结算
    if 'if (state != State.IN_TRANSIT || !arrived(now))' not in cargo:
        problems.append('CargoBatch.settle 没有落「在途 + 到点」的幂等闸门')
    # 结算前必须查结算簿
    if 'if (settled.contains(b.id))' not in cargo:
        problems.append('CargoBatch.Book.tick 结算前没有查结算簿（可能重复发货）')

if '"cargoSettled"' not in state_txt:
    problems.append('WillState 没有持久化物流结算簿（重启后可能重复发货）')
if '"cargo"' not in state_txt:
    problems.append('WillState 没有持久化在途批次')

# ---------------------------------------------------------------- 14. 野生兽群

herd_path = os.path.join(WILL, 'BeastHerd.java')
if not os.path.isfile(herd_path):
    problems.append('will/ 缺少 BeastHerd（栖息地/种群/承载力）')
else:
    herd = read(herd_path)
    for need in ('public int drive(', 'public int harvest(', 'public double grow(',
                 'public boolean migrate(', 'capacity', 'COLLAPSED'):
        if need not in herd:
            problems.append(f'BeastHerd 缺少「{need}」')
    # 生成即扣：赶兽必须夹在种群以内
    if 'int got = Math.min(can, count);' not in herd:
        problems.append('BeastHerd.drive 没有把赶出的数量夹在种群以内（会凭空刷怪）')

herds_world = os.path.join(WORLD, 'BeastHerds.java')
if not os.path.isfile(herds_world):
    problems.append('world/ 缺少 BeastHerds（兽群没有接到世界里）')
else:
    hw = read(herds_world)
    for need in ('herd.drive(', 'tianyi_herd', 'recordKill(', 'census('):
        if need not in hw:
            problems.append(f'BeastHerds 缺少「{need}」')
    # 先扣种群再生成：drive 必须出现在 spawnEntity 之前
    if hw.find('herd.drive(') > hw.find('WorldExec.spawnEntity('):
        problems.append('BeastHerds 先生成后扣种群（顺序反了，等于白送兽）')

if '"herds"' not in state_txt:
    problems.append('WillState 没有持久化野生兽群账')

# ---------------------------------------------------------------- 15. 炼蛊证据绑定

alch_path = os.path.join(WILL, 'AlchemyTracker.java')
if not os.path.isfile(alch_path):
    problems.append('will/ 缺少 AlchemyTracker（炼蛊证据判定）')
else:
    alch = read(alch_path)
    # 必须用精确满值，不能用 >= 100（否则 100.0001 也算完成）
    if 'COMPLETE = 100.0' not in alch:
        problems.append('AlchemyTracker 没有把完成判据固定为 100.0')
    if re.search(r'progress\s*>=\s*COMPLETE', alch):
        problems.append('AlchemyTracker 用 >= 判完成（会把 100.0001 也算成完成）')
    for need in ('seenBelow', 'stale-100', 'no-product'):
        if need not in alch:
            problems.append(f'AlchemyTracker 缺少「{need}」守卫')
    # 必须显式拒绝"停在 100 重复计数"
    if 'seenBelow = false;' not in alch:
        problems.append('AlchemyTracker 完成后没有复位（满值滞留会重复计数）')

# ---------------------------------------------------------------- 16. 身份证据链

idc_path = os.path.join(WILL, 'IdentityCase.java')
if not os.path.isfile(idc_path):
    problems.append('will/ 缺少 IdentityCase（身份证据链）')
else:
    idc = read(idc_path)
    # 识破必须有「最低可信度 + 至少几类不同证据」双门槛
    if 'IDENTIFY_AT' not in idc or 'MIN_KINDS' not in idc:
        problems.append('IdentityCase 缺少识破门槛（IDENTIFY_AT / MIN_KINDS）')
    if 'kindCount() >= MIN_KINDS' not in idc:
        problems.append('IdentityCase 判识破时没有要求「不同种类证据数」达标')
    # 证据只能记在本人 UUID 上
    if '!e.subjectUuid().equals(subjectUuid)' not in idc:
        problems.append('IdentityCase 没有把证据绑在事发时的 UUID 上')
    # 矛盾证据要能压低结论，但不能删记录
    if 'contradictedScore' not in idc:
        problems.append('IdentityCase 没有统计矛盾证据')

if '"identity"' not in state_txt:
    problems.append('WillState 没有持久化身份证据链')

# ---------------------------------------------------------------- 17. 载体统一状态

carrier_txt = read(os.path.join(WILL, 'Carrier.java'))
if 'enum State' not in carrier_txt:
    problems.append('Carrier 没有统一状态机（寄存/传播/消耗/清除/恢复应收敛到 State）')
for need in ('PURGED', 'DRAINED', 'DORMANT', 'REGROWING', 'ACTIVE', 'canHost('):
    if need not in carrier_txt:
        problems.append(f'Carrier 状态机缺少「{need}」')
# 状态判定必须只有一个入口
if 'public State state(long now)' not in carrier_txt:
    problems.append('Carrier 缺少唯一的 state() 判定入口')

# ---------------------------------------------------------------- 18. 炼蛊产物推断

witness_path = os.path.join(WORLD, 'AlchemyWitness.java')
if not os.path.isfile(witness_path):
    problems.append('world/ 缺少 AlchemyWitness（炼蛊证据没有接到世界里）')
else:
    wit = read(witness_path)
    for need in ('AlchemyTracker.diff(', 'AlchemyTracker.topProduct(',
                 'AlchemyTracker.COMPLETE'):
        if need not in wit:
            problems.append(f'AlchemyWitness 缺少「{need}」')
    # 挑不出/认不准产物时必须放弃，不能写假证据（3.7.1 起改为「只认唯一候选」）
    if 'grown.size() != 1' not in wit and 'top == null ? ""' not in wit:
        problems.append('AlchemyWitness 在认不出唯一产物时没有放弃（会写出假证据）')
    # 只统计本体命名空间，避免把随手捡的石头当产物
    if '"guzhenren".equals(key.getNamespace())' not in wit:
        problems.append('AlchemyWitness 没有限定只统计 guzhenren 命名空间')
    # 推断只能来自「会话基线 vs 当前背包」的差异
    if 'AlchemyTracker.diff(session.baseline' not in wit and 'diff(baseline, current)' not in wit:
        problems.append('AlchemyWitness 的产物不是从背包差异推断出来的')

alch2 = read(os.path.join(WILL, 'AlchemyTracker.java'))
if 'public static Map<String, Integer> diff(' not in alch2:
    problems.append('AlchemyTracker 缺少 diff()（只认增加的差异）')
if 'public static Map.Entry<String, Integer> topProduct(' not in alch2:
    problems.append('AlchemyTracker 缺少 topProduct()')

if 'CRAFT' not in read(os.path.join(WILL, 'Knowledge.java')):
    problems.append('Knowledge.Kind 缺少 CRAFT（炼成事实无处可记）')

# ---------------------------------------------------------------- 19. 3.7.0 审查修复的回归锁

cfg_txt = read(os.path.join(WILL, 'WillConfig.java'))
wexec = read(os.path.join(WORLD, 'WorldExec.java'))
obs = read(os.path.join(WORLD, 'Observation.java'))
wserv = read(os.path.join(WORLD, 'WillServer.java'))
wcore = read(os.path.join(WILL, 'WillCore.java'))
opp = read(os.path.join(WILL, 'Opportunity.java'))
herd_w = read(os.path.join(WORLD, 'BeastHerds.java'))
herd_c = read(os.path.join(WILL, 'BeastHerd.java'))
wit = read(os.path.join(WORLD, 'AlchemyWitness.java'))
alch = read(os.path.join(WILL, 'AlchemyTracker.java'))
fact = read(os.path.join(WORLD, 'FactSource.java'))
events_txt = read(os.path.join(SRC, 'TianyiEvents.java'))

# B01 申请与兑现分开
if 'settleOne(' not in wexec or 'pendingFor(' not in wexec:
    problems.append('B01：扶持没有「先兑现成熟机缘 + 不重复申请」两段式')
if 'public boolean due(' not in opp or 'markSettled(' not in opp:
    problems.append('B01：Opportunity 缺少 due()/markSettled()')
if '"offers"' not in state_txt:
    problems.append('B01：WillState 没有持久化机缘（重启后成熟机缘会丢）')
if 'settleDueOpportunities(' not in wserv:
    problems.append('B01：调度器没有定期兑现成熟机缘')

# B02 盲区不得带入实时真值
if 'boolean observable' not in obs:
    problems.append('B02：Observation 没有区分「可见/不可见」两条画像路径')
if '.power(tp.value())' not in obs:
    problems.append('B02：不可见分支没有改用知识快照的实力')
if 'if (observable)' not in obs:
    problems.append('B02：炼蛊见证没有载体触达检查')

# B03 姿态翻转接入
if 'switchPosture(' not in wserv:
    problems.append('B03：调度器没有每轮调用 switchPosture（压制会变单行道）')

# B04 可恢复配额 + 执行后结算
if 'ACTION_WINDOW' not in cfg_txt:
    problems.append('B04：WillConfig 缺少 ACTION_WINDOW（配额无法恢复）')
if 'quotaLeft(' not in wcore or 'noteActionExecuted(' not in wcore:
    problems.append('B04：WillCore 缺少窗口配额或执行后结算')
if 'actionCount.put(key, n + 1)' in wcore:
    problems.append('B04：还在意图生成时就扣配额（失败也耗机会）')
if 'noteActionExecuted(' not in wserv:
    problems.append('B04：调度器没有在执行成功后结算配额')
if '"windowStart"' not in state_txt:
    problems.append('B04：窗口起点没有持久化（重启即清空配额）')

# B05 炼蛊证据不得被拾取污染
if 'notePickup(' not in wit:
    problems.append('B05：炼蛊见证没有扣除期间拾取')
if 'grown.size() != 1' not in wit:
    problems.append('B05：炼蛊差异没有「只认唯一候选」的校验')
_inprog = alch.split('if (progress < COMPLETE)')[1].split('}')[0] if 'if (progress < COMPLETE)' in alch else ''
if alch.count('seenBelow = false;') < 2:
    problems.append('B05：进度归零没有清掉 seenBelow')

# B06 拾取数量用差值
if 'getOriginalStack()' not in events_txt:
    problems.append('B06：拾取没有用原始栈-当前栈算数量')

# B07 兽群守恒
if 'herd.harvest(' in herd_w:
    problems.append('B07：已实体化个体被杀又扣了一次种群（重复记账）')
if 'returnPopulation(' not in herd_w:
    problems.append('B07：生成失败没有归还预留种群')
if 'fallback-spawn' in wexec:
    problems.append('B07：种群耗尽时仍有不扣数的兜底生成')

# B08 崩溃可恢复
if 'RECOLONIZE_DELAY' not in herd_c or 'recolonized' not in herd_c:
    problems.append('B08：崩溃兽群没有任何恢复路径')

# B10 渠道与情报校验
if 'node != null && node.effectiveReach' not in wserv:
    problems.append('B10：载体不存在仍被当成渠道可用')
if 'tracked.live()' not in wserv:
    problems.append('B10：没有把「没被遮蔽」和「有新鲜情报」分开')

# B11 目击三维判定
if 'VISIBLE_DY' not in fact:
    problems.append('B11：目击只核对水平距离（隔层也算看见）')

# ---------------------------------------------------------------- 20. 《窥天录》知识边界

PKG = SRC
JOURNAL = os.path.join(PKG, 'journal')
CLIENT = os.path.join(PKG, 'client')
NETP = os.path.join(PKG, 'net')

# 20.1 玩家知识是纯逻辑，不能碰 Minecraft
jp = os.path.join(JOURNAL, 'PlayerJournal.java')
if not os.path.isfile(jp):
    problems.append('缺少 journal/PlayerJournal（玩家知识记录）')
else:
    jt = read(jp)
    if 'import net.minecraft' in jt or 'import net.neoforged' in jt:
        problems.append('PlayerJournal 引入了 Minecraft 依赖（无法离线测试）')

# 20.2 玩家档案只能来自玩家自己，不能读世界总账
js = os.path.join(WORLD, 'JournalStore.java')
if not os.path.isfile(js):
    problems.append('缺少 world/JournalStore（玩家档案存取）')
else:
    jst = read(js)
    for bad in ('Knowledge.Store', 'IntelState', 'FactLedger', '.core()', 'IdentityCase.Ledger'):
        if bad in jst:
            problems.append(f'JournalStore 读了世界总账「{bad}」——玩家档案必须独立')

# 20.3 下发包必须由按玩家筛选后的档案构造
jn = os.path.join(NETP, 'JournalNet.java')
if not os.path.isfile(jn):
    problems.append('缺少 net/JournalNet（同步与动作处理）')
else:
    jnt = read(jn)
    if 'JournalStore.load(player)' not in jnt:
        problems.append('JournalNet 没有按玩家读档后再下发（可能下发世界总账）')
    if 'SYNC_MIN_GAP' not in jnt:
        problems.append('JournalNet 没有同步频率限制')
    if 'handleAction' not in jnt:
        problems.append('JournalNet 没有服务端动作重校验')

# 20.4 客户端不得持有任何世界侧类型
for f in java_files(CLIENT):
    ct = read(f)
    rel = os.path.relpath(f, SRC)
    for bad in ('Knowledge', 'WillState', 'IntelState', 'FactLedger', 'IdentityCase',
                'WillCore', 'Assessment'):
        if bad in ct:
            problems.append(f'{rel} 引用了世界侧类型「{bad}」——客户端只该画下发的数据')

# 20.5 打开面板不暂停世界（多人）
scr = os.path.join(CLIENT, 'JournalScreen.java')
if os.path.isfile(scr):
    st = code_only(read(scr))
    if 'isPauseScreen' not in st or 'return false' not in st.split('isPauseScreen')[1][:80]:
        problems.append('JournalScreen 没有声明不暂停世界')
    if '尚无相关见闻' not in st:
        problems.append('JournalScreen 缺少真实空状态文案')
    # 3.8.1 实机教训：调 super.render 会触发 renderBackground → renderBlurredBackground，
    # 把已经画好的面板连世界一起高斯模糊（表现为「画面糊」）。
    if 'super.render(' in st:
        problems.append('JournalScreen 调了 super.render（会触发原版背景模糊，画面会糊）')
    if 'renderBackground' not in st:
        problems.append('JournalScreen 没有屏蔽 renderBackground（模糊链没断）')
    # 素材必须 1:1 平铺/原尺寸，不能整体拉伸（拉伸也会糊）
    if 'g.blit(' in st and 'private void tile(' not in st:
        problems.append('JournalScreen 缺少平铺工具（大图拉伸会糊）')
    # 3.8.2 教训：整体缩放（0.5/非整数）会让整屏发糊；必须改成自适应布局 + 1:1 绘制
    if 'private void layout(' not in st and 'JournalLayout.of(width, height)' not in st:
        problems.append('JournalScreen 没有自适应布局（可能仍在整体缩放面板）')
    if '.scale(' in st and 'return 1.0f;' not in st:
        problems.append('JournalScreen 的 currentScale() 不是恒 1.0（整体缩放会让整屏发糊）')
    # 不得出现伪造示例数据
    for fake in ('示例', '假数据', 'demo'):
        if fake in st:
            problems.append(f'JournalScreen 里出现示例数据痕迹「{fake}」')

# 20.6 按键可配置（用 KeyMapping，而不是硬编码按键判断）
keys = os.path.join(CLIENT, 'JournalKeys.java')
if not os.path.isfile(keys) or 'new KeyMapping(' not in read(keys):
    problems.append('按键没有用 KeyMapping 注册（无法改绑）')

# 20.7 客户端初始化必须被 dist 挡住（独立服务器不加载客户端类）
mod_txt = read(os.path.join(PKG, 'TianyiMod.java'))
if 'Dist.CLIENT' not in mod_txt:
    problems.append('客户端初始化没有被 dist 挡住（独立服务器可能加载客户端类）')

# 20.8 炼蛊只能记「尝试 + 结果未核实」，不得把背包差分写成炼成
if 'noteAlchemyAttempt(' not in wit:
    problems.append('炼蛊没有按「尝试 + 未核实」记录')
if 'Knowledge.Kind.CRAFT' in wit:
    problems.append('炼蛊仍在用背包差分写 CRAFT 事实（差分不能证明炼成）')
if 'CRAFT_TRACE' in wit:
    problems.append('炼蛊仍在用背包差分写身份下手痕迹（不可靠证据）')

# 20.9 网络通道 id 必须显式命名空间
#      3.8.0 实机崩溃教训：CustomPacketPayload.createType("tianyi:journal") 会把整串当 path，
#      命名空间写死 minecraft，得到 minecraft:tianyi:journal → ResourceLocationException。
import re as _re
def _code_only(src):
    """去掉注释后再做文本断言，避免注释里提到旧写法就被误判。"""
    src = _re.sub(r'/\*.*?\*/', '', src, flags=_re.S)
    out = []
    for line in src.splitlines():
        i = line.find('//')
        out.append(line[:i] if i >= 0 else line)
    return '\n'.join(out)

for f in java_files(NETP):
    nt = code_only(read(f))
    rel = os.path.relpath(f, SRC)
    if 'createType(' in nt:
        problems.append(f'{rel} 用了 createType()（会把整串当 path，命名空间变 minecraft）')
    for m in _re.finditer(r'fromNamespaceAndPath\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', nt):
        ns, path = m.group(1), m.group(2)
        if ns != 'tianyi':
            problems.append(f'{rel} 通道命名空间不是 tianyi（{ns}）')
        if not _re.fullmatch(r'[a-z0-9/._-]+', path):
            problems.append(f'{rel} 通道 path 含非法字符：{path}')

# ---------------------------------------------------------------- 21. 宿命蛊实物与账本（3.13.0 切片 2）
# 锁的是「实物只是壳、账本才是权威」这套纪律，而不是玩法数值。
ITEM_DIR = os.path.join(SRC, 'item')
DEST_ITEM = os.path.join(ITEM_DIR, 'DestinyGuItem.java')
DEST_ITEMS = os.path.join(ITEM_DIR, 'TianyiItems.java')
DEST_STATE = os.path.join(WORLD, 'DestinyCustodyState.java')
DEST_SVC = os.path.join(WORLD, 'DestinyCustodyService.java')
DEST_VESSEL = os.path.join(WILL, 'DestinyVessel.java')

for p in (DEST_ITEM, DEST_ITEMS, DEST_STATE, DEST_SVC, DEST_VESSEL):
    if not os.path.isfile(p):
        problems.append(f'3.13.0 宿命蛊缺少文件 {os.path.relpath(p, SRC)}')

# 21.1 物品只是壳：不可堆叠、右键无效、自己不下结论
if os.path.isfile(DEST_ITEM):
    item_raw = read(DEST_ITEM)
    item = code_only(item_raw)
    if 'stacksTo(1)' not in item_raw:
        problems.append('宿命蛊物品没有 stacksTo(1)（必须不可堆叠）')
    if 'InteractionResult' in item:
        problems.append('宿命蛊物品覆写了 use/交互（右键即触发效果是红线）')
    for bad in ('MING_BAI', 'completeRepair', 'completeBodyDestruction',
                'completeSplitRefinement', 'setRepairProgress'):
        if bad in item:
            problems.append(f'宿命蛊物品出现 {bad}（物品不得改命运/推进阶段）')
    if 'validate(' in item:
        problems.append('宿命蛊物品自己调用 validate（客户端不得判真伪）')
    for bad in ('issue(', 'markDuplicate(', 'addItem('):
        if bad in item:
            problems.append(f'宿命蛊物品出现 {bad}（发/复制实物只能走账本与命令）')

if os.path.isfile(DEST_ITEMS):
    reg = code_only(read(DEST_ITEMS))
    if '"tianyi"' not in reg:
        problems.append('宿命蛊注册命名空间不是 tianyi（不得冒充本体注册项）')
    if '"suminggu"' not in reg:
        problems.append('宿命蛊注册 id 不是 suminggu')
    if 'CreativeModeTab' in reg:
        problems.append('宿命蛊挂进了创造模式物品栏（等于自动发物品）')

# 21.2 不准全世界扫描、不准强制加载：只核对「手上的」与「面前的」
for p in (DEST_ITEM, DEST_STATE, DEST_SVC):
    if not os.path.isfile(p):
        continue
    txt = code_only(read(p))
    rel = os.path.relpath(p, SRC)
    for bad in ('getAllLevels', 'getEntities(', 'getAllEntities', 'forEachPlayer',
                'loadedEntities', 'getAllPlayers'):
        if bad in txt:
            problems.append(f'{rel} 出现 {bad}（宿命实物不得扫描全世界）')
    for bad in ('setChunkForced', 'forceLoad', 'ChunkStatus', 'getChunk('):
        if bad in txt:
            problems.append(f'{rel} 出现 {bad}（不得强制加载区块）')

# 21.3 载体丢失 / 复制 / 隔离都只是保管状态，不得推进阶段
if os.path.isfile(DEST_SVC):
    svc = code_only(read(DEST_SVC))
    if 'carrierLost(' not in svc:
        problems.append('载体丢失没有落到账本（service 里找不到 carrierLost）')
    for bad in ('completeRepair', 'completeBodyDestruction', 'completeSplitRefinement',
                'setRepairProgress'):
        if bad in svc:
            problems.append(f'DestinyCustodyService 出现 {bad}（保管流程不得推进阶段）')
    if 'JournalStore.note(' not in svc:
        problems.append('宿命蛊正品见闻没有写本人条目（JournalStore.note）')
    for bad in ('JournalSyncPayload', 'JournalActionPayload', 'sendSystemMessage',
                'displayClientMessage'):
        if bad in svc:
            problems.append(f'DestinyCustodyService 出现 {bad}（不得新开下行通道或播报）')

# 21.4 普通死亡、杀怪、灾劫都不许损宿命
ev_txt = code_only(read(os.path.join(SRC, 'TianyiEvents.java')))
_m = re.search(r'void onDeath\([^)]*\)\s*\{(.*?)\n    \}', ev_txt, re.S)
if not _m:
    problems.append('TianyiEvents.onDeath 结构异常（无法审计「普通死亡不损宿命」）')
else:
    for bad in ('carrierLost', 'onCarrierLost', 'markDuplicate', 'completeBodyDestruction'):
        if bad in _m.group(1):
            problems.append(f'死亡路径出现 {bad}（普通死亡与宿命无关）')
for name in ('WillServer.java', 'WorldExec.java'):
    p = os.path.join(WORLD, name)
    if os.path.isfile(p):
        t = code_only(read(p))
        for bad in ('onCarrierLost', 'markDuplicate('):
            if bad in t:
                problems.append(f'{name} 出现 {bad}（杀怪/灾劫不得损宿命）')

# 21.5 实体化只有一条路：账本包装 + 命令。别处一律不许 issue
_issue_owners = {'will' + os.sep + 'DestinyCustody.java',
                 'world' + os.sep + 'DestinyCustodyState.java',
                 'world' + os.sep + 'DestinyCustodyService.java'}
for _f in java_files(WILL) + java_files(WORLD) + (java_files(ITEM_DIR) if os.path.isdir(ITEM_DIR) else []):
    _t = code_only(read(_f))
    if '.issue(' in _t:
        _rel = os.path.relpath(_f, SRC)
        if _rel not in _issue_owners:
            problems.append(f'{_rel} 调用了 issue（实体化只能走单一世界级事务）')

# 21.6 命令只查询 + 两个受控操作（都要 note），且没有「按玩家名改命运」
for need in ('literal("custody")', 'literal("status")', 'literal("recover")',
             'literal("release")', 'greedyString()', 'NOT_IMPLEMENTED'):
    if need not in cmds:
        problems.append(f'命令里缺少宿命保管入口 {need}')
for bad in ('getPlayerByName', 'literal("life")', 'literal("lifespan")', 'literal("turns")',
            'setStage', 'setLifespan'):
    if bad in cmds:
        problems.append(f'命令里出现 {bad}（不得按玩家名/寿命/转数改命运）')

# 21.7 账本存档键固定 + 快照口径必须含它（避免只回退物品、不回退规则）
if os.path.isfile(DEST_STATE):
    st_txt = read(DEST_STATE)
    if 'STORAGE_KEY = "tianyi_destiny_custody"' not in st_txt:
        problems.append('账本存档键不是 tianyi_destiny_custody（旧键不得改）')
    if 'schemaVersion' not in st_txt:
        problems.append('账本没有 schemaVersion（旧档无法安全识别）')
    if 'setDirty()' not in st_txt:
        problems.append('账本改了不落盘（缺少 setDirty）')
snap_txt = read(os.path.join(WORLD, 'SnapshotService.java'))
if 'DESTINY_LEDGER_FILE' not in snap_txt or 'coversDestinyLedger' not in snap_txt:
    problems.append('快照口径没有声明覆盖宿命账本（可能只回退物品、不回退规则）')

# ---------------------------------------------------------------- 22. 天意强度档位（3.14）
# 锁的是「档位必须真的改变行为，又不能破掉既有纪律」，不是档位数值好不好玩。
FORCE = os.path.join(WILL, 'WillForce.java')
FSET = os.path.join(WORLD, 'WillForceSettings.java')
PRESSURE = os.path.join(WORLD, 'WillPressure.java')

for p in (FORCE, FSET, PRESSURE):
    if not os.path.isfile(p):
        problems.append(f'3.14 天意强度缺少文件 {os.path.relpath(p, SRC)}')

if os.path.isfile(FORCE):
    ft = read(FORCE)
    for need in ('public int intensity()', 'public boolean isOff()', 'public boolean pressureEnabled()',
                 'public double calamityChance()', 'public boolean beastWaveEnabled()',
                 'public double baitSinThreshold()', 'public double baitCap()',
                 'public int decisionPeriod()', 'public int decisionBatch()',
                 'public static double roll(', 'public boolean fires(',
                 'public static String eventKey('):
        if need not in ft:
            problems.append(f'WillForce 缺少「{need}」')
    # 档位 0 必须是「零」而不是「概率很低」：执行/压制/灾劫/兽群的要害全为 0
    if 'case OFF -> 0;' not in ft:
        problems.append('WillForce 的关闭档没有把动作上限压到 0（关闭必须零干预）')
    if 'case OFF -> 2.0;' not in ft:
        problems.append('WillForce 的关闭档没有把诱饵门限压到永不触发（|sin| ≤ 1）')
    # 诱饵占比的硬上限不允许被档位放宽（否则「全都是陷阱」）
    if 'BAIT_RATIO_CAP' not in ft:
        problems.append('WillForce.baitCap 没有沿用 WillConfig.BAIT_RATIO_CAP 硬上限')
    # 档位不得凭空造物/刷怪
    for bad in ('addItem(', 'setItem(', 'spawnEntity(', 'sendSystemMessage'):
        if bad in ft:
            problems.append(f'WillForce 出现 {bad}（档位只能是乘数与阈值）')

# 22.1 强度 0 = 真的零干预（顺序：先拦、再评估）
_core2 = read(os.path.join(WILL, 'WillCore.java'))
if 'public List<Intent> decide(Assessment.Situation s)' not in _core2:
    problems.append('WillCore.decide 签名变了（审计无法核对关闭档）')
else:
    _decide = _core2.split('public List<Intent> decide(Assessment.Situation s)', 1)[1]
    _off = _decide.find('.isOff()')
    _assess = _decide.find('Assessment.assess(')
    if _off < 0 or _assess < 0 or _off > _assess:
        problems.append('WillCore.decide 没有在任何评估/意图之前拦住强度 0（关闭档会仍有动作）')
_serv2 = read(os.path.join(WORLD, 'WillServer.java'))
if 'force.isOff()' not in _serv2:
    problems.append('WillServer.runDecision 没有拦住强度 0（关闭档仍会观测/执行）')

# 22.2 强度 ≥ 2 时既有压制渠道真的可达（并且旧开关仍然有效）
if os.path.isfile(FSET):
    _fset = read(FSET)
    if 'legacyPressure()' not in _fset:
        problems.append('WillForceSettings 没有把旧开关 legacyAutomaticPressure 并入（旧整合包会变行为）')
    if 'intensity' not in _fset:
        problems.append('WillForceSettings 没有 intensity 配置项')
if 'WillForceSettings.pressureOn()' not in _serv2:
    problems.append('WillServer 没有用「旧开关或强度≥2」决定压制渠道（强度≥2 也压不动）')
if 'pressureOn()' not in _serv2:
    problems.append('WillServer 不再读压制开关（压制渠道会被永久关闭）')

# 22.3 灾劫在强度 ≥ 2 时真的会发生（仍是既有四等四阶段）
if 'calamityChance' not in _core2 or 'calamity-urge' not in _core2:
    problems.append('WillCore 没有按档位选中既有灾劫渠道（强度≥2 仍不会降灾）')
_wexec2 = read(os.path.join(WORLD, 'WorldExec.java'))
if 'WillForceSettings.force()' not in _wexec2:
    problems.append('WorldExec 没有读档位（灾劫/兽群/人劫不会随强度变化）')
if 'calamityScaleFactor' not in _wexec2:
    problems.append('WorldExec 开灾劫时没有按档位改规模')
_cal2 = read(os.path.join(WILL, 'Calamity.java'))
for _t in ('DI_ZAI', 'TIAN_JIE', 'HAO_JIE', 'WAN_JIE'):
    if _t not in _cal2:
        problems.append(f'Calamity 缺少既有灾劫种类 {_t}（强度不得删改四等）')

# 22.4 兽潮必须扣真实种群，且不得凭空刷怪/给货/发消息
if os.path.isfile(PRESSURE):
    _pw = read(PRESSURE)
    if 'driveToward(' not in _pw:
        problems.append('WillPressure 的兽潮没有走 BeastHerds.driveToward（必须扣真实种群）')
    if 'fires(' not in _pw or 'roll(' not in _pw:
        problems.append('WillPressure 没有用幂等窗口判定（同一事件会在同窗口重复发生）')
    if 'scrambleNear(' not in _pw:
        problems.append('WillPressure 的产地枯损没有落到真实产地的储量账上')
    for bad in ('addItem(', 'setItem(', 'spawnEntity(', 'sendSystemMessage',
                'displayClientMessage', 'TianyiVoice', 'spawnParticle('):
        if bad in _pw:
            problems.append(f'WillPressure 出现 {bad}（现象层只能改天气/储量/真实兽群）')
    for bad in ('setChunkForced', 'forceLoad', 'ChunkStatus', 'getChunk('):
        if bad in _pw:
            problems.append(f'WillPressure 出现 {bad}（不得强制加载区块）')

# 22.5 机缘诱饵：比例可以随档位升，但硬上限与「真实物品」不变
_opp2 = read(os.path.join(WILL, 'Opportunity.java'))
if 'BAIT_RATIO_CAP' not in _opp2:
    problems.append('Opportunity 不再检查诱饵占比硬上限（可能全是陷阱）')
if 'baitSinThreshold' not in _opp2:
    problems.append('Opportunity 没有接收档位给的诱饵门限（诱饵比例不会随强度变化）')
if 'addItem(' in _opp2 or 'setItem(' in _opp2:
    problems.append('Opportunity 直接发物品（机缘只能落成世界里的真实产地）')

# 22.6 档位必须登记进离线测试，否则「只是加了个枚举」
_rt = read(os.path.join(ROOT, '_pack', 'run_tests.py'))
if 'WillForce.java' not in _rt:
    problems.append('WillForce 没有进 run_tests.py 的纯逻辑编译清单')
if 'WillForceTest' not in _rt:
    problems.append('WillForceTest 没有登记进 run_tests.py 的 SUITES')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('天意硬约束 OK（核心分离 / 隐性 / 不塞背包 / 寿蛊只动供给 / 针对性上限 / '
      '天外不免疫 / 回溯边界 / 运行期不写档 / 旧系统退场 / 身份规范 / 无 AI 依赖 / '
      '产地五本账 / 采集夹储量 / 产地持久化 / 物流幂等结算 / 兽群生成即扣 / 炼蛊精确满值 / '
      '身份双门槛 / 载体统一状态 / 炼蛊产物推断 / 3.7.1 审查修复回归锁 B01-B11 / '
      '玩家知识独立 / 客户端不碰世界账本 / 炼蛊只记未核实 / '
      '宿命实物只装凭证 / 无全世界扫描 / 无强制加载 / 载体丢失与复制不改阶段 / '
      '普通死亡不损宿命 / 复制不得产生第二正品 / 快照含宿命账本 / '
      '强度 0 零干预 / 强度≥2 压制与灾劫可达 / 兽潮扣种群 / 诱饵不放宽硬上限）')
