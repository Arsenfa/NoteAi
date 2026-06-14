# Tentang Proyek: NoteAi 📝✨

**NoteAi** adalah aplikasi pencatatan (*note-taking*) modern untuk platform Android yang dirancang dengan performa tinggi dan fitur pintar berbasis **Kecerdasan Buatan (AI)**. Aplikasi ini dibuat menggunakan arsitektur modern Android dan berfungsi sebagai portofolio implementasi teknologi terbaru.

---

## 🚀 Fitur Utama

### 1. Manajemen & Organisasi Catatan
*   **Pencatatan Pintar:** Membuat, mengedit, menghapus, menandai (*tagging*), dan menyematkan (*pin*) catatan penting.
*   **Format Checklist:** Mendukung pembuatan daftar tugas (*checklist*) interaktif yang disimpan secara lokal dalam format JSON.
*   **Tampilan Dinamis:** Pilihan tampilan daftar (*list*) atau kisi (*grid/masonry*) dengan transisi animasi yang mulus.
*   **Pencarian Pintar:** Fitur pencarian teks lengkap (*full-text search*) yang mencakup judul, isi catatan, hingga tag.

### 2. Integrasi Kecerdasan Buatan (Gemini AI)
Aplikasi ini terhubung langsung ke **Gemini REST API** menggunakan kunci API yang dapat dimasukkan pengguna sendiri di halaman Pengaturan (tanpa perlu server perantara).
*   **Auto AI Title:** Membuat judul catatan secara otomatis berdasarkan isi catatan.
*   **Fitur AI Ringkasan & Checklist:** Membuat ringkasan (*summarize*), mengekstrak daftar tugas dari teks catatan, dan menulis ulang teks.
*   **Chatbot NoteAi:** Overlay chat interaktif dengan riwayat obrolan untuk berdiskusi dengan AI seputar catatan Anda.
*   **Vision OCR:** Mengambil teks dari foto papan tulis (*whiteboard*) atau galeri gambar menggunakan kemampuan visi Gemini, serta mendeskripsikan isi gambar tersebut.

### 3. Voice & Input Suara
*   **Dikte Suara (Speech-to-Text):** Menggunakan fitur bawaan Android `SpeechRecognizer` untuk mengetik dengan suara secara real-time.
*   **Voice Memo:** Merekam suara dan melampirkannya ke dalam catatan lengkap dengan pelacakan durasi.

### 4. Sinkronisasi Cloud & Keamanan (Firebase)
*   **Firebase Authentication:** Login menggunakan Email/Password, Google One Tap (menggunakan CredentialManager terbaru), atau mode Anonim (Tamu).
*   **Penyatuan Akun:** Pengguna tamu dapat menautkan akun mereka ke Email atau Google tanpa kehilangan data lokal.
*   **Firestore Cloud Sync:** Sinkronisasi cloud otomatis dan aman yang dibatasi per-pengguna (`users/{uid}/notes/{noteUuid}`) dengan resolusi konflik berbasis waktu pembaruan terakhir (*updatedAt*).
*   **Mode Offline:** Opsi untuk mematikan sinkronisasi cloud dan menggunakan aplikasi sepenuhnya secara lokal.

---

## 🛠️ Tech Stack & Arsitektur

Proyek ini mengadopsi standar pengembangan Android modern 2026:

| Lapisan / Fitur | Teknologi yang Digunakan |
|---|---|
| **Bahasa Utama** | Kotlin 2.2.10 |
| **User Interface (UI)** | Jetpack Compose + Material 3 |
| **Arsitektur** | MVVM (Model-View-ViewModel) dengan StateFlow & Coroutines |
| **Database Lokal** | Room Database 2.7 (KSP) |
| **Penyimpanan Lokal** | DataStore Preferences (untuk session & settings) |
| **Backend & Sync** | Firebase Auth & Cloud Firestore |
| **Integrasi Google Sign-In** | AndroidX CredentialManager + `googleid` |
| **Kecerdasan Buatan (AI)** | Gemini REST API (`gemini-3.5-flash`) via OkHttp |
| **Voice & Media** | Android SpeechRecognizer & CameraX SDK |
| **Pemuatan Gambar** | Coil Compose |

---

## 🛠️ Perbaikan & Polesan Terbaru (Hasil Refactoring)

Aplikasi ini baru saja melewati tahap peningkatan kualitas besar-besaran untuk menjamin pengalaman pengguna yang premium:
*   **Kompatibilitas Dark Mode:** Semua warna keras (seperti putih/abu-abu statis) telah diganti menjadi MaterialTheme token sehingga teks tetap terbaca jelas saat beralih ke tema gelap.
*   **Penghapusan Bug Duplikasi Tag:** Tampilan kolom edit tag yang ganda pada halaman editor telah diganti dengan *inline tag editor* menggunakan `AnimatedVisibility` dan chip yang bisa diklik untuk menghapus tag.
*   **Navigasi Mulus:** Animasi transisi halaman sekarang menggunakan efek geser (*slide*) dan pudar (*fade*) yang halus.
*   **Optimalisasi Performa:** Pengumpulan data Flow di UI kini menggunakan `collectAsStateWithLifecycle()` untuk mencegah kebocoran memori saat aplikasi sedang di latar belakang.
*   **Floating Dock Premium:** Menu navigasi bawah kini melayang dengan efek bayangan dan memiliki getaran (*haptic feedback*) saat tombol tambah ditekan.
