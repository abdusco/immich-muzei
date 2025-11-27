# Immich Muzei Plugin

A [Muzei](https://muzei.co/) plugin that displays photos from your [Immich](https://immich.app/) library as your Android wallpaper.

## Features

- Connect to your Immich server using server URL and API key
- Browse and select albums to display
- Browse and select tags to filter photos
- Show only favorited photos (optional)
- Automatically rotates through random photos from selected albums/tags
- "Open in Immich" action to view the current photo in your Immich instance
- "Add to favorites" action within Muzei UI
- **🆕 Home screen shortcut** to favorite the current wallpaper with one tap (Android 7.1+)

## Building

To build the debug APK:

```bash
./gradlew immich:assembleDebug
```

The APK will be generated at:
```
immich/build/outputs/apk/debug/immich-debug.apk
```

To build the release APK:

```bash
./gradlew immich:assembleRelease
```

To build with a specific version:

```bash
./gradlew immich:assembleRelease -Pversion.name=1.0.0 -Pversion.code=1
```

## Releases

Releases are automated via GitHub Actions. To create a release:

**Option 1: Create tag first (recommended)**
```bash
git tag v1.0.0
git push origin v1.0.0
```
Then create a release on GitHub using that tag.

**Option 2: Create directly from GitHub**
Create a release (draft or published) on GitHub and specify a tag name (e.g., `v1.0.0`).

When you **create** the release (even as a draft), GitHub Actions will:
- Automatically build the APK with the version from the tag
- Attach the APK to the release as `immich-{version}.apk`
- The version code is automatically set to the GitHub run number

You can then review the APK in the draft and publish when ready.

## Project Structure

- `muzei-api/` - The Muzei API library required for creating Muzei plugins
- `immich/` - The main Immich plugin module

## Installation

1. Build the APK using the command above
2. Install the APK on your Android device
3. Open Muzei and select "Immich" as your wallpaper source
4. Configure your Immich server URL and authentication in the plugin settings

## Requirements

- Android SDK 23 (Android 6.0) or higher
- An Immich server instance with API access

## Dependencies

- [Muzei API](https://github.com/muzei/muzei) - 3.4.2 (from Maven Central)
- Jetpack Compose
- Retrofit + OkHttp for API calls
- Coil for image loading
- Kotlinx Serialization

