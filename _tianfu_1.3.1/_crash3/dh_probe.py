"""Compile a probe that prints what get() returns for DeferredHolder<ParticleType<?>, SimpleParticleType>."""
import glob, json, os, subprocess

MC_DIR = r'D:/game/mcmod/1/.minecraft'
LIBS = os.path.join(MC_DIR, 'libraries')
JAVAC = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\dhprobe'
os.makedirs(os.path.join(OUT, 'p'), exist_ok=True)

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
cp = [os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-client.jar'),
      os.path.join(LIBS, f'net/minecraft/client/{mc}-{nfs}/client-{mc}-{nfs}-srg.jar'),
      os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar')]
for lib in best.get('libraries', []):
    p = lib.get('downloads', {}).get('artifact', {}).get('path')
    if p and os.path.isfile(os.path.join(LIBS, p)):
        cp.append(os.path.join(LIBS, p))

SRC = r'''
package p;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public class Probe {
    // what guzhenren does: raw DeferredHolder<ParticleType<?>, SimpleParticleType>, cast to ParticleType
    static void raw(RegisterParticleProvidersEvent e,
            DeferredHolder<ParticleType<?>, SimpleParticleType> h,
            ParticleEngine.SpriteParticleRegistration<SimpleParticleType> f) {
        e.registerSpriteSet((ParticleType<SimpleParticleType>) h.get(), f);
    }

    // typed helper
    static <T extends ParticleOptions> void typed(RegisterParticleProvidersEvent e,
            DeferredHolder<ParticleType<?>, T> h,
            ParticleEngine.SpriteParticleRegistration<T> f) {
        e.registerSpriteSet((ParticleType<T>) h.get(), f);
    }
}
'''
src = os.path.join(OUT, 'p/Probe.java')
open(src, 'w', encoding='utf-8').write(SRC)
r = subprocess.run([JAVAC, '-encoding', 'UTF-8', '-proc:none', '-d', OUT,
                    '-cp', ';'.join(cp), src],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('exit', r.returncode)
print((r.stdout or '') + (r.stderr or ''))
