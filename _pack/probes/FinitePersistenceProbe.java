import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.guzhenren.tianyi.resource.*;
import net.guzhenren.tianyi.world.FiniteSiteState;
import net.minecraft.nbt.*;

/** Real production NBT classes, no fake Minecraft stubs. */
public final class FinitePersistenceProbe {
    static int checks;
    static void check(boolean b, String message) { checks++; if(!b) throw new AssertionError(message); }
    static void reject(Runnable r) { try { r.run(); } catch(IllegalArgumentException | IllegalStateException e) { checks++; return; } throw new AssertionError("accepted invalid operation"); }
    static FiniteSite site(FiniteSite.Kind kind) {
        return new FiniteSite(UUID.randomUUID(),"minecraft:overworld",1,64,1,"tianyi:test",1,kind,FiniteSite.Phase.RESERVED,0,"minecraft:overworld/0/0",SiteInventory.reserved(4,kind==FiniteSite.Kind.PLANT?4:0),
            List.of(new FiniteSite.Slot(0,1,64,1,"minecraft:air","minecraft:barrel",false)),List.of(),0,true);
    }
    /** Same shape with explicit coordinates/region so geometry and multi-site rules can be probed. */
    static FiniteSite siteAt(FiniteSite.Kind kind, int x, int y, int z, String region) {
        return new FiniteSite(UUID.randomUUID(),"minecraft:overworld",x,y,z,"tianyi:test",1,kind,FiniteSite.Phase.RESERVED,0,region,SiteInventory.reserved(4,kind==FiniteSite.Kind.PLANT?4:0),
            List.of(new FiniteSite.Slot(0,x,y,z,"minecraft:air","minecraft:barrel",false)),List.of(),0,true);
    }
    static CompoundTag save(FiniteSiteState s) { return s.save(new CompoundTag(),null); }
    static void isolated(CompoundTag raw) {
        CompoundTag original=raw.copy(); var state=FiniteSiteState.load(raw,null);
        check(!state.mutationReady(),"bad NBT must stop mutations");
        raw.putInt("externalMutation",1);
        check(save(state).equals(original),"raw envelope must survive unchanged and detached");
        CompoundTag returned=save(state); returned.putInt("externalMutation",2);
        check(save(state).equals(original),"save must not leak preserved object");
        reject(() -> state.reserve(site(FiniteSite.Kind.ORE)));
    }
    /** H1: an unresolved write freezes only its own site; the rest of the ledger keeps working. */
    static void pendingFreezesOnlyItsSite(FiniteSiteState s, UUID siteId) {
        var reloaded=FiniteSiteState.load(save(s),null);
        check(reloaded.mutationReady(),"pending transaction must not freeze the whole ledger");
        var frozen=reloaded.sites().get(siteId);
        check(frozen!=null && frozen.phase()==FiniteSite.Phase.QUARANTINED,"pending transaction quarantines only its site");
        check(reloaded.sites().size()==s.sites().size(),"pending transaction drops no site");
        check(reloaded.regions().size()==s.regions().size(),"pending transaction drops no region marker");
    }
    /** Static source guard for behaviour that needs a live ServerLevel (cannot be executed offline). */
    static void sourceGuard(String relative, String... needles) {
        Path file=Path.of(relative);
        if(!Files.isRegularFile(file)) throw new AssertionError("run from the repository root; missing "+relative);
        String text;
        try { text=Files.readString(file); } catch(IOException failure) { throw new AssertionError(failure); }
        for(String needle:needles) check(text.contains(needle),"source guard missing in "+relative+": "+needle);
    }
    public static void main(String[] args) {
        var p=site(FiniteSite.Kind.PLANT); var original=p;
        p=p.beginMaterialization(); check(original.phase()==FiniteSite.Phase.RESERVED,"immutable phase");
        p=p.materializeSlot(0,4); check(p.materializeSlot(0,4)==p,"slot retry no-op");
        check(p.inventory().physicalRemaining()==4,"physical accounting");
        check(p.growLoaded(72000,false,true)==p,"unloaded growth paused");
        check(p.growLoaded(72000,true,false)==p,"invalid environment paused");
        for(int i=0;i<4;i++) { p=p.observeRemaining(0,false); p=p.growLoaded(72000,true,true); check(p.inventory().unmaterialized()==1,"growth pending only"); p=p.materializeGrowth(); }
        p=p.observeRemaining(0,false); check(p.phase()==FiniteSite.Phase.DEPLETED,"lifetime exhausted");
        check(p.growLoaded(72000,true,true)==p,"no post-exhaustion growth");
        var ore=site(FiniteSite.Kind.ORE).beginMaterialization().materializeSlot(0,4);
        check(ore.growLoaded(72000,true,true)==ore,"ore never grows");
        var dead=ore.destroyCarrier(); check(dead.inventory().destroyed()==4,"carrier loss consumes");
        check(dead.destroyCarrier()==dead,"destruction idempotent"); reject(() -> dead.observeRemaining(1,false));
        var guard=site(FiniteSite.Kind.GUARDED).beginMaterialization(); UUID guardian=UUID.randomUUID(); guard=guard.bindGuardian(guardian);
        check(guard.bindGuardian(guardian)==guard && guard.matchesGuardianDeath(guardian),"stable guardian binding");
        check(!guard.matchesGuardianDeath(UUID.randomUUID()),"unrelated death ignored");
        var inv=SiteInventory.reserved(Integer.MAX_VALUE,1); reject(() -> inv.growVerified(1));
        var state=new FiniteSiteState(); var reserved=site(FiniteSite.Kind.ORE); check(state.reserve(reserved),"reserve"); check(state.reserve(reserved),"repeat reservation");
        check(!state.reserve(site(FiniteSite.Kind.ORE)),"region marker prevents duplicate");
        var mat=reserved.beginMaterialization(); check(state.replace(reserved,mat),"CAS phase"); check(!state.replace(reserved,mat),"stale CAS rejected");
        var tx=new FiniteSite.Transaction(UUID.randomUUID(),mat.siteId(),0,mat.revision(),FiniteSite.TxPhase.PREPARED);
        check(state.prepare(tx) && state.prepare(tx),"prepare retry");
        pendingFreezesOnlyItsSite(state, mat.siteId());
        check(!state.prepare(new FiniteSite.Transaction(UUID.randomUUID(),mat.siteId(),0,mat.revision(),FiniteSite.TxPhase.PREPARED)),"one writer per slot");
        reject(() -> state.advance(tx.txnId(),FiniteSite.TxPhase.PREPARED,FiniteSite.TxPhase.COMMITTED));
        check(state.advance(tx.txnId(),FiniteSite.TxPhase.PREPARED,FiniteSite.TxPhase.APPLIED),"apply");
        pendingFreezesOnlyItsSite(state, mat.siteId());
        var active=mat.materializeSlot(0,4); check(state.replace(mat,active),"record materialization");
        check(state.advance(tx.txnId(),FiniteSite.TxPhase.APPLIED,FiniteSite.TxPhase.COMMITTED),"commit");
        CompoundTag valid=save(state); var restored=FiniteSiteState.load(valid,null);
        check(restored.mutationReady() && restored.sites().get(active.siteId()).equals(active),"full NBT round trip");
        check(save(restored).equals(valid),"all envelope fields round trip");
        var end=active.observeRemaining(0,false); check(state.replace(active,end),"deplete"); check(state.retire(end.siteId()),"retire"); check(state.retire(end.siteId()),"retire retry");
        check(!state.reserve(site(FiniteSite.Kind.ORE)),"tombstone retains region"); check(FiniteSiteState.load(save(state),null).mutationReady(),"tombstone transaction roundtrip");

        // ---- M6: a candidate inside 32 blocks of a live site must be refused --------------------
        var ledger=new FiniteSiteState();
        check(ledger.reserve(siteAt(FiniteSite.Kind.ORE,1000,64,1000,"m6-a")),"M6: far site reserved");
        check(!ledger.reserve(siteAt(FiniteSite.Kind.ORE,1020,64,1000,"m6-b")),"M6: site 20 blocks away rejected");
        check(ledger.reserve(siteAt(FiniteSite.Kind.ORE,1000,64,1032,"m6-c")),"M6: site 32 blocks away accepted");

        // ---- H1: a pending write freezes only its own site, and stays recoverable ----------------
        var untouched=siteAt(FiniteSite.Kind.ORE,4000,64,4000,"h1-other");
        var target=siteAt(FiniteSite.Kind.ORE,2000,64,2000,"h1-target");
        check(ledger.reserve(untouched) && ledger.reserve(target),"H1: two independent sites");
        var live=target.beginMaterialization();
        check(ledger.replace(target,live),"H1: materializing");
        var pending=new FiniteSite.Transaction(UUID.randomUUID(),live.siteId(),0,live.revision(),FiniteSite.TxPhase.PREPARED);
        check(ledger.prepare(pending),"H1: prepared transaction");
        var reloaded=FiniteSiteState.load(save(ledger),null);
        check(reloaded.mutationReady(),"H1: pending write must not freeze the whole ledger");
        check(reloaded.sites().size()==ledger.sites().size(),"H1: pending write drops no site");
        check(reloaded.regions().size()==ledger.regions().size(),"H1: pending write drops no region marker");
        check(reloaded.tombstones().size()==ledger.tombstones().size(),"H1: pending write drops no tombstone");
        check(reloaded.sites().get(untouched.siteId()).equals(untouched),"H1: the untouched site keeps working");
        check(reloaded.sites().get(target.siteId()).phase()==FiniteSite.Phase.QUARANTINED,"H1: only the affected site is frozen");
        check(reloaded.transactions().containsKey(pending.txnId()),"H1: the unresolved row is kept, never replayed");
        check(!reloaded.releaseQuarantine(target.siteId()),"H1: release refused while the row is unresolved");
        check(!reloaded.clearTransaction(UUID.randomUUID()),"H1: unknown row refused");
        check(reloaded.clearTransaction(pending.txnId()),"H1: operator clears the reconciled row");
        check(reloaded.releaseQuarantine(target.siteId()),"H1: operator releases the single site");
        check(reloaded.sites().get(target.siteId()).phase()!=FiniteSite.Phase.QUARANTINED,"H1: released site leaves quarantine");
        check(reloaded.sites().get(target.siteId()).inventory().equals(live.inventory()),"H1: release never refills");
        check(!reloaded.releaseQuarantine(target.siteId()),"H1: release is not repeatable");
        check(reloaded.mutationReady() && reloaded.sites().size()==ledger.sites().size(),"H1: ledger usable after recovery");

        // ---- M1: a slot that can never be placed must not hold a MAX_SITES place forever --------
        var stuck=siteAt(FiniteSite.Kind.ORE,8000,64,8000,"m1-stuck");
        check(ledger.reserve(stuck),"M1: reserve stuck site");
        var stuckMat=stuck.beginMaterialization();
        check(ledger.replace(stuck,stuckMat),"M1: materializing");
        var abandoned=stuckMat.abandon();
        check(abandoned.phase()==FiniteSite.Phase.DESTROYED,"M1: abandon ends DESTROYED");
        check(abandoned.inventory().destroyed()==4 && abandoned.inventory().physicalRemaining()==0
                && abandoned.inventory().unmaterialized()==0,"M1: abandon books the remainder as destroyed");
        check(abandoned.revision()==stuckMat.revision()+1,"M1: abandon advances revision");
        check(ledger.replace(stuckMat,abandoned),"M1: abandon is recorded");
        check(ledger.retire(stuck.siteId()),"M1: abandoned site retires");
        check(!ledger.sites().containsKey(stuck.siteId()),"M1: abandoned site no longer occupies a slot");
        var stuck2=siteAt(FiniteSite.Kind.ORE,9000,64,9000,"m1-stuck2");
        check(ledger.reserve(stuck2) && ledger.replace(stuck2,stuck2.beginMaterialization()),"M1: second stuck site");
        check(ledger.retire(stuck2.siteId()),"M1: stuck MATERIALIZING with no physical units retires");
        var neverPlaced=siteAt(FiniteSite.Kind.ORE,9500,64,9500,"m1-never");
        check(ledger.reserve(neverPlaced),"M1: reserve never-placed site");
        check(ledger.replace(neverPlaced,neverPlaced.abandon()),"M1: RESERVED site can be abandoned");

        // ---- M3: terminal sites are never observed again ---------------------------------------
        var terminalSource=siteAt(FiniteSite.Kind.ORE,12000,64,12000,"m3-frozen");
        check(ledger.reserve(terminalSource),"M3: reserve");
        var terminalMat=terminalSource.beginMaterialization();
        check(ledger.replace(terminalSource,terminalMat),"M3: materializing");
        var placed=terminalMat.materializeSlot(0,4);
        check(ledger.replace(terminalMat,placed),"M3: materialized");
        var frozenSite=placed.quarantine();
        check(ledger.replace(placed,frozenSite),"M3: quarantine is recorded");
        check(frozenSite.terminal() && frozenSite.inventory().physicalRemaining()==4,"M3: terminal site still holds physical units");
        reject(() -> ledger.replace(frozenSite,frozenSite.observeRemaining(0,false)));
        reject(() -> ledger.replace(frozenSite,frozenSite.destroyCarrier()));
        sourceGuard("src/main/java/net/guzhenren/tianyi/world/SitePlacement.java",
                "if (site.terminal()) return new Observation(false,false,false,-1);");
        sourceGuard("src/main/java/net/guzhenren/tianyi/world/SiteProtection.java",
                "public static boolean retryable(Decision decision)");

        CompoundTag bad=valid.copy(); bad.putInt("schemaVersion",2); isolated(bad);
        bad=valid.copy(); bad.putString("schemaVersion","1"); isolated(bad);
        bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).add(bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).copy()); isolated(bad);
        bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).putLong("x",1); isolated(bad);
        bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).putByte("rootIntact",(byte)2); isolated(bad);
        bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getCompound("inventory").putString("initial","4"); isolated(bad);
        bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).putString("phase","FUTURE"); isolated(bad);
        bad=valid.copy(); bad.getList("regionMarkers",Tag.TAG_COMPOUND).add(bad.getList("regionMarkers",Tag.TAG_COMPOUND).getCompound(0).copy()); isolated(bad);
        bad=valid.copy(); bad.getList("transactions",Tag.TAG_COMPOUND).add(bad.getList("transactions",Tag.TAG_COMPOUND).getCompound(0).copy()); isolated(bad);
        bad=valid.copy(); bad.put("regionMarkers",new ListTag()); isolated(bad);
        bad=valid.copy(); bad.putInt("futureField",1); isolated(bad);
        bad=valid.copy(); ListTag strings=new ListTag(); strings.add(StringTag.valueOf("wrong row type")); bad.put("sites",strings); isolated(bad);
        bad=valid.copy(); ListTag many=new ListTag(); for(int i=0;i<=FiniteSiteState.MAX_SITES;i++) many.add(valid.getList("sites",Tag.TAG_COMPOUND).getCompound(0).copy()); bad.put("sites",many); isolated(bad);
        for(String name:List.of("regionMarkers","tombstones","transactions","quarantine")) {
            bad=valid.copy(); ListTag overflow=new ListTag();
            int limit=name.equals("transactions")?FiniteSiteState.MAX_TRANSACTIONS:FiniteSiteState.MAX_REGIONS;
            for(int i=0;i<=limit;i++) overflow.add(new CompoundTag()); bad.put(name,overflow); isolated(bad);
        }
        CompoundTag guardRow=FiniteSiteState.encodeSite(guard);
        guardRow.getList("guardians",Tag.TAG_COMPOUND).add(guardRow.getList("guardians",Tag.TAG_COMPOUND).getCompound(0).copy());
        reject(() -> FiniteSiteState.decodeSite(guardRow));
        for (String key : valid.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getAllKeys()) {
            bad=valid.copy(); CompoundTag row=bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0);
            if(row.contains(key,Tag.TAG_STRING)) row.putInt(key,1); else row.putString(key,"wrong type"); isolated(bad);
        }
        CompoundTag invFields=valid.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getCompound("inventory");
        for(String key:invFields.getAllKeys()) {
            bad=valid.copy(); bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getCompound("inventory").putLong(key,1); isolated(bad);
        }
        CompoundTag slotFields=valid.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getList("slots",Tag.TAG_COMPOUND).getCompound(0);
        for(String key:slotFields.getAllKeys()) {
            bad=valid.copy(); CompoundTag row=bad.getList("sites",Tag.TAG_COMPOUND).getCompound(0).getList("slots",Tag.TAG_COMPOUND).getCompound(0);
            if(row.contains(key,Tag.TAG_STRING)) row.putInt(key,1); else row.putString(key,"wrong type"); isolated(bad);
        }
        for(FiniteSite s:List.of(p,ore,guard,dead)) check(FiniteSiteState.decodeSite(FiniteSiteState.encodeSite(s)).equals(s),"complete site codec");
        System.out.println("FinitePersistenceProbe PASS: "+checks+" checks (no world/server execution)");
    }
}
