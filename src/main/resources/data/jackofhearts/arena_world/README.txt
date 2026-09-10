Map arena dibungkus di sini sebagai dunia jadi, bukan schematic.

Isi folder ini persis isi folder dimensi arena di sebuah dunia:

  region/    <- berkas .mca, ini yang wajib
  entities/  <- entitas yang ikut disimpan chunk (opsional)
  poi/       <- titik minat (opsional)
  data/      <- data dimensi, termasuk daftar wadah susu (opsional)

Cara mengisinya:

  1. Bangun map di dimensi arena (/jack gotoarena).
  2. Jalankan /jack arena export.
  3. Pindahkan isi config/jackofhearts/arena_world/ ke sini.
  4. Build ulang mod-nya.

Saat server menyala, isi folder ini disalin ke dunia yang sedang dibuka HANYA kalau
dimensi arenanya masih kosong. Dunia yang sudah punya isi tidak pernah ditimpa.
