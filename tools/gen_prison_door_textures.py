# Generator tekstur Pintu Sel (jackofhearts:prison_door).
#
#   python tools/gen_prison_door_textures.py [folder_resources] [pratinjau.png]
#
# Dijalankan manual saat teksturnya mau diubah; PNG hasilnya yang di-commit,
# skrip ini cuma supaya tekstur berikutnya tidak digambar ulang dari nol.
#
# Daun pintunya digambar utuh sebagai satu kanvas 16x32, baru dipotong dua di
# akhir. Itu bukan kerapian belaka: menggambar dua tekstur 16x16 terpisah bikin
# tiap separuh punya bingkai atas-bawahnya sendiri, dan hasilnya di dunia terbaca
# sebagai dua panel setinggi satu blok yang kebetulan ditumpuk — mirip trapdoor,
# bukan satu pintu. Di kanvas utuh, garis sambungnya tidak pernah ada sejak awal
# dan ada tiga hal yang sengaja melintasinya: engsel tengah, pegangan tarik, dan
# guratan bajanya sendiri.
import zlib, struct, sys, os

W, HALF, H = 16, 16, 32

# --- palet baja gelap ---
OUT  = (0, 0, 0, 0)
EDGE = (24, 27, 30, 255)
DARK = (36, 41, 46, 255)
MID  = (52, 58, 64, 255)
MID2 = (60, 67, 74, 255)
LIT  = (78, 86, 94, 255)
HI   = (104, 114, 124, 255)
VOID = (18, 20, 23, 255)

RED_MID = (214, 48, 42, 255)
RED_HI  = (255, 116, 102, 255)
RED_LO  = (108, 20, 18, 255)
GRN_MID = (46, 196, 92, 255)
GRN_HI  = (150, 255, 176, 255)
GRN_LO  = (18, 92, 46, 255)


class Tex:
    def __init__(self, w=W, h=H, fill=OUT):
        self.w, self.h = w, h
        self.p = [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y * self.w + x] = c

    def get(self, x, y):
        return self.p[y * self.w + x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c); self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c); self.set(x1, y, c)

    def crop(self, y0, y1):
        out = Tex(self.w, y1 - y0 + 1)
        for y in range(y0, y1 + 1):
            for x in range(self.w):
                out.set(x, y - y0, self.get(x, y))
        return out

    def upscale(self, n):
        big = Tex(self.w * n, self.h * n)
        for y in range(big.h):
            for x in range(big.w):
                big.p[y * big.w + x] = self.p[(y // n) * self.w + (x // n)]
        return big

    def save(self, path):
        raw = b''
        for y in range(self.h):
            raw += b'\x00' + bytes(v for x in range(self.w) for v in self.p[y * self.w + x])
        def chunk(tag, data):
            body = tag + data
            return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xffffffff)
        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(raw, 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


# Tangga nada baja, gelap -> terang. Semua bayangan dipilih dari sini supaya
# seluruh pintu tetap satu keluarga warna.
LADDER = [EDGE, DARK, MID, MID2, LIT, HI]

# Guratan tegak pelat baja: indeks ke LADDER per kolom. Permukaannya jadi terlihat
# disikat memanjang — sekaligus menegaskan arah tegak, yang justru kita butuhkan
# supaya dua blok tingginya terbaca sebagai satu daun.
STREAK = [0, 1, 4, 1, 3, 2, 3, 3, 2, 3, 2, 3, 3, 2, 1, 0]


def base(x, y):
    i = STREAK[x]
    if 0 < x < 15:
        # Gradasi cahaya dihitung terhadap tinggi pintu utuh, bukan per separuh:
        # gradasi per separuh bikin ada pita terang di tengah pintu, persis tanda
        # sambungan yang sedang kita hindari.
        if y <= 2:
            i += 1
        elif y >= 28:
            i -= 1
    return LADDER[max(0, min(len(LADDER) - 1, i))]


def panel(t):
    """Pelat baja bergurat + tepi kiri (tiang engsel) dan tepi kanan (sisi kusen)."""
    for y in range(H):
        for x in range(W):
            t.set(x, y, base(x, y))
    # Cuma ujung atas dan ujung bawah pintu yang ditutup garis gelap. Baris 15/16 —
    # tempat dua bloknya bertemu — sengaja dibiarkan polos.
    for x in range(W):
        t.set(x, 0, EDGE)
        t.set(x, H - 1, EDGE)
    t.rect(3, 1, 3, H - 2, DARK)


def hinge(t, y0, y1):
    """Engsel di tiang kiri. Yang di tengah sengaja melintasi baris 15/16."""
    t.rect(0, y0, 2, y1, LIT)
    t.rect(0, y0, 2, y0, HI)
    t.rect(0, y1, 2, y1, EDGE)
    t.set(1, y0 + 1, EDGE)
    t.set(1, y1 - 1, EDGE)


def lamp_colors(locked):
    return (RED_LO, RED_MID, RED_HI) if locked else (GRN_LO, GRN_MID, GRN_HI)


def door(locked):
    """Satu daun pintu utuh, 16x32."""
    t = Tex()
    panel(t)
    lo, mid, hi = lamp_colors(locked)

    # tiga engsel; yang tengah menjahit dua blok jadi satu daun
    hinge(t, 2, 6)
    hinge(t, 13, 18)
    hinge(t, 25, 29)

    # --- lampu status, tepat di atas celah ---
    t.rect(5, 1, 10, 3, EDGE)
    t.rect(6, 1, 9, 2, mid)
    t.rect(6, 1, 9, 1, hi)
    t.set(5, 2, lo); t.set(10, 2, lo)

    # --- celah intip: bolong sungguhan (alpha 0), berjeruji ---
    # Barisnya dipilih supaya jatuh di ketinggian mata pemain berdiri: lubangnya
    # menutup 1,44-1,63 blok dari lantai, dan mata pemain tegak ada di 1,62.
    #
    # Ukurannya sengaja kecil. Lubang ini tembus pandang beneran, jadi yang terlihat
    # lewat situ adalah ruangan gelap di baliknya — dan lubang besar di separuh atas
    # bikin blok itu terbaca "menganga" sementara separuh bawahnya padat, seolah
    # pintunya menutup sendiri-sendiri. Celah sempit berjeruji terbaca sebagai
    # jendela kecil di daun pintu yang utuh, dan tetap bisa dipakai mengintip.
    t.rect(3, 4, 12, 10, LIT)           # bezel logam tebal, mengelilingi celah
    t.rect(3, 4, 12, 4, HI)             # sorot di bibir atas bezel
    t.rect(3, 10, 12, 10, EDGE)         # bayangan di bibir bawah
    t.rect(4, 5, 11, 9, MID)
    t.rect(5, 6, 10, 8, OUT)            # bagian yang benar-benar bolong
    for x in (6, 9):                    # jeruji
        t.rect(x, 6, x, 8, HI)
    t.rect(5, 5, 10, 5, EDGE)           # bibir dalam atas, kesannya cekung

    # --- pegangan tarik tegak, melintasi sambungan dua blok ---
    t.rect(11, 12, 14, 13, EDGE)
    t.rect(11, 18, 14, 19, EDGE)
    t.rect(12, 12, 13, 19, LIT)
    t.rect(12, 12, 12, 19, HI)
    t.rect(13, 12, 13, 19, MID2)

    # --- gerendel: tanda kedua, terbaca dari dekat ---
    t.rect(7, 21, 14, 26, EDGE)
    t.rect(8, 22, 13, 25, VOID)
    if locked:
        # terlempar penuh sampai menembus kusen
        t.rect(8, 23, 15, 25, LIT)
        t.rect(8, 23, 15, 23, HI)
        t.rect(8, 25, 15, 25, EDGE)
        t.rect(14, 23, 15, 25, mid)
        t.set(14, 24, hi)
    else:
        # ditarik masuk; ceruk yang ditinggalkannya kelihatan kosong
        t.rect(8, 23, 11, 25, LIT)
        t.rect(8, 23, 11, 23, HI)
        t.rect(8, 25, 11, 25, EDGE)
        t.rect(11, 23, 11, 25, mid)
        t.set(11, 24, hi)

    # --- pelat tendang di kaki pintu ---
    t.rect(1, 28, 14, 30, DARK)
    t.rect(1, 28, 14, 28, LIT)
    for x in (5, 8, 11):
        t.set(x, 29, HI)
    return t


def item():
    """Ikon item: daun pintu yang diciutkan, tetap harus kebaca di slot 16x16."""
    t = Tex(W, HALF)
    for y in range(HALF):
        for x in range(3, 13):
            t.set(x, y, LADDER[max(0, min(5, STREAK[x] + (1 if y <= 1 else 0)))])
    t.frame(3, 0, 12, 15, EDGE)
    t.rect(4, 1, 4, 14, LIT)   # tiang engsel
    t.set(4, 3, EDGE); t.set(4, 11, EDGE)
    # lampu status
    t.rect(6, 2, 10, 3, EDGE)
    t.rect(7, 2, 9, 2, RED_MID)
    t.set(8, 2, RED_HI)
    # celah intip berjeruji
    t.frame(5, 4, 11, 8, EDGE)
    t.rect(6, 5, 10, 7, VOID)
    t.rect(8, 5, 8, 7, LIT)
    # pegangan tarik tegak
    t.rect(9, 9, 10, 12, LIT)
    t.rect(9, 9, 9, 12, HI)
    t.rect(9, 9, 10, 9, EDGE)
    t.rect(9, 12, 10, 12, EDGE)
    # pelat tendang
    t.rect(4, 13, 11, 14, DARK)
    t.rect(4, 13, 11, 13, LIT)
    return t


def main():
    # Argumen 1: folder resources (kosong / tak diisi = resources repo ini).
    # Argumen 2: berkas pratinjau besar, opsional.
    args = sys.argv[1:]
    out = args[0] if args and args[0] else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
    preview = args[1] if len(args) > 1 else None

    blocks = os.path.join(out, 'assets/jackofhearts/textures/block')
    items = os.path.join(out, 'assets/jackofhearts/textures/item')
    made = {}
    for locked in (True, False):
        tag = 'locked' if locked else 'unlocked'
        leaf = door(locked)
        made[os.path.join(blocks, 'prison_door_top_%s.png' % tag)] = leaf.crop(0, HALF - 1)
        made[os.path.join(blocks, 'prison_door_bottom_%s.png' % tag)] = leaf.crop(HALF, H - 1)
    made[os.path.join(items, 'prison_door.png')] = item()
    for path, tex in made.items():
        tex.save(path)
        print('tulis', path)

    if preview:
        bg = (26, 26, 30, 255)  # latar gelap: meniru ruangan di balik pintu
        canvas = Tex(W * 3 + 8, H + 2, bg)
        for i, leaf in enumerate([door(True), door(False)]):
            for y in range(H):
                for x in range(W):
                    c = leaf.get(x, y)
                    if c[3]:
                        canvas.set(i * 18 + x + 1, y + 1, c)
        ic = item()
        for y in range(HALF):
            for x in range(W):
                c = ic.get(x, y)
                if c[3]:
                    canvas.set(37 + x, y + 9, c)
        canvas.upscale(12).save(preview)
        print('pratinjau', preview)


if __name__ == '__main__':
    main()
