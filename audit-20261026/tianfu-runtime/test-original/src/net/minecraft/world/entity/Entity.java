package net.minecraft.world.entity;
public class Entity {
 public EntityType<?> type=new EntityType<>("test"); private final java.util.UUID uuid=java.util.UUID.randomUUID();
 public EntityType<?> getType(){return type;} public java.util.UUID getUUID(){return uuid;}
 public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}
}