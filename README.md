![app icon](https://github.com/user-attachments/assets/55bea361-6a4e-44a1-bf95-e8bed641b10a)

# Immich Muzei Plugin

A [Muzei](https://muzei.co/) plugin that displays photos from your [Immich](https://immich.app/) library as your Android wallpaper.

<img height="600" alt="immich-muzei-screenshot" src="https://github.com/user-attachments/assets/467b08e0-431f-474c-8285-04382e6b9da5" />


## Current features

- Connect to your Immich server using server URL and API key
- Browse and select albums to display
- Browse and select tags to filter photos
- Filter photos by date range
- Show only favorited photos (optional)
- Automatically rotates through random photos from selected albums/tags
- "Open in Immich" action to view the current photo in your Immich instance
- "Add to favorites" action within Muzei UI

## Quick start

Prefer using Android Studio for editing, building and installing the app. Open the project in Android Studio and run the `:immich` module on a device or emulator.

If you need CLI commands, run these from the repository root (fish/zsh/bash):

Build debug APK:

```bash
./gradlew :immich:assembleDebug
```

Build release APK:

```bash
./gradlew :immich:assembleRelease
```

You can also pass version properties for a release build:

```bash
./gradlew :immich:assembleRelease -Pversion.name=1.0.0 -Pversion.code=1
```

## Requirements

- Android SDK 23 (Android 6.0) or higher
- An Immich server instance with API access

## Dependencies

- [Muzei API](https://github.com/muzei/muzei) - 3.4.2 (from Maven Central)
- Jetpack Compose
- Retrofit + OkHttp for API calls
- Coil for image loading
- Kotlinx Serialization
