"""2.9.3：把「模型接口」重新摆到面板上（四页不变，塞进因果页）。

四页重做时把原来的「降谕」页删了，AI 的显示入口随之消失——配置、三条通路、
全部命令其实都还在，只是玩家看不到。这里在不破四页的前提下，把接口状态放回因果页底部：

    后端   numen / provider / dsh / http / local
    模型   当前模型 id（没配就显示 endpoint 主机）
    密钥   来源（玩家自备 / 自定义接口 / DSH / 服主兜底 / 无）
    今日   剩余祈问次数

**只读显示**：不变量、不发密钥、不触发请求。密钥本来就只在服务端解析，
状态包里从来只发 source/model/host 三个非敏感字段。
"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi',
                 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()

anchor = '''        drawWrapped(g, "情债与契约记录只读接入本体 NPC 数据，不写好感、不生成收益。"'''

add = '''        // ---- 模型接口（只显示状态，不发密钥、不发请求）
        y += 6;
        divider(g, x, x + w, y);
        y += 9;
        g.drawString(font, "模型接口", x, y, T_GOLD, true);
        g.drawString(font, "判定全在服务端 · AI 只表达", x + 50, y, T_DIM, true);
        y += LINE;
        CompoundTag ai = TianyiClientState.get().getCompound("ai");
        String backend = ai.getString("backend");
        String backendText = switch (backend) {
            case "numen" -> "Numen（本体 LLM 同伴）";
            case "provider" -> "自定义接口 · " + ai.getString("provider");
            case "dsh" -> "DSH 凭据";
            case "http" -> "自建 HTTP";
            default -> "本地模板（未联网）";
        };
        y = stat(g, x, y, "后端", backendText, ai.getBoolean("enabled") ? T_BODY : T_WARN);
        String model = ai.getString("model");
        y = stat(g, x, y, "模型", model.isEmpty()
                ? (ai.getString("host").isEmpty() ? "未配置" : ai.getString("host")) : model, T_BODY);
        String src = ai.getString("source");
        String srcText = switch (src) {
            case "player" -> "玩家自备 key";
            case "provider" -> "自定义接口";
            case "dsh" -> "DSH 本体凭据";
            case "server" -> "服主兜底 key";
            default -> ai.getBoolean("hasKey") ? src : "没有 key（离线可用）";
        };
        y = stat(g, x, y, "密钥", srcText, ai.getBoolean("hasKey") || "local".equals(backend)
                ? T_BODY : T_DIM);
        y = stat(g, x, y, "今日", "剩余祈问 " + ai.getInt("remain") + " 次", T_DIM);
        y += 2;
        y = drawWrapped(g, "配置：/tianyi ai status 看详情；setkey / setendpoint / setmodel / "
                + "providers 配置；或编辑 config/tianyi-ai-providers.json。", x, y, w, 2, T_DIM);
        y += 4;
'''

if '模型接口' in s:
    print('  已有接口显示，跳过')
else:
    if anchor in s:
        s = s.replace(anchor, add + anchor)
        open(P, 'w', encoding='utf-8', newline='\n').write(s)
        print('  + 因果页已加入「模型接口」状态块')
    else:
        print('  ! 锚点未匹配')
