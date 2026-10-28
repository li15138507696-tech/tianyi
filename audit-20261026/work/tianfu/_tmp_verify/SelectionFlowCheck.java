import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.guzhenren.tianfu.*;
import net.guzhenren.tianfu.net.*;
import net.guzhenren.tianfu.v2.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Production transaction and packet methods, with explicit native effect/player adapters. */
public final class SelectionFlowCheck {
    static int checks;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static IPayloadContext context(ServerPlayer p){return (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class<?>[]{IPayloadContext.class},(proxy,m,a)->{
        if(m.getName().equals("player"))return p;
        if(m.getName().equals("enqueueWork")&&a[0] instanceof Runnable r){r.run();return CompletableFuture.completedFuture(null);}
        throw new AssertionError(m.getName());});}
    static ServerPlayer player(){var p=new ServerPlayer();TianfuData.markApplied(p);TianfuV2Store.migrate(p);return p;}
    static ServerPlayer ready(){var p=player();TianfuData.setDifficulty(p,0);TianfuData.addPoints(p,8);return p;}
    static String tag(ServerPlayer p){return p.getPersistentData().toString();}
    static void roll(ServerPlayer p){TianfuRollPayload.handle(new TianfuRollPayload("hell"),context(p));}
    static void accept(ServerPlayer p){TianfuRollAcceptPayload.handle(new TianfuRollAcceptPayload(),context(p));}
    static void resetAdapter(){NeoForge1211Adapter.failFirst=false;NeoForge1211Adapter.failAfterBase=false;NeoForge1211Adapter.throwDuringSettle=false;NeoForge1211Adapter.preflights=0;NeoForge1211Adapter.settlements=0;TianfuNative.forceStale=false;TianfuAttributes.fail=false;}
    
    static void originRoundtrip(){
        var p=ready(); var s=TianfuV2Store.read(p);
        var chosen=TianfuEconomy.selectOrigin(s,new TianfuSelection.Origin("beiyuan","clan"));
        check(chosen.ok(),"clan selection accepted");
        check(TianfuV2Store.commit(p,new TianfuSelection.Change(s.revision(),chosen.snapshot())),"origin committed");
        s=TianfuV2Store.read(p);
        check(s.originReceipt().paidCost()==-1 && s.originReceipt().grant()==1,"origin receipt survives NBT roundtrip with cost and grant");
        for(int i=0;i<5;i++){
            var result=TianfuEconomy.selectOrigin(s,s.origin());
            check(result.ok() && result.snapshot().points()==9 && result.snapshot().negativeGrant()==1,"reselecting clan cannot farm free points");
            TianfuV2Store.commit(p,new TianfuSelection.Change(s.revision(),result.snapshot()));s=TianfuV2Store.read(p);
        }
        var switchOrigin=TianfuEconomy.selectOrigin(s,new TianfuSelection.Origin("nanjiang","apprentice"));
        check(switchOrigin.ok() && switchOrigin.snapshot().points()==6,"changing origin reverses the original grant before charging the new cost");
    }
    static void nestedLegacyReceipt(){
        var p=ready();var root=p.getPersistentData().getCompound(TianfuData.ROOT);
        var receipt=new net.minecraft.nbt.CompoundTag();receipt.putString("id","clan");receipt.putInt("paidCost",-1);receipt.putInt("grant",1);
        var wrapped=new net.minecraft.nbt.CompoundTag();wrapped.put("origin",receipt);root.put(TianfuV2Store.ORIGIN_RECEIPT,wrapped);
        root.putString(TianfuV2Store.ORIGIN_REGION,"beiyuan");root.putString(TianfuV2Store.ORIGIN_BACKGROUND,"clan");root.putInt("points",9);
        var s=TianfuV2Store.read(p);
        check(s.originReceipt().paidCost()==-1 && s.negativeGrant()==1,"1.3.4 nested origin receipts retain their historical cost");
    }
    static void unopenedOrigin(){
        var p=new ServerPlayer();p.opened=false;TianfuData.setDifficulty(p,0);TianfuData.addPoints(p,8);
        var before=tag(p);TianfuOriginPayload.handle(new TianfuOriginPayload("beiyuan","clan",0),context(p));
        check(tag(p).equals(before),"origin handler cannot migrate or grant points before opening");
        p=ready();TianfuData.markSettled(p);before=tag(p);
        TianfuOriginPayload.handle(new TianfuOriginPayload("beiyuan","clan",TianfuV2Store.read(p).revision()),context(p));
        check(tag(p).equals(before),"settled origin handler is zero write");
    }
    static void rollWithPurchases(){
        var p=ready();check(TianfuData.purchase(p,"neg",-2),"existing negative purchased");
        var s=TianfuV2Store.read(p);var ids=TianfuEconomy.planRoll(s,new Random(7));
        check(!ids.isEmpty(),"owned negative cannot invalidate otherwise feasible reroll");
        check(!ids.contains("neg"),"reroll skips existing purchases");
        check(TianfuEconomy.acceptRoll(s,ids).ok(),"planned candidate remains atomically affordable");
    }
    public static void main(String[] args){
        if(args.length>0){switch(args[0]){case "origin"->originRoundtrip();case "legacy"->nestedLegacyReceipt();case "gate"->unopenedOrigin();case "roll"->rollWithPurchases();default->throw new AssertionError(args[0]);}System.out.println(args[0]+": "+checks+" checks passed");return;}

        var p=player();TianfuV2StatePayload.clear();var initialDifficulty=TianfuV2Store.read(p);TianfuV2StatePayload.handle(new TianfuV2StatePayload(initialDifficulty),context(p));TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));var selectedDifficulty=TianfuV2Store.read(p);check(selectedDifficulty.revision()==initialDifficulty.revision()+1&&selectedDifficulty.points()==8&&selectedDifficulty.difficulty()==0,"difficulty and base points commit with a higher authority revision");TianfuV2StatePayload.handle(new TianfuV2StatePayload(selectedDifficulty),context(p));check(TianfuV2StatePayload.latest().points()==8&&TianfuV2StatePayload.latest().difficulty()==0,"real client cache accepts newly selected difficulty instead of retaining revision zero");String before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("hell"),context(p));check(tag(p).equals(before)&&TianfuData.points(p)==8,"repeat difficulty selection cannot issue points or change mode");
        p=player();p.opened=false;before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));check(tag(p).equals(before),"unopened difficulty request is zero write");p=player();TianfuData.markConfirmed(p);before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));check(tag(p).equals(before),"confirmed difficulty request is zero write");p=player();TianfuData.markSettled(p);before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));check(tag(p).equals(before),"settled difficulty request is zero write");p=new ServerPlayer();before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("invalid"),context(p));check(tag(p).equals(before),"unknown difficulty request does not migrate or issue points");p=player();TianfuData.addPoints(p,Integer.MAX_VALUE);before=tag(p);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));check(tag(p).equals(before),"overflowing point award cannot partially select difficulty");p=player();TianfuData.addPoints(p,3);TianfuDifficultyPayload.handle(new TianfuDifficultyPayload("easy"),context(p));check(TianfuData.points(p)==11,"difficulty adds one base grant without replacing an existing balance");TianfuV2StatePayload.clear();
        p=ready();p.opened=false;before=tag(p);roll(p);check(tag(p).equals(before),"unopened roll writes nothing");
        p=player();before=tag(p);roll(p);check(tag(p).equals(before),"unselected difficulty cannot roll or award points");
        p=ready();for(int i=0;i<3;i++){roll(p);check(TianfuData.rollCount(p)==i+1,"successful roll increments server count");check(!TianfuData.rollPending(p).isEmpty(),"valid candidate retained");}
        before=tag(p);roll(p);check(tag(p).equals(before),"fourth request cannot reroll");check(TianfuData.points(p)==8&&TianfuData.difficulty(p)==0,"rerolls cannot change difficulty or reissue base points");
        TianfuData.markConfirmed(p);before=tag(p);roll(p);check(tag(p).equals(before),"confirmed player cannot reroll");
        p=ready();TianfuData.setRollPending(p,List.of("entry"));TianfuData.setRollCount(p,1);TianfuData.purchase(p,"entry",1);TianfuData.purchase(p,"positive",2);TianfuData.purchase(p,"neg",-2);TianfuData.purchase(p,"v2_lifespan_scar",-8);TianfuData.addPoints(p,-TianfuData.points(p));before=tag(p);roll(p);check(tag(p).equals(before),"infeasible reroll preserves prior candidate and count");
        p=ready();TianfuData.setRollPending(p,List.of("entry"));p.opened=false;before=tag(p);accept(p);check(tag(p).equals(before),"unopened player cannot accept");
        p.opened=true;TianfuData.markConfirmed(p);before=tag(p);accept(p);check(tag(p).equals(before),"confirmed player cannot accept");
        p=ready();TianfuData.setRollPending(p,List.of("unknown"));before=tag(p);accept(p);check(tag(p).equals(before),"invalid batch preserves candidate and wallet");
        p=ready();TianfuData.setRollPending(p,List.of("entry"));accept(p);check(TianfuData.points(p)==7&&TianfuData.has(p,"entry"),"valid candidate appended with actual catalog cost");check(TianfuData.rollPending(p).isEmpty()&&TianfuData.isDeferred(p)&&!TianfuData.isConfirmed(p),"accept clears candidate but waits for final confirmation");before=tag(p);accept(p);check(tag(p).equals(before),"repeat accept cannot pay twice");

        resetAdapter();p=ready();p.opened=false;before=tag(p);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NOT_OPENED&&tag(p).equals(before),"confirmation requires opening");
        p=player();before=tag(p);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NO_DIFFICULTY&&tag(p).equals(before),"confirmation requires difficulty");
        p=ready();p.vars.kongqiao=0;before=tag(p);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.CONFLICT&&tag(p).equals(before),"confirmation requires native aptitude or anchor");
        p=ready();TianfuData.purchase(p,"entry",1);NeoForge1211Adapter.failFirst=true;before=tag(p);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.UNSAFE_LIFESPAN&&tag(p).equals(before)&&p.vars.total==0,"preflight failure leaves fields and lock unchanged");
        resetAdapter();p=ready();TianfuData.purchase(p,"body",3);TianfuData.purchase(p,"entry",1);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NONE,"final confirmation succeeds");check(TianfuData.isConfirmed(p)&&TianfuData.isSettled(p)&&p.vars.tizhi==24&&p.vars.total==1060,"native body, entry and V2 effects all applied");check(TianfuV2Store.read(p).originEffectKeys().contains("settlement_complete"),"successful settlement records completion");before=tag(p);double total=p.vars.total;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NONE&&tag(p).equals(before)&&p.vars.total==total,"duplicate final confirmation is idempotent");
        resetAdapter();p=ready();TianfuData.purchase(p,"entry",1);before=tag(p);NeoForge1211Adapter.throwDuringSettle=true;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.UNVERIFIED_EFFECT&&tag(p).equals(before)&&p.vars.total==0,"native exception restores fields, root and confirmation lock");
        resetAdapter();p=ready();TianfuData.purchase(p,"body",3);before=tag(p);NeoForge1211Adapter.failAfterBase=true;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.UNSAFE_LIFESPAN&&tag(p).equals(before)&&p.vars.total==0&&p.vars.tizhi==0,"unsafe post-talent lifespan restores native effects");
        resetAdapter();p=ready();TianfuData.addPoints(p,20);TianfuData.purchase(p,"sijue",16);before=tag(p);TianfuNative.forceStale=true;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.STALE_REVISION&&tag(p).equals(before)&&p.vars.total==0&&p.vars.kongqiao==2,"stale commit restores native fields and entire original transaction");
        resetAdapter();p=ready();TianfuData.purchase(p,"entry",1);TianfuData.markConfirmed(p);TianfuApply.applyAllConfirmed(p,false);check(TianfuData.isSettled(p)&&p.vars.total==60,"third-party confirmed state can settle without pre-marking settled");
        resetAdapter();p=ready();TianfuData.purchase(p,"entry",1);TianfuConfirmPayload.handle(new TianfuConfirmPayload(),context(p));check(TianfuData.isConfirmed(p)&&TianfuData.isSettled(p)&&p.vars.total==60,"actual final-confirm packet invokes the complete confirmation entry");before=tag(p);TianfuConfirmPayload.handle(new TianfuConfirmPayload(),context(p));check(tag(p).equals(before)&&p.vars.total==60,"repeat confirmation packet has no duplicate effects");
        resetAdapter();p=ready();var initial=TianfuV2Store.read(p);var old=new TianfuSelection.Snapshot(2,initial.revision()+1,5,0,false,initial.origin(),null,List.of("body"),Map.of("body",new TianfuSelection.Receipt("body",3,0,true)),0,Set.of());TianfuV2Store.commit(p,new TianfuSelection.Change(initial.revision(),old));p.vars.tizhi=24;p.vars.total=1000;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NONE&&p.vars.total==1050,"legacy instant purchase is not applied a second time when newly confirmed");
        resetAdapter();p=ready();var objective=p.getScoreboard().addObjective("test",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("test"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,true,null);var holder=net.minecraft.world.scores.ScoreHolder.forNameOnly(p.getScoreboardName());p.getScoreboard().getOrCreatePlayerScore(holder,objective).set(7);NeoForge1211Adapter.throwDuringSettle=true;TianfuApply.confirmFinal(p);check(p.getScoreboard().getPlayerScoreInfo(holder,objective).value()==7,"native exception restores player score adapter");
        resetAdapter();p=ready();TianfuData.purchase(p,"entry",1);before=tag(p);TianfuAttributes.fail=true;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.UNVERIFIED_EFFECT&&tag(p).equals(before)&&p.vars.total==0,"attribute synchronization failure cannot retain a success lock or escape rollback");
        resetAdapter();p=ready();TianfuData.purchase(p,"v2_lifespan_scar",-8);check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NONE&&p.vars.shouyuan==600,"real scar FIELD_ADD simulation and adapter cannot both deduct 400; 1000 becomes 600");before=tag(p);TianfuApply.confirmFinal(p);check(p.vars.shouyuan==600&&tag(p).equals(before),"repeated scar confirmation cannot deduct again");
        resetAdapter();p=ready();TianfuData.purchase(p,"v2_lifespan_scar",-8);NeoForge1211Adapter.throwDuringSettle=true;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.UNVERIFIED_EFFECT&&p.vars.shouyuan==1000&&!TianfuData.isConfirmed(p),"failed scar settlement restores lifespan before retry");NeoForge1211Adapter.throwDuringSettle=false;check(TianfuApply.confirmFinal(p)==TianfuSelection.Rejection.NONE&&p.vars.shouyuan==600,"retry after scar rollback pays exactly once");
        System.out.println("SelectionFlowCheck: "+checks+" production handler/economy/store/confirmation checks passed (native effects use adapters; no game started)");
    }
}
