package net.minecraft.world.entity;
public class LivingEntity extends Entity {
 public java.util.Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>,net.minecraft.world.effect.MobEffectInstance> effects=new java.util.HashMap<>();
 public java.util.Collection<net.minecraft.world.effect.MobEffectInstance> getActiveEffects(){return effects.values();}
 public net.minecraft.world.effect.MobEffectInstance getEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h){return effects.get(h);}
 public void removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h){effects.remove(h);}
 public void addEffect(net.minecraft.world.effect.MobEffectInstance f){var old=effects.get(f.effect);if(old==null)effects.put(f.effect,f);else old.merge(f);}
}