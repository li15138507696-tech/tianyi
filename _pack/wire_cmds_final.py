"""2.9.1 收尾：命令与面板彻底下线「商城/主动转世」，接上命契命令。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

# ---------------------------------------------------------------- 1. 命令
P = os.path.join(SRC, 'TianyiCommands.java')
s = open(P, encoding='utf-8').read()

old_tail = '''                // 系统功能（M4 / M6）
                .then(Commands.literal("天机阁").executes(TianyiCommands::pavilion))
                .then(Commands.literal("指瑕").executes(TianyiCommands::insight))
                .then(Commands.literal("演算").executes(TianyiCommands::simulate))
                .then(Commands.literal("转世").executes(TianyiCommands::rebirth)));
    }'''

new_tail = '''                // 命契 / 推演 / 炼道（2.9.1：商城与主动转世入口已下线）
                .then(Commands.literal("fate")
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

    private static int doneDefy(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r"
                + TianyiFate.tryCompleteDefy(p)), false);
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

    private static int studyStart(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        String id = StringArgumentType.getString(ctx, "formula");
        p.displayClientMessage(Component.literal("§6[天意] §r"
                + TianyiFate.studyStart(p, id)), false);
        return 1;
    }

    private static int studySettle(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        p.displayClientMessage(Component.literal("§6[天意] §r" + TianyiFate.studySettle(p)), false);
        return 1;
    }'''

if old_tail in s:
    s = s.replace(old_tail, new_tail)
    print('  ~ 命令：旧入口下线 + 命契命令接入')
else:
    print('  ! 命令尾块未匹配')

# 删掉四个旧方法体（pavilion/insight/simulate/rebirth）
for name in ('pavilion', 'insight', 'simulate', 'rebirth'):
    pat = re.compile(r'    private static int ' + name + r'\(CommandContext<CommandSourceStack> ctx\)'
                     r'[^{]*\{.*?\n    \}\n\n', re.S)
    s2 = pat.sub('', s)
    if s2 != s:
        s = s2
        print(f'  - 删除旧命令方法 {name}()')
open(P, 'w', encoding='utf-8', newline='\n').write(s)

# ---------------------------------------------------------------- 2. 网络动作：去掉主动转世
P = os.path.join(SRC, 'TianyiNet.java')
s = open(P, encoding='utf-8').read()
for piece in ('            case "rebirth" -> LifeBook.settle(player);\n',):
    if piece in s:
        s = s.replace(piece, '')
        print('  - 网络动作 rebirth 已下线')
open(P, 'w', encoding='utf-8', newline='\n').write(s)

# ---------------------------------------------------------------- 3. 面板：去掉结算按钮
P = os.path.join(SRC, 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()
btn = '''        goldButton(g, x, y, x + 86, y + 14, "结算这一世", "rebirth", "", true);'''
if btn in s:
    s = s.replace(btn, '''        g.drawString(font, "结算入口已下线：仅在本体真实转世信号确认后由服务端自动触发",
                x, y + 4, T_DIM, true);''')
    print('  ~ 面板：结算按钮改为说明文字')
s = s.replace('                case "rebirth" -> TianyiClientHandler.action("rebirth", "");\n', '')
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('  - 面板动作 rebirth 已删除')
