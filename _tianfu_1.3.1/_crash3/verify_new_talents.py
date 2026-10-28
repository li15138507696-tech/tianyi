import zipfile, os, re, struct

jar = r'D:\daima\mcmod\蛊真人附属\build\libs\tianfu-1.2.14.jar'
z = zipfile.ZipFile(jar)
data = z.read('net/guzhenren/tianfu/TianfuTalents.class')
z.close()

# constant pool
pos = 8
n = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
strs = []
i = 1
while i < n:
    tag = data[pos]; pos += 1
    if tag == 1:
        ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        strs.append(data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
    elif tag in (7, 8, 16, 19, 20):
        pos += 2
    elif tag == 15:
        pos += 3
    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
        pos += 4
    elif tag in (5, 6):
        pos += 8; i += 1
    else:
        raise SystemExit('tag %d' % tag)
    i += 1

need = ['bingxue_daoti', 'hundao_ronghe', 'qiyun_yizhi', 'shen_liupai_jianji',
        'guzhenren_addon:ice_path_boost', 'hundao_addon:hundao_zengfu',
        'yundao:yizhi_qiankun', 'amp:', 'isLoaded']
for x in need:
    print(('OK  ' if x in strs else 'MISS'), x)

print()
print('total utf8 entries:', len(strs))
