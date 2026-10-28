"""审计：客户端按键/监听注册不再依赖已废弃的 @EventBusSubscriber(bus = GAME)。

背景：有用户反馈「用希望蛊开窍后按 K 完全没反应」。
逐层核对后确认本体 10.4 与 tianfu 字节码完全兼容（引用的 10 个本体类与全部成员都在），
所以问题只可能落在客户端按键这一层。

本脚本锁死三点，防止以后回退：
  1. TianfuClientEvents 不得再用 @EventBusSubscriber(bus = Bus.GAME)
  2. 客户端 tick 监听必须由 TianfuKeyMappings.onRegisterKeys 显式挂载
     （与按键注册绑同一条依赖，不会出现「键在、监听没挂」的半死状态）
  3. /tianfu open 命令存在，且能报出 kaiqiao / 空窍 / 已结算 / 难度 / 点数
"""
import re
import os

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianfu')
CLIENT = os.path.join(SRC, 'client')

fails = []
oks = []


def ok(m):
    oks.append(m)


def bad(m):
    fails.append(m)


def read(p):
    return open(p, encoding='utf-8').read()


def code_of(text):
    """剥掉注释，只查代码。"""
    t = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return re.sub(r'//[^\n]*', '', t)


events = read(os.path.join(CLIENT, 'TianfuClientEvents.java'))
keys = read(os.path.join(CLIENT, 'TianfuKeyMappings.java'))
cmds = read(os.path.join(SRC, 'TianfuCommands.java'))
opening = read(os.path.join(SRC, 'TianfuOpening.java'))

ev_code = code_of(events)
key_code = code_of(keys)
cmd_code = code_of(cmds)

# ---- 1. 不再用 GAME 总线注解 ----
if re.search(r'@EventBusSubscriber\([^)]*Bus\.GAME', ev_code):
    bad('TianfuClientEvents 仍在用 @EventBusSubscriber(bus = Bus.GAME)')
else:
    ok('TianfuClientEvents 已不依赖 GAME 总线注解')

# ---- 2. 显式挂载 ----
if re.search(r'NeoForge\.EVENT_BUS\.register\(\s*TianfuClientEvents\.class\s*\)', key_code):
    ok('TianfuKeyMappings 显式注册 TianfuClientEvents 到 game 总线')
else:
    bad('TianfuKeyMappings 未显式注册客户端 tick 监听')

if re.search(r'event\.register\(VIEW\)', key_code):
    ok('按键注册仍在（与监听同一处）')
else:
    bad('按键注册缺失')

# 监听方法必须仍在
if '@SubscribeEvent' in ev_code and 'onClientTick' in ev_code:
    ok('onClientTick 订阅方法仍在')
else:
    bad('onClientTick 缺失')

# 诊断日志
if '收到 K 键' in events:
    ok('K 键被收到时会打诊断日志')
else:
    bad('缺少 K 键诊断日志')

# ---- 3. /tianfu open ----
for lit in ('"open"', '"打开"'):
    if lit in cmd_code:
        ok('/tianfu %s 命令存在' % lit.strip('"'))
    else:
        bad('/tianfu %s 缺失' % lit.strip('"'))

for needle, label in (('kaiqiaoScoreOf', 'kaiqiao 原始分数'),
                      ('isApplied', '已结算'),
                      ('difficulty', '难度'),
                      ('points', '点数')):
    if needle in cmd_code:
        ok('open 命令报出%s' % label)
    else:
        bad('open 命令缺%s' % label)

# kaiqiaoScoreOf 必须是 public
if re.search(r'public static int kaiqiaoScoreOf\(', code_of(opening)):
    ok('TianfuOpening.kaiqiaoScoreOf 是 public')
else:
    bad('kaiqiaoScoreOf 不是 public')

# 命令里必须真的发状态包（否则 open 打不开界面）
if re.search(r'TianfuNet\.sendState\(player\)', cmd_code):
    ok('open 命令真的发送状态包')
else:
    bad('open 命令没有发送状态包')

print('=' * 68)
print('客户端按键注册 / 诊断审计')
print('=' * 68)
for m in oks:
    print('  OK   ', m)
for m in fails:
    print('  FAIL ', m)
print('-' * 68)
print('通过 %d / 失败 %d' % (len(oks), len(fails)))
if fails:
    raise SystemExit(1)
