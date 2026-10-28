"""Run actual lifecycle handlers and actual counter/cooldown methods with small Java doubles."""
from pathlib import Path
import subprocess
from regression import HERE, ORIGINAL, JAVA, STUBS

def method(text, signature):
    start=text.index(signature)
    brace=text.index('{',start)
    depth=1
    end=brace+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}')
        end+=1
    return text[start:end]

EXTRA={
'net/minecraft/nbt/Tag.java': '''package net.minecraft.nbt;
public class Tag {public static final int TAG_LONG=4,TAG_COMPOUND=10;}''',
'net/minecraft/nbt/CompoundTag.java': '''package net.minecraft.nbt;
public class CompoundTag extends Tag {
 public java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean contains(String k,int t){return t==TAG_LONG?values.get(k) instanceof Long:values.get(k) instanceof CompoundTag;}
 public long getLong(String k){return values.get(k) instanceof Long l?l:0;}
 public void putLong(String k,long n){values.put(k,n);} public void putBoolean(String k,boolean n){values.put(k,n);}
 public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));}
 public CompoundTag getCompound(String k){return values.get(k) instanceof CompoundTag c?c:new CompoundTag();}
 public void put(String k,CompoundTag c){values.put(k,c);} public void remove(String k){values.remove(k);}
 public CompoundTag copy(){var n=new CompoundTag();values.forEach((k,v)->n.values.put(k,v instanceof CompoundTag c?c.copy():v));return n;}
}''',
'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;
public class Entity {
 public EntityType<?> type=new EntityType<>("test"); public java.util.UUID uuid=java.util.UUID.randomUUID();
 private net.minecraft.nbt.CompoundTag data=new net.minecraft.nbt.CompoundTag();
 public EntityType<?> getType(){return type;} public java.util.UUID getUUID(){return uuid;}
 public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}
 public net.minecraft.nbt.CompoundTag getPersistentData(){return data;}
}''',
'net/guzhenren/tianfu/TianfuData.java': '''package net.guzhenren.tianfu;
public class TianfuData {
 public static final String ROOT="tianfu";
 public static boolean isConfirmed(net.minecraft.server.level.ServerPlayer p){return p.getPersistentData().getCompound(ROOT).getBoolean("confirmed");}
}''',
'net/guzhenren/tianfu/TianfuTalents.java': '''package net.guzhenren.tianfu;
public class TianfuTalents {
 public static double counterBonus(java.util.Set<String> ids){return ids.contains("counter")?.35:0;}
 public static int counterTicks(java.util.Set<String> ids){return 100;}
}''',
'net/guzhenren/tianfu/TianfuSlay.java': '''package net.guzhenren.tianfu;
public class TianfuSlay {public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e){}}''',
'net/guzhenren/tianfu/TianfuEffects.java': '''package net.guzhenren.tianfu;
public class TianfuEffects {public static void onTick(net.minecraft.server.level.ServerPlayer p){}}''',
'net/guzhenren/tianfu/TianfuZhuCombat.java': '''package net.guzhenren.tianfu;
public class TianfuZhuCombat {public static void forget(net.minecraft.world.entity.Entity p){}}''',
'net/guzhenren/tianfu/TianfuVfxEntity.java': '''package net.guzhenren.tianfu;
public class TianfuVfxEntity {public static void forget(net.minecraft.server.level.ServerPlayer p){}}''',
'net/neoforged/bus/api/SubscribeEvent.java': '''package net.neoforged.bus.api;public @interface SubscribeEvent {}''',
'net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent.java': '''package net.neoforged.neoforge.event.entity.living;public class LivingIncomingDamageEvent {}''',
'net/neoforged/neoforge/event/entity/player/PlayerEvent.java': '''package net.neoforged.neoforge.event.entity.player;
import net.minecraft.world.entity.player.Player;
public class PlayerEvent {
 public record Clone(Player entity,Player original){public Player getEntity(){return entity;}public Player getOriginal(){return original;}}
 public record PlayerLoggedInEvent(Player entity){public Player getEntity(){return entity;}}
 public record PlayerLoggedOutEvent(Player entity){public Player getEntity(){return entity;}}
 public record PlayerChangedDimensionEvent(Player entity){public Player getEntity(){return entity;}}
}''',
'net/neoforged/neoforge/event/tick/PlayerTickEvent.java': '''package net.neoforged.neoforge.event.tick;
public class PlayerTickEvent {public record Post(net.minecraft.world.entity.player.Player entity){public net.minecraft.world.entity.player.Player getEntity(){return entity;}}}''',
}

HARNESS='''package net.guzhenren.tianfu;
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
}'''

def run(label,source):
    base=HERE/('clone-test-'+label)
    src=base/'src'
    out=base/'classes'
    files=dict(STUBS)
    files.update(EXTRA)
    # Opening's root-copy handler is executed unchanged.
    opening=(ORIGINAL/'net/guzhenren/tianfu/TianfuOpening.java').read_text(encoding='utf-8')
    clone=method(opening,'public static void onClone(')
    files['net/guzhenren/tianfu/TianfuOpening.java']='''package net.guzhenren.tianfu;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;import net.minecraft.nbt.Tag;
public class TianfuOpening {public static void onLogin(ServerPlayer p){}
'''+clone+'}'
    combatpath=source/'net/guzhenren/tianfu/TianfuCombat.java'
    if not combatpath.exists():combatpath=ORIGINAL/'net/guzhenren/tianfu/TianfuCombat.java'
    combat=combatpath.read_text(encoding='utf-8')
    methods=method(combat,'private static double counterMultiplier(')+'\n'+method(combat,'public static void forget(')
    if 'public static void copyCooldownOnClone(' in combat:
        methods+='\n'+method(combat,'public static void copyCooldownOnClone(')
    files['net/guzhenren/tianfu/TianfuCombat.java']='''package net.guzhenren.tianfu;
import java.util.Map;import java.util.Set;import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.Entity;
public class TianfuCombat {
'''+''.join(line+'\n' for line in combat.splitlines() if line.strip().startswith(('private static final Map<java.util.UUID, Integer> COUNTER_TICK','private static final Map<java.util.UUID, Map<String, Integer>> ADAPT_STACK','private static final String TAG_UNDYING_READY')))+methods+'''
 public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e){}
 public static void seed(ServerPlayer p,int tick){COUNTER_TICK.put(p.getUUID(),tick);}
 public static double probeCounter(ServerPlayer p){return counterMultiplier(p,Set.of("counter"));}
}'''
    eventspath=source/'net/guzhenren/tianfu/TianfuEvents.java'
    if not eventspath.exists():eventspath=ORIGINAL/'net/guzhenren/tianfu/TianfuEvents.java'
    files['net/guzhenren/tianfu/TianfuEvents.java']=eventspath.read_text(encoding='utf-8')
    files['net/guzhenren/tianfu/CloneRegression.java']=HARNESS
    for rel,body in files.items():
        dest=src/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body,encoding='utf-8')
    out.mkdir(parents=True,exist_ok=True)
    argfile=base/'args.txt'
    argfile.write_text('-encoding\nUTF-8\n-proc:none\n-d\n"'+out.as_posix()+'"\n'+'\n'.join('"'+p.as_posix()+'"' for p in src.rglob('*.java')),encoding='utf-8')
    r=subprocess.run([str(JAVA/'javac.exe'),'@'+str(argfile)],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if r.returncode:raise RuntimeError(r.stdout+r.stderr)
    r=subprocess.run([str(JAVA/'java.exe'),'-cp',str(out),'net.guzhenren.tianfu.CloneRegression'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(label.upper()+'\n'+r.stdout+r.stderr)
    (HERE/('clone-regression-'+label+'.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
    return r

if __name__=='__main__':
    old=run('original',ORIGINAL)
    for name in ['ordinary clone retains future undying cooldown','respawn counter not permanently active','end return clears stale counter memory']:
        assert 'FAIL '+name in old.stdout, 'Original did not reproduce: '+name
    new=run('patched',HERE/'patched-src')
    assert new.returncode==0,'Patched lifecycle regression failed'
    print('Original reproduced clone bugs; patched passed all lifecycle cases.')
