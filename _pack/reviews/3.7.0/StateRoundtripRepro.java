import net.guzhenren.tianyi.world.WillState;
import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.Calamity;
import net.minecraft.nbt.CompoundTag;

/** Uses real Minecraft NBT with packaged addon classes, without opening a world. */
public class StateRoundtripRepro {
    public static void main(String[] args) {
        var state = new WillState();
        String id = "00000000-0000-0000-0000-000000000001";
        state.core().ploys().open("audit:ploy", "human-tribulation", id, "audit:npc",
                                 "audit:source", "done", 100, true);
        state.core().calamities().open("audit:calamity", id, "minecraft:overworld", 0,
                                     Calamity.spec(Calamity.Tier.DI_ZAI, .3, 8, false), 100);
        var actor = new Assessment.Actor(id, Assessment.ActorKind.PLAYER, "").power(1);
        var situation = new Assessment.Situation(100);
        situation.add(actor);
        state.core().opportunities().propose(Assessment.assess(situation), id,
            Assessment.Channel.RESOURCE, actor, 100, "minecraft:overworld", 0, "audit source");
        var tag = state.save(new CompoundTag(), null);
        var loaded = WillState.load(tag, null);
        System.out.println("Ploys: " + state.core().ploys().active().size() + " -> " + loaded.core().ploys().active().size());
        System.out.println("Calamities: " + state.core().calamities().active().size() + " -> " + loaded.core().calamities().active().size());
        System.out.println("Offers: " + state.core().opportunities().active().size() + " -> " + loaded.core().opportunities().active().size());
        if (loaded.core().ploys().active().size() != 0 || loaded.core().calamities().active().size() != 0
            || loaded.core().opportunities().active().size() != 0) {
            throw new AssertionError("Documented persistence gap not reproduced");
        }
    }
}
