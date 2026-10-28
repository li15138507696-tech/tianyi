package net.guzhenren.network;
public class GuzhenrenModVariables {
 public static final java.util.function.Supplier<PlayerVariables> PLAYER_VARIABLES=PlayerVariables::new;
 public static class PlayerVariables {
  public double kongqiao=2,tizhi=0,shouyuan=1000,total=0; public int sync;
  public void markSyncDirty(){sync++;}
  public net.minecraft.nbt.CompoundTag serializeNBT(Object registry){var n=new net.minecraft.nbt.CompoundTag();n.putDouble("q",kongqiao);n.putDouble("t",tizhi);n.putDouble("s",shouyuan);n.putDouble("a",total);return n;}
  public void deserializeNBT(Object registry,net.minecraft.nbt.CompoundTag n){kongqiao=n.getDouble("q");tizhi=n.getDouble("t");shouyuan=n.getDouble("s");total=n.getDouble("a");}
 }
}