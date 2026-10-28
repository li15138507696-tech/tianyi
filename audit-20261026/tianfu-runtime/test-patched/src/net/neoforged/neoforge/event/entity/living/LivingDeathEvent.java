package net.neoforged.neoforge.event.entity.living;
public record LivingDeathEvent(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.damagesource.DamageSource source){
 public net.minecraft.world.entity.LivingEntity getEntity(){return entity;} public net.minecraft.world.damagesource.DamageSource getSource(){return source;}
}