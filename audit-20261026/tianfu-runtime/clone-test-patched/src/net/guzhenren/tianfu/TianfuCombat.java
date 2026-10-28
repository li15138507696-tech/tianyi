package net.guzhenren.tianfu;
import java.util.Map;import java.util.Set;import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.Entity;
public class TianfuCombat {
    private static final Map<java.util.UUID, Integer> COUNTER_TICK = new ConcurrentHashMap<>();
    private static final String TAG_UNDYING_READY = "tianfu_malice_undying_ready";
    private static final Map<java.util.UUID, Map<String, Integer>> ADAPT_STACK = new ConcurrentHashMap<>();
private static double counterMultiplier(ServerPlayer player, Set<String> ids) {
        double bonus = TianfuTalents.counterBonus(ids);
        if (bonus <= 0.0) {
            return 1.0;
        }
        Integer last = COUNTER_TICK.get(player.getUUID());
        if (last == null) {
            return 1.0;
        }
        int window = TianfuTalents.counterTicks(ids);
        return player.tickCount - last <= window ? 1.0 + bonus : 1.0;
    }
public static void forget(java.util.UUID id) {
        COUNTER_TICK.remove(id);
        ADAPT_STACK.remove(id);
    }
public static void copyCooldownOnClone(ServerPlayer player, Entity original) {
        if (!TianfuData.isConfirmed(player)) {
            return;
        }
        var oldData = original.getPersistentData();
        if (oldData.contains(TAG_UNDYING_READY, net.minecraft.nbt.Tag.TAG_LONG)) {
            player.getPersistentData().putLong(TAG_UNDYING_READY, oldData.getLong(TAG_UNDYING_READY));
        }
    }
 public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e){}
 public static void seed(ServerPlayer p,int tick){COUNTER_TICK.put(p.getUUID(),tick);}
 public static double probeCounter(ServerPlayer p){return counterMultiplier(p,Set.of("counter"));}
}