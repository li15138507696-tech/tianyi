package net.minecraft.tags;
public record TagKey<T>(net.minecraft.resources.ResourceLocation id) {
 public static <T> TagKey<T> create(Object ignored,net.minecraft.resources.ResourceLocation id){return new TagKey<T>(id);}
}