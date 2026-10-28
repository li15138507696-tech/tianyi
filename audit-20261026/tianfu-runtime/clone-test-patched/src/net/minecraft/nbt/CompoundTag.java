package net.minecraft.nbt;
public class CompoundTag extends Tag {
 public java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean contains(String k,int t){return t==TAG_LONG?values.get(k) instanceof Long:values.get(k) instanceof CompoundTag;}
 public long getLong(String k){return values.get(k) instanceof Long l?l:0;}
 public void putLong(String k,long n){values.put(k,n);} public void putBoolean(String k,boolean n){values.put(k,n);}
 public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));}
 public CompoundTag getCompound(String k){return values.get(k) instanceof CompoundTag c?c:new CompoundTag();}
 public void put(String k,CompoundTag c){values.put(k,c);} public void remove(String k){values.remove(k);}
 public CompoundTag copy(){var n=new CompoundTag();values.forEach((k,v)->n.values.put(k,v instanceof CompoundTag c?c.copy():v));return n;}
}