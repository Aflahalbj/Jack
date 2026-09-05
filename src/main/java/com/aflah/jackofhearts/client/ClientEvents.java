package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.entity.MannequinEntity;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ClientGameState.clientTick();
        ClientCameraOverride.tick();

        // Kunci first person: F5 boleh ditekan, tapi kamera langsung dipaksa balik,
        // jadi pemain tidak pernah bisa mengintip simbol di punggungnya sendiri.
        if (ClientGameState.lockView && minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /**
     * Sembunyikan jubah selama seragam dipakai. Jubah menutupi seluruh punggung,
     * sedangkan simbol kartunya justru ada di punggung - tanpa ini pemain berjubah
     * jadi kebal ditebak.
     *
     * <p>Kondisinya dibaca dari isi slot dada pemain yang sedang dirender, bukan dari
     * state game, supaya berlaku untuk semua pemain yang terlihat tanpa client perlu
     * tahu siapa saja pesertanya. Nilainya diset ulang tiap frame, jadi jubah kembali
     * normal sendiri begitu seragamnya lepas.</p>
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        boolean wearingUniform = event.getEntity().getItemBySlot(EquipmentSlot.CHEST)
                .getItem() instanceof SymbolArmorItem;
        event.getRenderer().getModel().cloak.visible = !wearingUniform;
    }

    /**
     * Sembunyikan tangan first person selama pandangan dialihkan ke entitas lain.
     *
     * <p>Minecraft menggambar tangan berdasarkan tipe kamera (first person), bukan
     * berdasarkan entitas mana yang jadi sudut pandang. Saat kamera eksekusi aktif,
     * kameranya ada di armor stand tapi tipe kameranya tetap first person — jadi
     * tangan pemain ikut tergambar melayang di tengah adegan sinematik.</p>
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            event.setCanceled(true);
        }
    }

    /**
     * Klik kanan di meja kafetaria dihentikan di client juga, bukan cuma di server.
     *
     * <p>Ini perbaikan bug yang bikin makanan "hilang dua kali": client menebak sendiri
     * hasil klik kanan sebelum server menjawab, dan tebakannya dijalankan lewat
     * {@code use()} milik mod makanan - yang memindahkan makanan dari tangan ke tumpukan.
     * Server menolak dan diam, jadi tidak ada yang membantah tebakan itu, dan di layar
     * barangnya hilang dari tas sekaligus dari meja.</p>
     *
     * <p>Semua tangan diproses, bukan cuma tangan utama: klik kanan yang tidak
     * menghasilkan apa-apa di tangan utama akan dicoba lagi dengan tangan kiri, dan
     * percobaan kedua itu jalur yang sama persis.</p>
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Wajib: di dunia singleplayer kejadian ini juga lewat sini untuk sisi server,
        // dan membatalkannya di situ berarti pengambilan makanan tidak pernah sampai ke
        // mesin gamenya sama sekali.
        if (!event.getLevel().isClientSide) {
            return;
        }
        if (ClientCafeteria.intercepts(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Nama sesama Jack tergambar merah, dan cuma di layar Jack.
     *
     * <p>Yang menjaga rahasianya bukan pemeriksaan di sini, melainkan datanya: daftar
     * Jack tidak pernah dikirim ke client yang bukan Jack, jadi di sana kumpulan ini
     * selalu kosong dan tidak ada satu pun nama yang berubah warna.</p>
     *
     * <p>Badan pengganti peserta yang sedang offline ikut diwarnai lewat pemiliknya,
     * bukan lewat UUID entitasnya sendiri — kalau tidak, rekan Jack yang kebetulan
     * sedang keluar dari server justru jadi satu-satunya nama yang tidak merah.</p>
     */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (ClientGameState.jacks.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        UUID uuid = entity instanceof MannequinEntity mannequin ? mannequin.getOwner() : entity.getUUID();
        if (ClientGameState.jacks.contains(uuid)) {
            event.setContent(event.getContent().copy().withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGameState.reset();
        ClientLasers.clear();
        ClientRoster.reset();
        ClientCafeteria.reset();
        // Skin yang diingat ikut dilupakan: sambungan berikutnya bisa ke server lain,
        // dan di sana UUID yang sama belum tentu orang yang sama.
        PlayerSkins.reset();
    }
}
