"""修正 audit_ui 对开窍桩的判定：看内容，不看文件名。"""
import os

p = os.path.join(r'D:\daima\mcmod\天意', '_pack', 'audit_ui.py')
s = open(p, encoding='utf-8').read()

old = """    for bad in ('TianfuOpening.java',):
        if bad in names:
            problems.append(f'open/{bad} 是开窍注入，用户口径明确不要（应为空桩纯签名）')"""

new = """    op = os.path.join(open_dir, 'TianfuOpening.java')
    if os.path.isfile(op):
        src_op = open(op, encoding='utf-8').read()
        if '空桩' not in src_op:
            problems.append('open/TianfuOpening.java 不是空桩（须有「空桩」说明，内部不写玩家数据）')
        for bad_call in ('TianfuData.', 'TianfuTalents.', 'TianfuApply.'):
            if bad_call in src_op:
                problems.append('开窍桩里调用了 ' + bad_call + ' —— 空桩只能有签名，不能有逻辑')
        if len(src_op.splitlines()) > 60:
            problems.append('开窍桩超过 60 行，说明逻辑被塞回来了')"""

if old not in s:
    print('! 未找到待替换片段，请人工检查')
else:
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('audit_ui: 空桩判定改为看内容')
