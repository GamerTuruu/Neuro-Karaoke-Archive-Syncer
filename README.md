# Neuro Karaoke Archive Syncer

An Android app that keeps your local [Neuro Karaoke Archive](https://drive.google.com/drive/folders/1B1VaWp-mCKk15_7XpFnImsTdBJPOGx7a) MP3 collection in sync with Google Drive and automatically applies accurate ID3 tags from the [community metadata repository](https://github.com/Nyss777/Neuro-Karaoke-Archive-Metadata).

## Features

- **Drive sync** — Download new songs from the official Google Drive archive folder
- **Metadata sync** — Pull HJSON metadata from GitHub and embed ID3v2.4 tags (title, artist, album, disc, track, cover art)
- **Tag presets** — Choose how tags are formatted: Default, OG-only (Japanese), or English-only
- **Song browser** — Search and filter your local archive; long-press to exclude songs
- **Scheduled background sync** — WorkManager-based periodic sync (configurable interval)
- **Manual sync** — One-tap sync from the home screen with live progress

## Requirements

- Android 10 (API 29) or later
- A [Google Drive API key](https://console.cloud.google.com/) (no OAuth needed — read-only public files)
- Optionally a GitHub Personal Access Token for higher API rate limits

## Setup

1. Install the APK (download from [Releases](../../releases))
2. Open the app → **Settings**
3. Pick your local MP3 folder
4. Enter your Google Drive API key
5. Tap **Sync Now** on the home screen

## Building from Source

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/Neuro-Karaoke-Archive-Syncer.git
cd Neuro-Karaoke-Archive-Syncer

# Build debug APK
./gradlew :composeApp:assembleDebug

# APK output: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Requirements: JDK 21, Android SDK 34, Gradle (wrapper included).

## Tech Stack

- **Kotlin Multiplatform + Compose Multiplatform** (Android-first)
- **SQLDelight** — local database
- **Ktor** — HTTP client for Drive API and GitHub API
- **Koin** — dependency injection
- **WorkManager** — background sync
- **JAudioTagger** — ID3 tag reading/writing
- **hjson-java** — HJSON metadata parsing

## License

[MIT](LICENSE)
