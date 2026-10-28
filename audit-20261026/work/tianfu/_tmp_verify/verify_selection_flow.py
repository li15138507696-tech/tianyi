"""Run production handlers/economy/store/confirmation against explicit player/effect adapters.

Minecraft/NeoForge compile compatibility is checked separately by javac_check.py.
This probe does not start a game or claim native combat/UI runtime coverage.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '_tmp_verify/selection_flow'
SRC = OUT / 'src'
CLASSES = OUT / 'classes'
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')
args = (ROOT / '_crash3/javac_out/args.txt').read_text(encoding='utf-8').splitlines()
cp = args[args.index('-cp') + 1].strip('"')
cp = cp.replace('\\', '/')
stubs = {
'net/neoforged/neoforge/network/PacketDistributor.java': '''package net.neoforged.neoforge.network; public class PacketDistributor {public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,net.minecraft.network.protocol.common.custom.CustomPacketPayload payload,net.minecraft.network.protocol.common.custom.CustomPacketPayload... rest){}}''',
'net/minecraft/world/scores/Objective.java': '''package net.minecraft.world.scores; public record Objective(String name){}''',
'net/minecraft/world/scores/ScoreHolder.java': '''package net.minecraft.world.scores; public record ScoreHolder(String name){public static ScoreHolder forNameOnly(String n){return new ScoreHolder(n);}}''',
'net/minecraft/world/scores/ScoreAccess.java': '''package net.minecraft.world.scores; public class ScoreAccess{private int value;public void set(int v){value=v;}public int value(){return value;}}''',
'net/minecraft/world/scores/Scoreboard.java': '''package net.minecraft.world.scores;
public class Scoreboard {
 private final java.util.Map<String,Objective> objectives=new java.util.HashMap<>();
 private final java.util.Map<String,java.util.Map<Objective,ScoreAccess>> players=new java.util.HashMap<>();
 public java.util.Collection<Objective> getObjectives(){return objectives.values();}
 public Objective getObjective(String n){return objectives.get(n);}
 public Objective addObjective(String n,Object criteria,net.minecraft.network.chat.Component label,Object render,boolean auto,Object format){var o=new Objective(n);objectives.put(n,o);return o;}
 public ScoreAccess getPlayerScoreInfo(ScoreHolder p,Objective o){return players.getOrDefault(p.name(),java.util.Map.of()).get(o);}
 public ScoreAccess getOrCreatePlayerScore(ScoreHolder p,Objective o){return players.computeIfAbsent(p.name(),k->new java.util.HashMap<>()).computeIfAbsent(o,k->new ScoreAccess());}
 public void resetAllPlayerScores(ScoreHolder p){players.remove(p.name());}
}''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
public class Player {
 public boolean opened=true; public final net.minecraft.nbt.CompoundTag pd=new net.minecraft.nbt.CompoundTag();
 public final net.guzhenren.network.GuzhenrenModVariables.PlayerVariables vars=new net.guzhenren.network.GuzhenrenModVariables.PlayerVariables();
 private final net.minecraft.world.scores.Scoreboard board=new net.minecraft.world.scores.Scoreboard();
 public net.minecraft.nbt.CompoundTag getPersistentData(){return pd;}
 public <T> T getData(java.util.function.Supplier<T> supplier){return (T)vars;}
 public net.minecraft.world.scores.Scoreboard getScoreboard(){return board;}
 public String getScoreboardName(){return "probe";}
 public Object registryAccess(){return null;}
}''',
'net/minecraft/server/level/ServerPlayer.java': '''package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {
 public int messages; public java.util.Random getRandom(){return new java.util.Random(7);}
 public void displayClientMessage(net.minecraft.network.chat.Component c,boolean overlay){messages++;}
}''',
'net/guzhenren/network/GuzhenrenModVariables.java': '''package net.guzhenren.network;
public class GuzhenrenModVariables {
 public static final java.util.function.Supplier<PlayerVariables> PLAYER_VARIABLES=PlayerVariables::new;
 public static class PlayerVariables {
  public double kongqiao=2,tizhi=0,shouyuan=1000,total=0; public int sync;
  public void markSyncDirty(){sync++;}
  public net.minecraft.nbt.CompoundTag serializeNBT(Object registry){var n=new net.minecraft.nbt.CompoundTag();n.putDouble("q",kongqiao);n.putDouble("t",tizhi);n.putDouble("s",shouyuan);n.putDouble("a",total);return n;}
  public void deserializeNBT(Object registry,net.minecraft.nbt.CompoundTag n){kongqiao=n.getDouble("q");tizhi=n.getDouble("t");shouyuan=n.getDouble("s");total=n.getDouble("a");}
 }
}''',
'net/guzhenren/tianfu/TianfuMod.java': '''package net.guzhenren.tianfu;
public class TianfuMod { public static final String MODID="tianfu"; public static final Log LOGGER=new Log(); public static class Log {public void error(String s,Throwable t){}} }''',
'net/guzhenren/tianfu/TianfuOpening.java': '''package net.guzhenren.tianfu;
public class TianfuOpening {public static boolean hasOpenedQiao(net.minecraft.world.entity.player.Player p){return p.opened;} }''',
'net/guzhenren/tianfu/TianfuNet.java': '''package net.guzhenren.tianfu;
public class TianfuNet { public static int sent; public static void sendAll(net.minecraft.server.level.ServerPlayer p){sent++;} }''',
'net/guzhenren/tianfu/TianfuRoll.java': '''package net.guzhenren.tianfu; public class TianfuRoll { public static final int MAX_ROLLS=3; }''',
'net/guzhenren/tianfu/TianfuPool.java': '''package net.guzhenren.tianfu;
public class TianfuPool {
 public enum Kind {BODY,ENTRY,SIJUE,JIYUAN}
 public record Spec(String id,String name,Kind kind,String quality,int cost,int tizhi,String desc){}
 private static final java.util.Map<String,Spec> ALL=java.util.Map.of(
  "body",new Spec("body","体质",Kind.BODY,"凡体",3,24,""),
  "entry",new Spec("entry","词条",Kind.ENTRY,"黄",1,0,""),
  "sijue",new Spec("sijue","十绝体",Kind.SIJUE,"十绝体",16,1,""));
 public static Spec byId(String id){return ALL.get(id);}
 public static java.util.List<Spec> sijues(){return java.util.List.of(ALL.get("sijue"));}
 public static java.util.List<Spec> entries(){return java.util.List.of(ALL.get("entry"));}
}''',
'net/guzhenren/tianfu/TianfuTalents.java': '''package net.guzhenren.tianfu;
public class TianfuTalents {
 public record Talent(String id,int cost){}
 private static final java.util.List<Talent> ALL=java.util.List.of(new Talent("neg",-2),new Talent("positive",2),new Talent("v2_lifespan_scar",-8));
 public static java.util.List<Talent> all(){return ALL;}
 public static Talent byId(String id){return ALL.stream().filter(t->t.id().equals(id)).findFirst().orElse(null);}
 public static TianfuPool.Spec byIdIncludingUnavailable(String id){var t=byId(id);return t==null?null:new TianfuPool.Spec(t.id(),t.id(),TianfuPool.Kind.ENTRY,"黄",t.cost(),0,"");}
}''',
'net/guzhenren/tianfu/TianfuEffects.java': '''package net.guzhenren.tianfu;
public class TianfuEffects {
 public static int applied; public static void applyOnce(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,java.util.Set<String> ids,int body){applied++;v.total+=ids.size()*10;if(ids.contains("v2_lifespan_scar"))v.shouyuan-=400;}
}''',
'net/guzhenren/tianfu/TianfuDaohen.java': '''package net.guzhenren.tianfu;
public class TianfuDaohen {public static void write(net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,String id){v.total+=1000;}}''',
'net/guzhenren/tianfu/TianfuNative.java': '''package net.guzhenren.tianfu;
public class TianfuNative {
 public static boolean forceStale;
 public static void applySijue(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,int n){v.tizhi=n;v.kongqiao=3;v.total+=2000;if(forceStale)p.getPersistentData().getCompound(TianfuData.ROOT).putLong("v2_revision",999);}
 public static void applyJiyuan(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,String id){v.kongqiao=2;}
}''',
'net/guzhenren/tianfu/TianfuAttributes.java': '''package net.guzhenren.tianfu;
public class TianfuAttributes { public static int syncs; public static boolean fail; public static void sync(net.minecraft.world.entity.player.Player p){syncs++;if(fail)throw new IllegalStateException("attribute unavailable");} }''',
'net/guzhenren/tianfu/v2/NeoForge1211Adapter.java': '''package net.guzhenren.tianfu.v2;
public class NeoForge1211Adapter implements VersionAdapter {
 public static int preflights,settlements; public static boolean failFirst,failAfterBase,throwDuringSettle;
 public TianfuSelection.Rejection preflight(net.minecraft.server.level.ServerPlayer p,TianfuSelection.Snapshot s){preflights++;return failFirst||(failAfterBase&&p.vars.total>0)||(s.purchased().contains("v2_lifespan_scar")&&p.vars.shouyuan<=400)?TianfuSelection.Rejection.UNSAFE_LIFESPAN:TianfuSelection.Rejection.NONE;}
 public void settle(net.minecraft.server.level.ServerPlayer p,TianfuSelection.Snapshot s){settlements++;p.vars.total+=50;if(s.purchased().contains("v2_lifespan_scar"))p.vars.shouyuan-=400;net.guzhenren.tianfu.TianfuData.putBoolean(p,"during",true);if(throwDuringSettle){var b=p.getScoreboard();var o=b.getObjective("test");if(o==null)o=b.addObjective("test",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("test"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,true,null);b.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(p.getScoreboardName()),o).set(999);throw new IllegalStateException("simulated native failure");}}
 public void maintain(net.minecraft.server.level.ServerPlayer p,TianfuSelection.Snapshot s){}
 public long extraIncome(net.minecraft.server.level.ServerPlayer p,long n,String k){return 0;}
}''',
}
for name, text in stubs.items():
    target = SRC / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
production = ['TianfuApply.java','TianfuData.java','TianfuDifficulty.java',
              'v2/TianfuSelection.java','v2/TianfuV2Store.java','v2/TianfuLegacyPrices.java',
              'v2/TianfuEconomy.java','v2/TianfuMindTable.java','v2/TianfuOriginTable.java','v2/VersionAdapter.java',
              'net/TianfuRollPayload.java','net/TianfuRollAcceptPayload.java','net/TianfuConfirmPayload.java',
              'net/TianfuDifficultyPayload.java','net/TianfuV2StatePayload.java','net/TianfuOriginPayload.java','net/TianfuVfxPayload.java']
sources = list(SRC.rglob('*.java')) + [ROOT/'src/main/java/net/guzhenren/tianfu'/p for p in production]
sources += [ROOT/'_tmp_verify/SelectionFlowCheck.java']
CLASSES.mkdir(exist_ok=True)
argfile=OUT/'compile.txt'
argfile.write_text('-encoding\nUTF-8\n-proc:none\n-d\n"'+CLASSES.as_posix()+'"\n-cp\n"'+cp+'"\n' +
                   '\n'.join('"'+p.as_posix()+'"' for p in sources),encoding='utf-8')
subprocess.run([str(JDK/'javac.exe'),'@'+str(argfile)],check=True,cwd=ROOT)
subprocess.run([str(JDK/'java.exe'),'-Dfile.encoding=UTF-8','-cp',str(CLASSES)+';'+cp,'SelectionFlowCheck'] + __import__('sys').argv[1:],check=True,cwd=ROOT)
