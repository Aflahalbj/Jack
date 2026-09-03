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
   selain itu turun lurus dari langit. Selama ditembak, pandangan korban dipindah
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
3. (Opsional) Berdiri di titik spawn ruangan lalu jalankan `/jack setroom`, dan set
   `teleportToGuessRoom = true` di config supaya semua pemain otomatis diteleport
   ke ruangan saat fase menebak dimulai.

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
| `/jack shuffle <pemain> [<jack>] [as_jack\|as_ace]` | Putar animasi kartu untuk preview, tanpa memulai game |
| `/jack setroom` | Simpan posisimu sekarang sebagai titik ruang tebak |
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
| `teleportToGuessRoom` | false | Teleport otomatis ke ruang tebak (butuh `/jack setroom`) |
| `eliminateOnWrongGuess` | true | Salah tebak = laser + jadi spectator |

---

## Struktur kode

```
game/       GameManager (mesin state), Role, Symbol, GamePhase, PlayerData, ServerEvents
item/       SymbolArmorItem (baju/celana + NBT simbol), JackArmorMaterial
block/      GuessBlock (klik kanan -> GUI)
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
