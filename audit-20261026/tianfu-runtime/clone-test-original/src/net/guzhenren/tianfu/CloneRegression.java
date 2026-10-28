package net.guzhenren.tianfu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.damagesource.DamageSource;
public class CloneRegression {
 static int failed;
 static final String KEY="tianfu_malice_undying_ready";
 static void check(String s,boolean ok){System.out.println((ok?"PASS ":"FAIL ")+s);if(!ok)failed++;}
 static ServerPlayer oldPlayer(){var p=new ServerPlayer();var root=new CompoundTag();root.putBoolean("confirmed",true);p.getPersistentData().put("tianfu",root);p.getPersistentData().putLong(KEY,206000L);p.tickCount=200000;return p;}
 static ServerPlayer replacement(ServerPlayer old){var p=new ServerPlayer();p.uuid=old.uuid;p.tickCount=0;return p;}
 public static void main(String[] args){
  var old=oldPlayer();TianfuCombat.seed(old,old.tickCount);
  check("counter initially active",TianfuCombat.probeCounter(old)>1);
  TianfuEvents.onDeath(new LivingDeathEvent(old,new DamageSource(new net.minecraft.world.entity.Entity())));
  check("death clears old counter memory",TianfuCombat.probeCounter(old)==1);
  var fresh=replacement(old);TianfuEvents.onClone(new PlayerEvent.Clone(fresh,old));
  check("ordinary clone retains confirmed talents",TianfuData.isConfirmed(fresh));
  check("ordinary clone retains future undying cooldown",fresh.getPersistentData().getLong(KEY)==206000L);
  check("respawn counter not permanently active",TianfuCombat.probeCounter(fresh)==1);
  // End return clones without a death event, but tickCount still restarts.
  old=oldPlayer();TianfuCombat.seed(old,old.tickCount);fresh=replacement(old);
  TianfuEvents.onClone(new PlayerEvent.Clone(fresh,old));
  check("end return clears stale counter memory",TianfuCombat.probeCounter(fresh)==1);
  check("end return retains undying cooldown",fresh.getPersistentData().getLong(KEY)==206000L);
  // A reincarnation wipes the root before clone; cooldown must start fresh as well.
  old=oldPlayer();old.getPersistentData().remove("tianfu");fresh=replacement(old);
  TianfuEvents.onClone(new PlayerEvent.Clone(fresh,old));
  check("new life does not inherit old cooldown",fresh.getPersistentData().getLong(KEY)==0);
  if(failed>0)System.exit(1);
 }
}