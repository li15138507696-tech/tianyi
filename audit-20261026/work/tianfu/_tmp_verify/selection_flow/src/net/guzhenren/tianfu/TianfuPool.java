package net.guzhenren.tianfu;
public class TianfuPool {
 public enum Kind {BODY,ENTRY,SIJUE,JIYUAN}
 public record Spec(String id,String name,Kind kind,String quality,int cost,int tizhi,String desc){}
 private static final java.util.Map<String,Spec> ALL=java.util.Map.of(
  "body",new Spec("body","体质",Kind.BODY,"凡体",3,24,""),
  "entry",new Spec("entry","词条",Kind.ENTRY,"黄",1,0,""),
  "sijue",new Spec("sijue","十绝体",Kind.SIJUE,"十绝体",16,1,""));
 public static Spec byId(String id){return ALL.get(id);}
 public static java.util.List<Spec> sijues(){return java.util.List.of(ALL.get("sijue"));}
 public static java.util.List<Spec> entries(){return java.util.List.of(ALL.get("entry"));}
}