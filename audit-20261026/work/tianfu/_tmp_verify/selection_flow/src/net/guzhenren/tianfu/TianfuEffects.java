package net.guzhenren.tianfu;
public class TianfuEffects {
 public static int applied; public static void applyOnce(net.minecraft.server.level.ServerPlayer p,net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v,java.util.Set<String> ids,int body){applied++;v.total+=ids.size()*10;if(ids.contains("v2_lifespan_scar"))v.shouyuan-=400;}
}