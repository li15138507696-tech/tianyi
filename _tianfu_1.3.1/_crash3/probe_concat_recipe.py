"""Decode the exact StringConcatFactory recipe used by UiShiliProcedure / UishenfenProcedure."""
import struct
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)


def parse(data):
    """Return (utf8_strings, bootstrap_method_arguments_recipes)."""
    i = 10
    count = struct.unpack('>H', data[8:10])[0]
    pool = {}
    idx = 1
    while idx < count:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            pool[idx] = ('utf8', data[i + 3:i + 3 + ln].decode('utf-8', 'replace'))
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            pool[idx] = ('ref', struct.unpack('>H', data[i + 1:i + 3])[0])
            i += 3
        elif tag in (15,):
            pool[idx] = ('mh', data[i + 1], struct.unpack('>H', data[i + 2:i + 4])[0])
            i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pool[idx] = ('num', None)
            i += 5
        elif tag in (5, 6):
            pool[idx] = ('long', None)
            i += 9
            idx += 1
        else:
            return pool, None
        idx += 1
    return pool, i


for cls in ('net/guzhenren/procedures/UiShiliProcedure.class',
            'net/guzhenren/procedures/UishenfenProcedure.class'):
    data = z.read(cls)
    pool, _ = parse(data)
    print('===', cls.split('/')[-1])
    # find the BootstrapMethods-referenced utf8: it is the one containing \u0001
    for k, v in pool.items():
        if v[0] == 'utf8' and '\u0001' in v[1]:
            print('   recipe repr:', repr(v[1]))
            print('   recipe cjk  :', v[1].replace('\u0001', '{arg}'))
