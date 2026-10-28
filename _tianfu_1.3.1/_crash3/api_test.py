"""Minimal compile test: can we construct SimpleParticleType in 1.21.1?"""
import glob, json, os, subprocess, sys

MC_DIR = r'D:/game/mcmod/1/.minecraft'
LIBS = os.path.join(MC_DIR, 'libraries')
JAVAC = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\apitest'
os.makedirs(OUT, exist_ok=True)

best, best_count = None, -1
for ver_dir in sorted(glob.glob(os.path.join(MC_DIR, 'versions', '*'))):
    for f in glob.glob(os.path.join(ver_dir, '*.json')):
        try:
            p = json.load(open(f, encoding='utf-8'))
        except Exception:
            continue
        if not isinstance(p, dict):
            continue
        strs = [a for a in p.get('arguments', {}).get('game', []) if isinstance(a, str)]
        if '--fml.neoForgeVersion' not in strs:
            continue
        if len(p.get('libraries', [])) > best_count:
            best, best_count = p, len(p['libraries'])

strs = [a for a in best['arguments']['game'] if isinstance(a, str)]
arg = lambda k: strs[strs.index(k) + 1]
neo, mc, nfs = arg('--fml.neoForgeVersion'), arg('--fml.mcVersion'), arg('--fml.neoFormVersion')
core = [
    os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-client.jar'),
    os.path.join(LIBS, f'net/minecraft/client/{mc}-{nfs}/client-{mc}-{nfs}-srg.jar'),
    os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar'),
]
cp = list(core)
for lib in best.get('libraries', []):
    p = lib.get('downloads', {}).get('artifact', {}).get('path')
    if p and os.path.isfile(os.path.join(LIBS, p)):
        cp.append(os.path.join(LIBS, p))

TEST = r'''
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ApiTest {
    static final DeferredRegister<ParticleType<?>> R =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "tianfu");
    public static void main(String[] a) {
        System.out.println("compiled ok");
    }
}
'''
src = os.path.join(OUT, 'ApiTest.java')
open(src, 'w', encoding='utf-8').write(TEST)
r = subprocess.run([JAVAC, '-encoding', 'UTF-8', '-proc:none', '-d', OUT,
                    '-cp', ';'.join(cp), src],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('exit', r.returncode)
print((r.stdout or '') + (r.stderr or ''))

# Now test: is there any public static factory on SimpleParticleType?
TEST2 = r'''
import net.minecraft.core.particles.SimpleParticleType;
public class ApiTest2 {
    public static void main(String[] a) throws Exception {
        for (var m : SimpleParticleType.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                System.out.println("STATIC: " + m);
            }
        }
        for (var c : SimpleParticleType.class.getDeclaredConstructors()) {
            System.out.println("CTOR: " + c + " public=" + java.lang.reflect.Modifier.isPublic(c.getModifiers()));
        }
        var f = SimpleParticleType.class.getDeclaredField("overrideLimiter");
        System.out.println("field overrideLimiter: " + f);
    }
}
'''
src2 = os.path.join(OUT, 'ApiTest2.java')
open(src2, 'w', encoding='utf-8').write(TEST2)
r2 = subprocess.run([JAVAC, '-encoding', 'UTF-8', '-proc:none', '-d', OUT,
                     '-cp', ';'.join(cp), src2],
                    capture_output=True, text=True, encoding='utf-8', errors='replace')
print('test2 exit', r2.returncode, (r2.stdout or '') + (r2.stderr or ''))
