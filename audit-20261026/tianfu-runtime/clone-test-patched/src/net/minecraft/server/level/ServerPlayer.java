package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {
 public java.util.Set<String> ids=java.util.Set.of(); public boolean confirmed=true;
 public java.util.Map<String,Long> longs=new java.util.HashMap<>();
 public java.util.Map<String,Double> doubles=new java.util.HashMap<>(); public int tickCount=101;
}