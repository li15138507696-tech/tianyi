# -*- coding: utf-8 -*-
"""命运由天（随机天赋）审计。

三重校验：
  1. 静态：新增的包/类是否被正确注册、客户端是否真的发了这两个包、接受端是否校验资质
  2. 常量：从 TianfuRoll.java 现场读 MAX_ROLLS / 概率 / 轮数上限（改代码后脚本自动跟随）
  3. 模拟：从 TianfuPool.java + TianfuTalents.java 解析真实价目表，
          用与 Java 完全同构的算法跑 N 次，断言所有不变量

跑法：python _crash3/audit_roll.py
"""
import io
import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java", "net", "guzhenren", "tianfu")

fails = []
warns = []


def read(path):
    with io.open(path, "r", encoding="utf-8") as f:
        return f.read()


def ok(msg):
    print("  OK    " + msg)


def fail(msg):
    fails.append(msg)
    print("  FAIL  " + msg)


def warn(msg):
    warns.append(msg)
    print("  WARN  " + msg)


# ---------------------------------------------------------------- 1. 静态
print("=== 1. 静态接线 ===")
roll_src = read(os.path.join(SRC, "TianfuRoll.java"))
mod_src = read(os.path.join(SRC, "TianfuMod.java"))
accept_src = read(os.path.join(SRC, "net", "TianfuRollAcceptPayload.java"))
payload_src = read(os.path.join(SRC, "net", "TianfuRollPayload.java"))
screen_src = read(os.path.join(SRC, "client", "TianfuViewScreen.java"))
state_src = read(os.path.join(SRC, "net", "TianfuStatePayload.java"))
data_src = read(os.path.join(SRC, "TianfuData.java"))

if "TianfuRollPayload::handle" in mod_src:
    ok("TianfuMod 已注册 roll 包")
else:
    fail("TianfuMod 未注册 TianfuRollPayload")

if "TianfuRollAcceptPayload::handle" in mod_src:
    ok("TianfuMod 已注册 roll_accept 包")
else:
    fail("TianfuMod 未注册 TianfuRollAcceptPayload")

if "TianfuRoll.hasAnchor(pending)" in accept_src:
    ok("接受端校验「必含资质」")
else:
    fail("接受端没有校验 hasAnchor —— 会接受一份无法确认的随机结果")

if "TianfuData.applyRoll" in accept_src:
    ok("接受端走原子落库 applyRoll")
else:
    fail("接受端没有走 applyRoll（逐条 purchase 会留下半成功状态）")

if "TianfuRollPayload" in screen_src and "TianfuRollAcceptPayload" in screen_src:
    ok("客户端两个包都发了")
else:
    fail("客户端缺少摇签/接受包的发送")

for key in ("roll", "rollpending"):
    if '"%s"' % key in data_src:
        ok("TianfuData 使用 NBT 键 %s" % key)
    else:
        fail("TianfuData 缺少 NBT 键 %s" % key)

if "buf.writeVarInt(payload.rolls)" in state_src and "payload.pending" in state_src:
    ok("TianfuStatePayload 已带 rolls + pending")
else:
    fail("TianfuStatePayload 未同步新字段")

if "EntityType.create" in roll_src or "addFreshEntity" in roll_src:
    fail("随机层不该生成实体")
else:
    ok("随机层零实体")

# ---------------------------------------------------------------- 2. 常量
print("")
print("=== 2. 常量（从 TianfuRoll.java 现场读取） ===")


def const(name, pattern, cast=int):
    m = re.search(pattern, roll_src)
    if not m:
        fail("TianfuRoll.java 里找不到 %s" % name)
        return None
    return cast(m.group(1))


MAX_ROLLS = const("MAX_ROLLS", r"MAX_ROLLS\s*=\s*(\d+)")
MAX_STEPS = const("MAX_STEPS", r"MAX_STEPS\s*=\s*(\d+)")
BODY_CHANCE = const("BODY_CHANCE", r"BODY_CHANCE\s*=\s*([\d.]+)", float)
if None in (MAX_ROLLS, MAX_STEPS, BODY_CHANCE):
    print("常量解析失败，终止")
    sys.exit(1)
print("  MAX_ROLLS=%d  MAX_STEPS=%d  BODY_CHANCE=%.2f"
      % (MAX_ROLLS, MAX_STEPS, BODY_CHANCE))

if MAX_ROLLS < 1:
    fail("MAX_ROLLS 必须 >= 1")

# 第一层必须是「槽位等概率」，不能再退回「先按概率试十绝体」的老写法
if "rng.nextInt(slots)" in roll_src:
    ok("第一层抽取走槽位等概率（slots 归一化）")
else:
    fail("TianfuRoll 里找不到槽位抽取 rng.nextInt(slots)")

if "SIJUE_CHANCE" in roll_src:
    fail("TianfuRoll 仍残留 SIJUE_CHANCE —— 第一层应改为槽位等概率")
else:
    ok("已移除旧的 SIJUE_CHANCE 概率门")

if "pick(sijuePool, rng)" in roll_src:
    ok("第二层：十绝体内部再抽一次")
else:
    fail("缺少十绝体内部的第二层抽取")

# ---------------------------------------------------------------- 3. 价目表
print("")
print("=== 3. 从源码解析真实价目表 ===")
pool_src = read(os.path.join(SRC, "TianfuPool.java"))
talents_src = read(os.path.join(SRC, "TianfuTalents.java"))

m = re.search(r"BODY_COST\s*=\s*(\d+)", pool_src)
BODY_COST = int(m.group(1)) if m else 3
m = re.search(r"SIJUE_COST\s*=\s*(\d+)", pool_src)
SIJUE_COST = int(m.group(1)) if m else 16

bodies = [("body:" + i, BODY_COST, "BODY")
          for i in re.findall(r'body\("([^"]+)"', pool_src)]
sijues = [("sijue_" + n, SIJUE_COST, "SIJUE")
          for n in re.findall(r'sijue\((\d+),', pool_src)]
if re.search(r'new Spec\("shashen"', pool_src):
    sijues.append(("shashen", SIJUE_COST, "SIJUE"))
jiyuans = [(i, int(c), "JIYUAN") for i, c in
           re.findall(r'new Spec\("(jiyuan_\w+)",\s*"[^"]*",\s*Kind\.JIYUAN,\s*"[^"]*",\s*(\d+)', pool_src)]

builtin_entries = [(i, int(c), "ENTRY") for i, c in
                   re.findall(r'entry\("([^"]+)",\s*"[^"]*",\s*"[^"]*",\s*(-?\d+)', pool_src)]

# 词条：t("id", "名", "品质", cost, "描述", ...)
head, _, tail = talents_src.partition("MALICE = List.of(")
base_entries = [(i, int(c), "ENTRY") for i, c in
                re.findall(r'\bt\("([^"]+)",\s*"[^"]*",\s*"[^"]*",\s*(-?\d+)', head)]
malice_entries = [(i, int(c), "ENTRY") for i, c in
                  re.findall(r'\bt\("([^"]+)",\s*"[^"]*",\s*"[^"]*",\s*(-?\d+)', tail)]

entries = builtin_entries + base_entries + malice_entries

print("  凡体 %d · 十绝体 %d · 机缘 %d · 词条 %d（内置 %d + 扩展 %d + 恶意 %d）"
      % (len(bodies), len(sijues), len(jiyuans), len(entries),
         len(builtin_entries), len(base_entries), len(malice_entries)))
for name, lst in (("凡体", bodies), ("十绝体", sijues), ("机缘", jiyuans), ("词条", entries)):
    if not lst:
        fail("%s 解析为空 —— 价目表正则与源码对不上" % name)

jiyuan_costs = sorted(set(c for _, c, _ in jiyuans))
print("  机缘档位成本: %s" % jiyuan_costs)

# ---------------------------------------------------------------- 4. 模拟
print("")
print("=== 4. 模拟（算法与 TianfuRoll.generate 同构） ===")

DIFFICULTIES = [("顺天应命", 24), ("凡尘问道", 16), ("逆天争命", 12), ("万劫磨道", 8)]


def weight_of(cost):
    return 1 if cost < 0 else max(1, 18 - cost)


def generate(points, rng):
    out = []
    budget = points
    has_body = False

    # ① 第一层：候选 = {十绝体} ∪ {甲, 乙, 丙, 丁}，买不起的剔除后归一化
    jiyuan_pool = [s for s in jiyuans if s[1] <= budget]
    sijue_pool = [s for s in sijues if s[1] <= budget]
    sijue_slot = len(sijue_pool) > 0
    slots = (1 if sijue_slot else 0) + len(jiyuan_pool)
    anchor = None
    if slots > 0:
        slot = rng.randrange(slots)
        if sijue_slot and slot == 0:
            # ② 第二层：十绝体内部再抽一次
            anchor = rng.choice(sijue_pool)
            has_body = True
        else:
            anchor = jiyuan_pool[slot - 1] if sijue_slot else jiyuan_pool[slot]
    if anchor is not None:
        out.append(anchor)
        budget -= anchor[1]

    if not has_body and budget >= BODY_COST and rng.random() < BODY_CHANCE:
        ok = [s for s in bodies if s[1] <= budget]
        if ok:
            s = rng.choice(ok)
            out.append(s)
            budget -= s[1]

    steps = 0
    while steps < MAX_STEPS:
        steps += 1
        taken = set(s[0] for s in out)
        ok = [s for s in entries if s[0] not in taken and (s[1] < 0 or s[1] <= budget)]
        if not ok:
            break
        total = sum(weight_of(s[1]) for s in ok)
        r = rng.randrange(total)
        pick = ok[-1]
        for s in ok:
            r -= weight_of(s[1])
            if r < 0:
                pick = s
                break
        out.append(pick)
        budget -= pick[1]
    return out, budget


TRIALS = 1000
rng = random.Random(20261023)
summary = []
for name, pts in DIFFICULTIES:
    sijue_hit = 0
    body_hit = 0
    counts = []
    nets = []
    lefts = []
    anchor_count = {}
    for _ in range(TRIALS):
        res, budget = generate(pts, rng)
        ids = [s[0] for s in res]
        kinds = [s[2] for s in res]
        net = sum(s[1] for s in res)

        # ---- 不变量 ----
        if len(ids) != len(set(ids)):
            fail("[%s] 出现重复 id: %s" % (name, ids))
        body_like = sum(1 for k in kinds if k in ("BODY", "SIJUE"))
        if body_like > 1:
            fail("[%s] 体质超过 1 个: %s" % (name, ids))
        if kinds.count("JIYUAN") > 1:
            fail("[%s] 机缘超过 1 个: %s" % (name, ids))
        if not any(k in ("SIJUE", "JIYUAN") for k in kinds):
            fail("[%s] 不含资质（会卡死最终确认）: %s" % (name, ids))
        if net > pts:
            fail("[%s] 净成本 %d 超过点数 %d: %s" % (name, net, pts, ids))
        if budget < 0:
            fail("[%s] 剩余点数为负 %d: %s" % (name, budget, ids))
        if len(ids) > MAX_STEPS + 2:
            fail("[%s] 条目数异常 %d（疑似死循环）" % (name, len(ids)))
        if "SIJUE" in kinds and pts < SIJUE_COST:
            fail("[%s] 点数不足却摇出十绝体" % name)

        if "SIJUE" in kinds:
            sijue_hit += 1
        if "BODY" in kinds:
            body_hit += 1
        anc = next((s for s in res if s[2] in ("SIJUE", "JIYUAN")), None)
        if anc is not None:
            key = "十绝体" if anc[2] == "SIJUE" else anc[0].replace("jiyuan_", "")
            anchor_count[key] = anchor_count.get(key, 0) + 1
        counts.append(len(ids))
        nets.append(net)
        lefts.append(budget)

    summary.append((name, pts, sum(counts) / TRIALS, min(counts), max(counts),
                    sum(nets) / TRIALS, sum(lefts) / TRIALS,
                    sijue_hit * 100.0 / TRIALS, body_hit * 100.0 / TRIALS, anchor_count))

print("  难度        点数  平均条目  最少  最多  平均净耗  平均剩余  十绝体%  凡体%")
for s in summary:
    print("  %-10s  %3d   %7.2f  %4d  %4d   %7.2f  %8.2f  %6.1f  %5.1f"
          % (s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8]))

print("")
print("  第一层抽取分布（期望 = 可负担槽位等概率）")
for s in summary:
    name, pts, anchor_count = s[0], s[1], s[9]
    slots = (1 if pts >= SIJUE_COST else 0) + len([1 for _, c, _ in jiyuans if c <= pts])
    exp = 100.0 / slots
    parts = []
    for k, v in sorted(anchor_count.items(), key=lambda kv: -kv[1]):
        pct = v * 100.0 / TRIALS
        parts.append("%s %.1f%%" % (k, pct))
        if abs(pct - exp) > 5.0:
            fail("[%s] 槽位「%s」占比 %.1f%%，偏离期望 %.1f%%" % (name, k, pct, exp))
    print("  %-10s 槽位 %d · 期望各 %.1f%% · %s" % (name, slots, exp, "  ".join(parts)))

# ---- 抽样展示 ----
print("")
print("=== 5. 抽样（每档 3 份） ===")
lookup = {}
for lst in (bodies, sijues, jiyuans, entries):
    for s in lst:
        lookup[s[0]] = s
rng2 = random.Random(7)
for name, pts in DIFFICULTIES:
    for _ in range(3):
        res, budget = generate(pts, rng2)
        net = sum(s[1] for s in res)
        print("  [%s %d点] 净耗%d 剩%d: %s"
              % (name, pts, net, budget, "、".join(s[0] for s in res)))

# ---------------------------------------------------------------- 汇总
print("")
print("-" * 70)
print("失败 %d / 警告 %d" % (len(fails), len(warns)))
if fails:
    for f in fails[:20]:
        print("  ! " + f)
    sys.exit(1)
print("ALL OK")
