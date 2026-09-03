package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.GuessBlock;
import com.aflah.jackofhearts.block.MonitorBlock;
import com.aflah.jackofhearts.block.MonitorFrameBlock;
import com.aflah.jackofhearts.block.SittableStairBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<Block> GUESS_BLOCK = BLOCKS.register("guess_block",
            () -> new GuessBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> GUESS_BLOCK_ITEM = ModItems.ITEMS.register("guess_block",
            () -> new BlockItem(GUESS_BLOCK.get(), new Item.Properties()));

    /**
     * Papan peserta. Menyala sendiri ({@code lightLevel 10}) karena ini layar, bukan
     * dinding — papan yang ikut gelap bersama ruangannya kehilangan seluruh gunanya
     * justru di ruang remang tempat permainan ini berlangsung.
     */
    public static final RegistryObject<Block> MONITOR = BLOCKS.register("monitor",
            () -> new MonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 10)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> MONITOR_ITEM = ModItems.ITEMS.register("monitor",
            () -> new BlockItem(MONITOR.get(), new Item.Properties()));

    /**
     * Pelat pengisi dinding papan. Sifatnya disamakan dengan Papan Peserta, termasuk
     * cahayanya, supaya bidang yang tersusun dari keduanya tidak belang terang-gelap.
     */
    public static final RegistryObject<Block> MONITOR_FRAME = BLOCKS.register("monitor_frame",
            () -> new MonitorFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 10)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> MONITOR_FRAME_ITEM = ModItems.ITEMS.register("monitor_frame",
            () -> new BlockItem(MONITOR_FRAME.get(), new Item.Properties()));

    // Sifat blok (kekerasan, suara, dsb.) disalin dari padanan vanillanya lewat
    // Properties.copy, supaya perilakunya (kecepatan tambang, suara langkah, tahan
    // ledakan) tetap identik dengan tangga aslinya - cuma interaksi klik kanannya
    // yang beda.
    public static final RegistryObject<Block> SITTABLE_QUARTZ_STAIRS = BLOCKS.register("sittable_quartz_stairs",
            () -> new SittableStairBlock(Blocks.QUARTZ_STAIRS::defaultBlockState,
                    BlockBehaviour.Properties.copy(Blocks.QUARTZ_STAIRS)));

    public static final RegistryObject<Item> SITTABLE_QUARTZ_STAIRS_ITEM = ModItems.ITEMS.register("sittable_quartz_stairs",
            () -> new BlockItem(SITTABLE_QUARTZ_STAIRS.get(), new Item.Properties()));

    public static final RegistryObject<Block> SITTABLE_JUNGLE_STAIRS = BLOCKS.register("sittable_jungle_stairs",
            () -> new SittableStairBlock(Blocks.JUNGLE_STAIRS::defaultBlockState,
                    BlockBehaviour.Properties.copy(Blocks.JUNGLE_STAIRS)));

    public static final RegistryObject<Item> SITTABLE_JUNGLE_STAIRS_ITEM = ModItems.ITEMS.register("sittable_jungle_stairs",
            () -> new BlockItem(SITTABLE_JUNGLE_STAIRS.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
