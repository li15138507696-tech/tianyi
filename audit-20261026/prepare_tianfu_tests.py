from pathlib import Path

ROOT = Path(__file__).resolve().parent / 'work/tianfu'
harness = ROOT / '_tmp_verify/verify_selection_flow.py'
text = harness.read_text(encoding='utf-8')
text = text.replace("'net/TianfuDifficultyPayload.java','net/TianfuV2StatePayload.java']", "'net/TianfuDifficultyPayload.java','net/TianfuV2StatePayload.java','net/TianfuOriginPayload.java']")
text = text.replace("'SelectionFlowCheck'],check=True,cwd=ROOT)", "'SelectionFlowCheck'] + __import__('sys').argv[1:],check=True,cwd=ROOT)")
harness.write_text(text, encoding='utf-8')
check = ROOT / '_tmp_verify/SelectionFlowCheck.java'
text = check.read_text(encoding='utf-8')
text = text.replace('public static void main(String[] args){', '''
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
''')
check.write_text(text, encoding='utf-8')
print('Tianfu regression cases prepared')
