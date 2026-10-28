package net.guzhenren.tianfu;
public class TianfuTalents {
 public record Talent(String id,int cost){}
 private static final java.util.List<Talent> ALL=java.util.List.of(new Talent("neg",-2),new Talent("positive",2),new Talent("v2_lifespan_scar",-8));
 public static java.util.List<Talent> all(){return ALL;}
 public static Talent byId(String id){return ALL.stream().filter(t->t.id().equals(id)).findFirst().orElse(null);}
 public static TianfuPool.Spec byIdIncludingUnavailable(String id){var t=byId(id);return t==null?null:new TianfuPool.Spec(t.id(),t.id(),TianfuPool.Kind.ENTRY,"黄",t.cost(),0,"");}
}