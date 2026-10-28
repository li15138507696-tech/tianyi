package net.guzhenren.tianfu;
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
}