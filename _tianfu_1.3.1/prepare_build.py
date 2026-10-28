from pathlib import Path

root = Path(__file__).resolve().parent
old = r'D:\daima\mcmod\蛊真人附属'
for p in (root / '_crash3').glob('*.py'):
    text = p.read_text(encoding='utf-8-sig')
    # Only the working-copy build tooling is redirected. Native dependency paths remain read-only.
    if p.name == 'javac_check.py':
        text = text.replace("SRC = r'" + old, "SRC = r'" + str(root))
        text = text.replace("OUT = r'" + old, "OUT = r'" + str(root))
    else:
        text = text.replace("ROOT = r'" + old + "'", "ROOT = r'" + str(root) + "'")
    p.write_text(text, encoding='utf-8')
print('Working-copy build tools prepared:', root)
