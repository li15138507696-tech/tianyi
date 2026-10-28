"""清掉 AI 模块删除后剩下的 4 处引用（Events / Net / QuestEngine ×2）。"""
import os
import re

SRC = os.path.join(r'D:\daima\mcmod\天意', 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')


def rw(rel, fn):
    p = os.path.join(SRC, rel.replace('/', os.sep))
    s = open(p, encoding='utf-8').read()
    s2 = fn(s)
    if s2 != s:
        open(p, 'w', encoding='utf-8', newline='\n').write(s2)
        print('  清理', rel)
    else:
        print('  ! 未改动', rel)


# 1) Events：关服不再关 AI 线程池
def events(s):
    s = re.sub(r'\n    /\*\* 关服：中断 AI 线程池.*?\n    \}\n', '\n', s, flags=re.S)
    return s


# 2) Net：删掉整段 backendOf（含上面那条孤立注释）
def net(s):
    s = re.sub(r'    /\*\* 只取 endpoint 的 host 给面板显示.*?\n    /\*\* 当前生效的 AI 后端.*?\n    \}\n\n',
               '', s, flags=re.S)
    s = re.sub(r'    /\*\* 当前生效的 AI 后端.*?\n    \}\n\n', '', s, flags=re.S)
    return s


# 3) Engine：两处 AI 任务循环整体删掉
def engine(s):
    s = re.sub(r'        // ---- AI 降谕的猎杀目标 ----\n        for \(net\.guzhenren\.tianyi\.ai.*?\n        \}\n',
               '', s, flags=re.S)
    s = re.sub(r'        // AI 任务的同类目标（目前只有 SURVIVE_MIN）\n        for \(net\.guzhenren\.tianyi\.ai.*?\n        \}\n',
               '', s, flags=re.S)
    return s


rw('TianyiEvents.java', events)
rw('TianyiNet.java', net)
rw('quest/TianyiQuestEngine.java', engine)
