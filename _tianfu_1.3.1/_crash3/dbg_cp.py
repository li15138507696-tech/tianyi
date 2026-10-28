"""Debug: parse one KeyMapping class and print all int constants + method Code offsets."""
import struct
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
JAR = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods\tianfu-1.2.17.jar'
CLS = 'net/guzhenren/tianfu/client/TianfuKeyMappings.class'

z = zipfile.ZipFile(JAR)
data = z.read(CLS)
print('magic ok:', data[:4] == b'\xca\xfe\xba\xbe', 'len', len(data))

i = 10
count = struct.unpack('>H', data[8:10])[0]
print('cp count', count)
cp = {}
idx = 1
while idx < count:
    tag = data[i]
    if tag == 1:
        ln = struct.unpack('>H', data[i + 1:i + 3])[0]
        cp[idx] = ('utf8', data[i + 3:i + 3 + ln].decode('utf-8', 'replace'))
        i += 3 + ln
    elif tag == 7:
        cp[idx] = ('class', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
    elif tag == 8:
        cp[idx] = ('string', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
    elif tag in (9, 10, 11):
        cp[idx] = ('ref', struct.unpack('>HH', data[i + 1:i + 5])); i += 5
    elif tag == 12:
        cp[idx] = ('nat', struct.unpack('>HH', data[i + 1:i + 5])); i += 5
    elif tag in (3, 4):
        cp[idx] = ('int', struct.unpack('>i', data[i + 1:i + 5])[0]); i += 5
    elif tag in (5, 6):
        cp[idx] = ('long', None); i += 9; idx += 1
    elif tag == 15:
        cp[idx] = ('mh', None); i += 4
    elif tag == 16:
        cp[idx] = ('mt', None); i += 3
    elif tag in (17, 18):
        cp[idx] = ('dyn', None); i += 5
    elif tag in (19, 20):
        cp[idx] = ('mod', None); i += 3
    else:
        print('UNKNOWN TAG', tag, 'at', i); break
    idx += 1
print('cp parsed, i now', i)
print('int constants:', {k: v[1] for k, v in cp.items() if v[0] == 'int'})
print('strings:', [v[1] for v in cp.values() if v[0] == 'utf8' and v[1].startswith('key.')])
