
package net.minecraft.client.gui;
import java.util.*; import net.minecraft.network.chat.Component; import net.minecraft.util.FormattedCharSequence;
public class Font {
 public int width(String s){return s.codePoints().map(c->c==' '?4:c>255?9:6).sum();}
 public String plainSubstrByWidth(String s,int max){StringBuilder out=new StringBuilder();for(int c:s.codePoints().toArray()){String n=out.toString()+new String(Character.toChars(c));if(width(n)>max)break;out.appendCodePoint(c);}return out.toString();}
 public List<FormattedCharSequence> split(Component c,int max){List<FormattedCharSequence> out=new ArrayList<>();for(String p:c.text().split("\\n",-1)){while(!p.isEmpty()){String part=plainSubstrByWidth(p,max);if(part.isEmpty())part=p.substring(0,1);out.add(new FormattedCharSequence(part));p=p.substring(part.length());}}return out;}
}