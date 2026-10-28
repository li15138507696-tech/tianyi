"""模拟宙侵施加数学：验证 amp=0 是否真的无副作用，以及时长公式。"""

BASE_DURATION = 200
AMPLIFIER = 0

def on_start(baifenbi, amp):
    """ZhoudaobuffZhuangTaiXiaoGuoKaiShiYingYongShiProcedure: baifenbi -= amp*0.1"""
    return baifenbi - amp * 0.1

def on_end(baifenbi, amp):
    """ZhoudaobuffZhuangTaiXiaoGuoJieShuShiProcedure: baifenbi += amp*0.1"""
    return baifenbi + amp * 0.1

def duration(mult):
    return max(20, BASE_DURATION * mult)

print('=== amp=0 是否无副作用 ===')
for b in (1.0, 3.0):
    s = on_start(b, AMPLIFIER)
    e = on_end(s, AMPLIFIER)
    print(f'  baifenbi {b} -> 开始后 {s} -> 结束后 {e}  对称: {abs(e-b)<1e-9}  无变化: {abs(s-b)<1e-9}')

print()
print('=== 时长公式（mult = 目标 zhoudao_shijian_baifenbi）===')
for m in (1.0, 3.0, 0.0, -1.0):
    eff = m if m > 0 else 1.0
    print(f'  mult={m:>5} -> 生效 {eff} -> duration {duration(eff)} tick')

print()
print('=== amp=1 会怎样（我们刻意不用）===')
b = 1.0
s = on_start(b, 1)
print(f'  baifenbi {b} -> {s}  即目标后续宙侵时长 ×{s}（缩短）——会削弱宙道爆发，故取 amp=0')

print()
print('ALL OK')
