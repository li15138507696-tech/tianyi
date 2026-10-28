package net.neoforged.neoforge.event.entity.player;
import net.minecraft.world.entity.player.Player;
public class PlayerEvent {
 public record Clone(Player entity,Player original){public Player getEntity(){return entity;}public Player getOriginal(){return original;}}
 public record PlayerLoggedInEvent(Player entity){public Player getEntity(){return entity;}}
 public record PlayerLoggedOutEvent(Player entity){public Player getEntity(){return entity;}}
 public record PlayerChangedDimensionEvent(Player entity){public Player getEntity(){return entity;}}
}