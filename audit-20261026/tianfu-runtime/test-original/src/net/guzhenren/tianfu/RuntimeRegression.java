package net.guzhenren.tianfu;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.init.GuzhenrenModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
public class RuntimeRegression {
 static int failed;
 static void check(String n,boolean ok){System.out.println((ok?"PASS ":"FAIL ")+n);if(!ok)failed++;}
 static double cooldown(ServerPlayer player,Set<String> ids){PlayerVariables v=player.variables; boolean touched=false;
         double guCd = TianfuTalents.guCd(ids);
        if (guCd > 0.0 && v.cd > 0.0) {
            v.cd = Math.max(0.0, v.cd - guCd);
            touched = true;
        }

 return v.cd;
 }
 static void silence(ServerPlayer player,Set<String> ids){
         if (ids.contains("xinshen_ruyuan")) {
            var chenmo = player.getEffect(GuzhenrenModMobEffects.CHENMO);
            long floor = TianfuData.getLong(player, "chenmo_floor", 0L);
            if (chenmo == null) {
                if (floor != 0L) {
                    TianfuData.putLong(player, "chenmo_floor", 0L); // 效果结束，重置标记
                }
            } else if (chenmo.getDuration() > 40 && chenmo.getDuration() > floor) {
                int target = chenmo.getDuration() / 2;
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        GuzhenrenModMobEffects.CHENMO, target,
                        chenmo.getAmplifier(), chenmo.isAmbient(), chenmo.isVisible()));
                TianfuData.putLong(player, "chenmo_floor", target);
            }
        }


 }
 static void tickV2(ServerPlayer player){PlayerVariables v=player.variables;boolean touched=false;Set<String> ids=player.ids;
         // ---- 周期性重建属性修正（幂等；同时兜住星辉照夜变的昼夜切换）----
        if (player.tickCount % 100 == 0) {
            TianfuAttributes.sync(player);
            TianfuTalents.maintainEffects(player, ids);   // 常驻药水效果（夜视 / 抗性 / 防火 等）
        }

        if (touched) {
            v.markSyncDirty();
        }
    }


     public static void maintainV2(ServerPlayer player) {
        if (player == null) {
            return;
        }
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        if (!snapshot.confirmed()) {
            return;
        }
        new net.guzhenren.tianfu.v2.NeoForge1211Adapter().maintain(player, snapshot);    }
 public static void main(String[] args){
  var p=new ServerPlayer();p.variables.cd=5;
  check("negative cooldown penalty",Math.abs(cooldown(p,Set.of("v2_gu_cd_ningse"))-5.10)<1e-9);
  p.variables.cd=0;check("no penalty while idle",cooldown(p,Set.of("v2_gu_cd_ningse"))==0);
  p.variables.cd=5;check("positive cooldown preserved",Math.abs(cooldown(p,Set.of("v2_mind_cooldown_1"))-4.85)<1e-9);
  p.variables.cd=5;check("net positive cooldown preserved",Math.abs(cooldown(p,Set.of("v2_mind_cooldown_1","v2_gu_cd_ningse"))-4.95)<1e-9);
  p=new ServerPlayer();p.addEffect(new MobEffectInstance(GuzhenrenModMobEffects.CHENMO,200,2,true,false,false));
  silence(p,Set.of("xinshen_ruyuan"));
  check("silence duration halves",p.getEffect(GuzhenrenModMobEffects.CHENMO).getDuration()==100);
  check("silence metadata preserved",p.getEffect(GuzhenrenModMobEffects.CHENMO).getAmplifier()==2&&p.getEffect(GuzhenrenModMobEffects.CHENMO).isAmbient()&&!p.getEffect(GuzhenrenModMobEffects.CHENMO).isVisible()&&!p.getEffect(GuzhenrenModMobEffects.CHENMO).showIcon());
  silence(p,Set.of("xinshen_ruyuan"));check("silence not halved twice",p.getEffect(GuzhenrenModMobEffects.CHENMO).getDuration()==100);
  var victim=new net.minecraft.world.entity.LivingEntity();victim.type.tags.add(TianfuSlay.TEXIAO);
  p=new ServerPlayer();p.ids=Set.of("shiyuan_zhixing");
  TianfuSlay.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(victim,new net.minecraft.world.damagesource.DamageSource(p)));
  check("shiyuan works without slay or tuntian",p.variables.zhenyuan==15&&p.variables.niantou==20);
  check("shiyuan does not grant slay",TianfuData.slay(p)==0);
  p=new ServerPlayer();p.ids=Set.of("shiyuan_zhixing");p.confirmed=false;
  TianfuSlay.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(victim,new net.minecraft.world.damagesource.DamageSource(p)));
  check("unconfirmed shiyuan inactive",p.variables.zhenyuan==10&&p.variables.niantou==10);
  p=new ServerPlayer();p.ids=Set.of("v2_luck_forfeit","v2_aptitude_shackled","v2_lifespan_scar");
  TianfuData.putDouble(p,"v2_lock_kongqiao",7);TianfuData.putDouble(p,"v2_lifespan_cap",100);
  p.variables.kongqiao=9;p.variables.qiyun=30;p.variables.qiyun_shangxian=50;p.variables.shouyuan=90;
  tickV2(p);
  check("tick maintains luck and aptitude",p.variables.kongqiao==7&&p.variables.qiyun==0&&p.variables.qiyun_shangxian==0);
  check("lifespan cap follows natural loss",TianfuData.getDouble(p,"v2_lifespan_cap",-1)==90);
  check("constraint writes sync player data",p.variables.dirty==1);
  p.variables.shouyuan=95;tickV2(p);
  check("lifespan recovery blocked after natural loss",p.variables.shouyuan==90);
  int dirty=p.variables.dirty;tickV2(p);check("stable constraints do not dirty",p.variables.dirty==dirty);
  p=new ServerPlayer();p.ids=Set.of("v2_luck_forfeit","v2_aptitude_shackled","v2_lifespan_scar");p.confirmed=false;
  p.variables.kongqiao=9;p.variables.qiyun=30;p.variables.qiyun_shangxian=50;p.variables.shouyuan=90;
  TianfuData.putDouble(p,"v2_lock_kongqiao",7);TianfuData.putDouble(p,"v2_lifespan_cap",80);tickV2(p);
  check("unconfirmed constraints inactive",p.variables.kongqiao==9&&p.variables.qiyun==30&&p.variables.qiyun_shangxian==50&&p.variables.shouyuan==90&&p.variables.dirty==0);
  if(failed>0)System.exit(1);
 }
}