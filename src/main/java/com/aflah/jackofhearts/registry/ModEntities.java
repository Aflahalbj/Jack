package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.entity.CorpseEntity;
import com.aflah.jackofhearts.entity.MannequinEntity;
import com.aflah.jackofhearts.entity.SeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .clientTrackingRange(8)
                    .noSave()
                    .noSummon()
                    .build("seat"));

    /**
     * Mayat peserta yang gugur kena laser.
     *
     * <p>Kotaknya seukuran pemain berdiri supaya jatuhnya ke tanah berhenti di
     * ketinggian yang sama dengan kaki korban tadi; badan yang sudah rebah menjulur
     * keluar dari kotak itu, dan pelebaran untuk keperluan render diurus
     * {@code CorpseEntity#getBoundingBoxForCulling}.</p>
     *
     * <p>{@code updateInterval(1)} dipakai karena umur animasinya disinkronkan tiap
     * tick selama satu setengah detik pertama. Dengan jeda bawaan, animasi ambruknya
     * akan tersendat di mata penonton. Sesudah animasinya selesai tidak ada lagi yang
     * berubah, jadi tidak ada paket yang terkirim walau jedanya satu tick.</p>
     *
     * <p>{@code noSave()} berarti mayatnya tidak ikut tersimpan ke disk: server yang
     * dimatikan di tengah permainan tidak akan menyisakan mayat menggeletak entah dari
     * game kapan.</p>
     */
    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build("corpse"));

    /**
     * Badan pengganti peserta yang sedang offline.
     *
     * <p>Jangkauan pemuatannya lebih jauh daripada mayat: badan ini berdiri diam
     * sepanjang ronde dan orang harus bisa melihatnya dari seberang ruangan, sama
     * seperti pemain sungguhan. Mayat cukup dekat karena dia cuma dilihat oleh yang
     * kebetulan ada di tempat kejadian.</p>
     *
     * <p>{@code updateInterval(2)} sudah cukup: satu-satunya yang berubah selama
     * hidupnya adalah arah hadap saat laser mengunci, dan client menginterpolasi
     * putaran itu sendiri.</p>
     *
     * <p>{@code noSave()} berarti badan ini tidak pernah tersimpan ke disk. Permainan
     * sendiri hidup di memori dan berhenti saat server dimatikan, jadi badan yang
     * tersimpan akan bangkit sebagai sisa permainan yang sudah tidak ada.</p>
     */
    public static final RegistryObject<EntityType<MannequinEntity>> MANNEQUIN = ENTITIES.register("mannequin",
            () -> EntityType.Builder.<MannequinEntity>of(MannequinEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build("mannequin"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(ModEntities::onEntityAttributes);
    }

    /** Keduanya LivingEntity, dan setiap LivingEntity wajib punya atribut. */
    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CORPSE.get(), CorpseEntity.createAttributes().build());
        event.put(MANNEQUIN.get(), MannequinEntity.createAttributes().build());
    }
}
