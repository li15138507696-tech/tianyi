package net.minecraft.client.gui;
public class GuiGraphics {public final java.util.List<String> commands=new java.util.ArrayList<>();
public void fill(int x,int y,int r,int b,int c){commands.add("R|"+x+"|"+y+"|"+(r-x)+"|"+(b-y)+"|"+Integer.toUnsignedLong(c));}
public void drawString(Font f,String s,int x,int y,int c,boolean shadow){commands.add("T|"+x+"|"+y+"|"+Integer.toUnsignedLong(c)+"|"+java.util.Base64.getEncoder().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
public void enableScissor(int x,int y,int r,int b){if(r<x||b<y)throw new AssertionError("invalid clip");commands.add("C|"+x+"|"+y+"|"+r+"|"+b);}public void disableScissor(){commands.add("E");}}