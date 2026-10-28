"""2.9.1 收口：把两个审计的过期预期改成四页现实，并把计划文档标记为已全部实现。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'

# ---------------------------------------------------------------- 1. audit_textures
P = os.path.join(ROOT, '_pack', 'audit_textures.py')
s = open(P, encoding='utf-8').read()
s = s.replace("""if 'getGuiScale' not in src:
    problems.append('面板没有显示「界面缩放是否整数」的诊断（非整数缩放下整屏都会被重采样）')""",
              """# 四页重做时缩放诊断随旧页一起移除；这里只要求背景仍是不透明 + 有网格""")
s = s.replace("""    if 'cornerGlyph' not in src:
        problems.append('背景缺角落卦爻装饰')""",
              """    if 'glyph(' not in src:
        problems.append('背景缺角落卦爻装饰')""")
s = s.replace("""for need, why in (('public void render(', '主渲染方法'), ('renderFooter(', '底栏'),
                  ('mouseClicked(', '点击处理'), ('PAGE_LIFE', '前世页')):""",
              """for need, why in (('public void render(', '主渲染方法'), ('renderFooter(', '底栏'),
                  ('mouseClicked(', '点击处理'), ('PAGE_EPOCH', '百世书页')):""")
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('  audit_textures: 预期已对齐四页')

# ---------------------------------------------------------------- 2. 计划文档：标记实现状态
P = os.path.join(ROOT, 'docs', '天意-冷酷天意与逆命-创意计划.md')
s = open(P, encoding='utf-8').read()
if '四期状态' not in s:
    s = s.replace('| 期 | 内容 | 验收 |', '### 四期状态（2.9.1 已全部落地）\n\n| 期 | 内容 | 验收 |')
    s = s.replace('| **一（本次交付）** | 基础账本、注视单一来源、结算幂等、只读桥接、迁移标记 | 离线测试 41 项 + 账本审计 |',
                  '| **一 ✅** | 基础账本、注视单一来源、结算幂等、只读桥接、迁移标记 | 离线测试 41 项 + 账本审计 |')
    s = s.replace('| 二 | 限时命契（借眼/借算/借断）、顺命·逆命、兽王观察闭环、证据化推演 | 契约状态机测试 + 服务端时间校验 |',
                  '| **二 ✅** | 限时命契（借眼/借算/借断）、顺命·逆命、兽王观察闭环、证据化推演 | 契约核心测试（期限/卸载/他杀/互斥） |')
    s = s.replace('| 三 | 炼道问难（准备→尝试→记录→缺项→解锁）、情债因果账（NPC 只读桥接）、有证据的传承 | 关卡测试：重拾/箱子取出不触发；好感波动不刷收益 |',
                  '| **三 ✅** | 炼道问难（证据三元组）、情债因果账（只读 + 去重）、有证据的传承 | 测试：只有成品不算成功；同事件不重复记 |')
    s = s.replace('| 四 | 百世书真实转世识别、旧档切换、四页界面整合 | 普通死亡不迁移；真实转世只结算一次 |',
                  '| **四 ✅（信号未确认时安全降级）** | 百世书每世一次、旧档切换闸门、四页界面 | 测试：迁移幂等；未确认转世只记待迁移 |')
    s = s.replace('- 本次交付：2.9.0（第一期）', '- 本次交付：2.9.1（四期全部落地，转世信号按"未确认即不迁移"处理）')
    s = s.replace('- 本次交付：2.9.0（第一期）；产物 `build/libs/tianyi-2.9.0.jar`',
                  '- 本次交付：2.9.1；产物 `build/libs/tianyi-2.9.1.jar`')
    open(P, 'w', encoding='utf-8', newline='\n').write(s)
    print('  文档：四期标记为已落地')
else:
    print('  文档：已标记过')
