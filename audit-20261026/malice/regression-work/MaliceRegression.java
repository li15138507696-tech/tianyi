
import java.util.*;
public class MaliceRegression {
    private static final String TAG_BASE = "GuzhenrenMaliceBase";
    private static final String TAG_APPLIED = "GuzhenrenMaliceApplied";
    public static void onInitPost(HostilityInitEvent.Post event) {
        if (event.getPhase() != HostilityInitEvent.InitPhase.INIT) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!MaliceTags.isTiered(entity.getType())) {
            return;
        }
        // 玩家自己驯服的妖兽不跟着难度膨胀（本体妖兽大多是 TamableAnimal，
        // 恶意的 allowTraitOnOwnable=false 只挡词条，等级还得我们自己挡住）。
        // 主人离线或在别的维度时 getOwner() 会返回 null，归属以 UUID 为准。
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        MobTraitCap cap = event.getData();
        int lv = cap.getLevel();
        if (lv <= 0) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        int base = lv; // INIT（包括 reinit）每次都提供本轮的新原始等级。

        double mul = DifficultyState.get(server).current().levelMul();
        int target = Math.max(1, (int) Math.round(base * mul));
        if (target != lv) {
            cap.setLevel(entity, target);
            // setLevel 只改 MAX_HEALTH 修饰符，当前血量还停在旧上限。
            // 恶意自己在 TraitManager.fill 末尾会 setHealth(getMaxHealth())，
            // 我们改完等级要补一次同样的满血，否则刷出来的怪只有半管血。
            entity.setHealth(entity.getMaxHealth());
        }
        data.putInt(TAG_BASE, base);
        data.putInt(TAG_APPLIED, cap.getLevel());
    }

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
