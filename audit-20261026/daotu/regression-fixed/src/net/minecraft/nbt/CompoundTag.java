package net.minecraft.nbt;
public class CompoundTag extends Tag {
 final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean contains(String k,int kind){Object v=values.get(k);return switch(kind){case TAG_COMPOUND->v instanceof CompoundTag;case TAG_LIST->v instanceof ListTag;case TAG_STRING->v instanceof String;case TAG_BYTE->v instanceof Boolean;case TAG_DOUBLE->v instanceof Double;default->v!=null;};}
 public boolean contains(String k){return values.containsKey(k);} public void put(String k,Tag v){values.put(k,v);} public void remove(String k){values.remove(k);} public CompoundTag getCompound(String k){return values.get(k) instanceof CompoundTag c?c:new CompoundTag();}
 public ListTag getList(String k,int kind){return values.get(k) instanceof ListTag l?l:new ListTag();}
 public void putDouble(String k,double v){values.put(k,v);} public double getDouble(String k){return values.get(k) instanceof Number n?n.doubleValue():0;}
 public void putInt(String k,int v){values.put(k,v);} public int getInt(String k){return values.get(k) instanceof Number n?n.intValue():0;}
 public void putLong(String k,long v){values.put(k,v);} public long getLong(String k){return values.get(k) instanceof Number n?n.longValue():0;}
 public void putString(String k,String v){values.put(k,v);} public String getString(String k){return values.get(k) instanceof String s?s:"";}
 public void putBoolean(String k,boolean v){values.put(k,v);} public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));} public java.util.Set<String> getAllKeys(){return values.keySet();}
 public CompoundTag copy(){CompoundTag out=new CompoundTag();values.forEach((k,v)->out.values.put(k,v instanceof CompoundTag c?c.copy():v instanceof ListTag l?l.copy():v));return out;}
}
