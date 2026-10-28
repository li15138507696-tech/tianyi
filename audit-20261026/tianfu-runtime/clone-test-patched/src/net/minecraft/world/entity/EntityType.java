package net.minecraft.world.entity;
public class EntityType<T> {
 public net.minecraft.resources.ResourceLocation id;
 public java.util.Set<net.minecraft.tags.TagKey<?>> tags=new java.util.HashSet<>();
 public EntityType(String path){id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("guzhenren",path);}
 public boolean is(net.minecraft.tags.TagKey<?> tag){return tags.contains(tag);}
}