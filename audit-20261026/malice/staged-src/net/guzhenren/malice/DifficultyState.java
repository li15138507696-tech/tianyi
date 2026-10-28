package net.guzhenren.malice;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每张存档各自的难度档位（存在 {@code <存档>/data/guzhenren_malice_preset.dat}）。
 *
 * <p>{@code chosen} 记录「玩家是不是自己选过」：没选过就在进服时把四档按钮推给他，
 * 选过之后（哪怕选的就是默认的标准）不再打扰。
 */
public class DifficultyState extends SavedData {

    public static final String FILE_NAME = "guzhenren_malice_preset";

    private int index = Difficulty.NORMAL.ordinal();
    private boolean chosen = false;

    public static DifficultyState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DifficultyState::new, DifficultyState::load), FILE_NAME);
    }

    public static DifficultyState get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static DifficultyState load(CompoundTag tag, HolderLookup.Provider provider) {
        DifficultyState state = new DifficultyState();
        int i = tag.getInt("difficulty");
        if (i < 0 || i >= Difficulty.values().length) {
            i = Difficulty.NORMAL.ordinal();
        }
        state.index = i;
        state.chosen = tag.getBoolean("chosen");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("difficulty", index);
        tag.putBoolean("chosen", chosen);
        return tag;
    }

    public Difficulty current() {
        return Difficulty.values()[index];
    }

    public boolean isChosen() {
        return chosen;
    }

    public void set(Difficulty difficulty) {
        boolean dirty = false;
        if (index != difficulty.ordinal()) {
            index = difficulty.ordinal();
            dirty = true;
        }
        if (!chosen) {
            chosen = true;
            dirty = true;
        }
        if (dirty) {
            setDirty();
        }
    }
}
