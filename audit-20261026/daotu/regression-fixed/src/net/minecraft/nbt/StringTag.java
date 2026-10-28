package net.minecraft.nbt;
public class StringTag extends Tag { final String value; private StringTag(String v){value=v;} public static StringTag valueOf(String v){return new StringTag(v);} }
