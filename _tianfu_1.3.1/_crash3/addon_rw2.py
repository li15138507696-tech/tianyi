import zipfile, os, struct, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'

FIELDS = ['qiyun_shangxian', 'qiyun', 'tizhi', 'zhenyuan', 'zuida_zhenyuan',
          'niantou', 'niantou_rongliang', 'shouyuan', 'kongqiao',
          'zuida_hunpo', 'hunpochengzaijixian', 'zuida_jingli', 'niantou_zhida',
          'LianGuJinDu', 'lg_ke', 'GuFang']

addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '蛊虫使用记录': '蛊虫使用记录-1.0.1.jar',
    '六合一大合集': '20261020-2036_六合一大合集_v1.0.0.jar',
}


def scan_class(data):
    """Return (read_fields, write_fields) by pairing getfield/putfield with CP Fieldref names."""
    try:
        assert data[:4] == b'\xca\xfe\xba\xbe'
        pos = 8
        n = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        cp = {}
        i = 1
        while i < n:
            tag = data[pos]; pos += 1
            if tag == 1:
                ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
                cp[i] = ('Utf8', data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = (tag, struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
            elif tag == 15:
                cp[i] = (tag, data[pos], struct.unpack('>H', data[pos+1:pos+3])[0]); pos += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                cp[i] = (tag, struct.unpack('>HH', data[pos:pos+4])); pos += 4
            elif tag in (5, 6):
                cp[i] = (tag, None); pos += 8; i += 1
            else:
                return set(), set()
            i += 1
    except Exception:
        return set(), set()

    # Fieldref -> (class_idx, nat_idx); nat -> (name_idx, desc_idx)
    def fld_name(idx):
        try:
            t, v = cp[idx]
            if t != 9:
                return None
            nat = cp[v[1]]
            return cp[nat[1][0]][1]
        except Exception:
            return None

    reads, writes = set(), set()
    for idx, v in cp.items():
        if v[0] == 9:
            nm = fld_name(idx)
            if nm in FIELDS:
                # 粗略：只按「该字段被引用」计入读写两侧无法区分，
                # 这里先记录引用，具体读写由下面的 opcode 扫描再分
                pass

    # opcode scan over method bodies is complex; approximate by searching raw
    # getfield(0xB4)/putfield(0xB5) followed by 2-byte CP index
    p = pos
    while p < len(data) - 3:
        op = data[p]
        if op in (0xB4, 0xB5):
            idx = struct.unpack('>H', data[p+1:p+3])[0]
            nm = fld_name(idx)
            if nm in FIELDS:
                (writes if op == 0xB5 else reads).add(nm)
            p += 3
        else:
            p += 1
    return reads, writes


for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    agg_r = collections.Counter()
    agg_w = collections.Counter()
    detail = collections.defaultdict(lambda: ([], []))
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        r, w = scan_class(z.read(n))
        for f in r:
            agg_r[f] += 1
            detail[f][0].append(n)
        for f in w:
            agg_w[f] += 1
            detail[f][1].append(n)
    z.close()
    print(f'===== {label}  ({fn})')
    for f in FIELDS:
        if agg_r.get(f) or agg_w.get(f):
            print(f'  {f:22s} R={agg_r.get(f,0):3d}  W={agg_w.get(f,0):3d}')
            for c in sorted(set(detail[f][1]))[:3]:
                print(f'       W {c}')
    print()
