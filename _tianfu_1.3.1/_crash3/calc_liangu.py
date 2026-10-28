# 计算炼蛊进度溢出速度
# TianfuEffects: v.LianGuJinDu += 0.05 * (1.0 + liangu) 每 tick
# shen_liangu_tianshu 的 liangu = 0.90
# 本体: 20 ticks = 1 秒

liangu = 0.90
per_tick = 0.05 * (1.0 + liangu)
per_sec = per_tick * 20

print(f'每 tick +{per_tick:.4f}')
print(f'每秒   +{per_sec:.4f}')
print(f'从 1.0 到 110 需要: {(110-1)/per_sec:.1f} 秒')
print(f'从 1.0 到 100 需要: {(100-1)/per_sec:.1f} 秒')
print()
print('本体区间: [0,10) [10,20) ... [90,100) [100,110)')
print('超过 110 → 全部 LianGuJinDuNProcedure 返回 false → 进度条整体不绘制')
