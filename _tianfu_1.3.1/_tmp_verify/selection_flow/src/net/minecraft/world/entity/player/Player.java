package net.minecraft.world.entity.player;
public class Player {
 public boolean opened=true; public final net.minecraft.nbt.CompoundTag pd=new net.minecraft.nbt.CompoundTag();
 public final net.guzhenren.network.GuzhenrenModVariables.PlayerVariables vars=new net.guzhenren.network.GuzhenrenModVariables.PlayerVariables();
 private final net.minecraft.world.scores.Scoreboard board=new net.minecraft.world.scores.Scoreboard();
 public net.minecraft.nbt.CompoundTag getPersistentData(){return pd;}
 public <T> T getData(java.util.function.Supplier<T> supplier){return (T)vars;}
 public net.minecraft.world.scores.Scoreboard getScoreboard(){return board;}
 public String getScoreboardName(){return "probe";}
 public Object registryAccess(){return null;}
}