package net.guzhenren.tianfu.client;

import net.guzhenren.tianfu.TianfuPool;
import net.guzhenren.tianfu.TianfuTalents;
import net.guzhenren.tianfu.v2.TianfuEconomy;
import net.guzhenren.tianfu.v2.TianfuSelection;

/** Read-only drafts; the server remains the sole authority that commits purchases. */
public final class TianfuUiRules {
    private TianfuUiRules() {}
    public record Decision(boolean allowed,String reason,int afterPoints) {}
    public static Decision purchase(TianfuSelection.Snapshot state,TianfuPool.Spec spec) {
        if(state==null)return new Decision(false,"等待服务端同步",0);
        if(spec==null)return new Decision(false,"该记录已不在目录",state.points());
        if(!TianfuTalents.isAvailable(spec.id()))return new Decision(false,"所需附属未安装，仅保留历史记录",state.points());
        if(spec.kind()==TianfuPool.Kind.BODY||spec.kind()==TianfuPool.Kind.SIJUE) {
            for(String id:state.purchased()) {
                var other=TianfuPool.byId(id);
                if(other!=null&&(other.kind()==TianfuPool.Kind.BODY||other.kind()==TianfuPool.Kind.SIJUE))
                    return new Decision(false,"已有体质，不能再选择另一种",state.points());
            }
        }
        return from(TianfuEconomy.buy(state,spec.id()));
    }
    public static Decision revoke(TianfuSelection.Snapshot state,TianfuPool.Spec spec) {
        if(state==null)return new Decision(false,"等待服务端同步",0);
        if(spec==null)return new Decision(false,"该记录已不在目录",state.points());
        if(spec.kind()==TianfuPool.Kind.SIJUE||spec.kind()==TianfuPool.Kind.JIYUAN)return new Decision(false,"十绝体与历史机缘不可撤销",state.points());
        return from(TianfuEconomy.revoke(state,spec.id()));
    }
    private static Decision from(TianfuSelection.Result r) {
        int balance=r.snapshot()==null?0:r.snapshot().points();
        if(r.ok())return new Decision(true,"提交后由服务端再次核对",balance);
        return new Decision(false,switch(r.reason()) {
            case LOCKED->"本世已锁定，或该项仅供历史查阅";
            case NO_DIFFICULTY->"请先选择开局难度";
            case INSUFFICIENT_POINTS->"点数不足；撤销负面也需扣回原补点";
            case NEGATIVE_CAP->"负面补点合计不能超过24点";
            case CONFLICT->"与已选条目冲突，或本项已选择";
            case LEGACY_PRICE_UNKNOWN->"缺少原成交记录，暂不能结算";
            case UNVERIFIED_EFFECT->"效果尚未通过验证";
            case UNSAFE_LIFESPAN->"当前寿元条件不满足";
            case NOT_OPENED->"请先完成本体开窍";
            case STALE_REVISION->"状态已更新，请重新查看";
            default->"当前目录不可购买此项";
        },balance);
    }
    public static String price(int cost) {return cost<0?"补点 +"+-cost:cost==0?"不耗点":"消耗 "+cost+" 点";}
}
