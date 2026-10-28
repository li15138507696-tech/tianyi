package net.guzhenren.tianfu;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;import net.minecraft.nbt.Tag;
public class TianfuOpening {public static void onLogin(ServerPlayer p){}
public static void onClone(ServerPlayer newPlayer, Entity oldPlayer) {
        CompoundTag oldData = oldPlayer.getPersistentData();
        if (oldData.contains(TianfuData.ROOT, Tag.TAG_COMPOUND)) {
            newPlayer.getPersistentData().put(TianfuData.ROOT, oldData.getCompound(TianfuData.ROOT).copy());
        }
        TianfuAttributes.sync(newPlayer);
    }}