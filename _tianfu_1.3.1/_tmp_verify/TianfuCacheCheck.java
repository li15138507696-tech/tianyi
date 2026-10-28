import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.guzhenren.tianfu.net.TianfuV2StatePayload;
import net.guzhenren.tianfu.v2.TianfuSelection;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Exercises the production packet handler against the actual NeoForge interface. */
public final class TianfuCacheCheck {
    private static int checks;
    private static final List<Runnable> queued = new ArrayList<>();
    private static final IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
            IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
            (proxy, method, args) -> {
                if (method.getName().equals("enqueueWork") && args[0] instanceof Runnable task) {
                    queued.add(task);
                    return CompletableFuture.completedFuture(null);
                }
                throw new AssertionError("Unexpected context access: " + method.getName());
            });

    private static TianfuSelection.Snapshot state(long revision, int points) {
        return new TianfuSelection.Snapshot(2, revision, points, -1, false,
                new TianfuSelection.Origin("", ""), null, List.of(), Map.of(), 0, Set.of());
    }
    private static void enqueue(TianfuSelection.Snapshot state) {
        TianfuV2StatePayload.handle(new TianfuV2StatePayload(state), context);
    }
    private static void drain() {
        while (!queued.isEmpty()) queued.removeFirst().run();
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        TianfuV2StatePayload.clear();
        check(TianfuV2StatePayload.latest() == null, "clear starts empty");
        var oldWorld = state(42, 14);
        enqueue(oldWorld);
        check(TianfuV2StatePayload.latest() == null, "handler waits for client work queue");
        drain();
        check(TianfuV2StatePayload.latest() == oldWorld, "first authority snapshot accepted");
        enqueue(state(41, 999)); enqueue(state(42, 888)); drain();
        check(TianfuV2StatePayload.latest() == oldWorld, "stale and equal revisions rejected in one lifecycle");
        var newer = state(43, 15); enqueue(newer); drain();
        check(TianfuV2StatePayload.latest() == newer, "strictly newer revision accepted");

        enqueue(state(500, 500));
        TianfuV2StatePayload.clear();
        check(TianfuV2StatePayload.latest() == null, "logout or clone clears cached authority");
        var newWorld = state(0, 0); enqueue(newWorld); drain();
        check(TianfuV2StatePayload.latest() == newWorld, "new save revision zero accepted");
        enqueue(null); drain();
        check(TianfuV2StatePayload.latest() == newWorld, "null authority ignored");

        enqueue(state(900, 900));
        TianfuV2StatePayload.clear();
        drain();
        check(TianfuV2StatePayload.latest() == null, "already queued old-lifecycle packet cannot repopulate cache");
        TianfuV2StatePayload.clear(); TianfuV2StatePayload.clear();
        var resetLife = state(0, 2); enqueue(resetLife); drain();
        check(TianfuV2StatePayload.latest() == resetLife, "repeated lifecycle resets still accept new authority");
        TianfuV2StatePayload.clear();
        System.out.println("TianfuCacheCheck: " + checks + " production packet queue checks passed (no game started)");
    }
}
