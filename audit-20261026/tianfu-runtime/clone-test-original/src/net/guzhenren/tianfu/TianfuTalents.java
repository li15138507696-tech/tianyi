package net.guzhenren.tianfu;
public class TianfuTalents {
 public static double counterBonus(java.util.Set<String> ids){return ids.contains("counter")?.35:0;}
 public static int counterTicks(java.util.Set<String> ids){return 100;}
}