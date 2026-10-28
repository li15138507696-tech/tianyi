package net.minecraft.world.effect;
public class MobEffectInstance {
 public final net.minecraft.core.Holder<MobEffect> effect;
 private int duration; private final int amp; private boolean ambient,visible,icon;
 public MobEffectInstance(net.minecraft.core.Holder<MobEffect> e,int d,int a,boolean am,boolean v){this(e,d,a,am,v,true);}
 public MobEffectInstance(net.minecraft.core.Holder<MobEffect> e,int d,int a,boolean am,boolean v,boolean i){effect=e;duration=d;amp=a;ambient=am;visible=v;icon=i;}
 public int getDuration(){return duration;} public int getAmplifier(){return amp;}
 public boolean isAmbient(){return ambient;} public boolean isVisible(){return visible;} public boolean showIcon(){return icon;}
 public net.minecraft.core.Holder<MobEffect> getEffect(){return effect;}
 // 1.21.1 update: same-level replacement cannot shorten an existing duration.
 public void merge(MobEffectInstance n){if(n.amp==amp && duration!=-1 && (n.duration==-1 || n.duration>duration))duration=n.duration;}
}