import os, re

BASE = 'src/main/java/net/guzhenren/tianfu'

def grab(fn):
    return open(os.path.join(BASE, fn), encoding='utf-8').read()

checks = []

# ---- TianfuApply.buy / revoke ----
apply_txt = grab('TianfuApply.java')
checks.append(('TianfuApply.buy(ServerPlayer,String)->Z',
               bool(re.search(r'public static boolean buy\(\s*ServerPlayer', apply_txt))))
checks.append(('TianfuApply.revoke(ServerPlayer,String)->Z',
               bool(re.search(r'public static boolean revoke\(\s*ServerPlayer', apply_txt))))

# ---- TianfuData methods ----
data_txt = grab('TianfuData.java')
for sig, pat in [
    ('addPoints(Player,int)->void', r'public static void addPoints\('),
    ('difficulty(Player)->int',     r'public static int difficulty\('),
    ('isConfirmed(Player)->boolean',r'public static boolean isConfirmed\('),
    ('markConfirmed(Player)->void', r'public static void markConfirmed\('),
    ('points(Player)->int',         r'public static int points\('),
    ('purchased(Player)->List',     r'public static List<String> purchased\('),
    ('setDifficulty(Player,int)',   r'public static void setDifficulty\('),
]:
    checks.append((sig, bool(re.search(pat, data_txt))))

# ---- TianfuPool statics ----
pool_txt = grab('TianfuPool.java')
for sig, pat in [
    ('bodies()->List',  r'public static List<Spec> bodies\('),
    ('byId(String)',    r'public static Spec byId\('),
    ('entries()->List', r'public static List<Spec> entries\('),
    ('jiyuans()->List', r'public static List<Spec> jiyuans\('),
    ('sijues()->List',  r'public static List<Spec> sijues\('),
]:
    checks.append((sig, bool(re.search(pat, pool_txt))))

# ---- Spec record accessors ----
for sig, pat in [
    ('Spec.cost()',    r'int cost'),
    ('Spec.desc()',    r'String desc'),
    ('Spec.id()',      r'String id'),
    ('Spec.kind()',    r'Kind kind'),
    ('Spec.name()',    r'String name'),
    ('Spec.quality()', r'String quality'),
    ('Spec.tizhi()',   r'int tizhi'),
]:
    checks.append((sig, bool(re.search(pat, pool_txt))))

# ---- Kind ordinal order ----
m = re.search(r'public enum Kind \{([^}]*)\}', pool_txt)
kind_order = [x.strip() for x in m.group(1).split(',') if x.strip()] if m else []
checks.append(('Kind order == BODY,ENTRY,SIJUE,JIYUAN',
               kind_order == ['BODY', 'ENTRY', 'SIJUE', 'JIYUAN']))

# ---- others ----
checks.append(('TianfuDifficulty.OPTIONS', bool(re.search(r'static final List<Option> OPTIONS', grab('TianfuDifficulty.java')))))
checks.append(('TianfuDifficulty.byId', bool(re.search(r'static Option byId\(', grab('TianfuDifficulty.java')))))
for sig, pat in [('Option.desc()', r'String desc'), ('Option.id()', r'String id'),
                 ('Option.name()', r'String name'), ('Option.points()', r'int points')]:
    checks.append((sig, bool(re.search(pat, grab('TianfuDifficulty.java')))))
checks.append(('TianfuNames.bodyName(double)', bool(re.search(r'static String bodyName\(double', grab('TianfuNames.java')))))
checks.append(('TianfuOpening.hasOpenedQiao(Player)', bool(re.search(r'static boolean hasOpenedQiao\(Player', grab('TianfuOpening.java')))))
checks.append(('TianfuTier.tierName(double)', bool(re.search(r'static String tierName\(double', grab('TianfuTier.java')))))

ok = 0
for sig, good in checks:
    print(('  OK    ' if good else '  FAIL  ') + sig)
    ok += bool(good)
print()
print(f'{ok}/{len(checks)} compatible')
print('Kind order found:', kind_order)
