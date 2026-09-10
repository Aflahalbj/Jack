package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.item.ContainerMarkerItem;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<Item> UNIFORM_SHIRT = ITEMS.register("uniform_shirt",
            () -> new SymbolArmorItem(ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static final RegistryObject<Item> UNIFORM_PANTS = ITEMS.register("uniform_pants",
            () -> new SymbolArmorItem(ArmorItem.Type.LEGGINGS,
                    new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    /**
     * Penanda wadah: menunjuk peti/tungku/dispenser/hopper/tong sebagai tempat susu.
     * Alat penyusun map, bukan barang permainan.
     */
    public static final RegistryObject<Item> CONTAINER_MARKER = ITEMS.register("container_marker",
            () -> new ContainerMarkerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
