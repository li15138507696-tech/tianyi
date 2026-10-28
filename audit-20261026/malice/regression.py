"""Compile and exercise the actual onInitPost method with minimal API fixtures.

The fixtures model the verified L2Hostility 3.0.18 event sequence. They do not
launch Minecraft and cannot verify world rendering or another mod's handlers.
Only this audit directory is written. Supply --source to test a patched file.
"""
from pathlib import Path
import argparse
import subprocess
import sys

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=Path(r'D:\daima\mcmod\蛊真人恶意\src\main\java\net\guzhenren\malice\MaliceEvents.java'))
args = parser.parse_args()
source = args.source.read_text(encoding='utf-8')
start = source.index('public static void onInitPost(')
opening = source.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end]
work = HERE / 'regression-work'
work.mkdir(exist_ok=True)
java = r'''
import java.util.*;
public class MaliceRegression {
    private static final String TAG_BASE = "GuzhenrenMaliceBase";
    private static final String TAG_APPLIED = "GuzhenrenMaliceApplied";
    __REAL_METHOD__

    static class Type { boolean tiered = true; }
    static class Level { boolean client; boolean isClientSide() { return client; } }
    static class MinecraftServer { double multiplier = 1.5; }
    static class CompoundTag {
        final Map<String, Integer> ints = new HashMap<>();
        int getInt(String key) { return ints.getOrDefault(key, 0); }
        void putInt(String key, int value) { ints.put(key, value); }
        boolean contains(String key) { return ints.containsKey(key); }
    }
    static class LivingEntity {
        final Level level = new Level();
        final Type type = new Type();
        final CompoundTag data = new CompoundTag();
        MinecraftServer server = new MinecraftServer();
        Level level() { return level; }
        Type getType() { return type; }
        MinecraftServer getServer() { return server; }
        CompoundTag getPersistentData() { return data; }
        float getMaxHealth() { return 100; }
        void setHealth(float health) { }
    }
    static class Player extends LivingEntity { }
    interface OwnableEntity { LivingEntity getOwner(); UUID getOwnerUUID(); }
    static class Pet extends LivingEntity implements OwnableEntity {
        LivingEntity owner;
        final UUID ownerUUID = UUID.randomUUID();
        public LivingEntity getOwner() { return owner; }
        public UUID getOwnerUUID() { return ownerUUID; }
    }
    static class MobTraitCap {
        int level;
        MobTraitCap(int level) { this.level = level; }
        int getLevel() { return level; }
        void setLevel(LivingEntity entity, int level) { this.level = level; }
    }
    static class MaliceTags { static boolean isTiered(Type type) { return type.tiered; } }
    static class DifficultyState {
        MinecraftServer server;
        static DifficultyState get(MinecraftServer server) {
            var state = new DifficultyState(); state.server = server; return state;
        }
        DifficultyState current() { return this; }
        double levelMul() { return server.multiplier; }
    }
    static class HostilityInitEvent {
        enum InitPhase { INIT, COPY, ARMOR, WEAPON }
        static class Post {
            LivingEntity entity; MobTraitCap cap; InitPhase phase;
            Post(LivingEntity e, MobTraitCap c, InitPhase p) { entity=e; cap=c; phase=p; }
            LivingEntity getEntity() { return entity; }
            MobTraitCap getData() { return cap; }
            InitPhase getPhase() { return phase; }
        }
    }
    static int failures;
    static void check(String name, int actual, int expected) {
        if (actual != expected) {
            failures++;
            System.out.println("FAIL " + name + ": expected=" + expected + " actual=" + actual);
        } else System.out.println("PASS " + name);
    }
    static void post(LivingEntity e, MobTraitCap c, HostilityInitEvent.InitPhase p) {
        onInitPost(new HostilityInitEvent.Post(e, c, p));
    }
    public static void main(String[] ignored) {
        var INIT = HostilityInitEvent.InitPhase.INIT;
        var COPY = HostilityInitEvent.InitPhase.COPY;
        var WEAPON = HostilityInitEvent.InitPhase.WEAPON;
        for (double multiplier : new double[]{0.6, 1.0, 1.5, 2.2}) {
            var parent = new LivingEntity(); parent.server.multiplier = multiplier;
            var cap = new MobTraitCap(20); post(parent, cap, INIT);
            int scaled = Math.max(1, (int)Math.round(20 * multiplier));
            check("INIT " + multiplier, cap.level, scaled);
            post(parent, cap, WEAPON);
            check("WEAPON preserves level " + multiplier, cap.level, scaled);
            var child = new LivingEntity(); child.server.multiplier = multiplier;
            var copyCap = new MobTraitCap(cap.level); // copyFrom copies source lv first
            post(child, copyCap, COPY); post(child, copyCap, WEAPON);
            check("COPY preserves inherited level " + multiplier, copyCap.level, scaled);
        }
        var entity = new LivingEntity();
        var cap = new MobTraitCap(30); post(entity, cap, INIT); // base30 -> applied45
        cap.level = 45; // reinit supplies a fresh raw level equal to old applied value
        post(entity, cap, INIT);
        check("reinit equal to old applied level", cap.level, 68);

        var betweenPhases = new LivingEntity(); var betweenCap = new MobTraitCap(20);
        post(betweenPhases, betweenCap, INIT);
        betweenPhases.server.multiplier = 2.2;
        post(betweenPhases, betweenCap, WEAPON);
        check("difficulty change affects new INIT only", betweenCap.level, 30);

        var offlinePet = new Pet(); var petCap = new MobTraitCap(20);
        post(offlinePet, petCap, INIT);
        check("owned pet with unloaded owner", petCap.level, 20);
        var onlinePet = new Pet(); onlinePet.owner = new Player(); var onlineCap = new MobTraitCap(20);
        post(onlinePet, onlineCap, INIT);
        check("owned pet with loaded owner", onlineCap.level, 20);

        var zero = new MobTraitCap(0); post(new LivingEntity(), zero, INIT);
        check("cancelled/zero INIT remains zero", zero.level, 0);
        var untiered = new LivingEntity(); untiered.type.tiered = false;
        var untieredCap = new MobTraitCap(20); post(untiered, untieredCap, INIT);
        check("untiered entity untouched", untieredCap.level, 20);
        var client = new LivingEntity(); client.level.client = true;
        var clientCap = new MobTraitCap(20); post(client, clientCap, INIT);
        check("client side untouched", clientCap.level, 20);
        if (failures > 0) throw new AssertionError(failures + " regression checks failed");
    }
}
'''.replace('__REAL_METHOD__', method)
java_file = work / 'MaliceRegression.java'
java_file.write_text(java, encoding='utf-8')
bin_dir = Path(r'C:\Users\ADMIN\.jdks\temurin-21\bin')
compiled = subprocess.run([str(bin_dir / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(work), str(java_file)], capture_output=True, text=True, encoding='utf-8', errors='replace')
if compiled.returncode:
    print(compiled.stdout + compiled.stderr)
    sys.exit(compiled.returncode)
result = subprocess.run([str(bin_dir / 'java.exe'), '-cp', str(work), 'MaliceRegression'], capture_output=True, text=True, encoding='utf-8', errors='replace')
print('Source:', args.source)
print(result.stdout + result.stderr, end='')
sys.exit(result.returncode)
