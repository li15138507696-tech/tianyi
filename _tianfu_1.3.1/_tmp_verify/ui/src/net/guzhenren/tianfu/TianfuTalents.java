package net.guzhenren.tianfu;
public class TianfuTalents {
public record Talent(String id,String name,String quality,int cost,String desc){}
private static final java.util.List<Talent> ALL=java.util.List.of(new Talent("v2_mind_xiulian_1","修炼速度 I","玄",2,"修炼速度 +20%。这是完整效果说明，用于检查详情区域按实际内容滚动，长说明不会在三行后丢失。"),new Talent("v2_mind_xiulian_2","修炼速度 II","地",3,"修炼速度 +40%"),new Talent("v2_lifespan_scar","命烛残尽","负",-8,"确认时寿元减少400，且本世寿元增加无效；寿元不足不得确认。"));
public static java.util.List<Talent> all(){return ALL;}public static Talent byId(String id){return ALL.stream().filter(t->t.id().equals(id)).findFirst().orElse(null);}public static boolean isAvailable(String id){return !id.equals("malice_undying");}
public static java.util.List<TianfuPool.Spec> specs(){return ALL.stream().map(TianfuTalents::spec).toList();}public static java.util.List<TianfuPool.Spec> maliceSpecs(){return java.util.List.of();}
private static TianfuPool.Spec spec(Talent t){return new TianfuPool.Spec(t.id(),t.name(),TianfuPool.Kind.ENTRY,t.quality(),t.cost(),0,t.desc());}public static TianfuPool.Spec byIdIncludingUnavailable(String id){var t=byId(id);return t==null?null:spec(t);}}
