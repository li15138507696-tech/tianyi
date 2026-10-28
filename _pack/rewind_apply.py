"""春秋蝉回溯的**离线交换**步骤（服务器停止后运行）。

    python _pack/rewind_apply.py --world "D:/game/mcmod/x/saves/xxx"
    python _pack/rewind_apply.py --world "..." --dry-run
    python _pack/rewind_apply.py --world "..." --list

## 为什么必须是离线步骤

运行期零散覆盖 `region/*.mca` 会直接毁档：区块正在被读写，复制到一半的文件
会让存档变成"半新半旧"，严重时整片地形异常、实体错位、甚至无法加载。

所以模组侧（`SnapshotService.requestRewind`）只做三件事：
校验快照 → 写 `tianyi_snapshots/pending_rewind.json` → 提示停机。
**真正的文件交换在本脚本里做**，此时服务器已经停止，没有任何句柄占着区块文件。

## 安全顺序（任一步失败即停止，不动存档）

1. 读待恢复标记；
2. 按 Java 侧同一算法重算快照目录的 SHA-256 分区摘要，与标记不符 → 拒绝；
3. 把**当前世界**整个复制到 `tianyi_rewind_backup_<时间戳>/`（回溯本身也能回退）；
4. 逐个子目录替换（先删后拷，只动标记里登记过的分区所在的目录）；
5. 写 `tianyi_snapshots/rewind_done_<分支>.json`，删掉待恢复标记。

## 明确边界

- 只还原快照**实际保存过**的路径；快照没有的目录保持原样。
- 现实玩家已经知道的信息无法被抹除：这件事脚本做不到，也不会假装做到。
- 施术者只带走意志与记忆：背包/资源/装备随世界一起回到过去，不会出现"双份"。
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import time

# 与 SnapshotService 的分区 → 子路径映射保持一致
SECTIONS = {
    'CHUNK': ['region', 'dimensions'],
    'BLOCK_ENTITY': ['poi'],
    'ENTITY': ['entities'],
    'PLAYER': ['playerdata'],
    'MOD_DATA': ['data'],
    'WORLD_STAGE': ['data'],
}
SNAP_DIR = 'tianyi_snapshots'
PENDING = 'pending_rewind.json'
BACKUP_PREFIX = 'tianyi_rewind_backup_'


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


def digest_paths(base, rels):
    """与 Java 的 digestPaths 同算法：按 rel 顺序、文件路径排序，行 'rel:sha256' 拼接后再哈希。"""
    lines = []
    files = 0
    total = 0
    for rel in rels:
        p = os.path.join(base, rel)
        if not os.path.isdir(p):
            continue
        collected = []
        for root, _, fs in os.walk(p):
            for f in fs:
                fp = os.path.join(root, f)
                collected.append(fp)
        for fp in sorted(collected):
            rp = os.path.relpath(fp, base).replace('\\', '/')
            lines.append(f'{rp}:{sha256_file(fp)}')
            files += 1
            total += os.path.getsize(fp)
    h = hashlib.sha256()
    for line in lines:
        h.update(line.encode('utf-8'))
    return h.hexdigest(), files, total


def find_pending(world):
    p = os.path.join(world, SNAP_DIR, PENDING)
    return p if os.path.isfile(p) else None


def verify(world, meta):
    """重算快照目录摘要，与标记比对。返回 (ok, messages)。"""
    msgs = []
    snap_dir = os.path.join(world, SNAP_DIR, meta['snapshot'])
    if not os.path.isdir(snap_dir):
        return False, [f'快照目录不存在：{snap_dir}']
    ok = True
    for part in meta.get('parts', []):
        sec = part['section']
        rels = SECTIONS.get(sec)
        if not rels:
            continue
        digest, files, total = digest_paths(snap_dir, rels)
        if digest != part['digest']:
            ok = False
            msgs.append(f'  分区 {sec} 摘要不符：磁盘 {digest[:16]}… != 记录 {part["digest"][:16]}…')
        else:
            msgs.append(f'  分区 {sec} OK（{files} 文件 / {total} 字节）')
    return ok, msgs


def backup_current(world):
    dest = os.path.join(world, BACKUP_PREFIX + time.strftime('%Y%m%d-%H%M%S'))
    os.makedirs(dest, exist_ok=True)
    for name in ('level.dat', 'level.dat_old', 'region', 'entities', 'poi', 'data',
                 'playerdata', 'dimensions'):
        src = os.path.join(world, name)
        if os.path.isfile(src):
            shutil.copy2(src, os.path.join(dest, name))
        elif os.path.isdir(src):
            shutil.copytree(src, os.path.join(dest, name))
    return dest


def swap(world, meta):
    """按分区替换目录。只动快照里登记过的分区对应目录。"""
    snap_dir = os.path.join(world, SNAP_DIR, meta['snapshot'])
    targets = set()
    for part in meta.get('parts', []):
        for rel in SECTIONS.get(part['section'], []):
            targets.add(rel)
    done = []
    for rel in sorted(targets):
        src = os.path.join(snap_dir, rel)
        if not os.path.isdir(src):
            continue
        dst = os.path.join(world, rel)
        if os.path.isdir(dst):
            shutil.rmtree(dst)
        elif os.path.isfile(dst):
            os.remove(dst)
        shutil.copytree(src, dst)
        done.append(rel)
    # level.dat 单独处理（不在分区里，但必须一起还原，否则世界种子/时间对不上）
    lvl = os.path.join(snap_dir, 'level.dat')
    if os.path.isfile(lvl):
        shutil.copy2(lvl, os.path.join(world, 'level.dat'))
        done.append('level.dat')
    return done


def main(argv):
    ap = argparse.ArgumentParser(description='春秋蝉：把存档还原到某个已校验的快照')
    ap.add_argument('--world', required=True, help='存档目录（含 level.dat 的那一层）')
    ap.add_argument('--dry-run', action='store_true', help='只校验与打印，不动文件')
    ap.add_argument('--list', action='store_true', help='列出已有快照')
    args = ap.parse_args(argv)

    world = os.path.abspath(args.world)
    if not os.path.isdir(world):
        print(f'FAIL 存档目录不存在：{world}')
        return 2

    snap_root = os.path.join(world, SNAP_DIR)
    if args.list:
        if not os.path.isdir(snap_root):
            print('没有快照目录')
            return 0
        for name in sorted(os.listdir(snap_root)):
            p = os.path.join(snap_root, name)
            if os.path.isdir(p):
                print(f'  快照 {name}')
            elif name.endswith('.json') and name != PENDING:
                print(f'  元数据 {name}')
        pending = find_pending(world)
        if pending:
            print(f'  注意：存在待恢复标记 {pending}')
        return 0

    pending = find_pending(world)
    if not pending:
        print('没有待恢复标记（tianyi_snapshots/pending_rewind.json）。')
        print('先在游戏里用 /tianyi rewind <快照id> confirm 发起回溯，服务器停机后再运行本脚本。')
        return 1

    with open(pending, encoding='utf-8') as f:
        meta = json.load(f)
    print(f'待恢复：快照 {meta["snapshot"]} → 新分支 {meta.get("newBranch", "?")}')
    print(f'保存范围：{meta.get("scope", "?")}')

    ok, msgs = verify(world, meta)
    for m in msgs:
        print(m)
    if not ok:
        print('FAIL 快照完整性校验未通过，拒绝还原（存档未改动）')
        return 1

    if args.dry_run:
        print('DRY-RUN：校验通过，未改动任何文件')
        return 0

    print('1) 备份当前世界 …')
    bdir = backup_current(world)
    print(f'   → {bdir}')
    print('2) 交换文件 …')
    done = swap(world, meta)
    print(f'   → 已还原：{", ".join(done)}')

    stamp = {
        'snapshot': meta['snapshot'],
        'branch': meta.get('branch'),
        'newBranch': meta.get('newBranch'),
        'appliedAt': time.strftime('%Y-%m-%d %H:%M:%S'),
        'backup': os.path.basename(bdir),
        'restored': done,
        'note': '施术者只带走意志与记忆；背包/资源/装备随世界回到过去，不产生双份',
    }
    done_path = os.path.join(snap_root, f'rewind_done_{meta.get("newBranch", "branch")}.json')
    with open(done_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(stamp, f, ensure_ascii=False, indent=2)
    os.remove(pending)
    print(f'3) 完成标记：{done_path}')
    print('回溯已完成。现在可以重新启动服务器。')
    print(f'如需撤销：把 {bdir} 里的内容拷回 {world}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
