# Launcher TV

Launcher Android TV buatan sendiri untuk Hisense 40E5G (Android 11, RAM 1GB). Ringan, tanpa iklan, dengan widget yang bisa diatur.

## Fitur

- Wallpaper slideshow (bawaan: 3 foto singa jantan dari Wikimedia Commons, bisa diganti)
- Widget cuaca, jadwal sholat, dan live CCTV (RTSP)
- Popup kecerahan dengan slider (tombol **Kecerahan** atau tombol MENU di remote)
- Suara navigasi dan klik
- Baris aplikasi terpasang (tahan OK pada aplikasi untuk membuka info aplikasinya)

## Pasang

1. Download `LauncherTV.apk` dari halaman **Releases** (rilis `latest`).
2. Pasang lewat adb:

   ```
   adb connect <IP_TV>:5555
   adb install -r LauncherTV.apk
   ```

3. Izin tambahan supaya peredup layar berlaku di semua aplikasi:

   ```
   adb shell appops set com.rafsan.tvlauncher SYSTEM_ALERT_WINDOW allow
   adb shell appops set com.rafsan.tvlauncher WRITE_SETTINGS allow
   ```

4. Buka "Launcher TV" dari daftar aplikasi, lalu tekan tombol Home dan pilih Launcher TV sebagai home kalau TV menanyakannya.

## Atur

Semua setelan ada di tombol **Pengaturan** di launcher: kota, negara, alamat RTSP, URL wallpaper, dan suara. Setelan disimpan di TV, tidak ada yang masuk ke repo ini.

Foto wallpaper sendiri (menggantikan URL):

```
adb push foto1.jpg /sdcard/Android/data/com.rafsan.tvlauncher/files/wallpapers/
```

Suara sendiri (menggantikan suara bawaan): taruh `nav.wav` dan `select.wav` di `/sdcard/Android/data/com.rafsan.tvlauncher/files/sounds/`.

## Catatan

- Kecerahan: Android TV tidak membuka kontrol backlight panel ke aplikasi biasa, jadi slider ini meredupkan gambar lewat lapisan gelap di atas layar. Backlight asli tetap diatur dari menu Picture TV.
- Sumber data: Open-Meteo (cuaca), Aladhan metode Kemenag (sholat).
- Foto bawaan dimuat langsung dari Wikimedia Commons saat launcher jalan (tidak disimpan di repo): "Male Lion 057" (Ltshears), "Lion (Panthera leo) (30941994012)" (Bernard Dupont), dan "Male lion, South Africa". Lisensi tiap foto ada di halaman Commons masing-masing.
- `keystore/debug.jks` adalah kunci debug publik, hanya supaya update APK bisa menimpa versi lama. Jangan dipakai untuk rilis ke orang lain.
