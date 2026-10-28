"""Execute the real Slay class and extracted tick blocks against narrow Java test doubles.

Both versions run: original must expose all three bugs, patched must pass. No game launch.
MobEffectInstance.update's duration merge contract was checked in the installed 1.21.1 jar;
its javap listing is retained next to this script. These are control-flow regressions,
not a claim of an in-game integration test.
"""
from pathlib import Path
import subprocess
import re

HERE = Path(__file__).resolve().parent
ORIGINAL = HERE.parent / 'baseline-src/tianfu/src/main/java'
JAVA = Path(r'C:\Users\ADMIN\.jdks\temurin-21\bin')

STUBS = {
'net/minecraft/core/Holder.java': '''package net.minecraft.core;
public record Holder<T>(T value) {}''',
'net/minecraft/resources/ResourceLocation.java': '''package net.minecraft.resources;
public record ResourceLocation(String namespace,String path) {
 public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}
 public String getPath(){return path;}
}''',
'net/minecraft/tags/TagKey.java': '''package net.minecraft.tags;
public record TagKey<T>(net.minecraft.resources.ResourceLocation id) {
 public static <T> TagKey<T> create(Object ignored,net.minecraft.resources.ResourceLocation id){return new TagKey<T>(id);}
}''',
'net/minecraft/core/registries/Registries.java': '''package net.minecraft.core.registries;
public class Registries {public static final Object ENTITY_TYPE=new Object();}''',
'net/minecraft/core/registries/BuiltInRegistries.java': '''package net.minecraft.core.registries;
public class BuiltInRegistries {public static final Types ENTITY_TYPE=new Types();
 public static class Types {public net.minecraft.resources.ResourceLocation getKey(net.minecraft.world.entity.EntityType<?> t){return t.id;}}
}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
public class Level {public long getGameTime(){return 10000;} public long getDayTime(){return 24000;}}''',
'net/minecraft/world/entity/EntityType.java': '''package net.minecraft.world.entity;
public class EntityType<T> {
 public net.minecraft.resources.ResourceLocation id;
 public java.util.Set<net.minecraft.tags.TagKey<?>> tags=new java.util.HashSet<>();
 public EntityType(String path){id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("guzhenren",path);}
 public boolean is(net.minecraft.tags.TagKey<?> tag){return tags.contains(tag);}
}''',
'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;
public class Entity {
 public EntityType<?> type=new EntityType<>("test"); private final java.util.UUID uuid=java.util.UUID.randomUUID();
 public EntityType<?> getType(){return type;} public java.util.UUID getUUID(){return uuid;}
 public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}
}''',
'net/minecraft/world/effect/MobEffect.java': '''package net.minecraft.world.effect;
public class MobEffect {public boolean isBeneficial(){return false;}}''',
'net/minecraft/world/effect/MobEffectInstance.java': '''package net.minecraft.world.effect;
public class MobEffectInstance {
 public final net.minecraft.core.Holder<MobEffect> effect;
 private int duration; private final int amp; private boolean ambient,visible,icon;
 public MobEffectInstance(net.minecraft.core.Holder<MobEffect> e,int d,int a,boolean am,boolean v){this(e,d,a,am,v,true);}
 public MobEffectInstance(net.minecraft.core.Holder<MobEffect> e,int d,int a,boolean am,boolean v,boolean i){effect=e;duration=d;amp=a;ambient=am;visible=v;icon=i;}
 public int getDuration(){return duration;} public int getAmplifier(){return amp;}
 public boolean isAmbient(){return ambient;} public boolean isVisible(){return visible;} public boolean showIcon(){return icon;}
 public net.minecraft.core.Holder<MobEffect> getEffect(){return effect;}
 // 1.21.1 update: same-level replacement cannot shorten an existing duration.
 public void merge(MobEffectInstance n){if(n.amp==amp && duration!=-1 && (n.duration==-1 || n.duration>duration))duration=n.duration;}
}''',
'net/minecraft/world/entity/LivingEntity.java': '''package net.minecraft.world.entity;
public class LivingEntity extends Entity {
 public java.util.Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>,net.minecraft.world.effect.MobEffectInstance> effects=new java.util.HashMap<>();
 public java.util.Collection<net.minecraft.world.effect.MobEffectInstance> getActiveEffects(){return effects.values();}
 public net.minecraft.world.effect.MobEffectInstance getEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h){return effects.get(h);}
 public void removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h){effects.remove(h);}
 public void addEffect(net.minecraft.world.effect.MobEffectInstance f){var old=effects.get(f.effect);if(old==null)effects.put(f.effect,f);else old.merge(f);}
}''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity {
 public net.guzhenren.network.GuzhenrenModVariables.PlayerVariables variables=new net.guzhenren.network.GuzhenrenModVariables.PlayerVariables();
 public <T> T getData(net.guzhenren.network.GuzhenrenModVariables.Key<T> key){return (T)variables;}
}''',
'net/minecraft/server/level/ServerPlayer.java': '''package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {
 public java.util.Set<String> ids=java.util.Set.of(); public boolean confirmed=true;
 public java.util.Map<String,Long> longs=new java.util.HashMap<>();
 public java.util.Map<String,Double> doubles=new java.util.HashMap<>(); public int tickCount=101;
}''',
'net/minecraft/world/damagesource/DamageSource.java': '''package net.minecraft.world.damagesource;
public record DamageSource(net.minecraft.world.entity.Entity entity){public net.minecraft.world.entity.Entity getEntity(){return entity;}}''',
'net/neoforged/neoforge/event/entity/living/LivingDeathEvent.java': '''package net.neoforged.neoforge.event.entity.living;
public record LivingDeathEvent(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.damagesource.DamageSource source){
 public net.minecraft.world.entity.LivingEntity getEntity(){return entity;} public net.minecraft.world.damagesource.DamageSource getSource(){return source;}
}''',
'net/guzhenren/network/GuzhenrenModVariables.java': '''package net.guzhenren.network;
public class GuzhenrenModVariables {
 public record Key<T>(){} public static Key<PlayerVariables> PLAYER_VARIABLES=new Key<>();
 public static class PlayerVariables {
  public double tizhi,zhenyuan=10,zuida_zhenyuan=100,niantou=10,niantou_rongliang=200,cd;
  public double dahen_qidao,dahen_zhoudao,daohen_jindao,daohen_lvdao,daohen_tudao,daohen_xuedao,daohen_yudao;
  public double shouyuan=1000,kongqiao=7,qiyun=10,qiyun_shangxian=100,yuanshishuliang;
  public int dirty; public void markSyncDirty(){dirty++;}
 }
}''',
'net/guzhenren/init/GuzhenrenModMobEffects.java': '''package net.guzhenren.init;
public class GuzhenrenModMobEffects {public static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> CHENMO=new net.minecraft.core.Holder<>(new net.minecraft.world.effect.MobEffect());}''',
'net/guzhenren/tianfu/TianfuApply.java': '''package net.guzhenren.tianfu;
public class TianfuApply {public static java.util.Set<String> effective(net.minecraft.server.level.ServerPlayer p){return p.confirmed?p.ids:java.util.Set.of();}}''',
'net/guzhenren/tianfu/TianfuData.java': '''package net.guzhenren.tianfu;
import net.minecraft.server.level.ServerPlayer;
public class TianfuData {
 public static int slay(ServerPlayer p){return (int)getLong(p,"slay",0);} public static void setSlay(ServerPlayer p,int n){putLong(p,"slay",n);}
 public static void addSlay(ServerPlayer p,int n){setSlay(p,slay(p)+n);}
 public static long getLong(ServerPlayer p,String k,long d){return p.longs.getOrDefault(k,d);} public static void putLong(ServerPlayer p,String k,long v){p.longs.put(k,v);}
 public static double getDouble(ServerPlayer p,String k,double d){return p.doubles.getOrDefault(k,d);} public static void putDouble(ServerPlayer p,String k,double v){p.doubles.put(k,v);}
 public static long slayDay(ServerPlayer p){return getLong(p,"day",0);} public static int slayDaily(ServerPlayer p){return (int)getLong(p,"daily",0);}
 public static long lastKill(ServerPlayer p){return getLong(p,"lastkill",0);}
 public static void setSlayProgress(ServerPlayer p,long k,long d,int n){putLong(p,"lastkill",k);putLong(p,"day",d);putLong(p,"daily",n);}
 public static String getType(ServerPlayer p){return "";} public static int getStreak(ServerPlayer p){return 0;} public static void setTypeStreak(ServerPlayer p,String s,int n){}
 public static long devourDay(ServerPlayer p){return 0;} public static int devourDaily(ServerPlayer p){return 0;} public static void setDevourProgress(ServerPlayer p,long d,int n){}
}''',
'net/guzhenren/tianfu/TianfuAttributes.java': '''package net.guzhenren.tianfu;
public class TianfuAttributes {public static void sync(net.minecraft.server.level.ServerPlayer p){}}''',
'net/guzhenren/tianfu/v2/TianfuSelection.java': '''package net.guzhenren.tianfu.v2;
public class TianfuSelection {
 public enum Rejection {UNVERIFIED_EFFECT,UNSAFE_LIFESPAN,NONE}
 public record Origin(String regionId,String backgroundId){}
 public record Snapshot(boolean confirmed,java.util.Set<String> purchased,Origin origin){}
}''',
'net/guzhenren/tianfu/v2/TianfuOriginTable.java': '''package net.guzhenren.tianfu.v2;
public class TianfuOriginTable {
 public record Region(String id,java.util.Map<String,Double> daohen){}
 public record Background(String id,double luckCapBonus,double yuanshiBonus){}
 public static java.util.List<Region> REGIONS=java.util.List.of();public static java.util.List<Background> BACKGROUNDS=java.util.List.of();
}''',
'net/guzhenren/tianfu/v2/TianfuV2Store.java': '''package net.guzhenren.tianfu.v2;
public class TianfuV2Store {public static TianfuSelection.Snapshot read(net.minecraft.server.level.ServerPlayer p){return new TianfuSelection.Snapshot(p.confirmed,p.ids,null);}}''',
}

HARNESS = '''package net.guzhenren.tianfu;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.init.GuzhenrenModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
public class RuntimeRegression {
 static int failed;
 static void check(String n,boolean ok){System.out.println((ok?"PASS ":"FAIL ")+n);if(!ok)failed++;}
 static double cooldown(ServerPlayer player,Set<String> ids){PlayerVariables v=player.variables; boolean touched=false;
 __COOLDOWN__
 return v.cd;
 }
 static void silence(ServerPlayer player,Set<String> ids){
 __SILENCE__
 }
 static void tickV2(ServerPlayer player){PlayerVariables v=player.variables;boolean touched=false;Set<String> ids=player.ids;
 __TICKTAIL__
 __MAINTAINV2__
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
}'''

def run(label, source):
    src = HERE / ('test-' + label) / 'src'
    out = src.parent / 'classes'
    files = dict(STUBS)
    talents = (ORIGINAL / 'net/guzhenren/tianfu/TianfuTalents.java').read_text(encoding='utf-8')
    rates = {}
    for id in ['v2_gu_cd_ningse','v2_mind_cooldown_1']:
        rates[id] = re.search(r't\("' + id + r'"[^\n]+f\(Op.GU_CD, ([-.0-9]+)\)', talents).group(1)
    files['net/guzhenren/tianfu/TianfuTalents.java'] = '''package net.guzhenren.tianfu;
public class TianfuTalents { public static double guCd(java.util.Set<String> ids){double n=0;
''' + ''.join(f'if(ids.contains("{k}"))n+={v};\n' for k,v in rates.items()) + 'return n;} public static void maintainEffects(net.minecraft.server.level.ServerPlayer p,java.util.Set<String> ids){}}'
    files['net/guzhenren/tianfu/TianfuSlay.java'] = (source / 'net/guzhenren/tianfu/TianfuSlay.java').read_text(encoding='utf-8')
    effects = (source / 'net/guzhenren/tianfu/TianfuEffects.java').read_text(encoding='utf-8')
    cooldown = effects[effects.index('        double guCd ='):effects.index('        double shazhaoCd =')]
    silence = effects[effects.index('        if (ids.contains("xinshen_ruyuan"))'):effects.index('        // ---- 御空踏云体')]
    ticktail = effects[effects.index('        // ---- 周期性重建属性修正'):effects.index('    // ---------------------------------------------------------------- 万法皆通')]
    maintain_start = effects.index('    public static void maintainV2(')
    maintain_brace = effects.index('{',maintain_start)
    depth=1
    maintain_end=maintain_brace+1
    while depth:
        depth += (effects[maintain_end]=='{')-(effects[maintain_end]=='}')
        maintain_end+=1
    maintain=effects[maintain_start:maintain_end]
    files['net/guzhenren/tianfu/RuntimeRegression.java'] = HARNESS.replace('__COOLDOWN__',cooldown).replace('__SILENCE__',silence).replace('__TICKTAIL__',ticktail).replace('__MAINTAINV2__',maintain)
    for rel in ['net/guzhenren/tianfu/v2/VersionAdapter.java','net/guzhenren/tianfu/v2/NeoForge1211Adapter.java']:
        candidate=source/rel
        if not candidate.exists():
            candidate=ORIGINAL/rel
        files[rel]=candidate.read_text(encoding='utf-8')
    for rel, body in files.items():
        dest = src / rel
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(body,encoding='utf-8')
    out.mkdir(parents=True,exist_ok=True)
    argfile = src.parent / 'args.txt'
    argfile.write_text('-encoding\nUTF-8\n-proc:none\n-d\n"' + out.as_posix() + '"\n' + '\n'.join('"' + p.as_posix() + '"' for p in src.rglob('*.java')),encoding='utf-8')
    built = subprocess.run([str(JAVA / 'javac.exe'),'@'+str(argfile)],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if built.returncode:
        raise RuntimeError(built.stdout+built.stderr)
    result = subprocess.run([str(JAVA / 'java.exe'),'-cp',str(out),'net.guzhenren.tianfu.RuntimeRegression'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(label.upper()+'\n'+result.stdout+result.stderr)
    (HERE / ('regression-' + label + '.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
    return result

if __name__ == '__main__':
    old = run('original',ORIGINAL)
    for name in ['negative cooldown penalty','silence duration halves','shiyuan works without slay or tuntian']:
        assert 'FAIL '+name in old.stdout, 'Original did not reproduce: '+name
    new = run('patched',HERE / 'patched-src')
    assert new.returncode==0, 'Patched regression failed'
    print('Original reproduced runtime bugs; patched passed all cases.')
