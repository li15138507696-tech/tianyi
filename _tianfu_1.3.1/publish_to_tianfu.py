"""Publish the reviewed working-copy files after an explicit directory permission grant."""
from pathlib import Path
import hashlib
import json
import shutil
import sys

ROOT=Path(__file__).resolve().parent
TARGET=Path(r'D:/daima/mcmod/蛊真人附属').resolve()
MANIFEST=ROOT/'publish-manifest.json'
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None

if '--prepare' in sys.argv:
    files=[]
    for folder in ['src','docs']:
        for p in (ROOT/folder).rglob('*'):
            if p.is_file() and digest(p)!=digest(TARGET/p.relative_to(ROOT)):
                files.append(p.relative_to(ROOT).as_posix())
    files+=['version.properties','build/libs/tianfu-1.3.1.jar']
    files+=['_tmp_verify/'+name for name in ['TianfuCacheCheck.java','verify_cache.py','SelectionFlowCheck.java','verify_selection_flow.py','UiCheck.java','verify_ui.py']]
    files+=['_tmp_verify/ui/'+name+'.png' for name in ['wide','compact-list','compact-detail','confirmation','readonly']]
    records=[{'path':f,'before':digest(TARGET/f),'after':digest(ROOT/f)} for f in sorted(set(files))]
    if any(r['after'] is None for r in records):raise SystemExit('Missing delivery file')
    old=TARGET/'build/libs/tianfu-1.3.0.jar'
    data={'target':str(TARGET),'files':records,'old_jar_sha256':digest(old)}
    MANIFEST.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Prepared',len(records),'files; new jar SHA256:',digest(ROOT/'build/libs/tianfu-1.3.1.jar'))
    for r in records: print(r['path'])
elif '--publish' in sys.argv:
    data=json.loads(MANIFEST.read_text(encoding='utf-8'))
    if Path(data['target']).resolve()!=TARGET:raise SystemExit('Unexpected destination')
    for r in data['files']:
        dest=(TARGET/r['path']).resolve()
        if not dest.is_relative_to(TARGET):raise SystemExit('Destination escapes project')
        if digest(dest)!=r['before']:raise SystemExit('Original changed since review: '+r['path'])
        if digest(ROOT/r['path'])!=r['after']:raise SystemExit('Delivery changed since review: '+r['path'])
    backup=TARGET/'_backup/ui-1.3.1-20261025'
    backup.mkdir(parents=True,exist_ok=False)
    for r in data['files']:
        dest=TARGET/r['path']
        if dest.is_file():
            saved=backup/r['path'];saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
    for r in data['files']:
        dest=TARGET/r['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/r['path'],dest)
        if digest(dest)!=r['after']:raise SystemExit('Copy verification failed: '+r['path'])
    if digest(TARGET/'build/libs/tianfu-1.3.0.jar')!=data['old_jar_sha256']:raise SystemExit('Old jar changed')
    shutil.copy2(MANIFEST,backup/'manifest.json')
    print('Published and hash-verified',len(data['files']),'files.')
    print('Jar:',TARGET/'build/libs/tianfu-1.3.1.jar')
    print('Backup:',backup)
    print('Old 1.3.0 jar preserved. No game instance was modified.')
else:raise SystemExit('Use --prepare or --publish')
