"""2.9.1 接线：命契进服务端动作/命令/事件，旧入口（商城/签到/摇命/主动转世）下线。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')


def patch(rel, pairs, label):
    p = os.path.join(SRC, rel.replace('/', os.sep))
    s = open(p, encoding='utf-8').read()
    ok = 0
    for a, b in pairs:
        if a in s:
            s = s.replace(a, b)
            ok += 1
        else:
            print(f'  ! {label}: 未匹配 {a[:56]!r}')
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print(f'  {label}: {ok}/{len(pairs)}')


# ---------------------------------------------------------------- 1. 命契记下 UUID 字符串
patch('TianyiFate.java', [
    ('''        TianyiContractCore.create(s, SLOT,''',
     '''        TianyiData.putString(player, "contract", SLOT + ".uuidStr",
                target.getUUID().toString());
        TianyiContractCore.create(s, SLOT,'''),
], 'TianyiFate 存 uuidStr')

# ---------------------------------------------------------------- 2. 事件：slowTick + 击杀结算
patch('TianyiEvents.java', [
    ('            TianyiQuestEngine.onSlowTick(player);',
     '            TianyiQuestEngine.onSlowTick(player);\n            TianyiFate.slowTick(player);'),
    ('''            TianyiBoard.addKill(sp);''',
     '''            TianyiBoard.addKill(sp);
            // 顺命：命中绑定的那只才算完成（判定在纯逻辑核心里）
            TianyiFate.onKill(sp, event.getEntity());'''),
], 'TianyiEvents')

# ---------------------------------------------------------------- 3. 网络动作：下线旧入口，接命契/推演/研究
p = os.path.join(SRC, 'TianyiNet.java')
s = open(p, encoding='utf-8').read()

# 删除旧动作（商城 / 签到 / 摇命 / 主动转世 / 旧推演 / 旧 AI 入口）
drop = [
    '            case "claim" -> TianyiQuestEngine.claim(player, arg);\n',
    '            case "sign" -> TianyiSign.sign(player);\n',
    '''            case "luck" -> {
                if (TianyiLuck.roll(player) == null) {
                    TianyiVoice.say(player, "luck:no", "天机不足（" + TianyiLuck.COST + " 点一摇）");
                }
            }
''',
    '            case "improvise" -> TianyiInsight.tell(player, TianyiInsight.improvise(player));\n',
    '            case "simulate" -> TianyiInsight.tell(player, TianyiInsight.simulate(player));\n',
    '            case "rebirth" -> LifeBook.settle(player);\n',
    '            case "ai_ask" -> net.guzhenren.tianyi.ai.TianyiAiOracle.ask(player);\n',
]
for d in drop:
    if d in s:
        s = s.replace(d, '')
    else:
        print('  ! 未匹配待删动作:', d.strip()[:50])

# 删除 buy_talent 块
s = re.sub(r'            case "buy_talent" -> \{.*?\n            \}\n', '', s, flags=re.S)

# 新动作
new_cases = '''            case "fate_follow" -> TianyiVoice.say(player, "fate",
                    TianyiFate.accept(player, false));
            case "fate_defy" -> TianyiVoice.say(player, "fate",
                    TianyiFate.accept(player, true));
            case "try_defy" -> TianyiVoice.say(player, "fate", TianyiFate.tryCompleteDefy(player));
            case "eye" -> TianyiVoice.say(player, "eye", TianyiFate.borrowEye(player));
            case "reckon" -> TianyiVoice.say(player, "reckon", TianyiFate.borrowReckon(player));
            case "sever" -> TianyiVoice.say(player, "sever", TianyiFate.borrowSever(player));
            case "divine" -> TianyiVoice.say(player, "divine", TianyiFate.divinate(player));
            case "study_start" -> TianyiVoice.say(player, "study",
                    TianyiFate.studyStart(player, arg));
            case "study_settle" -> TianyiVoice.say(player, "study",
                    TianyiFate.studySettle(player));
'''
anchor = '            case "request" -> sendState(player);\n'
if anchor in s:
    s = s.replace(anchor, anchor + new_cases)
else:
    print('  ! request 动作未匹配')

# 状态包：命契 / 推演三段 / 因果 / 百世书
add_state = '''        // ---- 命契（2.9.1）
        CompoundTag fate = new CompoundTag();
        fate.putString("summary", TianyiFate.summary(player));
        fate.putInt("observeSec", TianyiData.getInt(player, "contract", "fate.observe", 0) / 20);
        fate.putString("lastDiv", TianyiData.getString(player, "meta", "lastDivination", ""));
        fate.putString("study", TianyiData.getString(player, "study", "current", ""));
        t.put("fate", fate);
'''
anchor2 = '        t.put("ai", ai);'
if anchor2 in s:
    s = s.replace(anchor2, anchor2 + '\n' + add_state)
else:
    print('  ! ai 状态块未匹配')

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  TianyiNet: 旧动作下线 + 命契动作接入')

# ---------------------------------------------------------------- 4. 命令：旧入口下线
p = os.path.join(SRC, 'TianyiCommands.java')
s = open(p, encoding='utf-8').read()
# 删掉 insight/simulate/rebirth/pavilion 及其中文别名
s = re.sub(r'                \.then\(Commands\.literal\("insight"\).*?\.executes\(TianyiCommands::pavilion\)\)\n',
           '', s, flags=re.S)
# 新命令
new_cmds = '''                .then(Commands.literal("fate")
                        .requires(CommandSourceStack::isPlayer)
                        .then(Commands.literal("follow").executes(c -> fate(c, false)))
                        .then(Commands.literal("defy").executes(c -> fate(c, true)))
                        .then(Commands.literal("done").executes(TianyiCommands::doneDefy)))
                .then(Commands.literal("eye")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::eye))
                .then(Commands.literal("reckon")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::reckon))
                .then(Commands.literal("sever")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::sever))
                .then(Commands.literal("divine")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::divine))
                .then(Commands.literal("study")
                        .requires(CommandSourceStack::isPlayer)
                        .then(Commands.literal("start")
                                .then(Commands.argument("formula", StringArgumentType.word())
                                        .executes(TianyiCommands::studyStart)))
                        .then(Commands.literal("settle").executes(TianyiCommands::studySettle))));
    }

    private static int fate(CommandContext<CommandSourceStack> ctx, boolean defy)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.accept(p, defy)), false);
        return 1;
    }

    private static int doneDefy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.tryCompleteDefy(p)), false);
        return 1;
    }

    private static int eye(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.borrowEye(p)), false);
        return 1;
    }

    private static int reckon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.borrowReckon(p)), false);
        return 1;
    }

    private static int sever(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.borrowSever(p)), false);
        return 1;
    }

    private static int divine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal(TianyiFate.divinate(p)), false);
        return 1;
    }

    private static int studyStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        String id = StringArgumentType.getString(ctx, "formula");
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.studyStart(p, id)), false);
        return 1;
    }

    private static int studySettle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.studySettle(p)), false);
        return 1;
    }

    private static int __unusedAnchor(CommandContext<CommandSourceStack> ctx)'''
# 用新命令替换最后的 ")))" 收尾（根注册结尾）
s = s.replace('''                .then(Commands.literal("接口").executes(TianyiCommands::providers))));''',
              '''                .then(Commands.literal("接口").executes(TianyiCommands::providers)))
''' + new_cmds + '''(CommandContext<CommandSourceStack> c) {
        return 1;
    }''')
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  TianyiCommands: 旧入口下线 + 命契/推演/研究命令接入')
