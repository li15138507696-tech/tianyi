package net.guzhenren.tianfu.v2;
public class TianfuOriginTable {
 public record Region(String id,java.util.Map<String,Double> daohen){}
 public record Background(String id,double luckCapBonus,double yuanshiBonus){}
 public static java.util.List<Region> REGIONS=java.util.List.of();public static java.util.List<Background> BACKGROUNDS=java.util.List.of();
}