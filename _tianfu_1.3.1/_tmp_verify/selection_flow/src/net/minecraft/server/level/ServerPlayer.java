package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {
 public int messages; public java.util.Random getRandom(){return new java.util.Random(7);}
 public void displayClientMessage(net.minecraft.network.chat.Component c,boolean overlay){messages++;}
}