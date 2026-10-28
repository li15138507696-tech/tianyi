
package net.minecraft.core; public record BlockPos(int x,int y,int z){
 public static BlockPos of(long p){return new BlockPos((int)(p>>38),(int)(p<<52>>52),(int)(p<<26>>38));}
 public int getX(){return x;} public int getZ(){return z;}
}