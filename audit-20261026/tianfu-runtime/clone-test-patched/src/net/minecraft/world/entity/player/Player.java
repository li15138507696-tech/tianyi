package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity {
 public net.guzhenren.network.GuzhenrenModVariables.PlayerVariables variables=new net.guzhenren.network.GuzhenrenModVariables.PlayerVariables();
 public <T> T getData(net.guzhenren.network.GuzhenrenModVariables.Key<T> key){return (T)variables;}
}