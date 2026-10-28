package net.guzhenren.tianfu;
public class TianfuData {
 public static final String ROOT="tianfu";
 public static boolean isConfirmed(net.minecraft.server.level.ServerPlayer p){return p.getPersistentData().getCompound(ROOT).getBoolean("confirmed");}
}