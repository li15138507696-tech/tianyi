package net.minecraft.world.damagesource;
public record DamageSource(net.minecraft.world.entity.Entity entity){public net.minecraft.world.entity.Entity getEntity(){return entity;}}