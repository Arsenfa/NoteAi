<div align="center">
<img width="1200" height="400" alt="NoteAi Banner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

<h1 align="center">NoteAi</h1>

<p align="center">
  <b>Production-ready note-taking Android app</b> powered by Gemini AI.<br/>
  Built with Jetpack Compose, Material 3, Firebase, and MVVM — a showcase portfolio of modern Android development.
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2024.09-4285F4" />
  <img alt="Android" src="https://img.shields.io/badge/Android-24%2B-3DDC84?logo=android" />
  <img alt="Firebase" src="https://img.shields.io/badge/Firebase-BOM%2034.12-FFCA28?logo=firebase" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue" />
</p>

---

## Features

**Notes & Organization**
- Create, edit, tag, and pin notes
- Rich checklist support with JSON-persisted state
- Grid and list views with a toggle
- Full-text search across titles, bodies, and tags
- Category chips (Today, Action items, Meeting notes, Ideas)
- Pin/unpin toggle directly on note cards

**Gemini AI (user-supplied API key)**
- AI title generation from note body
- Note summarization, action-item extraction, rewrites
- "Ask NoteAi" chat overlay with conversation history
- **Vision OCR** — snap a whiteboard or upload a gallery image; Gemini extracts text and describes it
- Users paste their own free Google AI Studio key into Settings — no server-side key management

**Voice**
- Real speech-to-text dictation via Android `SpeechRecognizer` (not a mock)
- Voice memo attachments with duration tracking
- Live partial transcript preview while dictating

**Authentication**
- Firebase Auth: Email/Password, Google One Tap (real CredentialManager flow), Anonymous
- Account linking (anonymous → email or Google)
- Persistent login via DataStore preferences

**Cloud Sync**
- Firestore-backed cloud sync scoped per user (`users/{uid}/notes/{noteUuid}`)
- UUID-based dedup so notes don't collide across devices
- `updatedAt` timestamp conflict resolution
- Manual sync button in dashboard header (animated spinner)
- Automatic sync after create / update / delete / pin
- Offline mode toggle

**Daily Briefing**
- Time-aware greeting with the signed-in user's name
- Highlights of notes edited in the last 7 days
- Aggregated open action items from every checklist

**Polish**
- Material 3 theming (Light / Dark / Auto)
- Multi-language (English, Indonesian)
- Firebase Crashlytics wired for crash reporting
- Room database with additive migration (v1 → v2)

## Tech Stack

| Layer | Tech |
|---|---|
| UI | Jetpack Compose + Material 3 |
| State | Kotlin `StateFlow`, `combine`, `flatMapLatest` |
| Persistence | Room 2.7 (KSP), DataStore Preferences |
| Auth / Sync | Firebase Auth, Firestore, Crashlytics (BOM 34.12) |
| Google Sign-In | AndroidX CredentialManager + `googleid` 1.1.1 |
| AI | Gemini REST API (`gemini-3.5-flash`) via OkHttp |
| Voice | Android `SpeechRecognizer` |
| Camera | CameraX (camera2 + view + lifecycle) |
| Images | Coil Compose |
| Build | Gradle version catalog, Secrets plugin, KSP |

## Architecture

```
┌───────────────────────┐    ┌───────────────────────┐    ┌──────────────────┐
│  Compose Screens      │◀──▶│  ViewModels (MVVM)    │◀──▶│  Repository      │
│  (MainActivity.kt)    │    │  (NoteViewModel,      │    │  (NoteRepository,│
│                       │    │   AuthViewModel)      │    │   AuthRepository,│
│                       │    │                       │    │   AIRepository,  │
│                       │    │                       │    │   NoteSyncMgr)   │
└───────────────────────┘    └───────────────────────┘    └────────┬─────────┘
                                                                   │
                                      ┌────────────────────────────┼───────────────────────────┐
                                      ▼                            ▼                           ▼
                              ┌──────────────┐            ┌──────────────┐            ┌──────────────┐
                              │ Room DAO     │            │ Firebase     │            │ Gemini REST  │
                              │ (NoteDao)    │            │ Auth + FS    │            │ (GeminiSvc)  │
                              └──────────────┘            └──────────────┘            └──────────────┘
```

All network / DB work runs on `Dispatchers.IO` inside `viewModelScope.launch`. UI reads `StateFlow`s via `collectAsState()` — no LiveData.

## Run Locally

**Prerequisites:** Android Studio (latest stable), JDK 17+, Android SDK 36.

1. Clone the repo
   ```bash
   git clone https://github.com/Arsenfa/NoteAi.git
   cd NoteAi
   ```

2. **Firebase setup** — in the [Firebase console](https://console.firebase.google.com/):
   - Create a project and add an Android app with package `com.aistudio.noteai.kxmpzq`
   - Enable **Authentication** providers: Email/Password, Google, Anonymous
   - Create a **Cloud Firestore** database (any location)
   - Download the fresh `google-services.json` into `app/`
   - Copy the **Web client ID** (client_type 3) from `google-services.json` and update `GOOGLE_WEB_CLIENT_ID` in `app/build.gradle.kts`

3. **Gemini API key** — get a free key from [Google AI Studio](https://aistudio.google.com/apikey). Either:
   - Drop it in `.env` as `GEMINI_API_KEY=your_key` (used by the Secrets plugin as a build-time fallback), **or**
   - Skip this step and let users paste their own key at runtime via the Settings screen.

4. Open the project in Android Studio, let Gradle sync, then run on an emulator or physical device.

## Firebase Security Rules

`firestore.rules` ships with user-scoped read/write:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/notes/{noteUuid} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

Deploy with `firebase deploy --only firestore:rules` after `firebase init`.

## Project Layout

```
app/src/main/java/com/example/
├── MainActivity.kt              # All Compose screens + navigation host
├── data/
│   ├── Note.kt                  # Room entity
│   ├── NoteDao.kt               # Room DAO
│   ├── AppDatabase.kt           # Room DB + migrations
│   ├── NoteRepository.kt        # Local persistence wrapper
│   ├── NoteSyncManager.kt       # Firestore push/pull
│   ├── AuthRepository.kt        # Firebase Auth wrapper
│   ├── GoogleAuthHelper.kt      # CredentialManager One Tap
│   ├── GeminiService.kt         # Gemini REST client
│   ├── AIRepository.kt          # Text + vision AI calls
│   └── SpeechRecognitionHelper.kt
├── viewmodel/
│   ├── NoteViewModel.kt         # Notes, sync, AI, voice, settings
│   └── AuthViewModel.kt         # Auth state machine
└── ui/theme/                    # Material 3 theme + colors
```

## Testing

```bash
./gradlew testDebugUnitTest      # JVM unit tests (Robolectric)
./gradlew assembleDebug          # Debug APK
```

## License

MIT — free to fork, adapt, and ship your own version.
