import zipfile

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)

target = 'net/guzhenren/procedures/KaiShiLianZhiProcedure.class'
data = z.read(target)
z.close()

# dump constant pool strings-ish: find printable runs
import re
runs = re.findall(rb'[\x20-\x7e]{4,}', data)
print('=== strings in', target, '===')
for r in runs[:80]:
    print('  ', r.decode('ascii', 'replace'))
