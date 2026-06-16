## 🚀 Fitivy Deployment & Production Checklist

Sebagai Engineer, saya bertanggung jawab penuh untuk memastikan fitur ini siap untuk *Production* dan tidak menyebabkan *regression bug* bagi ribuan mahasiswa.

### 📱 Android Readiness (Google Play Store)
- [ ] **ProGuard & R8 Obfuscation**: Fitur/kode baru telah dikecualikan atau diuji dengan ProGuard (tidak ada `ClassNotFoundException` saat Release build).
- [ ] **APK Signing Configuration**: Konfigurasi Keystore Release aman dan tidak *hardcoded* di repository.
- [ ] **Google Play Target API Level**: Memenuhi standar terbaru Google Play (Saat ini API 34).
- [ ] **App Bundle (.aab)**: Rilis menggunakan `.aab` untuk optimisasi ukuran aplikasi.
- [ ] **Sensors Degradation Graceful Handling**: Jika sensor accelerometer/GPS mati atau permission ditolak, aplikasi tetap bisa diakses (tidak *Force Close*).
- [ ] **TalkBack / Accessibility**: `contentDescription` telah disertakan pada UI element yang baru.

### ⚙️ Backend Readiness (Laravel API)
- [ ] **Environment Variables (.env)**: `APP_ENV=production` dan `APP_DEBUG=false` sudah dipastikan.
- [ ] **Database Migration & Seeding**: `php artisan migrate --force` sudah dicoba dan tidak bersifat *destructive* (merusak data lama).
- [ ] **Queue Worker**: Redis / SQS Queue Worker siap untuk menangani WorkManager background sync.
- [ ] **Cron Jobs / Task Scheduling**: `php artisan schedule:run` sudah diatur di cron job OS server.
- [ ] **SSL / HTTPS**: API hanya berjalan di *HTTPS* untuk mencegah token *sniffing* di WiFi kampus.
- [ ] **Rate Limiting**: Endpoint API (/api/sessions, /api/auth) diproteksi dengan limit via Laravel Throttle.

### 🔔 Firebase & External Services
- [ ] **FCM Production Key**: Server Key Firebase di `.env` Laravel sudah terhubung dengan project Firebase Production.
- [ ] **Google Maps / Leaflet**: API Key Maps Production siap dan dibatasi *referer*-nya khusus untuk URL Dashboard.

### 🧪 QA & Zero-Regression Testing
- [ ] **Unit Tests Passed**: Seluruh tes unit untuk kalkulasi sensor (Kalori, Steps) berjalan hijau.
- [ ] **Integration Tests Passed**: Sinkronisasi offline/online lewat Room dan API sudah berhasil di Robolectric/MockWebServer.
- [ ] **Benchmark Test**: API Endpoint utama merespons di bawah **< 500ms**.
- [ ] **Load Test / Seeding (15k records)**: Sistem database dan query Dashboard kuat menangani data 500 mahasiswa x 30 hari tanpa menyebabkan PHP Memory Exhaustion.

### 📈 Monitoring & Logging
- [ ] **Sentry Error Logging**: Integrasi Sentry/Bugsnag aktif untuk mendeteksi *Fatal Error* Laravel dan *Crashlytics* Android.
- [ ] **Uptime Check**: Endpoint `/api/health` hidup dan dimonitor secara eksternal.

---
**Engineer Sign-off:**
*Dengan menandai checkbox ini, saya menjamin kode telah ditinjau dan lolos standard QA.*
- [ ] Lolos Peer Code Review
- [ ] Lolos QA Sandbox / Staging Environment
