package net.minecraft.core.registries;
public class BuiltInRegistries {public static final Types ENTITY_TYPE=new Types();
 public static class Types {public net.minecraft.resources.ResourceLocation getKey(net.minecraft.world.entity.EntityType<?> t){return t.id;}}
}