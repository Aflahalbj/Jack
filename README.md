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
| Peran | Kalah kalau |
|---|---|
| **Jack** | Tidak ada satu pun peserta yang gugur selama **3 ronde beruntun** — Jack dieksekusi laser |
| **Ace** | Semua Ace habis, atau Jack bertahan sampai selesai **ronde 10** |
| **Unknown** *(opsional)* | Gugur sebelum pihaknya ditentukan — lihat *Peran Unknown* di bawah |

Ambangnya bisa diubah lewat config (`maxDeathlessRounds`, `jackWinRound`).

### Peran Unknown

Bawaannya **mati**. Nyalakan dengan `/jack setting unknown on`, dan atur jumlahnya dengan
`/jack setting unknown max <n>` (bawaan 1). Jumlahnya dipotong sendiri kalau pemainnya
tidak cukup — selalu disisakan paling tidak satu Ace. Pemain tertentu bisa dipastikan
jadi Unknown dengan `/jack setting unknown set <pemain> [once|always]` (lihat
*Penunjukan peran* di bagian Perintah).

Unknown **belum punya pihak** saat kartunya dibalik. Pihaknya ditentukan oleh siapa yang
habis lebih dulu selagi dia masih hidup:

- **Ada Jack yang gugur lebih dulu** → dia ikut **Ace**. Sejak itu dia dihitung sebagai
  Ace: Jack harus menghabisinya juga untuk menang. Kalau permainannya masih berlanjut
  (Jack-nya lebih dari satu), dia diberi tahu lewat pesan pribadi.
- **Semua Ace habis dan dia tinggal sendiri** → dia jadi **Jack sebenarnya**, dan menang
  bersama Jack. Termasuk kalau Jack-nya ikut habis di detik yang sama: dia satu-satunya
  yang tersisa.

Unknown yang **gugur sebelum pihaknya ditentukan tidak ikut menang siapa-siapa**, dan
begitu juga yang masih belum memihak saat Jack menang karena bertahan sampai ronde
terakhir — di situ Jack-nya tidak gugur dan dia juga tidak sendirian. Jadi tujuan Unknown
satu: bertahan hidup sampai salah satu pihak runtuh.

Selain itu dia peserta biasa: memakai seragam, ikut menebak, bisa kelaparan, dan tidak
punya skill. Kartu Unknown ikut dijejer terbuka di animasi kocok kartu (`7 kartu di meja ·
1 JACK · 1 UNKNOWN`) — semua orang boleh tahu ada berapa Unknown, yang dirahasiakan cuma
siapa. Di daftar peran akhir, pihaknya ditulis di sampingnya (`UNKNOWN → ACE`).

Gambar kartunya Joker ungu, diturunkan dari
[Cards-Joker-Black.svg](https://commons.wikimedia.org/wiki/File:Cards-Joker-Black.svg)
karya GW Simulations di Wikimedia Commons (domain publik).

**Menang-kalahnya per tim.** Jack menang berarti semua Jack menang; Ace menang berarti
semua Ace menang — termasuk yang sudah gugur di ronde dua, karena kemenangan itu
dibangun dari tebakan-tebakan yang dia ikut bayar juga. Menghitungnya per orang berarti
separuh pemenang diberi tahu bahwa mereka kalah.

Kalau **semua orang mati**, yang menang **Jack**. Habisnya Ace diperiksa lebih dulu, dan
urutan itu yang menentukan: permainan ini soal bertahan dari Jack, jadi ruangan yang
habis tak bersisa bukan kemenangan pihak Ace.

Layarnya: judul `Selamat` / `Yahahaha`, keterangan `Kamu menang` / `Kamu kalah`. Sesudah
itu peran semua peserta dibuka di chat, satu orang satu baris.

### Pengumuman kematian

Siapa pun yang gugur diumumkan dengan satu kalimat yang sama: **`<nama> tereliminasi.`**
Apa pun sebabnya, dari siapa pun, termasuk yang sedang offline.

Caranya sengaja tidak disebut. Cara seseorang gugur adalah keterangan paling berharga di
permainan ini — "salah menebak" berarti simbolnya sempat dipercaya orang, "kelaparan"
berarti dia tidak kebagian meja, "tidak hadir" berarti dia sudah lama tidak ada. Semua
itu petunjuk, dan chat bukan tempat membagikannya gratis ke semua orang.

Yang bersangkutan sendiri tetap diberi tahu apa yang terjadi padanya, lewat pesan
pribadi.

### Tenggat Jack: harus ada yang gugur tiap 3 ronde

Jack tidak bisa melukai siapa-siapa (lihat *Cuma lapar yang bisa melukai* di bawah),
jadi yang dinilai dari Jack **bukan berapa orang yang dibunuhnya**, melainkan ada
tidaknya korban sama sekali. Kerjanya membohongi orang supaya salah menebak simbolnya,
atau membiarkan mereka kehabisan makanan — dan hasil kerja itu bentuknya cuma satu:
ada yang gugur.

Jadi hitungannya: **kalau tiga ronde beruntun lewat tanpa ada seorang pun yang gugur,
apa pun sebabnya, Jack dieksekusi laser di akhir fase menebak — walaupun tebakannya
sendiri benar.** Sesudah itu adegannya sama seperti eksekusi biasa: mayatnya ambruk,
kamera pindah ke Papan Peserta, panelnya padam, baru layar menang/kalah muncul.

Hitungannya berjalan lagi dari nol setiap kali ada yang gugur. Berhasil di ronde 2
berarti tenggat berikutnya ronde 5, bukan ronde 3.

Ronde tempat korbannya jatuh ikut terhitung sebagai ronde berkorban — termasuk korban
yang baru akan ditembak beberapa detik kemudian. Kalau tidak, Jack yang baru saja
berhasil justru dihukum tepat di detik keberhasilannya.

### Registrasi pemain

Susun dulu siapa saja yang ikut dengan `/jack regis`, `/jack regisall`,
`/jack unregis`, dan `/jack unregisall`. Daftarnya **bertahan lintas game**, jadi
kamu tidak perlu menyusun ulang tiap mau main lagi.

**Game selalu berpatok ke daftar ini, tidak pernah ke "siapa yang kebetulan online".**
`/jack start` gagal kalau daftarnya kosong — supaya tidak ada yang ikut main tanpa
sengaja hanya karena sedang login.

`/jack start` juga **gagal kalau ada satu saja pemain terdaftar yang sedang offline**,
dan namanya disebutkan. Memulai tanpa dia berarti dia dicoret diam-diam dari daftar
yang barusan disusun — dan itu baru ketahuan setelah peran dibagikan, saat sudah tidak
bisa diulang. Suruh dia masuk dulu, atau keluarkan dari daftar dengan `/jack unregis`.

`/jack listplayer` menampilkan isi daftar lengkap dengan status online/offline tiap orang.

### Tiga kemampuan Jack

Jack tidak bisa melukai siapa-siapa, jadi seluruh kerjanya lewat tiga skill ini.

| Skill | Cara pakai | Efeknya |
|---|---|---|
| **Racun** | Tatap piring kafetaria, tekan **P** | Satu porsi jenis itu jadi beracun |
| **Mabuk** | Tombol **M** (**tahan menunduk + M** = ke diri sendiri) | Sasaran **salah membaca kartu orang lain** sepanjang ronde berikutnya |
| | | *(ketiganya berbagi satu jatah tindakan per ronde)* |
| **Lapar** | Tombol **H** (**tahan menunduk + H** = ke diri sendiri) | Sasaran jadi 2x lebih cepat lapar sampai disembuhkan |

Ketiganya masing-masing **dua kali pakai** per permainan, dan cuma bisa dipakai saat
ronde berjalan.

Tombolnya ada di menu kontrol Minecraft, kategori *Jack of Hearts*: **Skill Racun**,
**Skill Mabuk**, **Skill Lapar**, dan **Skill ke diri sendiri**. Yang terakhir bawaannya
**kosong**, dan kosong berarti dipakai tombol menunduk — itu yang bikin "tahan menunduk
+ M" jalan tanpa perlu disetel apa-apa. Isi kalau mau tombol lain. Sengaja tidak langsung
dipasangi Shift kiri, karena Minecraft akan menandainya bentrok dengan tombol menunduk
dan mewarnainya merah, padahal keduanya memang dimaksudkan tombol yang sama.

#### Panel skill di pojok kanan bawah

Satu ikon per skill, masing-masing dengan sisa pemakaian dan huruf tombolnya. Ikonnya
**menyala** kalau skillnya benar-benar bisa dipakai detik itu — ada orang dalam
jangkauan 2 blok, atau tombol menunduk sedang ditahan, atau untuk racun: crosshair
sedang menunjuk piring kafetaria — dan **meredup** kalau tidak.

Itu satu-satunya isyarat jangkauan yang dipunya Jack. Menekan tombol lalu ditolak
diam-diam membuat dia tidak pernah tahu sedekat apa dia harus berdiri.

Panelnya tidak digambar sama sekali untuk yang bukan Jack, dan itu bukan penyaring
tampilan: sisa pemakaian skill memang tidak pernah dikirim ke client mereka.

#### Kotak sasaran

Orang yang akan kena skill dikotaki merah — **cuma di layar Jack**. Ini bukan hiasan:
skill mengenai orang terdekat, dan di ruangan yang ramai "terdekat" bisa berpindah dari
satu orang ke orang lain hanya karena bahu yang bergeser sedikit. Jatah yang cuma dua
kali itu terlalu mahal untuk dihabiskan sambil menebak.

Sasarannya dipilih client, dan id yang sama itu yang dikirim saat tombolnya ditekan —
jadi yang dikotaki dan yang kena selalu orang yang sama. Server tetap memeriksa
jaraknya sendiri, karena id yang dikirim client bisa menunjuk siapa saja.

#### Mabuk: matanya yang berbohong, bukan layarnya

Mabuk **bukan** efek mual. Yang berubah adalah kartu yang dia baca di punggung orang
lain: sepanjang ronde itu, tiap kartu yang dia lihat digeser 1–3 langkah dari yang
sebenarnya.

Itulah seluruh isi skill ini. Permainan berputar pada satu hal — kamu tidak bisa
melihat kartumu sendiri, jadi kamu bergantung pada apa yang dibacakan orang lain. Orang
yang mabuk membacakan kartu yang salah dengan penuh percaya diri, dan racun
informasinya menyebar jauh lebih luas daripada satu orang yang dibuat pusing sendirian.

Pergeserannya **tetap**, diturunkan dari UUID pemilik kartu dan nomor ronde, bukan
diacak tiap frame. Dua akibatnya sama-sama disengaja:

- kartu palsunya **tidak pernah kebetulan benar** (geserannya minimal satu langkah);
- kartu palsunya **tidak berkedip**. Kartu yang berganti-ganti sendiri akan langsung
  ketahuan palsu; kartu yang salah tapi diam adalah kartu yang dipercaya.

**Korbannya tidak diberi tahu apa-apa** — dan itu justru seluruh gunanya. Yang tahu
dirinya sedang dicurangi akan berhenti mempercayai matanya sendiri dan diam; yang tidak
tahu akan membacakan kartu yang salah dengan mantap ke seluruh ruangan. Sama untuk skill
lapar: korbannya cuma merasa perutnya cepat sekali kosong, tanpa pernah tahu kenapa.

Semuanya dikerjakan di client yang bersangkutan: tidak ada efek yang dipasang, tidak ada
yang perlu dilepas, dan keadaannya cuma satu boolean di paket sinkronisasi yang memang
sudah dikirim tiap 5 tick.

#### Racun

Tatap piring di kafetaria lalu tekan **P**: satu porsi dari **jenis** makanan itu jadi
beracun. Racunnya menempel ke jenisnya, bukan ke satu piring tertentu — yang diambil
orang dari meja adalah "satu roti", dan piring mana yang diklik tidak pernah jadi
pertimbangan siapa pun.

Racun skill, bukan barang. Tidak ada botol di tas Jack, jadi tidak ada yang bisa
ketahuan dari barang yang kebetulan sedang dipegang, dijatuhkan, atau diintip.

**Piring yang ditatap digaris ungu, cuma di layar Jack.** Perlu, bukan hiasan: selama
permainan semua peserta di mode adventure, dan di mode itu Minecraft tidak menggambar
garis blok yang ditatap sama sekali — tanpa garis ini Jack tidak pernah tahu piring mana
yang sedang ditunjuk crosshair-nya. Garisnya kelabu kalau racunnya sedang tidak bisa
dipakai (jatah habis, atau sudah ada skill yang dipakai ronde ini). Jangkauannya jangkauan
tangan: piring yang tidak bisa disentuh tidak bisa diracuni. Server memeriksa ulang
jaraknya sendiri, karena posisi yang dikirim client bisa menunjuk piring mana saja.

Sesudah itu, tiap kali ada yang mengambil roti, peluangnya **setengah** membawa pulang
porsi yang salah. Begitu porsi beracunnya terambil, sisanya aman semua.

Peluang setengah itu cuma berlaku selama masih ada porsi lain yang bisa terambil. **Kalau
sisa porsi jenis itu tinggal sebanyak yang diracuni, yang terambil pasti yang beracun** —
tidak ada lagi porsi aman untuk diundi. Yang memakannya
gugur **5 menit** kemudian.

Racun adalah satu-satunya dari ketiganya yang korbannya **diberi tahu**: ada rasa pahit
saat menelannya, hitung mundurnya berjalan di baris status di samping hasil pemungutan
suara (`Ronde 2 | 4:37 | 0/9 | 5:00`), dan ada peringatan sekali lagi semenit sebelum
waktunya habis. Bedanya disengaja — racun adalah lomba melawan waktu, dan lomba yang
tidak diketahui pesertanya bukan lomba.

#### Satu skill per ronde

Racun, mabuk, dan lapar berbagi **satu jatah tindakan per ronde**. Jatah pemakaiannya
sendiri tetap terpisah (dua kali mabuk, dua kali lapar, dua kali racun), tapi dalam satu
ronde Jack cuma boleh melakukan salah satunya.

Kalau tiap skill punya jatah rondenya sendiri, satu ronde bisa diisi mabuk, lapar, dan
racun sekaligus — dan ronde itu praktis menghabiskan seluruh persediaan Jack dalam sekali
jalan, sementara sembilan ronde sisanya dia berjalan dengan tangan kosong.

Ikon yang jatah rondenya sudah terpakai ikut meredup, sama seperti ikon yang di luar
jangkauan.

#### Menyalakan dan mematikan skill

```
/jack setting drunk on|off      /jack setting drunk max <jumlah>
/jack setting hunger on|off     /jack setting hunger max <jumlah>
/jack setting poison on|off     /jack setting poison max <jumlah>
```

`off` berarti Jack tidak pernah dibekali skill itu sama sekali, dan ikonnya tidak
digambar. `max` menentukan berapa kali skill itu boleh dipakai dalam satu permainan.
Berlaku mulai permainan berikutnya.

### Susu: penawarnya

**Satu gelas menyembuhkan satu penyakit, dan yang mana diundi.** Kalau seseorang sedang
kena dua atau tiga sekaligus, susunya cuma mengangkat salah satunya — dan pesannya
menyebutkan yang mana: `Racun telah disembuhkan.`

Kalau sekali minum membersihkan semuanya, susu yang langka justru jadi tombol reset:
siapa pun yang berhasil menemukan satu ember langsung bersih dari seluruh kerja Jack
sepanjang permainan.

Yang dipakai **ember susu vanilla**, dan susunya diselipkan ke dalam wadah yang **kamu
tunjuk sendiri**: peti, tungku, dispenser, hopper, dan tong.

```
/jack setting milk max <jumlah>     (bawaan 10)
/jack milkreveal                    (di mana saja susu yang belum diambil)
```

Jatahnya dibagi rata sepanjang `jackWinRound` ronde. Dengan bawaan 10 susu dan 10 ronde:
satu susu per ronde. Isi 9 dan ada satu ronde tanpa susu; isi 12 dan ada dua ronde yang
dapat dua. Pembagiannya lewat selisih pembulatan, jadi ronde yang kosong jatuh di
**tengah** permainan — ronde pertama yang kosong terasa seperti fiturnya rusak, ronde
kosong di tengah terasa seperti nasib.

**30% susu mendarat di tong, 70% di wadah jenis lain.** Tong berdiri di ruang tebak, yang
cuma bisa dimasuki di fase menebak — kalau semua wadah diundi rata, sebagian besar susu
akan mendarat di ruangan yang paling jarang bisa dibuka.

Dan memang begitu: **tong cuma bisa dibuka saat fase menebak.** Di luar fase itu tutupnya
menolak dibuka, supaya susunya tidak bisa disapu lebih dulu lewat celah pintu yang
kebetulan terbuka.

Mode kreatif **tidak** dikecualikan — selama permainan berjalan, host yang sedang kreatif
tetap orang yang bisa mengosongkan tong lebih dulu. Yang dikecualikan cuma Penanda Wadah,
karena menandai tong memang harus bisa dilakukan kapan saja.

#### Undiannya

Tiap susu diundi dua kali. Pertama jenisnya: **30% tong, 70% bukan tong**. Baru sesudah
itu wadahnya diacak dari jenis yang menang. Kalau jenis yang menang tidak punya satu pun
wadah dengan slot kosong, jatahnya jatuh ke jenis satunya daripada hilang begitu saja —
dan di situlah tong bisa terasa lebih sering dari 30%.

#### Penanda Wadah: menunjuk wadah mana yang ikut

Ambil **Penanda Wadah** dari creative tab, lalu klik kanan peti/tungku/dispenser/hopper/
tong mana pun. Klik lagi untuk mencabutnya.

Daftarnya **tersimpan ke dalam berkas dunia**, di
`<folder dunia>/data/jackofhearts_milkspots.dat` — jadi ikut ke mana pun map itu dibawa,
dan cukup sekali disusun untuk semua permainan berikutnya. Sama seperti penataan meja
kafetaria, ini penataan map, bukan keadaan satu permainan.

Wadahnya ditunjuk satu per satu, bukan dicari otomatis, karena map yang luas hampir pasti
punya wadah di luar arena permainan — peti penyimpanan, tungku dekorasi, hopper mesin
redstone. Susu yang mendarat di sana sama saja tidak pernah muncul.

Jenis wadah yang boleh dipakai bisa dimatikan satu per satu:

```
/jack milkspots hopper false
/jack milkspots hopper true
```

Mematikan satu jenis **tidak** menghapus titik yang sudah ditandai — titiknya cuma
dilewati saat susu dibagikan. Menyalakannya lagi mengembalikan semuanya apa adanya, tanpa
perlu ditandai ulang.

Untuk memeriksa mana saja yang sudah terdaftar:

```
/jack checkblock true      (wadah terdaftar berpercik hijau)
/jack checkblock false
/jack checkblock           (bergantian on/off)
```

Balasannya juga menyebut isi kolam undiannya:

```
Terdaftar: 4 tong, 8 lainnya | siap diisi: 4 tong, 2 lainnya
```

Selisih antara "terdaftar" dan "siap diisi" biasanya jadi jawaban kalau susunya terasa
menumpuk di satu jenis wadah: **wadah yang semua slotnya sudah terisi tidak ikut diundi
sama sekali**, jadi jatah 70% untuk wadah non-tong bisa jatuh balik ke tong karena tidak
ada tempat lain yang kosong.

Yang dipakai **percikan partikel**, bukan bloknya diganti jadi sesuatu yang mencolok.
Mengganti blok berarti menyimpan blok aslinya dan mengembalikannya nanti, dan satu
kegagalan di jalur itu — server mati di tengah pemeriksaan — meninggalkan map yang rusak.
Percikan tidak pernah bisa merusak apa pun.

Wadah yang sudah terdaftar tapi bloknya keburu dibongkar akan dicoret sendiri dari daftar
saat susu berikutnya dibagikan.

#### Kenapa memakai blok vanilla, bukan blok buatan sendiri

Setelah wadahnya ditunjuk satu per satu, blok tiruan tidak menyisakan satu pun kelebihan:
yang membedakan wadah permainan dari wadah biasa sekarang adalah daftarnya, bukan jenis
bloknya. Yang tersisa cuma pekerjaannya — peti digambar lewat block entity renderer
sendiri lengkap dengan animasi tutupnya, dan menirunya berarti menyalin seluruh jalur itu
hanya supaya hasilnya terlihat sama dengan yang sudah ada.

### Sesama Jack saling kenal

Kalau Jack lebih dari satu, tiap Jack melihat **nama rekannya berwarna merah** di atas
kepalanya. Ace melihat nama yang sama persis seperti biasa — putih.

Yang menjaga rahasianya bukan pemeriksaan saat menggambar, melainkan pengirimannya:
daftar Jack cuma dikirim ke client yang pemainnya memang Jack. Di client Ace daftar itu
selalu kosong, jadi tidak ada apa pun di memorinya yang bisa dibaca ulang oleh client
yang dimodifikasi.

Daftarnya baru dikirim **setelah animasi kocok kartu selesai**. Nama merah yang muncul
sementara kartunya masih berputar sama saja membocorkan hasilnya lebih dulu — dan
dengan alasan yang sama, pesan `Peranmu: ...` di chat juga baru dikirim di situ.

Badan pengganti peserta yang sedang offline ikut diwarnai lewat pemiliknya, jadi rekan
Jack yang kebetulan sedang keluar dari server tidak jadi satu-satunya nama yang tidak
merah.

### Pembagian peran

Tepat saat animasi dimulai, chat semua orang dapat spanduk pembuka `🔪 GAME DIMULAI 🔪`
lengkap dengan logo Discord dan kredit dev — bentuknya sama dengan spanduk mod Deception.
Logonya glyph dari font ikon mod ini sendiri (`assets/jackofhearts/font/icons.json`,
karakter U+E000), bukan emoji, jadi font vanilla tidak tersentuh.

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

### Baris status di atas hotbar

Selama ronde dan fase menebak, tiap peserta dapat satu baris di tempat pesan action
bar:

```
Ronde 2 | 4:37 | 3/5
```

Ronde, sisa waktu, dan hasil pemungutan suara percepat ronde. Yang sedang keracunan
dapat satu ruas lagi di ujungnya: sisa waktu sebelum racunnya bekerja. Di fase menebak, bagian
ketiga diganti hal yang paling menentukan detik itu — `Belum menebak!` atau
`Tebakan: ♥` — karena di situ sudah tidak ada yang bisa dipercepat lagi. Yang sudah
gugur melihat `TERELIMINASI`.

Barisnya dikirim dari server sedetik sekali, bukan digambar client, supaya angka
suaranya selalu sama dengan yang dipegang mesin game — client tidak pernah perlu tahu
siapa saja yang sudah memilih. Pesan sekilas yang menumpang tempat yang sama
(peringatan pintu sel membanting) menahan baris ini 3 detik supaya sempat dibaca.

### Voting percepat ronde

`/jack skip` bisa dipakai **semua pemain**, bukan cuma host — ini satu-satunya cabang
`/jack` yang tidak butuh OP. Sekali lagi untuk membatalkan pilihan: satu-satunya cara
menarik pilihan yang tidak bisa ditarik adalah menunggu ronde habis, padahal justru
menunggu itu yang sedang dihindari.

Begitu **semua** yang ditunggu sudah setuju, ronde langsung dipotong ke fase menebak.
Suaranya dikosongkan tiap ronde baru.

**Yang ditunggu = peserta hidup yang sedang online**, di pembilang maupun penyebut.
Ini yang bikin voting-nya tahan pemain offline:

- yang keluar dari server berhenti dihitung di kedua sisi, jadi dia tidak menahan ronde
  berjalan terus sementara semua orang sudah setuju;
- suaranya **tidak dihapus**, cuma berhenti dihitung — begitu dia masuk lagi, suaranya
  kembali sendiri tanpa perlu memilih ulang;
- kelengkapan suaranya diperiksa tiap tick, bukan cuma waktu ada yang memilih. Jumlah
  yang ditunggu bisa berubah tanpa ada yang memilih apa pun (ada yang keluar, ada yang
  gugur), dan justru perubahan itu yang sering melengkapi suaranya.

Host tetap punya `/jack forceskip` untuk memotong fase apa pun tanpa menunggu suara.

### Cuma lapar yang bisa melukai

Selama game berjalan, **semua kerusakan ke pemain dibatalkan**: pukulan pemain lain,
jatuh, api, mob, ledakan, semuanya. Yang tersisa cuma dua jalan keluar dari permainan
ini — laser fase menebak, yang tidak pernah lewat sistem kerusakan sama sekali
(korbannya langsung dijadikan penonton), dan perut yang kosong.

Pembatalannya di `LivingAttackEvent`, bukan `LivingHurtEvent`, supaya seluruh
kejadiannya batal: tidak ada dorongan, tidak ada kedipan merah, tidak ada bunyi
kesakitan. Kerusakan yang setengah batal justru bikin orang mengira dia sedang
kehabisan darah.

Yang tetap lolos selain lapar cuma kerusakan yang menembus kekebalan apa pun — jatuh
ke luar dunia dan `/kill`. Keduanya bukan bagian permainan: yang pertama tidak punya
dasar untuk berhenti sendiri, yang kedua alat host yang harus tetap bisa dipakai kalau
ada yang tersangkut.

Karena itu Jack dinilai dari **ada tidaknya korban**, bukan dari jumlah orang yang
dibunuhnya — lihat *Tenggat Jack* di atas.

Yang kehabisan nyawa karena lapar **tidak benar-benar mati**: kematiannya dibatalkan dan
orangnya langsung dijadikan penonton, persis seperti korban laser — tidak ada layar
kematian, tidak ada isi tas yang tercecer, dan tidak ada respawn yang harus diurus.
Nyawa dan perutnya dikembalikan penuh dulu, karena penonton bernyawa nol akan langsung
mati lagi begitu mode permainannya dikembalikan nanti.

Yang tertinggal di ruangan **mayatnya**, di tempat dia tumbang, tanpa laser dan tanpa
adegan apa pun — bukan kekosongan yang tidak memberi tahu siapa pun apa yang barusan
terjadi di situ.

### Alur satu ronde
1. **Fase ronde** (default 10 menit) — pemain bebas bergerak. Jack berburu, Ace bertahan.
2. **Fase menebak** (default 30 detik) — semua pemain hidup harus menebak simbol
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

## Pemain yang keluar di tengah permainan

Peserta yang keluar tidak dihapus dari permainan. Di tempat dia berdiri terakhir
kali muncul **badan pengganti** (mannequin): skinnya, namanya, dan seragamnya
lengkap dengan simbol di punggung — jadi ruangan tetap berisi orang sebanyak yang
tertulis di papan, dan simbolnya tetap bisa dilihat orang lain.

Nametag-nya nama pemain plus tanda `[BOT]`, supaya tidak ada yang mengira dia
pemain sungguhan yang sedang diam mengintai.

### Apa yang bisa dan tidak bisa dilakukan ke badan itu

Tidak ada. Badan pengganti tidak bisa dipukul, tidak bisa didorong, tidak
menghalangi jalan, dan tidak menerima kerusakan dari sumber mana pun. Satu-satunya
yang bisa menyentuhnya adalah laser fase menebak.

### Lapar dan nyawa tetap jalan

Perut pemiliknya tetap dihitung selama dia pergi, dengan aturan yang sama persis
dengan peserta yang hadir — termasuk percepatan `hungerMultiplier` dan tetesan
`hungerIdleDrain`. Kalau kenyangnya habis, dia mulai kehilangan nyawa mengikuti
aturan kesulitan vanilla, dan bisa benar-benar mati di sana tanpa pernah kembali.

Kalau lapar berhenti selama offline, keluar dari server jadi cara termurah untuk
melewati kelaparan — dan kafetaria, yang seluruh tegangannya bertumpu pada stok
makanan terbatas, kehilangan artinya.

Angka lapar dan nyawa hasil hitungan itu ditimpakan ke pemainnya begitu dia masuk
lagi.

### Kalau fase menebak lewat sementara dia masih pergi

Dia dihitung tidak menjawab, sama seperti peserta hadir yang tidak menebak.
Terminal Tebakan mengunci badan penggantinya, badan itu menoleh ke arah sinarnya,
lalu ditembak — mayatnya ambruk dengan animasi yang sama persis, dan panelnya di
Papan Peserta padam dalam barisan yang sama dengan korban lain di ronde itu.

Kalau chunk tempat badannya berdiri kebetulan tidak termuat, tidak ada yang bisa
ditembak: dia tetap gugur, hanya tanpa adegan.

### Waktu dia masuk lagi

Badan penggantinya dibubarkan, dan:

- **kalau masih hidup** — dia melanjutkan permainan dengan lapar dan nyawa yang
  sudah berjalan selama dia pergi;
- **kalau sudah gugur** — seragamnya dilepas dan dia langsung jadi penonton, karena
  tidak ada layar kematian yang bisa memberitahunya.

### Kalau yang pergi itu Jack: hitung mundur 90 detik

Ace yang pergi cukup dihukum fase menebak berikutnya. Jack tidak — permainan berhenti
berjalan kalau pemburunya tidak ada. Jadi begitu seorang Jack keluar, hitung mundur
`jackOfflineSeconds` (bawaan 90 detik) mulai berjalan.

Hitung mundurnya **cuma dicatat ke log server**, tidak pernah diumumkan ke pemain:
pengumuman apa pun tentangnya — bahkan sekadar "ada yang sedang dihitung mundur" —
sama saja mengumumkan siapa Jack, dan itu satu-satunya rahasia yang dijaga permainan
ini.

```
[JackOfHearts] Jack Budi keluar dari server. Hitung mundur 90 detik sebelum dieksekusi laser.
[JackOfHearts] Jack Budi masih offline: sisa 60 detik.
[JackOfHearts] Jack Budi masih offline: sisa 30 detik.
[JackOfHearts] Waktu habis: laser dari langit menembak badan Jack Budi.
```

Kalau waktunya habis, laser turun **dari langit** — bukan dari Terminal Tebakan,
karena hukuman ini memang tidak datang dari ruang tebak mana pun. Badannya ambruk
dengan animasi yang sama, dan panelnya padam.

Masuk lagi kapan pun sebelum sinarnya benar-benar jatuh membatalkan semuanya,
termasuk selama laser sudah mengisi daya — sinarnya tinggal memudar tanpa mengenai
apa pun. Yang dituju aturan ini adalah Jack yang menghilang, bukan Jack yang
koneksinya putus sebentar.

#### Kalau itu Jack terakhir

Permainan berakhir di situ, dan urutannya begini:

1. laser jatuh, badannya ambruk;
2. ~6,5 detik kemudian kamera **semua pemain** berpindah ke Papan Peserta;
3. panelnya padam di depan mata mereka;
4. barulah kemenangan Ace diumumkan.

Kemenangannya sengaja ditahan sampai adegan papan selesai. Kalau diumumkan lebih
dulu, layar kemenangan menutupi satu-satunya adegan yang menjelaskan **kenapa**
permainannya berakhir.

### Skin: kenapa UUID disimpan sejak pendaftaran

Client mengambil skin dari daftar pemainnya sendiri, dan orang yang keluar hilang
dari daftar itu — skinnya jatuh ke Steve/Alex. Padahal justru sesudah itu wajahnya
paling dibutuhkan: di potret papan, di badan penggantinya, dan di mayatnya.

Karena itu saat `/jack regis` (atau `regisall`, atau `/jack start`) server menyimpan
UUID beserta properti skin dari profil pemain, lalu membagikannya ke semua client.
Client juga mengingat sendiri setiap skin yang pernah dilihatnya. Titipan dari
server itu yang menutupi kasus terakhir: pemain yang baru bergabung **setelah**
orangnya keluar tidak pernah punya kesempatan melihatnya.

> Properti skin cuma ada di server yang memverifikasi akun ke Mojang (online-mode).
> Di server yang tidak, yang tersisa cuma ingatan masing-masing client dari saat
> orangnya masih online.

---

## Membungkus map ke dalam mod

Mod ini membawa **dimensi sendiri**: `jackofhearts:arena`. Map-nya ikut di dalam jar
sebagai **dunia jadi** — berkas region, bukan schematic — jadi dunia baru mana pun
langsung punya arena yang sama persis.

### Kenapa dimensi sendiri

Map permainan ini punya aturan yang bertabrakan dengan dunia biasa: pintu yang mengunci
sendiri, tong yang cuma bisa dibuka di fase tertentu, dan wadah yang ditandai jadi tempat
susu. Menaruhnya di overworld berarti aturan-aturan itu harus dijaga supaya tidak merembet
ke bangunan pemain di luar arena. Dimensi terpisah membuat batasnya jadi hal yang tidak
perlu dijaga sama sekali.

### Kenapa dunia jadi, bukan schematic

Schematic harus dipasang blok demi blok di satu tick: server diam beberapa detik, cahaya
dihitung ulang dari nol, dan tiap block entity dibangun ulang dari palet teks. Berkas
region tidak melewati satu pun dari itu — chunk-nya sudah jadi, lengkap dengan cahaya,
block entity, dan blok milik mod lain apa adanya. Yang dikerjakan cuma menyalin berkas.

### Cara membungkus map-mu

1. **Bangun map-nya di dimensi arena.** `/jack gotoarena` (dunia void, mulai dari nol),
   atau pasang sekali dari schematic dengan `/jack pastearena` lalu rapikan di situ.
2. **Tandai wadah susunya** dengan Penanda Wadah, lalu `/jack milkspots export` —
   berkasnya muncul di `config/jackofhearts/milkspots.json`, siap disalin ke
   `src/main/resources/data/jackofhearts/`.
3. **`/jack arena export`** — dunianya disimpan dulu, lalu berkasnya disalin ke
   `config/jackofhearts/arena_world/`.

> **Daftar wadah susu tidak ikut di dalam berkas dunia.** Dia tinggal di penyimpanan data
> overworld, karena itu satu-satunya dunia yang pasti ada. Yang membawanya berpindah dunia
> adalah `/jack milkspots export` dan `import` — dan `import` mendaftarkan semuanya ke
> **dimensi tempat perintahnya dijalankan**, jadi berdirilah di dalam map-nya saat
> menjalankannya.
4. **Pindahkan isinya** ke `src/main/resources/data/jackofhearts/arena_world/`, lalu build
   ulang.

```
src/main/resources/data/jackofhearts/arena_world/
├── region/     ← berkas .mca, ini yang wajib
├── entities/   ← entitas yang ikut disimpan chunk
├── poi/
└── data/       ← data dimensi
```

Sesudah itu, dunia baru mana pun tinggal `/jack gotoarena` — map-nya sudah ada di sana.

### Tidak ada mob di arena

Mob apa pun ditolak masuk dimensi arena, dari sumber mana pun — spawn alami, spawner,
telur, ternak yang beranak, maupun `/summon`. Bisa dimatikan lewat `arenaNoMobs`.

Diperiksa saat entitasnya **bergabung ke dunia**, bukan saat spawn alaminya dinilai:
spawn alami cuma satu dari banyak jalan masuk, dan satu pintu yang dijaga lebih rapat
daripada lima. Mayat, badan pengganti, kursi, dan kamera sinematik bukan mob, jadi
semuanya tetap bisa masuk.

### Kapan map bawaan dipasang

Saat server **hampir** menyala, sebelum satu dunia pun dimuat, dan **hanya kalau folder
dimensi arenanya masih kosong**. Waktunya menentukan: kalau dipasang belakangan, chunk
arena yang sudah terlanjur dibuat sebagai void akan menang dan berkas region bawaannya
tidak pernah terbaca.

Dunia yang arenanya sudah punya isi **tidak pernah ditimpa** — bangunan yang diubah di
server tetap milik server itu.

### Berkas lain yang bisa ikut dibungkus

| Berkas | Letaknya di sumber mod | Isinya |
|---|---|---|
| `food.schem` | `data/jackofhearts/schematics/` | Meja kafetaria, dipasang ulang tiap `/jack start` |
| `arena.schem` | `data/jackofhearts/schematics/` | Map arena versi schematic, kalau mau lewat `/jack pastearena` |
| `milkspots.json` | `data/jackofhearts/` | Daftar wadah susu |

Semuanya dibaca dari `config/jackofhearts/` lebih dulu kalau ada di situ — itu cara
menggantinya tanpa membongkar jar.

### Perintah arena

| Perintah | Fungsi |
|---|---|
| `/jack gotoarena` | Pindah ke arena, di titik `arenaSpawn` |
| `/jack gotooverworld` | Kembali ke titik spawn overworld |
| `/jack arena export` | Salin dimensi arena sekarang ke `config/`, siap dibungkus ke mod |
| `/jack pastearena` | Pasang map arena dari schematic (jalur alternatif) |
| `/jack milkspots export` | Tulis daftar wadah susu ke `config/jackofhearts/` |
| `/jack milkspots import` | Muat daftar wadah susu dari berkas (config, lalu jar) |

`/jack pastearena` cuma jalur alternatif kalau map-mu masih berupa schematic. Isinya bisa
jutaan blok dan dipasang di satu tick, jadi servernya akan diam beberapa detik — itu
sebabnya dia perintah tersendiri, dan sebabnya map yang sudah jadi lebih baik dibungkus
sebagai dunia.

### Yang terjadi saat `/jack start`

Sebelum satu kartu pun dibagikan, semuanya disiapkan dulu:

1. semua peserta dipindahkan ke **`arenaSpawn`** di dimensi arena;
2. mode permainan jadi **adventure**;
3. kesulitan dunia jadi **hard**;
4. **PvP dimatikan** (nilai lamanya disimpan dan dikembalikan saat permainan berhenti);
5. **jangkauan tangan jadi 3 blok** (`blockReach`) — vanilla 4,5, yang membuat setengah
   ruangan bisa disentuh dari ambang pintu;
6. **nyawa jadi 5 (2,5 hati)** (`playerHealth`) — batang nyawanya ikut memendek, bukan
   cuma diisi separuh, jadi yang terlihat pemain adalah keadaan yang sebenarnya;
7. **tas dikosongkan** dan **slot hotbar 1** yang terpegang;
8. baru animasi kocok kartu jalan.

Semuanya di satu tempat karena semuanya harus selesai **sebelum** layar kocok kartu muncul:
pemain yang masih tersebar di seluruh map saat animasinya jalan akan melihat kartunya
membalik sambil berdiri di tempat yang salah, dan begitu layarnya tertutup dia sudah
tertinggal satu ronde.

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

Ada dua cara stok satu game dihitung, dan `/jack setting maxfood` yang memilihnya:

| Perintah | Stok satu game |
|---|---|
| `/jack setting maxfood default` | Kembali ke bawaan: **8 porsi × jumlah pemain** |
| `/jack setting maxfood <porsi>` | **angka pasti**, berapa pun jumlah pemainnya |

Dengan setelan bawaan: 2 pemain = 16 porsi, 4 pemain = 32, 8 pemain = 64, 12 pemain =
96. Jumlah pemain yang dipakai adalah pemain yang **benar-benar ikut** saat
`/jack start`, bukan jumlah yang terdaftar — yang sedang offline tidak makan.

`default` mengembalikan **dua-duanya**: angka total dimatikan, dan jatah per pemain
dikembalikan ke 8. Kalau cuma angka totalnya yang dimatikan, jatah per pemain yang
pernah dipasang lewat versi lama perintah ini akan tertinggal di config — dan "default"
jadi bukan default.

Setelannya baru berlaku di game berikutnya. Untuk memberlakukannya sekarang juga,
pakai `/jack refill`.

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
tekanannya, dan ronde-ronde terakhir dijalani dengan perut kosong.

Perlu diingat permainannya jarang benar-benar sampai ronde 10: begitu semua Ace mati
atau Jack mati, permainannya selesai. Realistisnya **5–7 ronde ≈ 25–35 menit**, dan
angka di atas dihitung untuk itu.

> **Kalau ronde dipotong lebih cepat** (skip, atau `roundSeconds` lebih kecil), stok yang
> sama otomatis menutup lebih banyak ronde — hitungannya berbasis menit, bukan ronde.
> Sebaliknya, kalau `roundSeconds` masih 600 (10 menit per ronde) permainannya dua kali
> lebih panjang: kalikan dua `foodPerPlayer`.

### Kalau kafetarianya "kosong padahal mejanya jelas ada"

Hampir selalu satu sebab: **mesin permainan sedang melihat dimensi yang berbeda dengan
yang kamu tempati.** Ada dua setelan dimensi, dan keduanya gampang ketinggalan menunjuk
dunia lama:

| Setelan | Isinya |
|---|---|
| `arenaDimension` | Dunia tempat permainan berlangsung |
| `areaDimension` | Dunia tempat kotak kafetaria berada |

`/jack setting list` menampilkan keduanya beserta dunia yang sedang kamu tempati, dan
menandai yang dunianya tidak ada:

```
arena: jackofhearts:arena | kafetaria: minecraft:overworld | kamu di: minecraft:overworld
Arena dan kafetaria di dimensi yang berbeda. Biasanya keduanya harus sama.
```

Membetulkannya tidak perlu menyentuh berkas config — berdiri di tempatnya, lalu:

```
/jack setting arenahere              (dimensi arena + titik kumpul = posisimu)
/jack setting area <sudut> <sudut>   (kotak kafetaria + dimensinya = tempat kamu berdiri)
```

> Ingat: angka di kode cuma dipakai saat berkas config dibuat pertama kali. Server yang
> sudah pernah jalan memakai isi `config/jackofhearts-common.toml`, dan di situlah setelan
> lama bertahan.

### Meja dipasang ulang dari schematic

Kafetaria ini memulihkan mejanya sendiri dari potret yang dibaca berkala, dan itu cukup
untuk pemakaian biasa. Tapi potret cuma seakurat keadaan terakhir yang sempat terbaca:
meja yang telanjur diobrak-abrik sebelum potretnya sempat diperbarui akan dipulihkan ke
keadaan yang sama-sama berantakan.

Karena itu meja bisa dipasang ulang dari **schematic**, dan isinya tidak pernah berubah
lagi — tiap permainan mulai dari meja yang benar-benar sama.

**Schematic bawaannya sudah ikut di dalam jar mod**
(`data/jackofhearts/schematics/food.schem`), jadi tidak ada yang perlu disiapkan
sebelum bermain. Untuk menggantinya tanpa membongkar jar, taruh berkas dengan nama
yang sama di `config/jackofhearts/` — berkas di situ selalu menang.

Nama berkas dan koordinatnya diatur di config:

```toml
foodSchematic = "food.schem"
foodSchematicOrigin = "-4 1 -44"
```

`foodSchematicOrigin` adalah tempat mendaratnya **sudut schematic dengan koordinat
paling kecil** (blok pertama di dalamnya) — bukan titik tempat kamu berdiri saat
menyalinnya di WorldEdit. Kalau hasilnya meleset, geser titik ini sebanyak selisihnya.

> **Mengubah angkanya di kode tidak ada gunanya kalau servernya sudah pernah jalan.**
> Angka di `defineInRange`/`define` cuma dipakai saat berkas config dibuat pertama kali;
> sesudah itu isi `config/jackofhearts-common.toml` yang menang. Ubah lewat perintah:
>
> ```
> /jack setting foodschem ~ ~ ~     (berdiri tepat di sudut yang dituju)
> /jack setting foodschem -4 1 -45
> /jack refill                      (pasang sekarang juga)
> ```

Tiap kali dipasang, batas hasilnya dicatat ke log server:

```
[JackOfHearts] Schematic makanan: 320 blok, -4, 1, -45 .. 2, 3, -40
```

Bandingkan angka itu dengan sudut meja yang sebenarnya — selisihnya persis yang perlu
ditambahkan ke `foodschem`, jadi tidak perlu ditebak sama sekali.

Yang dibaca format **Sponge Schematic** (`.schem` keluaran WorldEdit), versi 2 maupun 3.
Format lama `.schematic` bawaan MCEdit tidak dibaca: isinya id blok berupa angka dari
zaman sebelum blockstate, dan tidak ada lagi tabel yang bisa menerjemahkannya di 1.20.

Blok udara di dalam schematic **ikut dipasang**, persis seperti `//paste` biasa. Itu
yang membuat pemasangan ulang bisa diandalkan: piring yang tertinggal di luar susunan
aslinya ikut tersapu, bukan menumpuk tiap kali dipasang lagi.

Dipasang ulang di dua tempat:

- **tiap `/jack start`**, sebelum penataan mejanya dibaca — yang dipakai sepanjang
  permainan adalah isi schematic, bukan potret keadaan terakhir;
- **tiap `/jack refill`**, sekalian mengisi stoknya kembali penuh dengan setelan
  `maxfood` yang berlaku sekarang.

Kalau berkasnya tidak ada, mod jatuh kembali ke potret berkala seperti biasa — tidak ada
yang rusak, cuma tidak sekonsisten schematic.

### Makanan tidak bisa dititipkan ke mana pun

Batas bawa satu porsi (`foodCarryLimit`) adalah seluruh isi mekanik kafetaria: ambil
satu, habiskan, baru boleh ambil lagi. Tanpa penjagaan tambahan batas itu tidak ada
artinya — ambil satu, taruh di peti, ambil lagi, dan dalam semenit satu orang sudah
memindahkan seluruh meja ke dalam kotaknya sendiri.

Jadi selama permainan berjalan:

- makanan kafetaria **tidak bisa dijatuhkan** — tombol Q maupun diseret keluar layar tas.
  Barangnya **kembali ke tas**, bukan lenyap: membatalkan kejadian jatuhnya cuma menahan
  barang itu masuk ke dunia, sedangkan dari tas dia sudah terlanjur dikeluarkan sebelum
  kejadiannya dipicu. Tanpa dikembalikan, menjatuhkan makanan justru jadi cara paling
  cepat menghabiskan stok;
- makanan yang dititipkan ke **wadah apa pun** — peti, tungku, barrel, shulker, kotak
  kerajinan — **balik lagi ke tas** dalam tick yang sama;
- makanan kafetaria **tidak bisa diklik-kanankan ke entitas apa pun** — bingkai barang,
  hewan, gerobak berpeti;
- bekal yang dibawa ikut lenyap bersama pemiliknya kalau dia mati, jadi mati kelaparan
  tidak bisa dipakai untuk menjatuhkan bekal buat orang lain.

**Yang dikembalikan cuma yang baru masuk.** Patokannya isi wadah saat pertama dibuka:
peti yang sudah ada rotinya sejak sebelum permainan dimulai adalah isi dunia, bukan
hasil curian meja, dan tidak disentuh. Patokannya ikut turun kalau isinya berkurang, jadi
mengambil roti yang memang sudah ada di peti itu tetap boleh.

Pengecekannya jalan tiap tick, bukan saat kliknya, karena tidak ada kejadian klik slot
yang bisa dicegat: Forge tidak menyediakannya, dan tiap wadah punya jalur pemindahannya
sendiri (klik, geser, shift-klik, tombol angka). Memeriksa hasilnya tiap tick menangkap
semuanya sekaligus.

Penolakannya muncul di baris status, bukan chat: percobaannya sering terjadi berkali-
kali dalam sedetik, dan chat yang membanjir bikin orang berhenti membaca chat sama
sekali.

**Mode kreatif tidak dikecualikan.** Selama permainan berjalan, host yang sedang kreatif
tetap orang yang bisa memindahkan makanan ke peti, dan pengecualian itu justru
satu-satunya lubang yang tersisa. Kalau memang perlu menata ulang dunia, hentikan dulu
permainannya.

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
pernah didatangi siapa pun. Karena itu ada `hungerIdleDrain`.

**`hungerIdleDrain` itu lantai, bukan tambahan.** Tiap tick yang dipakai adalah yang
lebih besar antara exhaustion dari kegiatan pemain dan tetesan diam ini, lalu baru
dikali `hungerMultiplier`:

```
drain = max(exhaustion kegiatan, hungerIdleDrain) × hungerMultiplier
```

Kalau tetesannya dijumlahkan (dan dulu memang begitu), yang berlari membayar dua kali —
biaya larinya dikali, lalu ditambah lagi tetesan yang seharusnya cuma untuk yang tidak
berbuat apa-apa. Pengali yang dirasakan tiap orang jadi berbeda-beda tergantung seberapa
aktif dia. Dengan lantai, **pengalinya 3× untuk semua orang**, dan angka di config
berarti apa adanya.

Bawaannya 0,07 × 3,0 = 0,21/detik untuk yang diam, artinya batang lapar penuh habis
sekitar 6,3 menit walau pemainnya cuma berdiri diam.

Inilah satu-satunya angka yang menentukan cepat-lambatnya lapar buat pemain yang cuma
jalan dan mengobrol — buat mereka sumber lapar yang lain hampir nol. Terlalu sering
bolak-balik ke kafetaria? Turunkan ke 0,05. Masih terasa lambat? Naikkan ke 0,09.

Ketiganya bisa disetel selagi server jalan: `/jack setting hungermultiplier <angka>`
(boleh pecahan, mis. `2.5`) dan `/jack setting foodhunger <lapar> <saturasi>`.

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
| `/jack setting jack max <angka>` | Berapa Jack yang dibagikan; tersimpan ke config |
| `/jack setting jack set <targets> [once\|always]` | Pemain ini **pasti** dapat peran Jack; tanpa argumen = `once` |
| `/jack setting jack unset <targets>` | Batalkan penunjukan itu |
| `/jack setting jack clear` | Kosongkan seluruh daftar penunjukan Jack |
| `/jack setting unknown set <targets> [once\|always]` | Pemain ini **pasti** dapat peran Unknown; tanpa argumen = `once` |
| `/jack setting unknown unset <targets>` | Batalkan penunjukan itu |
| `/jack setting unknown clear` | Kosongkan seluruh daftar penunjukan Unknown |
| `/jack refill` | Pasang ulang meja dari schematic dan isi stok penuh dengan `maxfood` terbaru |
| `/jack setting maxfood <porsi>` | Stok satu game, angka pasti |
| `/jack setting maxfood default` | Kembali ke bawaan: **8 porsi x jumlah pemain** |
| `/jack setting drunk on\|off\|max <n>` | Nyalakan/matikan skill Mabuk, atau atur jatahnya |
| `/jack setting hunger on\|off\|max <n>` | Sama, untuk skill Lapar |
| `/jack setting poison on\|off\|max <n>` | Sama, untuk skill Racun |
| `/jack setting unknown on\|off\|max <n>` | Bagikan peran Unknown atau tidak (bawaan **off**), dan berapa orang (bawaan **1**) |
| `/jack setting milk max <n>` | Berapa susu yang muncul sepanjang satu permainan |
| `/jack milkreveal` | Di mana saja susu yang belum diambil |
| `/jack checkblock [true\|false]` | Percikan penanda di atas tiap wadah terdaftar |
| `/jack milkspots clear` | Kosongkan seluruh daftar wadah susu |
| `/jack milkspots <jenis> <true\|false>` | Nyalakan/matikan satu jenis wadah: `chest`, `furnace`, `dispenser`, `hopper`, `barrel` |
| `/jack setting area <from> <to>` | Kotak kafetaria **beserta dimensinya**, diambil dari tempat kamu berdiri |
| `/jack setting arenahere` | Dimensi arena + titik kumpulnya, diambil dari tempat kamu berdiri |
| `/jack setting foodschem <x y z>` | Sudut mendaratnya schematic meja; `~ ~ ~` = posisimu |
| `/jack setting settimer round <detik>` | Durasi satu ronde |
| `/jack setting settimer guess <detik>` | Durasi fase menebak |
| `/jack setting hungermultiplier <angka>` | Pengali kecepatan lapar; boleh pecahan (`2.5`) |
| `/jack setting foodhunger <lapar> <saturasi>` | Nilai gizi satu porsi makanan kafetaria |
| `/jack setting list` | Tampilkan setelan yang berlaku sekarang |
| `/jack stop` | Hentikan game, lepas semua seragam |
| `/jack status` | Fase, ronde, sisa waktu, dan daftar peserta + status hidup/mati |
| `/jack skip` | **Semua pemain.** Satu suara untuk mempercepat ronde ke fase menebak |
| `/jack forceskip` | Host: potong sisa waktu fase yang sedang berjalan, tanpa menunggu siapa pun |
| `/jack debug true\|false [<targets>]` | Tebakan sasaran dinilai **benar** untuk **ronde yang sedang berjalan saja** (buat testing); tanpa target = dirimu sendiri |
| `/jack shuffle <pemain> [<jack>] [as_jack\|as_ace\|as_unknown]` | Putar animasi kartu untuk preview, tanpa memulai game |
| `/jack reveal` | Tampilkan peran + simbol semua peserta, hanya ke kamu (host) |

### Penunjukan peran: `jack set` dan `unknown set`

`jack max` adalah batas yang **dinaikkan** oleh `jack set`, bukan yang membatasinya.
Kalau `jack max` 2 tapi ada 3 orang yang ditunjuk, yang mengalah batasnya — game itu
dibagikan 3 Jack. Menolak orang ketiga berarti perintah yang sudah dijalankan host
diam-diam tidak berlaku, dan dia baru sadar setelah gamenya jalan.

`unknown set` sama persis terhadap `unknown max`, dan **tetap berlaku walau peran
Unknown sedang `off`**: host yang mengetik "orang ini Unknown" jelas ingin ada Unknown
di game itu. Satu-satunya yang bisa mengalahkannya adalah jumlah pemain — selalu
disisakan paling tidak satu Ace, dan yang ditunjuk tapi tidak kebagian kursi jadi Ace.

Satu orang cuma bisa ditunjuk untuk satu peran: menunjuknya jadi Unknown mencabut
penunjukannya jadi Jack, dan sebaliknya.

Yang ditunjuk pasti dapat perannya; sisa kursi yang belum terisi tetap jatuh ke orang
acak seperti biasa. Yang sedang offline atau jadi penonton saat `/jack start` tidak
dihitung sama sekali.

`once` (dan itu yang berlaku kalau argumennya tidak diisi) hangus begitu `/jack start`
dijalankan — di detik penunjukannya terpakai, bukan saat gamenya selesai. Game yang
dihentikan di tengah jalan tetap menghabiskan jatahnya, jadi "sekali" benar-benar
berarti sekali. `always` bertahan sampai dibatalkan dengan `unset`/`clear`.

Yang harus diketik justru `always`, karena penunjukan yang berlaku selamanya adalah
yang paling gampang dilupakan.

Daftarnya tidak pernah diumumkan ke pemain mana pun. Sasarannya sendiri juga tidak diberi tahu:
peran baru boleh diketahui pemiliknya lewat animasi pembagian peran, dan pesan "kamu
dipilih jadi Jack" akan mendahuluinya sekaligus membocorkan ke siapa pun yang
kebetulan melihat layarnya.

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
