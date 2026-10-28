"""把 refs/*.javap.txt 的成员声明压平成速查表（去 lambda / synthetic）。"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
REFS = os.path.join(ROOT, '_pack', 'refs')
OUT = os.path.join(REFS, 'API_SLIM.txt')


def main():
    lines_out = []
    for f in ('faction', 'npc', 'identity', 'quest'):
        p = os.path.join(REFS, f + '.javap.txt')
        if not os.path.isfile(p):
            continue
        lines_out.append('=' * 70)
        lines_out.append('## ' + f)
        for line in open(p, encoding='utf-8').read().splitlines():
            if line.startswith('### '):
                lines_out.append('')
                lines_out.append(line)
                continue
            s = line.strip()
            if 'lambda$' in s or 'synthetic' in s:
                continue
            if s.startswith(('public', 'protected', 'static', 'final')):
                lines_out.append('   ' + s)
            elif s.startswith('private final'):
                lines_out.append('   ' + s)
    text = '\n'.join(lines_out) + '\n'
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(text)
    print('wrote', OUT, len(text), 'chars')


if __name__ == '__main__':
    sys.exit(main())
