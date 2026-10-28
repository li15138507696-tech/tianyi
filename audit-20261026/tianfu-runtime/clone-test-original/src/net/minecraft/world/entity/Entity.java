package net.minecraft.world.entity;
public class Entity {
 public EntityType<?> type=new EntityType<>("test"); public java.util.UUID uuid=java.util.UUID.randomUUID();
 private net.minecraft.nbt.CompoundTag data=new net.minecraft.nbt.CompoundTag();
 public EntityType<?> getType(){return type;} public java.util.UUID getUUID(){return uuid;}
 public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}
 public net.minecraft.nbt.CompoundTag getPersistentData(){return data;}
}