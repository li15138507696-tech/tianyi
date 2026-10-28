
package net.minecraft.client;
import net.minecraft.client.gui.Font; import net.minecraft.client.gui.screens.Screen;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft(); public final Font font=new Font();
 public Screen screen; public Player player=new Player(); public static Minecraft getInstance(){return INSTANCE;}
 public void setScreen(Screen s){screen=s;if(s!=null&&s.width==0)s.testInit(720,420);}
 public static class Player {public double getX(){return 0;} public double getZ(){return 0;} public Level level(){return new Level();}}
 public static class Level {public Key dimension(){return new Key();}}
 public static class Key {public Object location(){return "minecraft:overworld";}}
}