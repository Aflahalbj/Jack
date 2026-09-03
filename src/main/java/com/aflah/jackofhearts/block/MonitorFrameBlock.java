package com.aflah.jackofhearts.block;

import net.minecraft.world.level.block.Block;

/**
 * Pelat pengisi dinding papan.
 *
 * <p>Papan peserta membentang {@value MonitorBlock#BOARD_WIDTH}x{@value MonitorBlock#BOARD_HEIGHT}
 * blok, tapi yang menggambarnya cuma satu blok pengendali — sisanya dinding apa pun
 * yang kebetulan ada di situ. Blok ini bahan untuk dinding itu.
 *
 * <p>Keenam sisinya memakai <b>tekstur yang sama persis</b> dengan sisi Papan Peserta,
 * bukan tekstur terpisah yang dibuat menyerupainya. Dua tekstur yang cuma mirip akan
 * selalu terlihat bedanya di sambungan, dan akan berbeda lagi setiap kali salah
 * satunya disentuh.</p>
 *
 * <p>Tidak punya arah hadap dan tidak punya muka khusus — bagian depannya toh tertutup
 * penuh oleh gambar papan, jadi yang perlu menyatu justru sisi-sisi yang terlihat dari
 * luar bidang.</p>
 */
public class MonitorFrameBlock extends Block {

    public MonitorFrameBlock(Properties properties) {
        super(properties);
    }
}
