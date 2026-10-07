# Desktop Remote Lite for Android

This module preserves the existing Android UI source as a starting point for the new Desktop-following connector. It was imported from the tracked `android-native` source and tests at `windows-client` commit `f8350e400cf6e7420d8283e03eb6efa37104ca79`. The old test signing key, build output, credentials, and `local.properties` are not part of this module. UI source here does not prove that an already installed APK contains these changes.

Build with JDK 17 and Android SDK 36:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

On Unix, use `./gradlew` for the same tasks. The self-contained wrapper is pinned to Gradle 8.13 with its distribution SHA-256 in `gradle/wrapper/gradle-wrapper.properties`; its wrapper JAR SHA-256 is `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. Android Gradle Plugin is 8.11.1. The first build downloads Gradle and Maven dependencies.

The debug APK uses Android's normal local or CI-generated debug key, has application ID `dev.codex.desktopremote.lite`, and is version `0.1.0-alpha.1` (code 1). It can coexist with the old app. Different debug keys may prevent updating an earlier build of this *new* package on another machine; uninstall that test build first if necessary. This does not claim signing compatibility with the old app.

The server field starts empty; enter the intended HTTPS server yourself. The old Android OTA check, APK download, and installer route have been removed from this app. To test a new build, install its APK manually. Keeping the existing UI and login code does not by itself establish compatibility with the new relay protocol or prove phone acceptance.

The UI includes material adapted from [HAPI](https://github.com/tiann/hapi), under AGPL-3.0; see [the attribution](../third-party/licenses/HAPI-NOTICE.md) and [license text](../third-party/licenses/AGPL-3.0.txt). JetBrains Mono's provenance and SIL OFL are in [FONTS.md](FONTS.md) and `app/src/main/assets/licenses/JetBrainsMono-OFL.txt`.
