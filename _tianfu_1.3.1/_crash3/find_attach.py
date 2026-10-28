import zipfile, re

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找注册 DataAttachment 的地方
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    d = z.read(n)
    if b'registerAttachments' in d or b'DataAttachment' in d or b'AttachmentType' in d:
        hits.append(n)

print('attachment-related classes:', len(hits))
for h in hits[:20]:
    print(' ', h)
