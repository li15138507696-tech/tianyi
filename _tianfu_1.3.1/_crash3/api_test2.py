"""Test whether new SimpleParticleType(false) compiles from a non-MC package."""
import glob, json, os, subprocess

MC_DIR = r'D:/game/mcmod/1/.minecraft'
LIBS = os.path.join(MC_DIR, 'libraries')
JAVAC = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\apitest2'
os.makedirs(os.path.join(OUT, 'net/guzhenren/tianfu'), exist_ok=True)

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
core = [os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-client.jar'),
        os.path.join(LIBS, f'net/minecraft/client/{mc}-{nfs}/client-{mc}-{nfs}-srg.jar'),
        os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar')]
cp = list(core)
for lib in best.get('libraries', []):
    p = lib.get('downloads', {}).get('artifact', {}).get('path')
    if p and os.path.isfile(os.path.join(LIBS, p)):
        cp.append(os.path.join(LIBS, p))

SRC = r'''
package net.guzhenren.tianfu;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class P {
    public static final DeferredRegister<ParticleType<?>> R =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "tianfu");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> A =
            R.register("a", () -> new SimpleParticleType(false));
}
'''
src = os.path.join(OUT, 'net/guzhenren/tianfu/P.java')
open(src, 'w', encoding='utf-8').write(SRC)
r = subprocess.run([JAVAC, '-encoding', 'UTF-8', '-proc:none', '-d', OUT,
                    '-cp', ';'.join(cp), src],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('direct new: exit', r.returncode)
print((r.stdout or '') + (r.stderr or ''))

# test reflection-based construction
SRC2 = r'''
package net.guzhenren.tianfu;
import java.lang.reflect.Constructor;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class Q {
    private static final Constructor<SimpleParticleType> CTOR;
    static {
        try {
            CTOR = SimpleParticleType.class.getDeclaredConstructor(boolean.class);
            CTOR.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    public static final DeferredRegister<ParticleType<?>> R =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "tianfu");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> A =
            R.register("a", () -> { try { return CTOR.newInstance(false); } catch (Exception e) { throw new RuntimeException(e); } });
}
'''
src2 = os.path.join(OUT, 'net/guzhenren/tianfu/Q.java')
open(src2, 'w', encoding='utf-8').write(SRC2)
r2 = subprocess.run([JAVAC, '-encoding', 'UTF-8', '-proc:none', '-d', OUT,
                     '-cp', ';'.join(cp), src2],
                    capture_output=True, text=True, encoding='utf-8', errors='replace')
print('reflection: exit', r2.returncode)
print((r2.stdout or '') + (r2.stderr or ''))
