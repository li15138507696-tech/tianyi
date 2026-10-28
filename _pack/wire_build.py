"""接线：javac_check 支持 TIANYI_UPSTREAM_JAR 覆盖；build.py 去掉重复跑审计。"""
import os

ROOT = r'D:\daima\mcmod\天意'
PACK = os.path.join(ROOT, '_pack')

# 1) javac_check：环境变量优先（供 --ephemeral 传临时依赖路径）
p = os.path.join(PACK, 'javac_check.py')
s = open(p, encoding='utf-8').read()
old = """GUZHENREN = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'libs',
                         'guzhenren-0.1-neoforge-1.21.1.jar')"""
new = """# 优先用环境变量（一键构建 --ephemeral 会传临时路径），否则用工程内缓存副本
GUZHENREN = os.environ.get('TIANYI_UPSTREAM_JAR') or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'libs',
    'guzhenren-0.1-neoforge-1.21.1.jar')"""
if old in s:
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('javac_check.py: 已支持 TIANYI_UPSTREAM_JAR')
else:
    print('! javac_check.py 未匹配（可能已改过）')

# 2) build.py：每个审计只跑一次
p = os.path.join(PACK, 'build.py')
s = open(p, encoding='utf-8').read()
old = """        failed = []
        for a in AUDITS:
            ok = run(py(a), env=env, allow_fail=True)
            path = os.path.join(PACK, a + '.py')
            r = subprocess.run([sys.executable, path], cwd=ROOT, env=env, **RUN_KW)
            if r.returncode != 0:"""
new = """        failed = []
        for a in AUDITS:
            path = os.path.join(PACK, a + '.py')
            r = subprocess.run([sys.executable, path], cwd=ROOT, env=env, **RUN_KW)
            if r.returncode != 0:"""
if old in s:
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('build.py: 审计去重（每个只跑一次）')
else:
    print('! build.py 未匹配')
