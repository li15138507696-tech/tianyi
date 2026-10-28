"""Compile unchanged production Java against minimal Minecraft test doubles.

No original project, game directory, or original jar is written. Run without
arguments for the baseline; pass --fixed to overlay patched-src.
"""
from pathlib import Path
import argparse
import subprocess
import shutil
import sys

sys.stdout.reconfigure(encoding='utf-8')
HERE = Path(__file__).resolve().parent
ORIGINAL = HERE.parent / 'baseline-src/daotu'
JDK = Path(r'C:\Users\ADMIN\.jdks\temurin-21\bin')
parser = argparse.ArgumentParser()
parser.add_argument('--fixed', action='store_true')
args = parser.parse_args()
WORK = HERE / ('regression-fixed' if args.fixed else 'regression-baseline')
WORK.mkdir(parents=True, exist_ok=True)
SRC = WORK / 'src'
CLASSES = WORK / 'classes'
CLASSES.mkdir(exist_ok=True)

def write(path, text):
    p = SRC / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8', newline='\n')

STUBS = {
'net/minecraft/nbt/Tag.java': '''package net.minecraft.nbt;
public class Tag { public static final int TAG_BYTE=1,TAG_INT=3,TAG_LONG=4,TAG_DOUBLE=6,TAG_STRING=8,TAG_LIST=9,TAG_COMPOUND=10; }
''',
'net/minecraft/nbt/StringTag.java': '''package net.minecraft.nbt;
public class StringTag extends Tag { final String value; private StringTag(String v){value=v;} public static StringTag valueOf(String v){return new StringTag(v);} }
''',
'net/minecraft/nbt/ListTag.java': '''package net.minecraft.nbt;
public class ListTag extends Tag { final java.util.List<Tag> values=new java.util.ArrayList<>(); public void add(Tag t){values.add(t);} public int size(){return values.size();} public void remove(int i){values.remove(i);} public String getString(int i){return ((StringTag)values.get(i)).value;} public ListTag copy(){ListTag out=new ListTag(); for(Tag t:values)out.add(t instanceof CompoundTag c?c.copy():t);return out;} }
''',
'net/minecraft/nbt/CompoundTag.java': '''package net.minecraft.nbt;
public class CompoundTag extends Tag {
 final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean contains(String k,int kind){Object v=values.get(k);return switch(kind){case TAG_COMPOUND->v instanceof CompoundTag;case TAG_LIST->v instanceof ListTag;case TAG_STRING->v instanceof String;case TAG_BYTE->v instanceof Boolean;case TAG_DOUBLE->v instanceof Double;default->v!=null;};}
 public boolean contains(String k){return values.containsKey(k);} public void put(String k,Tag v){values.put(k,v);} public void remove(String k){values.remove(k);} public CompoundTag getCompound(String k){return values.get(k) instanceof CompoundTag c?c:new CompoundTag();}
 public ListTag getList(String k,int kind){return values.get(k) instanceof ListTag l?l:new ListTag();}
 public void putDouble(String k,double v){values.put(k,v);} public double getDouble(String k){return values.get(k) instanceof Number n?n.doubleValue():0;}
 public void putInt(String k,int v){values.put(k,v);} public int getInt(String k){return values.get(k) instanceof Number n?n.intValue():0;}
 public void putLong(String k,long v){values.put(k,v);} public long getLong(String k){return values.get(k) instanceof Number n?n.longValue():0;}
 public void putString(String k,String v){values.put(k,v);} public String getString(String k){return values.get(k) instanceof String s?s:"";}
 public void putBoolean(String k,boolean v){values.put(k,v);} public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));} public java.util.Set<String> getAllKeys(){return values.keySet();}
 public CompoundTag copy(){CompoundTag out=new CompoundTag();values.forEach((k,v)->out.values.put(k,v instanceof CompoundTag c?c.copy():v instanceof ListTag l?l.copy():v));return out;}
}
''',
'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;
public record Component(String text){public static Component literal(String s){return new Component(s);}public String getString(){return text;}}
''',
'net/minecraft/util/RandomSource.java': '''package net.minecraft.util; public class RandomSource extends java.util.Random {}''',
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core; public record BlockPos(int x,int y,int z){public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}}''',
'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys; public class Vec3 {public double horizontalDistanceSqr(){return 0;}}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
public class Level {public boolean isClientSide(){return false;}public long getGameTime(){return 20;}public Biome getBiome(net.minecraft.core.BlockPos p){return new Biome();} public Key dimension(){return new Key();} public static class Biome{public java.util.Optional<Key> unwrapKey(){return java.util.Optional.of(new Key());}}public static class Key{public String location(){return "minecraft:plains";}}}
''',
'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity; public class Entity {public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}}''',
'net/minecraft/world/entity/LivingEntity.java': '''package net.minecraft.world.entity; public class LivingEntity extends Entity {public float getMaxHealth(){return 20;}public net.minecraft.network.chat.Component getName(){return net.minecraft.network.chat.Component.literal("test");}}''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity {private final net.minecraft.nbt.CompoundTag data=new net.minecraft.nbt.CompoundTag();public final net.guzhenren.network.GuzhenrenModVariables.PlayerVariables vars=new net.guzhenren.network.GuzhenrenModVariables.PlayerVariables();public net.minecraft.nbt.CompoundTag getPersistentData(){return data;}public net.guzhenren.network.GuzhenrenModVariables.PlayerVariables getData(Object ignored){return vars;}public net.minecraft.util.RandomSource getRandom(){return new net.minecraft.util.RandomSource();}}
''',
'net/minecraft/server/MinecraftServer.java': '''package net.minecraft.server;
public class MinecraftServer {public boolean isSameThread(){return true;}public final Board board=new Board();public Board getScoreboard(){return board;}public static class Board {public int score;public Object getObjective(String s){return new Object();}public Score getOrCreatePlayerScore(Object p,Object o){return new Score(score);} }public record Score(int value){public int get(){return value;}}}
''',
'net/minecraft/server/level/ServerPlayer.java': '''package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {public int tickCount;public final net.minecraft.server.MinecraftServer server=new net.minecraft.server.MinecraftServer();public java.util.UUID id=java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");public net.minecraft.server.MinecraftServer getServer(){return server;}public java.util.UUID getUUID(){return id;}public void displayClientMessage(net.minecraft.network.chat.Component c,boolean a){}public net.minecraft.core.BlockPos blockPosition(){return new net.minecraft.core.BlockPos(0,64,0);}public boolean isShiftKeyDown(){return false;}public net.minecraft.world.phys.Vec3 getDeltaMovement(){return new net.minecraft.world.phys.Vec3();}public boolean onGround(){return true;}}
''',
'net/minecraft/commands/CommandSourceStack.java': '''package net.minecraft.commands; public class CommandSourceStack {public net.minecraft.server.level.ServerPlayer getPlayer(){return null;}public void sendFailure(net.minecraft.network.chat.Component c){}public void sendSuccess(java.util.function.Supplier<net.minecraft.network.chat.Component> s,boolean b){}}''',
'net/guzhenren/network/GuzhenrenModVariables.java': '''package net.guzhenren.network;public class GuzhenrenModVariables{public static final Object PLAYER_VARIABLES=new Object();public static class PlayerVariables{public double zhuanshu=1;public void markSyncDirty(){}}}''',
'net/guzhenren/daotu/DaotuMod.java': '''package net.guzhenren.daotu; public class DaotuMod{public static final Log LOGGER=new Log();public static class Log{public void warn(String s,Object...a){}public void debug(String s,Object...a){}}}''',
'net/guzhenren/daotu/DaotuAttributes.java': '''package net.guzhenren.daotu;public class DaotuAttributes{public static void sync(net.minecraft.world.entity.player.Player p){}}''',
'net/guzhenren/daotu/DaotuNet.java': '''package net.guzhenren.daotu;public class DaotuNet{public static int prompts;public static void promptChoice(net.minecraft.server.level.ServerPlayer p){prompts++;}public static void sendState(net.minecraft.server.level.ServerPlayer p){}}''',
'net/guzhenren/daotu/DaotuGrowthApply.java': '''package net.guzhenren.daotu;public class DaotuGrowthApply{public static void refreshTitles(net.minecraft.world.entity.player.Player p){}public static boolean subDaohen(net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,String s,double d){return true;}}''',
'net/guzhenren/daotu/DaotuRunAttributes.java': '''package net.guzhenren.daotu;public class DaotuRunAttributes{public static void grant(net.minecraft.world.entity.player.Player p,String k,double d,boolean b){DaotuRun.setAttr(p,k,DaotuRun.attr(p,k)+d);}}''',
'net/guzhenren/daotu/DaotuDrawTable.java': '''package net.guzhenren.daotu;public class DaotuDrawTable{public static final String TALENT_PREFIX="talent:";public record Candidate(String key,String label,double amount,boolean percent){public boolean isTalent(){return key.startsWith(TALENT_PREFIX);}public String talentId(){return isTalent()?key.substring(TALENT_PREFIX.length()):null;}}public static double zhuanScale(double d){return 1;}public static java.util.List<Candidate> candidates(net.minecraft.world.entity.player.Player p,int i,net.minecraft.util.RandomSource r){return java.util.List.of(new Candidate("attack_damage","力量",0.03,true));}}''',
'net/guzhenren/daotu/DaotuTalentApply.java': '''package net.guzhenren.daotu;public class DaotuTalentApply{public static void apply(net.minecraft.server.level.ServerPlayer p,DaotuTalent.Entry e,double d){}public static void markOwned(net.minecraft.server.level.ServerPlayer p,String s){}}''',
'net/guzhenren/daotu/DaotuSpecial.java': '''package net.guzhenren.daotu;public class DaotuSpecial{public static boolean isSpecial(String s){return s.startsWith("special:");}public static String labelOf(String s){return s;}public static boolean grant(net.minecraft.server.level.ServerPlayer p,String s,double d){return true;}public static java.util.List<DaotuDrawTable.Candidate> candidates(net.minecraft.world.entity.player.Player p,int i,net.minecraft.util.RandomSource r){return DaotuDrawTable.candidates(p,i,r);}}''',
}
for entity in ['monster/Monster','boss/enderdragon/EnderDragon','boss/wither/WitherBoss']:
    pkg, name = entity.rsplit('/', 1)
    STUBS[f'net/minecraft/world/entity/{entity}.java'] = f'package net.minecraft.world.entity.{pkg.replace("/", ".")}; public class {name} extends net.minecraft.world.entity.LivingEntity {{}}'
for path, code in STUBS.items():
    write(path, code)

production = ['DaotuData','DaotuRun','DaotuMainDao','DaotuOpening','DaotuLiLian','DaotuDraw','DaotuMigrate','DaotuTalent']
production += ['chronicle/' + x for x in ['ChronicleModels','ChronicleTables','ChronicleModule','ChronicleRepository','CoreVersionAdapter','NbtChronicleRepository','ChronicleCoordinator','FactionJournalModule','MeritArchiveModule','ReputationJournalModule']]
for name in production:
    rel = 'src/main/java/net/guzhenren/daotu/' + name + '.java'
    overlay = HERE / 'patched-src' / rel
    source = overlay if args.fixed and overlay.exists() else ORIGINAL / rel
    write('net/guzhenren/daotu/' + name + '.java', source.read_text(encoding='utf-8-sig'))

write('net/guzhenren/daotu/AuditRegression.java', r'''package net.guzhenren.daotu;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.daotu.chronicle.*;
import net.guzhenren.daotu.chronicle.ChronicleModels.*;

public class AuditRegression {
 static int failures=0,checks=0;
 static void check(boolean value,String label){checks++;if(!value){failures++;System.out.println("FAIL "+label);}else System.out.println("PASS "+label);}
 static ServerPlayer opened(){ServerPlayer p=new ServerPlayer();p.server.board.score=1;return p;}
 static void cloneData(){ServerPlayer p=opened();DaotuRun.setAttr(p,"max_health",.5);DaotuRun.setDraws(p,12);DaotuRun.addLedger(p,"daohen_jiandao",100);DaotuMainDao.set(p,"jiandao");DaotuRun.setAutoPrompt(p,false);DaotuRun.setOffer(p,13,"attack_damage=0.03=1");ServerPlayer n=opened();DaotuOpening.onClone(n,p);check(DaotuRun.draws(n)==12,"clone retains draw count");check(DaotuRun.attr(n,"max_health")==.5,"clone retains attrs");check(DaotuRun.ledger(n).size()==1,"clone retains rollback ledger");check(DaotuMainDao.of(n).equals("jiandao"),"clone retains main dao");check(!DaotuRun.autoPrompt(n),"clone retains popup preference");check(DaotuRun.offerDraw(n)==13,"clone retains pending choice");DaotuRun.setAttr(n,"max_health",.8);check(DaotuRun.attr(p,"max_health")==.5,"clone deep copy does not alias original");}
 static void openingGate(){ServerPlayer p=new ServerPlayer();DaotuLiLian.award(p,1000,0,0,null);check(DaotuRun.pool(p)==0,"unopened combat earns no pool");DaotuRun.setPool(p,1000);DaotuRun.clearOffer(p);check(DaotuDraw.tryDraw(p)==0&&DaotuRun.offer(p).isEmpty(),"unopened draw produces no offer");DaotuRun.setOffer(p,1,"attack_damage=0.03=1");double before=DaotuRun.pool(p);check(!DaotuDraw.choose(p,0)&&DaotuRun.pool(p)==before&&DaotuRun.draws(p)==0,"unopened forged choice cannot spend/grant");ServerPlayer q=opened();DaotuLiLian.award(q,144,0,0,null);check(DaotuRun.pool(q)==144&&!DaotuRun.offer(q).isEmpty(),"opened player still gains pool and choice");check(DaotuDraw.choose(q,0)&&DaotuRun.draws(q)==1&&DaotuRun.pool(q)==0,"opened player chooses normally");}
 static void cap(){for(int i=1;i<=5;i++)check(DaotuDraw.capOf(i)==100,"rank "+i+" caps at 100");check(DaotuDraw.capOf(6)==Integer.MAX_VALUE,"rank 6 remains unlimited");ServerPlayer p=opened();p.vars.zhuanshu=5;DaotuRun.setDraws(p,100);DaotuLiLian.award(p,1000,0,0,null);check(DaotuRun.pool(p)==0&&DaotuRun.offer(p).isEmpty(),"rank 5 at cap cannot accrue or draw");}
 static void migration(){ServerPlayer p=opened();DaotuData.putString(p,"origin","guyue_dixi");check(DaotuMigrate.migrateIfNeeded(p),"legacy migration runs");check(DaotuRun.draws(p)==8,"8 migration rewards count as 8 draws");check(DaotuRun.history(p).size()==8&&DaotuRun.history(p).get(0).startsWith("1|"),"migration history starts at draw 1");check(!DaotuMigrate.migrateIfNeeded(p)&&DaotuRun.draws(p)==8,"migration is idempotent");}
 static void labels(){DaotuTalent.Entry entry=DaotuTalent.all().get(0);String key="talent:"+entry.id();String expected=entry.name()+(entry.summary().isEmpty()?"":" · "+entry.summary());check(DaotuDraw.labelOf(key).equals(expected),"talent candidate restores Chinese label");ServerPlayer p=opened();DaotuRun.setOffer(p,1,key+"=0.0=0");check(DaotuDraw.pendingOffer(p).get(0).label().equals(expected),"stored offer decodes with Chinese label");}
 static void repository(){ServerPlayer p=opened();NbtChronicleRepository repo=new NbtChronicleRepository();ChronicleState in=new ChronicleState(p.id,1,0,2,new FactionJournal(Set.of("pair"),"f","i",20),new MeritArchive(40,40,Set.of("merit_first","merit_record"),20),new ReputationJournal(1000,1000,Set.of("reputation_known","reputation_renowned"),20));check(repo.save(p,-1,in),"chronicle first commit");ChronicleState out=repo.load(p).orElseThrow();check(out.merit().unlockedEntries().equals(in.merit().unlockedEntries()),"merit unlocked round trip");check(out.reputation().unlockedEntries().equals(in.reputation().unlockedEntries()),"reputation unlocked round trip");check(out.faction().discoveredPairs().equals(Set.of("pair")),"faction pairs round trip");ServerPlayer n=opened();repo.copyForClone(p,n);check(repo.load(n).orElseThrow().merit().unlockedEntries().equals(in.merit().unlockedEntries()),"chronicle clone retains merit achievements");}
 static class MemoryRepo implements ChronicleRepository{ChronicleState state;public Optional<ChronicleState> load(ServerPlayer p){return Optional.ofNullable(state);}public boolean save(ServerPlayer p,long expected,ChronicleState n){state=n;return true;}public void copyForClone(ServerPlayer a,ServerPlayer b){}}
 static void newRun(){ServerPlayer p=opened();MemoryRepo repo=new MemoryRepo();repo.state=new ChronicleState(p.id,1,7,0,new FactionJournal(Set.of(),"f","i",20),new MeritArchive(70,70,Set.of(),20),new ReputationJournal(1000,1000,Set.of(),20));CoreVersionAdapter adapter=q->Optional.of(new CoreSnapshot(p.id,40,1,true,"f","i",70,1000));ChronicleCoordinator c=new ChronicleCoordinator(adapter,repo,List.of(new FactionJournalModule(),new MeritArchiveModule(),new ReputationJournalModule()));c.observe(p);check(repo.state.runId()==1,"new run committed after all modules");check(repo.state.revision()==8,"one revision per sample");check(repo.state.merit().unlockedEntries().isEmpty(),"merit new run establishes baseline only");check(repo.state.reputation().unlockedEntries().isEmpty(),"reputation new run establishes baseline only");}
 static void baselinesAndCrossings(){ServerPlayer p=opened();MemoryRepo repo=new MemoryRepo();final CoreSnapshot[] snap={new CoreSnapshot(p.id,20,0,true,"f","i",40,1000)};ChronicleCoordinator c=new ChronicleCoordinator(q->Optional.of(snap[0]),repo,List.of(new FactionJournalModule(),new MeritArchiveModule(),new ReputationJournalModule()));c.observe(p);check(repo.state.merit().unlockedEntries().isEmpty()&&repo.state.reputation().unlockedEntries().isEmpty(),"first sample establishes counter baselines only");snap[0]=new CoreSnapshot(p.id,40,0,true,"f","i",40,1000);c.observe(p);check(repo.state.merit().unlockedEntries().isEmpty(),"unchanged baseline never backfills old merit milestones");check(repo.state.reputation().unlockedEntries().isEmpty(),"unchanged baseline never backfills old reputation milestones");snap[0]=new CoreSnapshot(p.id,60,0,true,"f","i",70,5000);c.observe(p);check(repo.state.merit().unlockedEntries().equals(Set.of("merit_legacy")),"new peak unlocks only newly crossed merit milestone");check(repo.state.reputation().unlockedEntries().equals(Set.of("reputation_legend")),"new peak unlocks only newly crossed reputation milestone");MemoryRepo neg=new MemoryRepo();final CoreSnapshot[] ns={new CoreSnapshot(p.id,20,0,true,"f","i",0,-100)};ChronicleCoordinator nc=new ChronicleCoordinator(q->Optional.of(ns[0]),neg,List.of(new FactionJournalModule(),new MeritArchiveModule(),new ReputationJournalModule()));nc.observe(p);check(neg.state.reputation().peakObserved()==-100,"negative initial reputation keeps real peak");ns[0]=new CoreSnapshot(p.id,40,0,true,"f","i",10,100);nc.observe(p);check(neg.state.reputation().unlockedEntries().equals(Set.of("reputation_known")),"negative-to-positive crossing unlocks on first crossing sample");check(neg.state.merit().unlockedEntries().equals(Set.of("merit_first")),"normal first merit crossing unlocks");}
 public static void main(String[]a){cloneData();openingGate();cap();migration();labels();repository();newRun();baselinesAndCrossings();System.out.println("CHECKS="+checks+" FAILURES="+failures);if(failures>0)System.exit(1);}
}
''')

sources = sorted(SRC.rglob('*.java'))
argfile = WORK / 'javac.args'
argfile.write_text('-source\n21\n-target\n21\n-encoding\nUTF-8\n-proc:none\n-d\n"' + CLASSES.as_posix() + '"\n' + '\n'.join('"' + p.as_posix() + '"' for p in sources), encoding='utf-8')
compile_result = subprocess.run([str(JDK / 'javac.exe'), '@' + str(argfile)], capture_output=True, text=True, encoding='utf-8', errors='replace')
print(compile_result.stdout + compile_result.stderr)
if compile_result.returncode:
    sys.exit(compile_result.returncode)
result = subprocess.run([str(JDK / 'java.exe'), '-cp', str(CLASSES), 'net.guzhenren.daotu.AuditRegression'], capture_output=True, text=True, encoding='utf-8', errors='replace')
report = result.stdout + result.stderr
(WORK / 'results.txt').write_text(report, encoding='utf-8')
print(report)
sys.exit(result.returncode)
