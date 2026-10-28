"""2.10.0：删除 AI 模块（ai 包 13 类 + 全部接线 + 配置 + 审计 + mods.toml 依赖）。

## 删干净的标准（不是只删文件夹）

1. 源码：`net/guzhenren/tianyi/ai/**` 整包删除；
2. 入口：`TianyiMod` 里的 AI 配置注册与 Numen 探测删除；
3. 网络：状态包里的 `ai` 复合体、`hostOf/backendOf` 辅助方法删除；
4. 命令：`/tianyi ai …` 整棵子树删除（重写 TianyiCommands）；
5. 依赖：`TianyiQuestEngine`（AI 任务路由/列表）、`LifeBook`（AI 任务清空）、
   `TianyiEvents`（AI 战绩）里的引用删除；
6. 面板：因果页的「模型接口」块回退；
7. 构建：`mods.toml` 的 numen_api 可选依赖、`pack_jar` 探针、`build.py` 审计清单同步；
8. 审计脚本 `audit_ai.py` / `audit_numen.py` 退役（移到 `_pack/_retired`）。
"""
import os
import re
import shutil

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
PACK = os.path.join(ROOT, '_pack')


def read(rel):
    return open(os.path.join(SRC, rel.replace('/', os.sep)), encoding='utf-8').read()


def write(rel, text):
    open(os.path.join(SRC, rel.replace('/', os.sep)), 'w', encoding='utf-8', newline='\n').write(text)


# ---------------------------------------------------------------- 1. 删 ai 包
ai_dir = os.path.join(SRC, 'ai')
if os.path.isdir(ai_dir):
    n = sum(1 for b, _, fs in os.walk(ai_dir) for f in fs if f.endswith('.java'))
    lines = 0
    for b, _, fs in os.walk(ai_dir):
        for f in fs:
            if f.endswith('.java'):
                lines += len(open(os.path.join(b, f), encoding='utf-8').read().splitlines())
    shutil.rmtree(ai_dir)
    print(f'  删除 ai/：{n} 文件 / {lines} 行')

# ---------------------------------------------------------------- 2. TianyiMod
s = read('TianyiMod.java')
s = s.replace('''        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                net.guzhenren.tianyi.ai.TianyiAiConfig.SPEC,
                "tianyi-ai-server.toml");

''', '')
s = re.sub(r'        // Numen 在场才接入.*?\n        \}\n\n', '', s, flags=re.S)
s = s.replace(' *   <li>AI 降谕：Numen / 自定义接口 / DSH 凭据三条通路（AI 只写文案，数值由服务端定）。</li>\n', '')
s = s.replace('（系统本体：谕令 · 天机 · 因果 · AI 降谕）', '（系统本体：命契 · 推演 · 因果 · 百世书）')
write('TianyiMod.java', s)
print('  TianyiMod：AI 配置与 Numen 接入已移除')

# ---------------------------------------------------------------- 3. TianyiNet
s = read('TianyiNet.java')
s = re.sub(r'        // AI 降谕状态.*?\n        t\.put\("ai", ai\);\n', '', s, flags=re.S)
s = re.sub(r'    private static String backendOf\(ServerPlayer player\) \{.*?\n    \}\n\n', '', s, flags=re.S)
s = re.sub(r'    private static String hostOf\(String endpoint\) \{.*?\n    \}\n\n', '', s, flags=re.S)
s = s.replace(', TianyiQuests.size())', ')')  # 防残留
write('TianyiNet.java', s)
print('  TianyiNet：ai 状态体与 backendOf/hostOf 已移除')

# ---------------------------------------------------------------- 4. 重写 TianyiCommands（只剩命契/推演/炼道）
CMD = '''package net.guzhenren.tianyi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /tianyi …}：命契、推演、炼道研究（2.10.0：AI 子树已整体移除）。
 *
 * <p>全部子命令都要求是玩家（{@code isPlayer}）：控制台既没有自己的命契，
 * 也没有可推演的目标，让它调用只会抛异常。
 */
public final class TianyiCommands {
    private TianyiCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tianyi")
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

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static void say(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal("§6[天意] §r" + text), false);
    }

    private static int fate(CommandContext<CommandSourceStack> ctx, boolean defy)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.accept(p, defy));
        return 1;
    }

    private static int doneDefy(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.tryCompleteDefy(p));
        return 1;
    }

    private static int eye(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.borrowEye(p));
        return 1;
    }

    private static int reckon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.borrowReckon(p));
        return 1;
    }

    private static int sever(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.borrowSever(p));
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
        say(p, TianyiFate.studyStart(p, StringArgumentType.getString(ctx, "formula")));
        return 1;
    }

    private static int studySettle(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        say(p, TianyiFate.studySettle(p));
        return 1;
    }
}
'''
write('TianyiCommands.java', CMD)
print('  TianyiCommands：重写（只剩命契/推演/炼道，AI 子树删除）')

# ---------------------------------------------------------------- 5. 其余引用
s = read('quest/TianyiQuestEngine.java')
s = re.sub(r'        // AI 降谕的任务走自己的存储.*?\n        \}\n', '', s, flags=re.S)
s = re.sub(r'        // AI 任务排在前面.*?\n        \}\n', '', s, flags=re.S)
s = re.sub(r'        // ---- AI 降谕的同类目标.*?\n        \}\n    \}\n', '    }\n', s, flags=re.S)
write('quest/TianyiQuestEngine.java', s)
print('  TianyiQuestEngine：AI 任务路由/列表/推进已移除')

s = read('LifeBook.java')
s = s.replace('import net.guzhenren.tianyi.ai.TianyiAiQuests;\n', '')
s = s.replace('        TianyiAiQuests.wipeOnRebirth(player);\n', '')
write('LifeBook.java', s)
print('  LifeBook：AI 任务清理已移除')

s = read('TianyiEvents.java')
s = re.sub(r'\s*// 战绩（给 AI 快照用）\n\s*net\.guzhenren\.tianyi\.ai\.TianyiAiSnapshot\.addKill\(sp, tier\);\n', '\n', s)
s = re.sub(r'\s*net\.guzhenren\.tianyi\.ai\.TianyiAiSnapshot\.addKill\(sp, tier\);\n', '\n', s)
write('TianyiEvents.java', s)
print('  TianyiEvents：AI 战绩上报已移除')

s = read('client/TianyiPanelScreen.java')
s = re.sub(r'        // ---- 模型接口（只显示状态，不发密钥、不发请求）.*?y \+= 4;\n', '', s, flags=re.S)
write('client/TianyiPanelScreen.java', s)
print('  TianyiPanelScreen：模型接口显示块已回退')

# ---------------------------------------------------------------- 6. 元数据与脚本
p = os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml')
s = open(p, encoding='utf-8').read()
s = re.sub(r'# Numen（LLM 同伴）是\*\*可选\*\*前置.*?\n(?=\[\[dependencies)', '', s, flags=re.S)
s = re.sub(r'\[\[dependencies\.tianyi\]\]\nmodId = "numen_api"\n.*?side = "BOTH"\n\n?', '', s, flags=re.S)
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  mods.toml：numen_api 可选依赖已移除')

p = os.path.join(PACK, 'pack_jar.py')
s = open(p, encoding='utf-8').read()
s = re.sub(r"\s*'net/guzhenren/tianyi/ai/[^']*',\n", '\n', s)
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  pack_jar：ai 探针已移除')

p = os.path.join(PACK, 'build.py')
s = open(p, encoding='utf-8').read()
s = s.replace("    'audit_ledger', 'audit_bestiary', 'audit_ai', 'audit_numen',",
              "    'audit_ledger', 'audit_bestiary', 'audit_no_ai',")
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  build.py：审计清单换成 audit_no_ai')

retired = os.path.join(PACK, '_retired')
os.makedirs(retired, exist_ok=True)
for f in ('audit_ai.py', 'audit_numen.py'):
    src = os.path.join(PACK, f)
    if os.path.isfile(src):
        shutil.move(src, os.path.join(retired, f))
        print(f'  退役审计：{f} → _retired/')

# build.gradle 的 numen compileOnly
p = os.path.join(ROOT, 'build.gradle')
s = open(p, encoding='utf-8').read()
s = re.sub(r'\n\s*//.*numen.*\n\s*compileOnly files\([^)]*numen[^)]*\)', '', s, flags=re.I)
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('  build.gradle：numen 依赖行已清理')
