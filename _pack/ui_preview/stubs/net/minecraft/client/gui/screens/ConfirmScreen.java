
package net.minecraft.client.gui.screens;
import java.util.function.Consumer; import net.minecraft.network.chat.Component;
public class ConfirmScreen extends Screen {
 public final Consumer<Boolean> callback; public final Component warning;
 public ConfirmScreen(Consumer<Boolean> c,Component t,Component w,Component yes,Component no){super(t);callback=c;warning=w;}
 public void answer(boolean answer){callback.accept(answer);}
}