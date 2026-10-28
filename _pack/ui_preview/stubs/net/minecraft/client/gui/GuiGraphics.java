
package net.minecraft.client.gui;
import java.nio.file.*; import java.nio.charset.StandardCharsets; import java.util.*; import net.minecraft.util.FormattedCharSequence;
public class GuiGraphics {
 public final List<String> commands=new ArrayList<>(); public int clips;
 public void fill(int x,int y,int x2,int y2,int color){commands.add("R|"+x+"|"+y+"|"+(x2-x)+"|"+(y2-y)+"|"+Integer.toUnsignedString(color));}
 public int drawString(Font f,String s,int x,int y,int color,boolean shadow){commands.add("T|"+x+"|"+y+"|"+Integer.toUnsignedString(color)+"|"+Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));return f.width(s);}
 public int drawString(Font f,FormattedCharSequence s,int x,int y,int color,boolean shadow){return drawString(f,s.text(),x,y,color,shadow);}
 public void enableScissor(int x,int y,int x2,int y2){commands.add("C|"+x+"|"+y+"|"+x2+"|"+y2);clips++;}
 public void disableScissor(){commands.add("E");clips--;if(clips<0)throw new AssertionError("unbalanced clip");}
 public String text(){StringBuilder b=new StringBuilder();for(String s:commands)if(s.startsWith("T|"))b.append(new String(Base64.getDecoder().decode(s.split("\\|",5)[4]),StandardCharsets.UTF_8));return b.toString();}
 public void save(String file)throws Exception{if(clips!=0)throw new AssertionError("leaked clip");Files.write(Path.of(file),commands,StandardCharsets.UTF_8);}
}