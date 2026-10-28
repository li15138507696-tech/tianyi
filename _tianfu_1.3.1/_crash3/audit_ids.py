import re, sys

pool = open('src/main/java/net/guzhenren/tianfu/TianfuPool.java', encoding='utf-8').read()
dh = open('src/main/java/net/guzhenren/tianfu/TianfuDaohen.java', encoding='utf-8').read()

ids = set(re.findall(r'body\("(\w+)"', pool))
cases = set(re.findall(r'case "(\w+)" ->', dh))

print('pool bodies :', len(ids))
print('daohen cases:', len(cases))
print('body 无道痕(应仅 xueshi/shashen 类):', sorted(ids - cases))
print('道痕无对应 body(可疑):', sorted(cases - ids))
