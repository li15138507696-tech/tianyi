import re

apply = open('src/main/java/net/guzhenren/tianfu/TianfuApply.java', encoding='utf-8').read()
screen = open('src/main/java/net/guzhenren/tianfu/client/TianfuViewScreen.java', encoding='utf-8').read()
combat = open('src/main/java/net/guzhenren/tianfu/TianfuCombat.java', encoding='utf-8').read()
attrs = open('src/main/java/net/guzhenren/tianfu/TianfuAttributes.java', encoding='utf-8').read()
effects = open('src/main/java/net/guzhenren/tianfu/TianfuEffects.java', encoding='utf-8').read()

print("=== A. 服务端体质唯一门禁 ===")
# find the guard block in buy()
i = apply.find('Kind.BODY || spec.kind() == TianfuPool.Kind.SIJUE')
print(apply[i-120:i+260])

print()
print("=== B. 客户端 hasBody() 是否有 owned 回退 ===")
j = screen.find('private boolean hasBody()')
print(screen[j:j+700])
