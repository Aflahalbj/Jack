# Jack of Hearts — Minecraft Forge 1.20.1

Mod minigame bergaya *Alice in Borderland* (permainan Jack of Hearts). Peran dikocok
otomatis seperti mod Deception: ada **Jack** dan ada **Ace**. Setiap pemain memakai
seragam dengan simbol kartu acak di punggung — orang lain bisa melihatnya, pemiliknya
tidak. Tiap ronde berakhir dengan fase menebak di sebuah ruangan; salah menebak =
disambar petir.

- Minecraft **1.20.1**
- Forge **47.3.0**
- Java **17**, Gradle wrapper **8.1.1**
- Mod ID: `jackofhearts`

---

## Cara build

```bash
./gradlew build
```

Jar hasil build ada di `build/libs/jackofhearts-1.20.1-1.0.0.jar`.
Untuk uji coba langsung: `./gradlew runClient` atau `./gradlew runServer`.

---

## Aturan permainan

### Peran
| Peran | Menang kalau |
|---|---|
| **Ace** | Berhasil membunuh Jack, **atau** Jack belum membunuh siapa pun sampai akhir **ronde 3** |
| **Jack** | Bertahan hidup sampai selesai **ronde 10**, **atau** semua Ace sudah mati |

Semua ambang di atas bisa diubah lewat config (`aceWinRoundIfNoKill`, `jackWinRound`).

### Registrasi pemain

Susun dulu siapa saja yang ikut dengan `/jack regis`, `/jack regisall`,
`/jack unregis`, dan `/jack unregisall`. Daftarnya **bertahan lintas game**, jadi
kamu tidak perlu menyusun ulang tiap mau main lagi.

**Game selalu berpatok ke daftar ini, tidak pernah ke "siapa yang kebetulan online".**
`/jack start` gagal kalau daftarnya kosong — supaya tidak ada yang ikut main tanpa
sengaja hanya karena sedang login. Yang terdaftar tapi sedang offline dilewati, dan
namanya dilaporkan ke pemanggil perintah.

`/jack start <targets>` bukan jalur terpisah: ia **mengganti isi daftar** dengan target
itu lalu memulai seperti biasa, jadi tetap satu sumber kebenaran.

`/jack listplayer` menampilkan isi daftar lengkap dengan status online/offline tiap orang.

### Pembagian peran

`/jack start` mengocok peran, lalu setiap peserta melihat **animasi kocok kartu**
selama ~9 detik:

1. **Barisan kartu terbuka** — sebanyak jumlah pemain, maksimal **30 kartu** disusun
   grid **3 baris x 10 kolom**, mendarat satu per satu. Di antaranya ada kartu Jack
   sebanyak jumlah Jack sungguhan, jadi semua orang bisa menghitung sendiri ada berapa
   Jack yang berkeliaran.
2. Kartu **menutup** semua.
3. Kartu **menyatu** jadi satu deck di tengah.
4. Deck **dikocok** tiga putaran. Yang ikut dikocok maksimal **15 kartu**
   (`SHUFFLE_MAX_CARDS`), karena mengocok 30 sekaligus terlihat penuh sesak.
   Kelebihannya menumpuk di slot teratas saat tahap 3 lalu berhenti digambar; karena
   posisinya sudah saling menimpa, tidak ada yang terlihat menghilang.
5. Kartu teratas **terangkat dan membalik**, memperlihatkan JACK atau ACE.

> Kalau pemainnya lebih dari 30, kartunya berhenti di 30 tapi **teksnya tetap
> menampilkan angka sebenarnya** ("35 kartu di meja, 2 di antaranya JACK"), plus
> baris kecil yang memberitahu berapa kartu yang benar-benar digambar. Jadi
> informasinya tidak pernah bohong walau gambarnya tidak muat.

Ini fase tersendiri (`REVEAL`), bukan sekadar layar tambahan. Selama animasi berjalan
**ronde 1 belum mulai menghitung** dan semua peserta **dibuat kebal**. Alasannya:
layar reveal mengunci input pemain sementara dunia tetap berjalan — tanpa fase
terpisah, Jack kehilangan 9 detik waktu berburu, dan lebih parah, peserta bisa
dibunuh atau kena damage jatuh saat tidak bisa bergerak.

Atur lewat `roleRevealSeconds`. **Isi 0 untuk mematikan animasi** dan langsung masuk
ronde 1 — berguna saat testing berulang. Nilai 1–7 otomatis dinaikkan ke 8, karena
animasinya sendiri butuh 7,2 detik sampai kartunya selesai membalik.

### Alur satu ronde
1. **Fase ronde** (default 10 menit) — pemain bebas bergerak. Jack berburu, Ace bertahan.
2. **Fase menebak** (default 20 detik) — semua pemain hidup harus menebak simbol
   di punggungnya sendiri lewat Terminal Tebakan di ruangan.
3. **Resolusi** — yang benar aman, yang salah **atau tidak menebak sama sekali**
   ditembak laser dan tereliminasi jadi spectator. Lasernya satu tembakan: energi
   menumpuk dulu jadi bola cahaya di ujung Terminal Tebakan (belum ada garis sama
   sekali), lalu sinarnya meluncur ke korban dalam sepersepuluh detik, menyala
   sesaat, dan padam. Garis yang menyala terus akan terbaca sebagai beam penyembuh,
   bukan laser. Korban laser tidak dibunuh:
   tidak ada layar kematian, tidak ada isi tas yang tercecer, tidak ada respawn.
   Sinarnya keluar dari Terminal Tebakan kalau korban berdiri di lantai penanda
   ruang tebak (`roomMarkerBlock`, default beehive, dicek sampai 4 blok ke bawah);
   selain itu turun lurus dari langit.

   Terminal yang dipakai adalah yang terakhir diklik korban. Kalau dia belum pernah
   mengklik satu pun — misalnya lewat `/jack debug laser` — ruangannya **ditelusuri
   seperti orang berjalan**: petak demi petak lewat ruang yang bisa dilalui, sampai
   ketemu terminal terdekat yang benar-benar bisa dicapai dari tempatnya berdiri.
   Tembok menghentikan penelusuran, begitu juga pintu sel yang sedang tertutup.

   Terdekat saja tidak cukup, karena jarak lurus menembus tembok: terminal di kamar
   sebelah sering lebih dekat daripada terminal di kamar yang sedang ditempati, dan
   lasernya akan keluar dari kamar orang lain. Garis pandang juga tidak cukup — ia
   lolos lewat pintu yang kebetulan sedang terbuka. Penelusurannya dibatasi
   `ROOM_FLOOD_LIMIT` (4096 petak) supaya ruangan terbuka atau langit-langit yang
   bolong tidak membuatnya melebar ke seluruh peta; kalau tidak ketemu sama sekali,
   barulah dipakai yang terdekat apa adanya. Selama ditembak, pandangan korban dipindah
   ke kamera sinematik supaya dia menonton dirinya sendiri dieksekusi.

### Kamera eksekusi

Kameranya **diikatkan ke Terminal Tebakan**, bukan ke koordinat yang ditulis per
ruangan — kamu yang menaruh terminalnya, jadi posisinya otomatis benar di ruang tebak
mana pun:

- Kamera berada **3 blok di atas Terminal Tebakan** (`CAMERA_HEIGHT`). Yang berpindah
  hanya pandangannya; badan korban tetap di tempatnya, jadi dia bisa melihat sendiri
  apakah kena atau selamat.
- **Yaw** (kiri-kanan) mengikuti korban, **pitch** (atas-bawah) dikunci menunduk 60
  derajat lewat `CAMERA_PITCH`. Pitch sengaja tidak dihitung dari posisi korban supaya
  bingkainya konsisten, tidak berubah-ubah tergantung korban berdiri jauh atau dekat.
  Di layar F3, pasangan itu ditampilkan sebagai `(yaw / pitch)`.
- Kalau korban **di luar ruangan** (laser dari langit), tidak ada terminal untuk
  dijadikan jangkar. Kamera mundur `CAMERA_OPEN_DISTANCE` (5,5 blok) menyamping dan
  turun hampir ke tanah — `CAMERA_OPEN_HEIGHT` (0,3 blok di atas **kaki**) — lalu
  **mendongak** ke kepala korban, dengan pitch dihitung, bukan dikunci 60. Lasernya
  turun dari langit, jadi sudut rendah dari bawah inilah yang menangkap seluruh
  panjang sinarnya; kamera dari atas justru membuangnya ke luar bingkai.

  > Raycast pembatas temboknya di mode ini ditarik **mendatar** di ketinggian kamera,
  > bukan dari kepala korban. Kalau ditarik dari kepala, sinarnya menukik ke bawah dan
  > langsung membentur tanah sendiri, sehingga kamera selalu tertarik menempel ke korban.

### Akhir ronde: adegan laser lalu papan peserta

**Setiap** ronde masuk fase eksekusi, termasuk ronde yang tidak memakan korban sama
sekali. Yang menebak benar tetap melihat lasernya mengisi daya lalu padam — gertakan
yang sama persis dengan `/jack debug safe`. Ini bukan hiasan: kalau adegannya cuma
muncul saat ada yang mati, adegan itu sendiri sudah membocorkan hasilnya sebelum
sinarnya jatuh.

Sesudah itu kamera semua orang berpindah ke depan **Papan Peserta**, juga di ronde
tanpa korban — dengan alasan yang sama, papan yang cuma muncul kalau ada yang mati
jadi kabar buruk hanya dengan muncul.

Jedanya dihitung dari **adegan yang paling lama selesai, lalu ditambah 5 detik**. Ada
dua adegan yang jalan bersamaan dan panjangnya beda: sinarnya sendiri baru habis
memudar 30 tick setelah jatuh (berlaku juga untuk yang gertakan dan yang turun dari
langit), sementara korban yang ambruk butuh 30 tick (`CorpseEntity.ANIM_TICKS`). Ronde
tanpa korban tidak punya adegan ambruk, jadi yang dihitung cuma sinarnya.

Panel peserta baru **padam setelah kameranya sampai** di depan papan, bukan sebelumnya
— panel yang padam saat tidak ada yang menonton sama saja tidak padam. Ronde tanpa
korban tentu saja tidak memadamkan panel siapa pun.

### Pandangan korban ikut menoleh

Selama laser mengunci, **model** korban perlahan menoleh ke titik asal laser — bukan
kameranya, yang sudah dialihkan ke entitas lain dan tetap diam membingkai adegan.

Satu aturan menangani dua situasi sekaligus, karena sasaran pandangannya adalah titik
asal laser itu sendiri: di dalam ruangan titik itu permukaan atas Terminal Tebakan,
jadi korban menoleh ke terminal; di luar ruangan titik itu tepat di atas kepalanya,
jadi korban mendongak ke langit. Satu-satunya kasus khusus adalah saat lasernya tegak
lurus dari atas — arah mendatarnya tidak terdefinisi di situ, jadi yaw korban
dibiarkan apa adanya supaya dia tidak berputar tanpa alasan.

> Rotasinya dipaksa lewat `ServerPlayer.lookAt`, bukan `setYRot`. Rotasi pemain
> dikendalikan client: nilai yang diset langsung di server akan ditimpa balik oleh
> paket rotasi client di tick berikutnya. `lookAt` mengirim paket yang memaksa client
> memutar pandangannya, jadi perubahannya bertahan dan ikut terlihat di layar korban
> sendiri.

Putarannya selesai tepat saat laser menembak, memakai `Mth.rotLerp` untuk yaw supaya
tidak berputar jauh melewati 360 derajat.

### Tangan first person disembunyikan

Minecraft menggambar tangan berdasarkan **tipe kamera** (first person), bukan
berdasarkan entitas mana yang sedang jadi sudut pandang. Saat kamera eksekusi aktif,
tipe kameranya tetap first person, jadi tangan pemain ikut tergambar melayang di
tengah adegan sinematik. `RenderHandEvent` dibatalkan selama sudut pandang bukan badan
sendiri, jadi ini juga otomatis benar untuk kamera pihak ketiga mana pun nanti.
- **Raycast ke atas** menghentikan kamera sebelum langit-langit. Tanpa itu, ruangan
  beratap rendah bikin kameranya tertanam di dalam blok dan korban cuma melihat hitam.
- Kalau kamera berakhir hampir tepat di atas kepala korban, kameranya digeser
  menyamping dulu (`CAMERA_MIN_HORIZONTAL`). Sinar yang mengarah lurus ke kamera tidak
  punya lebar yang terlihat — quad billboard-nya menipis jadi garis dan lasernya hilang.
- Arah hadap kamera diperbarui tiap tick, jadi tetap terkunci walau korban bergeser.
- Korban dibekukan selama fase ini supaya tidak berjalan keluar frame.

Caranya: spawn armor stand tak terlihat di titik hasil hitungan, lalu kirim
`SetCameraPacket` ke client korban yang memanggil `Minecraft.setCameraEntity`.

> **Jangan pakai `ServerPlayer.setCamera`.** Method server itu ikut **menteleport
> pemain** ke entitas kameranya — perilaku yang benar untuk spectator, karena di sana
> memindahkan pemain memang tujuannya, tapi salah total di sini.
>
> Yang benar adalah sisi client: `Minecraft.setCameraEntity` itu public dan isinya
> hanya menyetel satu field plus satu pemeriksaan post-effect. Nol teleport. Itu juga
> cara mod freecam mengubah kamera tanpa menyentuh posisi pemain.
>
> Kalau ini keliru, akibatnya berlipat: korban tersedot ke atas terminal, dan karena
> kepalanya lalu berada tepat di titik kamera, perhitungan arah hadap menghasilkan
> selisih nol dan kameranya mendongak ke langit.

Satu urutan yang perlu diperhatikan: paket mod dikirim segera, sedangkan spawn entitas
baru dikirim server di akhir tick. Jadi paket kamera bisa tiba **sebelum** entitas
kameranya ada di client. Karena itu `ClientCameraOverride` mencoba ulang tiap tick
sampai entitasnya muncul.

Entitas kamera wajib dibuang setelah fase selesai — selain dibuang lewat daftarnya,
ada penyapuan berdasarkan tag entitas saat game start dan stop, untuk berjaga kalau
sesi sebelumnya berakhir tidak wajar. Di sisi client ada jaring pengaman terpisah:
kalau entitas kameranya hilang mendadak, pandangan otomatis balik ke badan sendiri.

> Simbol punggung tetap aman walau korban melihat badannya sendiri: penggantian
> tekstur jadi kartu `?` berbasis siapa yang menonton, bukan sudut kamera.
4. Simbol diacak ulang, masuk ronde berikutnya, sampai salah satu syarat menang terpenuhi.

### Simbol
Hati (♥), Wajik (♦), Keriting (♣), Sekop (♠).

Di awal tiap ronde simbol semua orang diacak ulang, dan selama ~1,5 detik simbol di
punggung berganti-ganti dulu sebelum menetap — jadi terlihat sedang dikocok, bukan
tiba-tiba berubah. Animasinya dipicu client sendiri saat nomor ronde di paket
sinkronisasi berubah, jadi tidak perlu paket tambahan. Pemakainya sendiri tetap
melihat kartu `?` sepanjang waktu.

---

## Yang harus kamu siapkan di dunia

1. Ambil **Terminal Tebakan** (`jackofhearts:guess_block`) dari creative tab
   *Jack of Hearts*, atau `/give @s jackofhearts:guess_block`.
2. Taruh satu atau beberapa di ruangan tebak. Blok ini yang diklik kanan pemain
   untuk membuka GUI pilihan simbol. **Muka bergambar simbolnya otomatis menghadap
   ke arahmu saat dipasang** — persis seperti furnace atau dispenser vanilla, jadi
   tidak perlu diputar manual dengan tangan.
3. Pasang **lantai penanda** di ruang tebak: blok `roomMarkerBlock` (bawaannya
   `minecraft:beehive`) di bawah lantai yang diinjak pemain, sampai 4 blok ke bawah
   masih terbaca. Lantai ini satu-satunya cara mod ini mengenali batas ruangan —
   dipakai laser untuk memutuskan sinarnya keluar dari terminal atau dari langit,
   **dan** dipakai Pintu Sel untuk tahu ruangannya masih terisi atau tidak.

---

## Tangga yang bisa diduduki

Dua blok dekoratif, tidak berhubungan langsung dengan aturan game: `jackofhearts:sittable_quartz_stairs`
dan `jackofhearts:sittable_jungle_stairs`. Bentuk, tekstur, kekerasan, dan suara
langkahnya **persis** tangga quartz/jungle vanilla — blockstate dan modelnya
menunjuk langsung ke resource vanilla (`minecraft:block/quartz_stairs`, dst.), jadi
tidak ada tekstur baru yang digandakan. Bedanya cuma satu: **klik kanan untuk duduk**.

Cara kerjanya: klik kanan men-spawn entitas "kursi" tak terlihat di atas anak tangga,
lalu pemain menunggangi entitas itu — bukan diteleport, jadi lepas duduknya otomatis
lewat tombol **sneak** yang sama dengan turun dari kuda atau perahu. Kursinya
membuang dirinya sendiri begitu tidak ada lagi yang menungganginya, jadi tidak
pernah menumpuk di dunia; sneaking saat klik kanan melewati duduk sama sekali,
supaya blok ini tetap bisa diperlakukan seperti tangga biasa kalau perlu.

> Kalau blok yang diduduki hancur, pemainnya otomatis dilepas dari kursinya
> (`SittableStairBlock.onRemove`), supaya tidak menggantung di kursi tak terlihat
> yang kehilangan alasnya.

Detail teknis yang mungkin berguna kalau mau bikin blok serupa: Forge mewajibkan
setiap entity type punya renderer terdaftar walau tidak pernah dirender apa pun
(`SeatEntityRenderer` yang selalu `shouldRender() == false`), dan entity type-nya
didaftarkan dengan `.noSave()` supaya tidak pernah ikut tersimpan ke disk — kursi
yang selamat dari restart server cuma jadi sampah entitas.

---

## Pintu Sel

`jackofhearts:prison_door` — pintu baja dua blok tinggi dengan **celah intip
berjeruji setinggi mata**, seperti pintu ruang tahanan di permainan Jack of Hearts.
Ambil dari creative tab *Jack of Hearts* atau `/give @s jackofhearts:prison_door`.
Dipasang dan dihancurkan persis seperti pintu besi vanilla (butuh beliung, yang
menjatuhkan item cuma separuh bawahnya).

Celah intipnya **lubang sungguhan**, bukan gambar lubang: piksel di situ beralpha
nol dan modelnya digambar di layer `cutout`, jadi dari luar kamu benar-benar
melihat isi sel lewat celah itu — termasuk pemain yang bergerak di baliknya.

### Dua modenya

| | Terkunci | Tidak terkunci |
|---|---|---|
| Kapan | **cuma** kalau ada yang mati di dalam kamarnya | selalu, selain itu |
| Lampu status (separuh atas) | **merah** | **hijau** |
| Gerendel (separuh bawah) | terlempar penuh, menembus kusen | ditarik masuk, ceruknya kosong |
| Klik kanan | cuma bunyi gerendel + pesan di action bar | pintu membuka/menutup |
| Sinyal redstone | diabaikan | membuka pintu seperti pintu besi biasa |

Tandanya sengaja dua jenis yang berbeda, bukan cuma beda warna: lampu terbaca dari
jauh dalam sekejap, gerendel terbaca dari dekat dan tetap jelas untuk pemain yang
tidak bisa membedakan merah dengan hijau. Blok ini juga menyala tipis
(`lightLevel 4`) supaya kedua tanda itu masih kebaca di lorong gelap — tanda yang
cuma kelihatan saat ruangannya terang tidak menandai apa pun.

### Kapan pintunya terkunci

Cuma satu hal yang mengunci Pintu Sel: **ada yang masuk selagi fase menebak berjalan**.
Pintu yang dia lewati membanting menutup dan mengunci di belakangnya; pintu lain tidak
ikut-ikutan.

Kuncinya lepas sendiri **begitu fase menebak berakhir** — semua pintu, tanpa syarat,
tanpa menunggu siapa pun keluar. Di luar itu Pintu Sel tidak pernah terkunci: tidak
saat ronde berjalan, tidak saat eksekusi, tidak saat tidak ada game, dan tidak juga
kalau ada yang mati di dalamnya.

> Karena kuncinya selalu dilepas di pergantian fase berikutnya, **tidak ada seorang pun
> yang bisa terkurung lebih lama dari satu fase menebak**. Rancangan-rancangan
> sebelumnya menahan kunci sampai ruangannya kosong, sampai penghuninya keluar sendiri,
> atau selamanya kalau ada yang mati di dalam — dan semuanya berujung pemain terjebak
> lebih lama dari yang pernah dimaksudkan aturan permainan.

Membantingnya diberi jeda 6 tick (0,3 detik) supaya pemainnya sempat masuk sepenuhnya
dulu — pintu yang menutup di tick yang sama dengan langkahnya akan menutup di badan
orangnya, bukan di belakangnya. Kalau fase menebak keburu berakhir dalam jeda itu,
penguncian dibatalkan.

Satu jebakan yang sudah pernah menggigit dan sekarang dijaga: **badan pemain menyentuh
kedua separuh pintu**, jadi `entityInside` dipanggil dua kali di tick yang sama.
Penjagaannya dipusatkan ke separuh bawah — satu titik untuk satu pintu.

### Cara pintunya ditemukan mesin game

Mengunci "semua pintu sel" berarti server harus tahu di mana pintunya. Server tidak
punya cara murah untuk mencari satu blok di dunia yang tak terbatas, jadi pintunya
yang mencatatkan diri (`PrisonDoorTracker`) — saat dipasang, **dan** saat chunk-nya
dimuat. Pencatatan lewat chunk itu perlu: pintu yang sudah berdiri sejak sebelum
server menyala tidak pernah lewat jalur pemasangan. Pemindaian chunk-nya membaca
palet section lebih dulu (`maybeHas`), jadi chunk yang tidak punya pintu tidak
pernah benar-benar ditelusuri per blok.

### Perilaku kecil yang mungkin bikin bingung

- Saat terkunci, arus redstone tetap dicatat walau tidak membuka pintu. Begitu fase
  menebak berakhir selagi arusnya masih menyala, pintunya **langsung membuka**.
- **Kedua separuh pintu selalu ditulis berbarengan** — buka/tutup, kunci, maupun
  status redstone. Vanilla cuma mengubah blok yang disentuh lalu mengandalkan
  rambatan `updateShape` untuk menyamakan separuh satunya; di sini keduanya ditulis
  langsung (`writeBothHalves`), jadi tidak mungkin ada separuh yang tertinggal.
  Efek sampingnya berguna: pintu yang separuhnya terlanjur berbeda memperbaiki
  dirinya sendiri begitu diklik sekali.

### Kalau mau mengubah tampilannya

Tekstur, model, dan blockstate-nya dibangkitkan skrip, bukan digambar/diketik satu
per satu — 64 varian blockstate dan 16 berkas model terlalu banyak untuk ditulis
tangan. PNG dan JSON hasilnya tetap yang di-commit; skripnya cuma supaya perubahan
berikutnya tidak mulai dari nol:

Satu hal yang penting kalau mau menggambar ulang teksturnya: **daun pintunya
digambar utuh sebagai satu kanvas 16x32, baru dipotong dua di akhir**. Menggambar
dua tekstur 16x16 terpisah bikin tiap separuh punya bingkai atas-bawahnya sendiri,
dan di dunia hasilnya terbaca sebagai dua panel setinggi satu blok yang kebetulan
ditumpuk — mirip trapdoor, bukan satu pintu. Di kanvas utuh, garis sambungnya tidak
pernah ada sejak awal, dan tiga hal sengaja melintasinya: engsel tengah, pegangan
tarik, dan guratan bajanya sendiri.

```bash
python tools/gen_prison_door_textures.py             # 4 tekstur blok + 1 tekstur item
python tools/gen_prison_door_textures.py "" prev.png # + pratinjau besar buat dilihat
python tools/gen_prison_door_models.py               # 16 model + blockstate + model item
```

---

## Kafetaria

Mekanik bertahan hidup kedua di samping tebak simbol: **lapar**. Selama game berjalan
pemain jadi lapar tiga kali lebih cepat daripada vanilla, dan satu-satunya sumber
makanan adalah meja kafetaria yang stoknya terbatas dan tidak tumbuh sendiri.

Makanan 3D-nya datang dari mod
[TW's Decorative Food](https://www.curseforge.com/minecraft/mc-mods/decorative-food).
Mod itu **tidak** jadi dependency compile — mod ini cuma membaca dunia.

### Tidak ada yang perlu disiapkan

Taruh makanannya di meja, selesai. Tidak ada perintah, tidak ada blok penanda yang
harus dipasang. Yang perlu benar cuma satu: **kotak koordinat mejanya di config**
(`areaFrom` / `areaTo`, bawaannya `-4 1 -44` sampai `1 1 -45` = 12 petak).

Penataan mejanya dipotret sendiri: selama tidak ada game berjalan, isi kotak itu
dibaca tiap 2 detik dan disimpan ke dunia. Apa pun yang kamu tata di meja di luar game
otomatis jadi penataan resminya — termasuk arah hadap tiap piring dan tinggi
tumpukannya.

Potret itu perlu karena permainan ini **menghapus** piring dari meja. Tiap
`/jack start` mejanya dipasang lagi dari potret, dan begitu game selesai mejanya
dipulihkan lagi — jadi map tidak pernah perlu ditata ulang dengan tangan. Urutan
"pulihkan dulu, baru potret lagi" itu penting: kalau dibalik, meja yang setengah kosong
justru yang akan tersimpan sebagai penataan resmi. Alasan yang sama bikin mejanya juga
dipulihkan saat server menyala — server yang mati di tengah game meninggalkan meja
setengah kosong.

### Kenapa batasannya kotak koordinat, bukan nama mod

Decorative Food mendaftarkan blok makanannya memakai **id vanilla** —
`minecraft:bread`, `minecraft:cooked_beef`, `minecraft:apple`, dan seterusnya. Registry
blok dan registry item memang terpisah, jadi id yang sama tidak bentrok; itu yang bikin
mod itu hampir tidak pernah bentrok dengan mod lain.

Akibatnya buat mod ini: **tidak ada nama mod yang bisa dipakai menyaring** mana piring
kafetaria dan mana bukan. Menyaring lewat nama blok juga tidak bisa — `minecraft:carrot`
di meja dan ladang wortel di seberang map tidak bisa dibedakan dari namanya. Karena itu
batasannya kotak koordinat: **di luar kotak, mod ini tidak menyentuh blok apa pun.**

Di dalam kotak, yang dianggap piring cuma blok yang id-nya punya item bisa dimakan —
jadi meja, lantai, dan taplaknya tetap tidak terhitung. Pemetaan makanannya pun jadi
sesederhana mungkin: blok `minecraft:cooked_beef` membagikan item
`minecraft:cooked_beef`.

### Stok: 12 piring, puluhan porsi

Di meja cuma ada 12 piring, tapi stok satu game jauh lebih banyak dari itu. Selisihnya
disimpan sebagai **angka**, bukan sebagai blok — papan melayang di atas meja
menunjukkan `sisa/total`, misalnya `48/48`.

| Stok | Yang terjadi saat diklik kanan |
|---|---|
| Lebih banyak dari isi meja | Angka turun (`48/48` → `47/48`), makanan masuk tas, **meja tetap penuh** |
| Sudah sama dengan isi meja | Angka turun **dan** piring yang diklik benar-benar berkurang satu porsi |
| Nol | Tidak ada yang keluar; papannya berubah jadi `KAFETARIA KOSONG` |

Ditolak juga kalau pemainnya **masih membawa makanan** — lihat bagian berikutnya.

Aturannya satu kalimat: **porsi di meja selalu `min(stok, isi meja saat ditata)`**.
Semua jalur — mengambil, isi ulang tiap ronde, memulai game — berakhir dengan
menegakkan aturan itu, jadi isi meja dan angka di papan tidak pernah bisa bercerita hal
yang berbeda.

Satu piring Decorative Food bisa **bertumpuk** (apel maksimal 5, kue 7, daging sapi
matang 3, ayam matang 1), dan jumlahnya disimpan di properti blockstate
`number_of_food`. Tumpukan itu ikut dihitung: piring berisi 3 daging = 3 porsi, dan
tiap pengambilan mengurangi tumpukannya satu-satu sebelum piringnya hilang. Jadi 12
piring berisi satu-satu = 12 porsi, persis seperti yang terlihat.

Selama game berjalan piring kafetaria juga **tidak bisa dipukul habis**. Tanpa itu
seluruh mekanik stok bisa dilewati: pukul piringnya, makanannya jatuh sebagai drop, dan
angka di papan tidak pernah bergerak.

### Satu porsi per orang

Pemain cuma boleh membawa **satu** makanan kafetaria (`foodCarryLimit`). Selama porsi
itu masih di tasnya, klik kanan di meja tidak mengeluarkan apa-apa — habiskan dulu,
baru boleh ambil lagi.

Batas ini yang membuat kafetarianya jadi tempat, bukan sekadar peti harta. Tanpa
batasnya, orang pertama yang sampai menyapu seluruh meja dalam sekali datang, pulang
membawa 20 porsi, dan sisa pemain kelaparan tanpa pernah punya kesempatan — sekali
datang, mekaniknya selesai.

Yang dihitung seluruh isi tas, bukan cuma tangan, termasuk makanan yang sudah
didaftarkan ulang Decorative Food dengan akhiran `_de`.

Penolakannya dijalankan di **client dan server**, bukan cuma server. Client menebak
sendiri hasil klik kanan sebelum server menjawab, dan tebakannya dijalankan lewat
`use()` milik mod makanan — yang memindahkan makanan dari tangan ke tumpukan. Kalau
server cuma diam karena menolak, tidak ada satu pun paket yang membantah tebakan itu,
dan di layar barangnya hilang dari tas *sekaligus* dari meja padahal di server tidak
terjadi apa pun. Karena itu kotak kafetarianya ikut dikirim ke client lewat paket (bukan
dibaca dari config client — config COMMON di server terpisah adalah berkas milik client
sendiri yang isinya belum tentu sama), dan setiap penolakan di server tetap memaksa
kirim ulang isi tas serta blok yang diklik.

### Dua kenop yang gampang ketuker

Ini yang paling sering salah disetel, jadi ditulis duluan:

| Kenop | Yang diaturnya |
|---|---|
| `foodNutrition` | **Seberapa sering** satu pemain harus berjalan ke kafetaria |
| `foodBase` / `foodPerPlayer` | **Apakah makanannya habis** sebelum permainan selesai |

Mengecilkan `foodNutrition` **tidak** membuat makanan lebih langka. Porsi yang lebih
kecil berarti tiap orang butuh lebih banyak porsi, jadi stok yang sama habis lebih
cepat — dan begitu stoknya dinaikkan untuk mengimbangi, garis waktu habisnya kembali
persis sama. Yang berubah cuma satu: jumlah klik. Setelan `nutrition 4 + stok 72` dan
`nutrition 8 + stok 36` sama-sama habis di ronde 5–6; yang pertama menyuruh orang bolak-
balik dua kali lebih sering untuk hasil akhir yang identik.

Jadi: setel `foodNutrition` sampai ritmenya enak, baru setel stok sampai habisnya di
ronde yang kamu mau.

### Berapa porsi untuk berapa pemain

Stok satu game = `foodBase + foodPerPlayer × jumlah pemain`, bawaannya `4 + 5 × n`:

| Pemain | Stok | Kira-kira habis di |
|---|---|---|
| 4 | 24 | ronde ~6 |
| 8 | 44 | ronde ~5 |
| 12 | 64 | ronde ~5 |
| 16 | 84 | ronde ~5 |

Jalan hitungnya, biar gampang disetel ulang:

1. Satu porsi = `foodNutrition` 6 lapar + `foodSaturation` 0,8 → 6 + 6×0,8×2 = **15,6
   poin**. Tiap 1 poin dibayar 4 exhaustion, jadi satu porsi menutup **62,4 exhaustion**.
2. Pemain diam membakar `hungerIdleDrain` 0,07 × `hungerMultiplier` 3,0 = **0,21
   exhaustion/detik**. Untuk permainan sosial di map penjara — banyak jalan dan
   mengobrol, sprint cuma saat panik — realistisnya sekitar **0,34/detik**. (Sprint itu
   sendiri 1,68/detik selagi berlari, tapi porsi waktunya kecil.)
3. Jadi satu porsi bertahan **~5 menit kalau diam**, **~3 menit kalau main aktif** →
   **satu sampai dua kali datang per ronde 5 menit**.
4. Tiap ronde ada yang kena laser, jadi jumlah pemain menyusut. Untuk 8 pemain dengan
   penyusutan wajar, kebutuhan menumpuk jadi ±13 porsi di ronde 1, ±24 sampai ronde 2,
   ±33 sampai ronde 3, ±44 sampai ronde 5. Stok 44 habis di sekitar situ.

Habis sebelum permainan selesai itu **memang maunya**: kelangkaan makanan adalah
tekanannya, dan ronde-ronde terakhir dijalani dengan perut kosong. Kalau mau
kafetarianya bertahan sampai ronde 10, naikkan `foodPerPlayer` ke sekitar 8.

Perlu diingat permainannya jarang benar-benar sampai ronde 10: begitu semua Ace mati
atau Jack mati, permainannya selesai. Realistisnya **5–7 ronde ≈ 25–35 menit**, dan
angka di atas dihitung untuk itu.

> **Kalau ronde dipotong lebih cepat** (skip, atau `roundSeconds` lebih kecil), stok yang
> sama otomatis menutup lebih banyak ronde — hitungannya berbasis menit, bukan ronde.
> Sebaliknya, kalau `roundSeconds` masih 600 (10 menit per ronde) permainannya dua kali
> lebih panjang: kalikan dua `foodPerPlayer`.

Jumlah pemain yang dipakai adalah pemain yang **benar-benar ikut** saat `/jack start`,
bukan jumlah yang terdaftar — yang sedang offline tidak makan.

### Semua makanan bernilai sama

Ke-12 makanan kafetaria (roti, sweet berry, kentang bakar, apel, semangka, wortel, bit,
kue, ayam/salmon/kod/daging sapi matang) disamakan nilainya: **6 lapar, saturasi ×0,8**
— tiga paha ayam di HUD, di antara roti dan ayam matang.

Angkanya dipilih dari ritmenya, bukan dari rasa: satu porsi harus cukup untuk sekitar
satu ronde, tidak lebih. Lebih besar dari ini, kafetarianya cuma didatangi sekali lalu
dilupakan; lebih kecil, ronde-nya habis buat bolak-balik ambil makan.

Alasannya bukan kerapian angka. Di vanilla satu bit mengisi 1 lapar sedangkan satu
daging sapi matang mengisi 8. Kalau dibiarkan, yang menentukan siapa bertahan lama
bukan permainannya, melainkan piring mana yang kebetulan paling dekat dengan tempat
duduknya.

Yang diubah adalah item vanillanya langsung, jadi roti dari mana pun asalnya bernilai
sama — tidak ada celah "bawa bekal sendiri lebih untung". Satu detail yang mudah
terlewat: Decorative Food **mengganti item vanillanya** (item asli didaftarkan ulang
jadi `<id>_de`, id lamanya diisi item milik mod itu supaya makanan bisa ditaruh sebagai
blok), jadi "item roti" bisa berarti dua benda berbeda dan **dua-duanya** ikut
disamakan. Matikan lewat `uniformFood` kalau tidak mau.

### Lapar yang dipercepat

`hungerMultiplier` (bawaan 3.0) mengalikan **exhaustion** yang didapat pemain dari
kegiatannya sendiri: berlari, melompat, memukul, menyembuhkan diri. Hasilnya persis
"tiga kali lebih cepat lapar daripada vanilla" untuk semua sumber sekaligus.

Tapi pengali saja tidak cukup, dan ini yang gampang terlewat: **di vanilla berjalan
biasa memakan exhaustion nol**. Tiga kali nol tetap nol, jadi pemain yang cuma berjalan
menyusuri lorong dan mengobrol tidak akan pernah lapar — dan kafetarianya tidak akan
pernah didatangi siapa pun. Karena itu ada `hungerIdleDrain`: tetesan tetap per detik
yang ikut dikali juga. Bawaannya 0,07 × 3,0 = 0,21/detik, artinya batang lapar penuh
habis sekitar 6,3 menit walau pemainnya cuma berdiri diam.

Inilah satu-satunya angka yang menentukan cepat-lambatnya lapar buat pemain yang cuma
jalan dan mengobrol — buat mereka sumber lapar yang lain hampir nol. Terlalu sering
bolak-balik ke kafetaria? Turunkan ke 0,05. Masih terasa lambat? Naikkan ke 0,09.

Lapar yang dipercepat cuma berlaku untuk **peserta yang masih hidup**, dan tidak
berjalan selama fase reveal — layarnya mengunci input pemain, dan membuat orang yang
tidak bisa bergerak jadi lapar bukan tantangan, cuma pajak.

### Papan angkanya

Papan `48/48` di atas meja **digambar client**, bukan entitas teks di dunia. Angkanya
berubah tiap kali ada yang mengambil makanan, dan entitas teks berarti ada benda
sungguhan yang harus dijaga tetap hidup, ikut tersimpan ke dunia, dan tertinggal di map
kalau server mati di tengah game.

Letaknya di tengah kotak kafetaria, melayang 1,4 blok di atas petak teratas — dihitung
dari kotak di config, bukan dari piring yang tersisa. Kalau ikut piring, papannya akan
merayap pelan-pelan mengikuti sisa piring terakhir sepanjang permainan.

Papannya sengaja **tidak tembus tembok**, dan cuma terlihat sampai 48 blok. Papan ini
perabot kafetaria, bukan penunjuk arah: kalau tembus tembok, satu angka melayang di
seberang map jadi memberi tahu semua orang di mana kafetarianya — termasuk Jack yang
sedang mencari mangsa.

---

## Perintah (butuh permission level 2 / OP)

| Perintah | Fungsi |
|---|---|
| `/jack regis` | Daftarkan dirimu sendiri ikut game |
| `/jack regis <targets>` | Daftarkan pemain tertentu |
| `/jack regisall` | Daftarkan semua pemain online |
| `/jack unregis [<targets>]` | Keluarkan dirimu / pemain tertentu dari daftar |
| `/jack unregisall` | Kosongkan seluruh daftar |
| `/jack listplayer` | Tampilkan daftar terdaftar + status online/offline |
| `/jack start` | Kocok peran dan mulai game dengan pemain **terdaftar**; gagal kalau daftar kosong |
| `/jack start <targets>` | Ganti isi daftar dengan target ini, lalu mulai |
| `/jack stop` | Hentikan game, lepas semua seragam |
| `/jack status` | Fase, ronde, sisa waktu, dan daftar peserta + status hidup/mati |
| `/jack skip` | Percepat fase yang sedang berjalan (buat testing) |
| `/jack debug true\|false [<targets>]` | Tebakan sasaran dinilai **benar** untuk **ronde yang sedang berjalan saja** (buat testing); tanpa target = dirimu sendiri |
| `/jack shuffle <pemain> [<jack>] [as_jack\|as_ace]` | Putar animasi kartu untuk preview, tanpa memulai game |
| `/jack reveal` | Tampilkan peran + simbol semua peserta, hanya ke kamu (host) |
| `/jack debugrole` | Daftar peran semua peserta dikelompokkan per peran, tanpa simbol |

---

## Cara simbol disembunyikan dari pemiliknya

Tiga lapis:

1. **Kamera dikunci first person.** Selama pemain masih hidup dan game berjalan,
   F5 boleh ditekan tapi kamera langsung dipaksa balik ke first person
   ([`ClientEvents.java`](src/main/java/com/aflah/jackofhearts/client/ClientEvents.java)).
2. **Tekstur diganti untuk pemilik.** Saat armor dirender dan pemakainya adalah
   pemain lokal, teksturnya diganti kartu tanda tanya — jadi di layar inventory,
   di cermin mod lain, atau kalau ada mod yang memaksa third person, yang terlihat
   tetap `?` ([`SymbolArmorItem.getArmorTexture`](src/main/java/com/aflah/jackofhearts/item/SymbolArmorItem.java)).
3. **Seragam dipaksa terpasang.** Setiap detik server memastikan pemain hidup
   memakai baju + celana dengan simbol ronde ini, jadi seragam tidak bisa dilepas
   atau dibuang.

   > Pengisian ulang slot armor itu sendiri pernah jadi sumber bug penggandaan:
   > memindahkan baju ke inventory membuat slot armor diisi **baju baru** sementara
   > yang dipindahkan tetap ada. Karena itu setiap kali diperiksa, seragam yang berada
   > di luar slot armor dibuang lebih dulu, dan `ItemTossEvent` untuk seragam
   > dibatalkan supaya tidak ada salinan yang lolos ke tanah.

4. **Jubah disembunyikan.** Jubah menutupi seluruh punggung, jadi tanpa ini pemain
   berjubah praktis kebal ditebak. Kondisinya dibaca dari isi slot dada pemain yang
   sedang dirender, bukan dari state game, jadi berlaku untuk semua pemain yang
   terlihat tanpa client perlu tahu siapa saja pesertanya — dan jubahnya kembali
   normal sendiri begitu seragam dilepas.

   > `PlayerModel.cloak` itu `private`, jadi mod ini memakai **Access Transformer**
   > (`META-INF/accesstransformer.cfg`) untuk membukanya. Nama field di berkas itu
   > harus nama SRG (`f_103373_`), bukan nama official — kalau ditulis `cloak`,
   > AT-nya diam-diam tidak berlaku.

> Catatan jujur: simbol tetap ada di NBT item yang dipegang client, jadi client yang
> dimodifikasi masih bisa membacanya. Untuk main santai bersama teman ini sudah cukup;
> kalau perlu anti-cheat sungguhan, simbol harus disimpan murni di server dan tidak
> pernah dikirim ke pemiliknya.

---

## Model baju & celana

Model dibuat di [`SymbolArmorModel.java`](src/main/java/com/aflah/jackofhearts/client/SymbolArmorModel.java):

Tiga angka inflate yang dipakai, semuanya di paling atas `SymbolArmorModel.java`:

| Konstanta | Nilai | Kena ke |
|---|---|---|
| `BODY_DEFORMATION` | 0.4 | Badan baju |
| `SLEEVE_DEFORMATION` | 0.3 | Lengan baju |
| `PANTS_DEFORMATION` | 0.3 | Pinggang + kaki celana |

Dua hal yang gampang salah waktu menyetel angka ini:

- **Lengan butuh angka lebih kecil dari badan.** Inflate menambah ukuran dalam satuan
  absolut, bukan persentase. Lengan pemain lebarnya 4, badan 8 — jadi inflate yang sama
  bikin lengan naik dua kali lipat lebih banyak secara proporsional (0.5 → lengan +25%,
  badan cuma +12%). Itu sebabnya lengan yang bikin siluet terbaca gemuk, dan kenapa dua
  konstantanya dipisah.
- **Batas bawahnya 0.25.** Lapisan luar skin pemain (jacket/sleeve/pants) duduk tepat di
  0.25. Apa pun yang lebih tipis tenggelam di dalam skin dan permukaannya kedip-kedip.
  Praktisnya jangan turun di bawah ~0.3. Perbandingannya: chestplate vanilla 1.0,
  leggings vanilla 0.5.
- **Celana harus lebih tipis dari badan baju**, karena kotak `body` dirender oleh
  baju **dan** celana. Angka yang sama persis = z-fighting di pinggang.
- **Lengan** — kotak lengan dipotong dari 12 unit (vanilla, menutup sampai tangan) jadi
  **9** unit lewat `SLEEVE_HEIGHT`, menyisakan tepat 3 piksel kulit di ujung lengan.
  Pivot bahunya tidak diubah, jadi animasi ayun tangan tetap normal.
- **Simbol** — dicetak langsung di tekstur muka belakang kotak badan, yaitu kotak
  UV (32,20) ukuran 8×12; simbolnya di (32,22) 8×8. Tidak ada kotak/patch terpisah
  yang menonjol keluar, dan tidak ada latar kartu — simbolnya putih polos langsung
  di atas kain. Keempat simbol wajib putih semua: di kain gelap, warna hitam untuk
  keriting dan sekop tidak akan terbaca sama sekali, jadi pembedanya murni bentuk.

### Resolusi tekstur

Tekstur armor dibuat **256x128**, empat kali lipat layout vanilla 64x32.

Ini bekerja karena `LayerDefinition.create(mesh, 64, 32)` cuma memakai angka itu
untuk **menormalkan** koordinat UV ke 0..1 — bukan untuk menentukan ukuran file PNG.
Jadi PNG yang lebih besar tidak menggeser apa pun, ia hanya menambah jumlah piksel
sungguhan di area UV yang sama. Semua koordinat kotak di kode model tetap ditulis
dalam satuan 64x32.

Ini penting karena muka belakang badan cuma selebar 8 unit model. Pada tekstur 64x32
itu berarti simbolnya mentok 8x8 piksel — terlalu kasar untuk terbaca sebagai simbol
kartu. Pada 256x128 area yang sama jadi 32x32 piksel.

Kalau mau lebih tajam lagi, ubah `SCALE` di skrip generator; kodenya tidak perlu
disentuh sama sekali. Ikon GUI juga ikut naik ke 64x64 (`ICON_TEX` di `GuessScreen`).

Tekstur ada di `assets/jackofhearts/textures/models/armor/`:
`uniform_heart_layer_1.png`, `uniform_diamond_layer_1.png`, `uniform_club_layer_1.png`,
`uniform_spade_layer_1.png`, `uniform_unknown_layer_1.png` (kartu `?`), dan
`uniform_layer_2.png` (celana). Semuanya mengikuti layout armor vanilla —
kalau mau ganti desain, edit file-file ini saja, kodenya tidak perlu disentuh.

Satu catatan kalau kamu mengubah `SLEEVE_HEIGHT`: panjang lengan menentukan berapa
baris UV yang dipakai kotak lengan di (40,16), yaitu `4 + SLEEVE_HEIGHT` baris.
Dengan tinggi 9 berarti area (40,16)–(56,29). Kalau lengan dipanjangkan tanpa
memperluas area tekstur itu, ujung lengannya jadi transparan.

Skrip pembuat tekstur aslinya adalah pixel-art sederhana; ganti PNG-nya dengan
karya sendiri kapan saja selama ukuran dan posisi kotak UV tetap sama.

---

## Cara kerja animasi kartu

Semua ada di [`RoleRevealScreen.java`](src/main/java/com/aflah/jackofhearts/client/RoleRevealScreen.java).
Tidak ada model, entity, atau rig animasi — seluruhnya menggambar tekstur 2D berulang
kali dengan posisi yang dihitung dari waktu.

### Gagasan intinya

Layar ini menghitung satu angka, `t` = **detik sejak animasi mulai**:

```java
float t = (ticks + partialTick) / 20.0F;
```

`ticks` bertambah 20x per detik lewat `tick()`. `partialTick` adalah pecahan di antara
dua tick — tanpa itu animasinya akan patah-patah di 20 FPS, bukan mengikuti FPS layarmu.

Setelah itu semuanya cuma pertanyaan: *"pada detik ke-`t`, kartu ke-`i` ada di mana?"*
Tidak ada state gerakan yang disimpan. Kalau kamu paham ini, sisanya gampang: setiap
fase punya satu fungsi yang menjawab pertanyaan itu.

### Garis waktu

Enam konstanta di atas kelas menentukan batas tiap fase, dalam detik:

| Konstanta | Nilai | Fase yang berakhir di situ |
|---|---|---|
| `T_LINEUP_END` | 2.0 | Kartu dijejer terbuka satu per satu |
| `T_CLOSE_END` | 2.9 | Semua kartu menutup |
| `T_STACK_END` | 3.7 | Barisan menyatu jadi satu deck di tengah |
| `T_SHUFFLE_END` | 5.9 | Deck dikocok |
| `T_GATHER_END` | 6.5 | Kartu menyatu lagi jadi tumpukan rapi |
| `T_FLIP_END` | 7.2 | Kartu teratas membalik dan peran terbaca |

`render()` cuma memilih fungsi mana yang dipanggil berdasarkan `t`:

```java
if (t < T_LINEUP_END)        renderLineup(...);
else if (t < T_CLOSE_END)    renderClosing(...);
else if (t < T_STACK_END)    renderStacking(...);
else if (t < T_SHUFFLE_END)  renderShuffling(...);
else if (t < T_GATHER_END)   renderGathering(...);
else                         renderFlip(...);
```

**Kalau kamu mengubah angka-angka ini, `MIN_REVEAL_SECONDS` di
[`GameManager.java`](src/main/java/com/aflah/jackofhearts/game/GameManager.java) harus
ikut naik.** Server yang menutup layarnya; kalau fase REVEAL berakhir sebelum
`T_FLIP_END`, layarnya hilang sebelum kartu peran sempat terbaca.

### Pola "progress lokal"

Setiap fungsi fase memulai dengan hal yang sama: mengubah `t` global jadi angka 0..1
khusus fase itu.

```java
float u = (t - T_SHUFFLE_END) / (T_GATHER_END - T_SHUFFLE_END);
```

Di awal fase `u` = 0, di akhir `u` = 1. Semua gerakan ditulis dalam `u`, bukan `t` —
jadi kalau kamu memanjangkan durasi sebuah fase, gerakannya otomatis melambat dan
tidak ada rumus lain yang perlu disentuh.

### `drawCard` — satu-satunya fungsi menggambar

```java
drawCard(graphics, texture, cx, cy, scaleX, scaleY, rotation, alpha);
```

`cx, cy` adalah **titik tengah** kartu. Ini penting: rotasi dan penyusutan berputar di
titik tengah, jadi kartu berputar pada porosnya, bukan pada sudut kiri-atas.

Cara kerjanya di dalam:

```java
pose.translate(cx, cy, 0);              // 1. pindah ke posisi tujuan
pose.mulPose(Axis.ZP.rotationDegrees(rotation));  // 2. putar di titik itu
pose.scale(scaleX, scaleY, 1);          // 3. besarkan/kecilkan
pose.translate(-CARD_W/2f, -CARD_H/2f, 0);        // 4. geser supaya titik tengah pas
graphics.blit(texture, 0, 0, ...);      // 5. gambar di (0,0) hasil transformasi
```

Urutannya dibaca dari bawah ke atas saat digambar. Alasan `blit` dipanggil di `(0, 0)`
dan bukan langsung di `(cx, cy)`: **`blit` cuma menerima koordinat bulat**, jadi kartu
akan meloncat per piksel saat bergerak pelan. PoseStack menerima pecahan, jadi
gerakannya mulus.

Kalau `alpha` dipakai, `RenderSystem.enableBlend()` wajib dinyalakan — tanpa itu
nilai alpha diabaikan diam-diam dan kartunya muncul mendadak, bukan memudar.

### Kocokan: kenapa kelihatan seperti dikocok

Ini bagian yang paling menarik untuk diutak-atik.

```java
float phase  = u * SHUFFLE_CYCLES + (index / (float) cardCount) * SHUFFLE_CASCADE;
float swing  = Mth.sin(phase * Mth.PI * 2.0F);   // -1 .. +1
int   side   = (index % 2 == 0) ? -1 : 1;
float offset = side * swing;
```

Empat baris itu yang menghasilkan seluruh gerakannya:

- **`swing` memakai sinus penuh (-1..+1), dan ini yang paling penting.** Kalau kamu
  pakai `Mth.sin(phase * Mth.PI)` saja, nilainya tidak pernah negatif — tiap kartu
  jadi cuma bisa keluar-masuk di sisinya sendiri dan tidak pernah menyeberang. Hasilnya
  terbaca sebagai dua kelompok yang bergerak searah, bukan berlawanan.
- `side` membalik tanda untuk kartu genap vs ganjil, jadi dua kelompok itu selalu jadi
  **bayangan cermin**: satu ke kiri, satu ke kanan, lalu bertukar lewat tengah.
- `SHUFFLE_CASCADE` memberi tunda fase kecil antar kartu dalam satu kelompok, supaya
  kelompoknya mengurai jadi kipas dan tidak bergerak sekaku satu papan.

Yang bisa kamu setel:

| Konstanta | Efek kalau dinaikkan |
|---|---|
| `SHUFFLE_SOUNDS_PER_CYCLE` | Bunyi kocokan lebih rapat. Bunyinya dipicu tepat saat kartu menyeberang tengah, jadi audio dan gerakan tetap sejalan kalau `SHUFFLE_CYCLES` diubah |
| `SHUFFLE_SOUND_VOLUME` | Bunyi kocokan lebih keras |
| `SHUFFLE_MAX_CARDS` | Lebih banyak kartu yang ikut berhamburan saat dikocok. Kelebihannya berhenti digambar begitu deck menyatu. Terpisah dari batas jejeran pembuka, yang boleh sampai 30 |
| `SHUFFLE_CYCLES` | Lebih banyak penyeberangan bolak-balik dalam durasi sama = kocokan terasa lebih cepat dan sibuk |
| `SHUFFLE_CASCADE` | Kelompok makin mengurai jadi kipas. **Jangan mendekati 0.5** — di situ kartu dalam kelompok yang sama mulai berlawanan arah sendiri dan efek cerminnya buyar |
| `SHUFFLE_SPREAD` | Kartu terlempar lebih jauh ke kiri-kanan |
| `SHUFFLE_LIFT` | Kartu terangkat lebih tinggi saat jauh dari tengah |
| angka `24.0F` di `shufflePos` | Kartu miring lebih ekstrem saat terlempar |

### Balik kartu

```java
float squash = Math.abs(Mth.cos(u * Mth.PI));
ResourceLocation face = u < 0.5F ? CARD_BACK : roleTexture();
drawCard(..., scale * Math.max(squash, 0.02F), scale, ...);
```

Triknya sederhana: **lebarnya** dikalikan `|cos|` sehingga menyusut ke nol di tengah
putaran lalu melebar lagi, sementara **tingginya** tetap. Tepat saat lebarnya nol
(`u` = 0.5) teksturnya ditukar dari punggung ke muka. Mata membacanya sebagai kartu
yang berputar, padahal tidak ada rotasi 3D sama sekali.

`Math.max(squash, 0.02F)` mencegah skala benar-benar nol, yang bisa bikin matriks
tidak valid.

### Easing

```java
private static float easeOut(float u) {
    return 1.0F - (1.0F - u) * (1.0F - u);
}
```

Gerakan linear terlihat kaku seperti mesin. `easeOut` bikin gerakan mulai cepat lalu
melambat di ujung, seperti benda yang berhenti karena gesekan. Dipakai di hampir semua
perpindahan posisi. Kalau mau kebalikannya (mulai pelan lalu cepat), ganti jadi
`u * u`.

### Menyetel sambil melihat hasilnya

```
/jack shuffle <jumlah pemain>
/jack shuffle <jumlah pemain> <jumlah jack>
/jack shuffle <jumlah pemain> <jumlah jack> as_jack
/jack shuffle <jumlah pemain> <jumlah jack> as_ace
```

Memutar animasinya untukmu sendiri **tanpa menyentuh state game** — tidak perlu
memulai game, tidak perlu pemain lain, tidak mengubah fase apa pun. ESC bisa dipakai
untuk menutup lebih awal, dan layarnya menutup sendiri 2,5 detik setelah kartu terbuka.

`<jumlah pemain>` boleh sampai 100 dan tetap dibatasi jadi maksimal 10 kartu, persis
seperti di game sungguhan — jadi `/jack shuffle 30` memperlihatkan apa yang benar-benar
akan dilihat pemain saat servermu ramai.

Alur kerja tercepat untuk mengedit: ubah angkanya, jalankan `./gradlew build`, lalu
`/jack shuffle 8` di dalam world.

---

## Menambahkan mod lain untuk testing

### Cara 1 — `run/mods/` (paling cepat, tapi terbatas)

Taruh jarnya di `run/mods/`, jalankan `./gradlew runClient`. Cukup untuk mod yang
tidak pakai mixin (mod resource, mod item/blok biasa).

Kalau mod itu pakai mixin, biasanya crash saat startup dengan pesan semacam:

```
@Shadow method m_6688_ ... was not located in the target class
```

Itu **bukan** salah mod kita. `m_6688_` adalah nama SRG. Jar mod produksi dikompilasi
terhadap nama SRG, sedangkan dev workspace ini pakai mapping `official` (lihat
`mapping_channel` di `gradle.properties`), jadi jarnya harus di-remap dulu.

### Cara 2 — `fg.deobf` di build.gradle (untuk mod bermixin)

ForgeGradle akan me-remap jarnya ke mapping dev sebelum dipakai.

Untuk jar lokal, taruh di folder `libs/` lalu:

```gradle
repositories {
    flatDir { dir 'libs' }
}

dependencies {
    // libs/namamod-1.2.3.jar  ->  'grup-bebas:namamod:1.2.3'
    runtimeOnly fg.deobf('local:namamod:1.2.3')
}
```

Untuk mod dari CurseForge, ambil projectId dan fileId dari URL halaman file:

```gradle
repositories {
    maven { url = 'https://cursemaven.com'; content { includeGroup 'curse.maven' } }
}

dependencies {
    runtimeOnly fg.deobf('curse.maven:jei-238222:4712868')
}
```

> `fg.deobf(files('libs/xxx.jar'))` **tidak bekerja** — ForgeGradle hanya bisa
> me-remap dependency modul, bukan file lepas. Kalau dipakai, ForgeGradle cuma
> memberi peringatan `Cannot deobfuscate dependency of type
> DefaultSelfResolvingDependency` lalu memuat jar aslinya, dan crash-nya sama saja.
> Karena itu `flatDir` wajib.

### Cara 3 — instalasi Forge asli (paling akurat)

Sebagian mod tetap gagal di dev workspace walaupun sudah di-deobf, kalau refmap-nya
tidak bisa dipetakan penuh ke mapping official. `curtain 1.3.2` termasuk yang seperti
itu: setelah di-deobf, error `@Shadow` hilang tapi ganti jadi
`@Inject ... could not find any targets matching ... m_6109_`.

Untuk kasus begitu — dan untuk tes yang benar-benar mewakili kondisi main —
pasang Forge 1.20.1 lewat launcher Minecraft biasa, lalu salin
`build/libs/jackofhearts-1.20.1-1.0.0.jar` bersama mod-mod lain ke `.minecraft/mods`.
Untuk mod ini cara itu memang lebih cocok, karena butuh minimal 2 pemain.

---

## Config

`config/jackofhearts-common.toml` (dibuat otomatis saat pertama jalan):

| Opsi | Default | Arti |
|---|---|---|
| `roundSeconds` | 600 | Durasi satu ronde bebas (10 menit) |
| `guessSeconds` | 20 | Durasi fase menebak di ruangan |
| `executionSeconds` | 3 | Durasi fase laser sebelum ronde berikutnya mulai |
| `roomMarkerBlock` | minecraft:beehive | Blok lantai penanda ruang tebak |
| `roomMarkerDepth` | 4 | Berapa blok ke bawah dicek untuk mencari penanda |
| `roleRevealSeconds` | 9 | Durasi animasi kocok kartu; 0 = matikan, 1-7 dinaikkan ke 8 |
| `aceWinRoundIfNoKill` | 3 | Ace menang kalau Jack nihil bunuh sampai ronde ini |
| `jackWinRound` | 10 | Jack menang kalau bertahan sampai ronde ini |
| `jackCount` | 1 | Berapa pemain yang jadi Jack |
| `reshuffleSymbolsEachRound` | true | Acak ulang simbol tiap ronde |
| `lockFirstPerson` | true | Kunci kamera first person |
| `eliminateOnWrongGuess` | true | Salah tebak = laser + jadi spectator |

Bagian `[cafeteria]`:

| Opsi | Default | Arti |
|---|---|---|
| `hungerMultiplier` | 3.0 | Pengali kecepatan lapar selama game berjalan |
| `hungerIdleDrain` | 0.07 | Exhaustion/detik untuk pemain diam, sebelum dikali; tanpa ini yang cuma jalan-jalan tidak pernah lapar |
| `resetHungerOnStart` | true | Kenyangkan semua peserta saat game dimulai |
| `uniformFood` | true | Samakan nilai gizi ke-12 makanan kafetaria |
| `foodNutrition` | 6 | Isi lapar tiap porsi; kenop **frekuensi** bolak-balik |
| `foodSaturation` | 0.8 | Pengali saturasi tiap porsi |
| `foodBase` | 4 | Bagian tetap dari stok satu game |
| `foodPerPlayer` | 5 | Tambahan stok per pemain; kenop **kelangkaan**, kalikan dua kalau `roundSeconds` 600 |
| `foodTotalOverride` | 0 | Kalau > 0, stok dipaksa ke angka ini dan rumusnya diabaikan |
| `foodRefillPerRound` | 0 | Stok yang ditambahkan tiap ronde baru; 0 = sekali habis, habis |
| `foodCarryLimit` | 1 | Maksimal porsi yang boleh dibawa satu pemain; 0 = tanpa batas |
| `areaDimension` | minecraft:overworld | Dimensi meja kafetaria |
| `areaFrom` | -4 1 -44 | Sudut pertama kotak meja, `"x y z"` |
| `areaTo` | 1 1 -45 | Sudut kedua kotak meja, `"x y z"` |

---

## Struktur kode

```
game/       GameManager (mesin state), Role, Symbol, GamePhase, PlayerData, ServerEvents
item/       SymbolArmorItem (baju/celana + NBT simbol), JackArmorMaterial
block/      GuessBlock (klik kanan -> GUI), PrisonDoorBlock + PrisonDoorTracker (pintu sel)
cafeteria/  Cafeteria (kotak meja, stok, isi meja), CafeteriaLayout (potret meja, tersimpan ke dunia),
            CafeteriaFood (pengenal piring + peta blok -> makanan),
            FoodBalance (nilai gizi disamakan), HungerRules (lapar dipercepat)
client/     SymbolArmorModel, ClientSetup, GuessScreen, RoleRevealScreen,
            JackHudOverlay, ClientEvents (kunci kamera), ClientGameState, ClientOnlyHooks
network/    SyncStatePacket, OpenGuessScreenPacket, OpenRoleRevealPacket,
            SubmitGuessPacket, ModNetwork
command/    JackCommand (/jack ...)
config/     JackConfig
```

State game hanya hidup di memori selama server menyala — restart server = game
di-reset. Itu disengaja, karena satu sesi memang dimaksudkan selesai dalam sekali main.

---

## Kredit aset pihak ketiga

Semua aset di bawah berlisensi **CC0 (public domain)** — boleh dipakai di proyek
pribadi maupun komersial, dan atribusi tidak diwajibkan. Dicatat di sini karena
tetap pantas, dan supaya jelas asalnya kalau mod ini disebarkan.

| Berkas | Asal | Pembuat | Lisensi |
|---|---|---|---|
| `textures/gui/card_back.png`<br>`textures/gui/card_jack.png`<br>`textures/gui/card_ace.png` | [Cards Set](https://opengameart.org/content/cards-set) (OpenGameArt) | Andrew Tidey | CC0 |

Kartunya diambil dari varian "Small" (47x62 px): `Back Red 1`, `Hearts 11` (Jack of
Hearts), dan `Spades 1` (Ace of Spades).
| `sounds/card_shuffle.ogg`<br>`sounds/card_flip.ogg`<br>`sounds/card_deal.ogg` | [Card Game Sounds](https://opengameart.org/content/card-game-sounds) (OpenGameArt) | HaelDB | CC0 |

Ketiga suara itu berasal dari `shuffle.wav`, `draw.wav`, dan `playcard.wav` di paket
aslinya, dipakai apa adanya selain dinormalisasi.

Semua dipakai tanpa diubah ukurannya. Suaranya aslinya WAV mono 44,1 kHz dan sangat
pelan, jadi dinormalisasi ke 90% skala penuh lalu dikonversi ke Ogg Vorbis
(`oggenc -q 6`) karena Minecraft hanya menerima format itu.

Sisa aset lain — tekstur seragam, blok, dan ikon simbol di GUI — digambar sendiri
untuk mod ini.

---

## Bahasa

`en_us.json` dan `id_id.json` dua-duanya berisi teks Bahasa Indonesia, supaya
langsung jalan tanpa peduli locale client. Kalau mau versi Inggris, edit
`assets/jackofhearts/lang/en_us.json`.
