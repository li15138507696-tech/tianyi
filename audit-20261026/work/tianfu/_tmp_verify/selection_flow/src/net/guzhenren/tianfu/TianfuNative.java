package net.guzhenren.tianfu;
public class TianfuNative {
 public static boolean forceStale;
 public static void applySijue(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,int n){v.tizhi=n;v.kongqiao=3;v.total+=2000;if(forceStale)p.getPersistentData().getCompound(TianfuData.ROOT).putLong("v2_revision",999);}
 public static void applyJiyuan(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,String id){v.kongqiao=2;}
}