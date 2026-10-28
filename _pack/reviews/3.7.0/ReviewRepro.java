import java.util.Map;
import net.guzhenren.tianyi.will.AlchemyTracker;
import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.BeastHerd;
import net.guzhenren.tianyi.will.Opportunity;
import net.guzhenren.tianyi.will.WillCore;
import net.guzhenren.tianyi.will.Knowledge;
import net.guzhenren.tianyi.will.Intent;

/** Diagnostic reproductions against the supplied JAR, not a repaired implementation. */
public class ReviewRepro {
    private static int reproduced;
    private static void found(boolean condition, String message) {
        if (!condition) throw new AssertionError("Not reproduced: " + message);
        reproduced++;
        System.out.println("REPRODUCED: " + message);
    }
    public static void main(String[] args) {
        var tracker = new AlchemyTracker.Tracker();
        tracker.observe(20, "fang:1", "", 0);
        var diff = AlchemyTracker.diff(Map.of(), Map.of(
            "guzhenren:wei_lian_hua_yu_she_gu", 1,
            "guzhenren:yuan_lao_gu_1", 64));
        var top = AlchemyTracker.topProduct(diff);
        var result = tracker.observe(100, "fang:1", top.getKey(), top.getValue());
        found(result.completed() && result.productId().equals("guzhenren:yuan_lao_gu_1"),
              "unrelated pickup of 64 items beats the actual crafted output of 1");

        tracker = new AlchemyTracker.Tracker();
        tracker.observe(20, "fang:1", "", 0);
        tracker.observe(0, "", "", 0);
        top = AlchemyTracker.topProduct(AlchemyTracker.diff(null,
            Map.of("guzhenren:yuan_lao_gu_1", 64)));
        result = tracker.observe(100, "fang:999", top.getKey(), top.getValue());
        found(result.completed(), "reset-to-zero leaves a stale crafting session; missing baseline counts existing inventory");

        var herd = new BeastHerd("herd", "guzhenren:hunshou_1", "minecraft:overworld",
                                0, 10, 18, .6, .2, 0);
        herd.drive(1, 20);
        herd.harvest(1, 40); // same call sequence as driveToward -> recordKill
        found(herd.population() == 8, "one driven beast then killed debits the herd twice (10 -> 8)");

        herd = new BeastHerd("extinct", "guzhenren:hunshou_1", "minecraft:overworld",
                             0, 1, 18, .6, .2, 0);
        herd.harvest(1, 1);
        herd.grow(1000, 24_000_001);
        found(herd.population() == 0 && herd.state() == BeastHerd.State.COLLAPSED,
              "collapsed herd never recovers, even after 1000 game days");

        var s = new Assessment.Situation(100);
        var visible = new Assessment.Actor("audit:visible", Assessment.ActorKind.PLAYER, "").power(10);
        var hidden = new Assessment.Actor("audit:hidden", Assessment.ActorKind.PLAYER, "")
                         .power(1).confidence(0).hidden(true);
        s.add(visible);
        s.add(hidden);
        double before = Assessment.assess(s).shareOf("audit:visible");
        hidden.power(1000); // Observation supplies live power even when observation is blocked
        double after = Assessment.assess(s).shareOf("audit:visible");
        found(before > .9 && after < .01,
              "unobserved hidden player's live power changes visible player's balance share: " + before + " -> " + after);

        var forge = new Opportunity.Forge();
        int materialized = 0;
        for (long now = 200; now <= 48_000; now += 200) {
            // Exact propose -> mature(same now) sequence used in WorldExec.support.
            var offer = forge.propose(Assessment.assess(s), visible.id,
                Assessment.Channel.RESOURCE, visible, now, "minecraft:overworld", 0,
                "audit resource").getFirst();
            if (offer.mature(now)) materialized++;
            forge.tick(now);
        }
        found(materialized == 0 && !forge.available(48_000).isEmpty(),
              "240 support attempts produce zero immediately mature offers; mature old offers have no world consumer");

        var weak = new Assessment.Actor("audit:weak", Assessment.ActorKind.PLAYER, "")
                       .power(.01).resources(1).morality(1000).confidence(1);
        var weakSituation = new Assessment.Situation(2_400_000).with(Assessment.Channel.RESOURCE);
        weakSituation.add(weak);
        weakSituation.add(new Assessment.Actor("audit:peer", Assessment.ActorKind.PLAYER, "").power(10));
        var freshCore = new WillCore(new Knowledge.Store(), "audit:world");
        boolean freshSupports = freshCore.decide(weakSituation).stream().anyMatch(i -> i.kind == Intent.Kind.SUPPORT);
        var previouslySuppressed = new WillCore(new Knowledge.Store(), "audit:world");
        previouslySuppressed.importState(1, Map.of(weak.id, Intent.Posture.SUPPRESS), Map.of(), Map.of(), 0);
        boolean switchedSupports = previouslySuppressed.decide(weakSituation).stream().anyMatch(i -> i.kind == Intent.Kind.SUPPORT);
        found(freshSupports && !switchedSupports,
              "previous SUPPRESS posture blocks support for a now-weak useful pawn; decide never calls switchPosture");

        var strong = new Assessment.Actor("audit:strong", Assessment.ActorKind.PLAYER, "")
                         .power(10).knownDefiance(5).confidence(1);
        var strongSituation = new Assessment.Situation(2_400_000).with(Assessment.Channel.BEAST);
        strongSituation.add(strong);
        var cappedCore = new WillCore(new Knowledge.Store(), "audit:world");
        cappedCore.importState(1, Map.of(), Map.of("suppress:" + strong.id, 2), Map.of(), 0);
        boolean cappedSuppresses = cappedCore.decide(strongSituation).stream().anyMatch(i -> i.kind == Intent.Kind.SUPPRESS);
        freshCore = new WillCore(new Knowledge.Store(), "audit:world");
        boolean freshSuppresses = freshCore.decide(strongSituation).stream().anyMatch(i -> i.kind == Intent.Kind.SUPPRESS);
        found(freshSuppresses && !cappedSuppresses,
              "persisted suppress count of two blocks all future suppression, even 100 game days later");
        System.out.println("reproduced=" + reproduced + "; all assertions describe current bugs, not fixes");
    }
}
