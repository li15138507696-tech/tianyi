package net.minecraft.resources;
public record ResourceLocation(String namespace,String path) {
 public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}
 public String getPath(){return path;}
}