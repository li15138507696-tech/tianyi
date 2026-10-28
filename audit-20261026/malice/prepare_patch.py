"""Stage the minimal fix and compile it; never modify the original project."""
from pathlib import Path
import difflib
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = Path(r'D:\daima\mcmod\蛊真人恶意')
original_src = ROOT / 'src/main/java'
staged_src = HERE / 'staged-src'
shutil.copytree(original_src, staged_src, dirs_exist_ok=True)
relative = Path('net/guzhenren/malice/MaliceEvents.java')
original_raw = (original_src / relative).read_bytes().decode('utf-8')
original = original_raw.replace('\r\n', '\n')
updated = original
start = updated.index('/**')
end = updated.index('public final class MaliceEvents', start)
updated = updated[:start] + '''/**
 * 恶意初始化钩子。等级倍率只处理 INIT 阶段：该阶段提供新计算的原始等级；
 * COPY 已继承源实体的最终等级，ARMOR / WEAPON 是同一轮初始化的装备阶段。
 * 依据事件阶段判断边界，避免复制叠倍率和 reinit 数值恰好相等时被误判为重复。
 */
''' + updated[end:]
updated = updated.replace('import net.minecraft.world.entity.player.Player;\n', '')
updated = updated.replace('    /** 我们最后一次写进 cap 的等级，用来识别「重复回调」。 */',
                          '    /** 最近一次 INIT 结算后的等级，供诊断命令查看。 */')
signature = '    public static void onInitPost(HostilityInitEvent.Post event) {\n'
assert signature in updated
updated = updated.replace(signature, signature + '''        if (event.getPhase() != HostilityInitEvent.InitPhase.INIT) {
            return;
        }
''', 1)
old_pet = 'if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player)'
assert old_pet in updated
updated = updated.replace(old_pet, 'if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)', 1)
updated = updated.replace('        // 恶意的 allowTraitOnOwnable=false 只挡词条，等级还得我们自己挡住）。',
                          '        // 恶意的 allowTraitOnOwnable=false 只挡词条，等级还得我们自己挡住）。\n'
                          '        // 主人离线或在别的维度时 getOwner() 会返回 null，归属以 UUID 为准。')
old_base = '''        int applied = data.getInt(TAG_APPLIED);
        int base;
        if (applied > 0 && lv == applied && data.contains(TAG_BASE)) {
            // 同一只怪的重复回调（tick / copyFrom），沿用原始等级，结果幂等
            base = data.getInt(TAG_BASE);
        } else {
            // 新刷的怪，或被 reinit 重算过：当前等级就是新的原始等级
            base = lv;
        }
'''
assert old_base in updated
updated = updated.replace(old_base, '        int base = lv; // INIT（包括 reinit）每次都提供本轮的新原始等级。\n', 1)
updated_raw = updated.replace('\n', '\r\n') if '\r\n' in original_raw else updated
(staged_src / relative).write_bytes(updated_raw.encode('utf-8'))
patch_path = HERE / 'malice-init-and-pets.patch'
diff_path = 'src/main/java/' + relative.as_posix()
patch_path.write_bytes(''.join(difflib.unified_diff(original_raw.splitlines(True), updated_raw.splitlines(True),
                                                  fromfile='a/' + diff_path, tofile='b/' + diff_path)).encode('utf-8'))

# Reuse only the read-only classpath listing, never execute hardcoded build scripts.
args = (ROOT / '_build/javac_out/args.txt').read_text(encoding='utf-8').splitlines()
classpath = args[args.index('-cp') + 1].split(';')
# The listing predates the move from profile "1" to "蛊2"; retain installed core
# libraries and select the exact currently installed mod dependencies explicitly.
classpath = [item for item in classpath if Path(item).is_file()]
mods = Path(r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事\mods')
classpath += [str(mods / '[莱特兰-恶意] l2hostility-3.0.18.jar'),
              str(mods / 'l2library-3.0.9.jar'),
              str(ROOT / '_build/libs_nested/l2core-3.0.9+1.jar')]
missing = [item for item in classpath if not Path(item).is_file()]
if missing:
    sys.exit('Classpath missing: ' + repr(missing))
out = HERE / 'compiled-classes'
out.mkdir(exist_ok=True)
sources = sorted(staged_src.rglob('*.java'))
cmd = [r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe', '-encoding', 'UTF-8', '-proc:none',
       '-d', str(out), '-cp', ';'.join(classpath)] + list(map(str, sources))
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
log = f'Compiled {len(sources)} staged Java sources; exit={result.returncode}\n' + result.stdout + result.stderr
(HERE / 'compile-result.txt').write_text(log, encoding='utf-8')
print(log, end='')
print('Patch:', patch_path)
sys.exit(result.returncode)
