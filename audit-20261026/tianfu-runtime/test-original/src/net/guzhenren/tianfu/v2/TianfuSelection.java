package net.guzhenren.tianfu.v2;
public class TianfuSelection {
 public enum Rejection {UNVERIFIED_EFFECT,UNSAFE_LIFESPAN,NONE}
 public record Origin(String regionId,String backgroundId){}
 public record Snapshot(boolean confirmed,java.util.Set<String> purchased,Origin origin){}
}