from pathlib import Path
import json, re

AUDIT = Path(__file__).resolve().parent
notes = {
    'tianfu': ('1.3.5', '修复出身成交账读写不一致与重复补点，兼容旧嵌套账目；出身请求补齐开窍和结算门禁；随机候选跳过已购及互斥条目；修复催蛊凝涩、心神如渊与噬元之性失效；恢复持续硬代价约束与寿元递减上限；保留普通重生免死冷却并清理反击运行状态。'),
    'daotu': ('1.6.1', '修复普通死亡及末地克隆丢失成长、未开窍领取成长、五转抽选上限、旧档迁移抽次双计数；修复功绩与声望解锁记录读错键、转世基线传播、历史里程碑误补发和负声望初始化；恢复天赋候选中文名称。'),
    'malice': ('1.1.1', '难度倍率只结算 INIT 阶段，修复复制实体重复叠倍率以及重初始化原始等级误判；根据持久归属 UUID 排除已驯服妖兽，主人离线或跨维度时仍然有效。保留 mc1020 的原始实体标签和补挂逻辑。'),
}
for name, (version, note) in notes.items():
    root = AUDIT / 'work' / name
    props = root / 'version.properties'
    text = props.read_text(encoding='utf-8')
    props.write_text(re.sub(r'^(version=)[^\r\n]+', lambda m: m[1] + version, text, count=1, flags=re.M), encoding='utf-8')
    meta = root / 'src/main/resources/META-INF/neoforge.mods.toml'
    text = meta.read_text(encoding='utf-8-sig')
    meta.write_text(re.sub(r'^(version\s*=\s*")[^"]+(".*)$', lambda m: m[1] + version + m[2], text, count=1, flags=re.M), encoding='utf-8')
    doc = root / 'docs/2026-10-26-bugfix.md'
    doc.write_text(f'# {name} {version} 修复记录\n\n日期：2026-10-26\n\n{note}\n\n验证：Java 21 全量编译、针对性 Java 回归、成品 JAR 的元数据、JSON、Mixin 类引用与资源完整性检查。回归使用最小游戏 API 替身；游戏内未实测。\n', encoding='utf-8')
    print(name, 'version', version)

root = AUDIT / 'work/tianfu'
for relative in ['docs/天赋系统介绍文档.md', 'docs/宣传文档.md']:
    path = root / relative
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'(?<=版本：)tianfu-\d+\.\d+\.\d+', 'tianfu-1.3.5', text)
    if relative.endswith('宣传文档.md'):
        marker = '**当前版本：tianfu-1.3.5**'
        text = text.replace(marker, marker + '\n\n- 1.3.5：' + notes['tianfu'][1], 1)
    else:
        marker = '\n## 一、模组简介'
        text = text.replace(marker, '\n> 1.3.5 修复内容见 [本次修复记录](2026-10-26-bugfix.md)。\n' + marker, 1)
    path.write_text(text, encoding='utf-8')

# Only mc1020 is rebuilt in this request; keep the other two variant links historical.
path = AUDIT / 'work/malice/docs/适配说明.md'
text = path.read_text(encoding='utf-8')
text = text.replace('> 版本 1.1.0', '> 版本 1.1.1', 1)
text = text.replace('guzhenren_malice-1.1.0-mc1020.jar', 'guzhenren_malice-1.1.1-mc1020.jar')
path.write_text(text, encoding='utf-8')
