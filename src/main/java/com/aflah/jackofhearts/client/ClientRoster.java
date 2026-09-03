package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.network.RosterPacket;
import com.aflah.jackofhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Salinan daftar peserta di client, satu-satunya sumber isi Papan Peserta.
 *
 * <p>Disimpan statis dan dipakai bersama oleh semua papan yang termuat: kalau
 * datanya menempel di masing-masing block entity, dua papan di ruangan yang sama
 * harus disinkronkan sendiri-sendiri dan bisa berbeda isi.</p>
 */
public final class ClientRoster {

    private ClientRoster() {
    }

    /**
     * Satu peserta di papan.
     *
     * @param dark panelnya sudah padam. Bukan sekadar "sudah mati" — ada jeda beberapa
     *             detik antara gugurnya seseorang dan padamnya panelnya.
     */
    public record Slot(UUID uuid, String name, boolean dark) {
    }

    private static List<Slot> slots = List.of();

    public static List<Slot> slots() {
        return slots;
    }

    public static void accept(RosterPacket packet) {
        List<Slot> next = new ArrayList<>(packet.entries().size());
        for (RosterPacket.Entry entry : packet.entries()) {
            next.add(new Slot(entry.uuid(), entry.name(), entry.dark()));
        }
        slots = List.copyOf(next);
    }

    public static void reset() {
        slots = List.of();
        ClientMonitors.clear();
    }

    /**
     * Satu panel baru saja padam: tandai gelap dan bunyikan kliknya.
     *
     * <p>Bunyinya dikeluarkan dari setiap papan yang termuat, bukan dari pemainnya.
     * Itulah gunanya klik ini — memberi tahu bahwa <b>papannya</b> yang bereaksi,
     * sehingga orang menoleh ke papan dan melihat siapa yang baru saja hilang.</p>
     */
    public static void panelOff(UUID uuid) {
        List<Slot> next = new ArrayList<>(slots.size());
        boolean changed = false;
        for (Slot slot : slots) {
            if (slot.uuid().equals(uuid) && !slot.dark()) {
                next.add(new Slot(slot.uuid(), slot.name(), true));
                changed = true;
            } else {
                next.add(slot);
            }
        }
        slots = List.copyOf(next);
        if (changed) {
            playClick();
        }
    }

    private static void playClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (BlockPos pos : ClientMonitors.loaded()) {
            minecraft.level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    ModSounds.PANEL_OFF.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }
}
