package net.guzhenren.tianfu;
public class TianfuTalents { public static double guCd(java.util.Set<String> ids){double n=0;
if(ids.contains("v2_gu_cd_ningse"))n+=-0.10;
if(ids.contains("v2_mind_cooldown_1"))n+=0.15;
return n;} public static void maintainEffects(net.minecraft.server.level.ServerPlayer p,java.util.Set<String> ids){}}