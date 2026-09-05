package com.aflah.jackofhearts.cafeteria;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Potret isi meja kafetaria: posisi tiap piring beserta blockstate-nya.
 *
 * <p>Wajib ada karena permainan ini <b>menghapus</b> piring dari meja saat stoknya
 * menipis. Tanpa potret, satu kali main akan menghabiskan hiasan kafetarianya untuk
 * selamanya dan map harus disusun ulang dengan tangan sebelum game berikutnya.
 * Potretnya dipasang lagi tiap {@code /jack start}.</p>
 *
 * <p>Yang disimpan blockstate lengkap, bukan cuma nama bloknya, supaya arah hadap
 * dan variasi tiap piring kembali persis seperti saat ditata - piring yang semuanya
 * menghadap ke satu arah setelah dipulihkan akan langsung terlihat salah.</p>
 *
 * <p>Disimpan di penyimpanan data overworld, tapi tiap titik membawa dimensinya
 * sendiri, jadi kafetaria di dimensi mana pun tetap tercatat utuh.</p>
 */
public class CafeteriaLayout extends SavedData {

    public static final String NAME = "jackofhearts_cafeteria";

    /**
     * @param blockEntity isi block entity piring itu, kalau mod makanannya memang
     *                    memakai block entity (misalnya untuk menyimpan berapa
     *                    tumpuk makanan yang tergambar). Ikut disimpan karena
     *                    memasang blok saja akan mengembalikan piring dalam keadaan
     *                    kosong bawaan, dan penataan meja jadi tidak benar-benar
     *                    pulih. Null untuk blok yang tidak punya block entity.
     */
    public record Spot(ResourceKey<Level> dimension, BlockPos pos, BlockState state,
                       @Nullable CompoundTag blockEntity) {
    }

    private final List<Spot> spots = new ArrayList<>();

    public static CafeteriaLayout get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CafeteriaLayout::load, CafeteriaLayout::new, NAME);
    }

    public List<Spot> spots() {
        return Collections.unmodifiableList(spots);
    }

    public boolean isEmpty() {
        return spots.isEmpty();
    }

    public void set(List<Spot> newSpots) {
        spots.clear();
        spots.addAll(newSpots);
        setDirty();
    }

    public int clear() {
        int size = spots.size();
        spots.clear();
        setDirty();
        return size;
    }

    public static CafeteriaLayout load(CompoundTag tag) {
        CafeteriaLayout layout = new CafeteriaLayout();
        ListTag list = tag.getList("spots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimensionId == null) {
                continue;
            }
            layout.spots.add(new Spot(
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    NbtUtils.readBlockPos(entry.getCompound("pos")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("state")),
                    entry.contains("blockEntity") ? entry.getCompound("blockEntity") : null));
        }
        return layout;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Spot spot : spots) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", spot.dimension().location().toString());
            entry.put("pos", NbtUtils.writeBlockPos(spot.pos()));
            entry.put("state", NbtUtils.writeBlockState(spot.state()));
            if (spot.blockEntity() != null) {
                entry.put("blockEntity", spot.blockEntity());
            }
            list.add(entry);
        }
        tag.put("spots", list);
        return tag;
    }
}
