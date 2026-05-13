# Exambro Overlay — Panduan Setup

## Struktur Project
```
ExambroOverlay/
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/exambro/overlay/
        │   ├── MainActivity.kt
        │   └── OverlayService.kt
        └── res/
            ├── drawable/
            │   ├── btn_footer_selector.xml
            │   └── ic_more_vert.xml
            ├── layout/
            │   ├── activity_main.xml
            │   ├── overlay_header.xml
            │   └── overlay_footer.xml
            └── raw/
                └── exit_sound.mp3  ← TARUH FILE BUNYI KAMU DI SINI
```

---

## Cara Tambah File Bunyi

1. Buat folder `app/src/main/res/raw/` jika belum ada
2. Taruh file audio kamu di sana
3. Rename jadi `exit_sound.mp3` (atau sesuaikan nama di `OverlayService.kt` baris:)
   ```kotlin
   val mediaPlayer = MediaPlayer.create(this, R.raw.exit_sound)
   ```

Format yang didukung: `.mp3`, `.wav`, `.ogg`

---

## Cara Import ke Android Studio

1. Buka Android Studio
2. File → New → Import Project
3. Pilih folder `ExambroOverlay`
4. Tunggu Gradle sync selesai
5. Tambah file bunyi di `res/raw/`
6. Run ke HP

---

## Permission yang Dibutuhkan (otomatis diminta)

- **Draw Over Other Apps** → muncul otomatis saat pertama kali tekan tombol
- Aktifkan manual di Settings → Apps → Exambro → Display over other apps

---

## Cara Pakai

1. Buka app Exambro
2. Tekan **"Aktifkan Overlay"**
3. Jika diminta permission, izinkan, lalu tekan tombol lagi
4. App otomatis minimize
5. Buka **Chrome** → buka Google Form
6. Header & footer Exambro muncul di atas Chrome
7. Untuk matiin: tekan **titik tiga** di header → "Matikan Overlay"
   atau tekan tombol **EXIT** di footer

---

## Tombol Footer

| Tombol | Fungsi |
|--------|--------|
| BACK | Info toast (gunakan tombol hardware) |
| EXIT | Matikan overlay + bunyi |
| REFRESH | Info toast |
| FORWARD | Info toast |

