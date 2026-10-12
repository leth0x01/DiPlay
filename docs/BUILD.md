# Build LynkPlay

For the stable Lynk 06 car-test package, signing setup and authentication provenance, see [LynkPlay car-test builds](LYNKPLAY-CAR-TEST.md).

Use this procedure to build the DiPlay app for an Android head unit.
Run the commands from the repository root, where `gradlew` and `settings.gradle.kts` are located.

## Select the application

This repository contains several Android applications.
Select `mobile` to build the main DiPlay app.

| Gradle module | Purpose | Debug APK |
| --- | --- | --- |
| `:mobile` | Main DiPlay app for Android head units | `mobile/build/outputs/apk/debug/mobile-debug.apk` |
| `:maphost` | Sample application that displays a map from DiPlay | `samples/maphost/build/outputs/apk/debug/maphost-debug.apk` |
| `:home` | Optional DiPlay Home launcher | `samples/home/build/outputs/apk/debug/home-debug.apk` |
| `:automotive` | Separate Android Automotive application inherited from xcertplay | `automotive/build/outputs/apk/debug/automotive-debug.apk` |

The `:common` and `:shared` modules are libraries.
The applications use these libraries.

If the installed app is **DiPlay map host**, you selected `:maphost` or its APK.
Build `:mobile` and select `mobile-debug.apk` to install the main app.
A command such as `./gradlew assembleDebug` can build more than one application.
Use the module name in each build command to select the required application.

## Prepare the build environment

Install these tools:

- JDK 25.
- Android SDK Platform 37 (`platforms;android-37.0`).
- Android SDK Build-Tools 36.0.0.
- Android SDK Platform-Tools.
- Android NDK 28.2.13676358.
- [uv](https://docs.astral.sh/uv/) for repository Python scripts.

Run repository Python scripts as `uv run scripts/<script-name>.py`; this form is the same on macOS and Windows.
Use the Gradle wrapper included in this repository.
The first build needs an internet connection to download dependencies.

1. Open the repository root in Android Studio.
2. Install the Android SDK packages with **Tools → SDK Manager**.
3. Set **Gradle JDK** to JDK 25 in the Android Studio Gradle settings.
4. Wait for Gradle sync to finish.

For a terminal build, set `JAVA_HOME` to your JDK 25 directory.
Set `ANDROID_HOME` to your Android SDK directory.
You can also specify the SDK directory in the root `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Keep `local.properties` outside Git.
Check the Java version before the build:

```sh
java -version
```

The output must identify Java 25.
On Windows, use `gradlew.bat` in place of `./gradlew` in the commands below.

## Build the main app

For a source build without runtime authentication assets:

```sh
./gradlew :mobile:assembleDebug
```

Check that this file exists:

```text
mobile/build/outputs/apk/debug/mobile-debug.apk
```

The debug application ID is `com.leth0x01.lynkplay.hudtest`.
The carTest and release application ID is `com.leth0x01.lynkplay`.

The source APK contains no accessory identity unless you supply runtime authentication assets.
Standalone CarPlay needs these assets to connect to an iPhone.
Use the car-test procedure below for that purpose.
Tests generate synthetic identities at runtime.
Keep test private keys outside Git.

### Use Android Studio

1. Open **Run → Edit Configurations**.
2. Select an **Android App** configuration, or create one.
3. Set **Module** to `mobile`.
4. Select this configuration in the toolbar.
5. Select the Android device.
6. Click **Run** to install the main debug app.

To build an APK without installation, run `:mobile:assembleDebug` in the Android Studio terminal.
Select the APK from the `mobile` output directory.

## Check the source build

Run the unit tests, lint checks, and main debug build:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

Check that Gradle reports `BUILD SUCCESSFUL`.
For the complete CI procedure, see [the Android workflow](../.github/workflows/android.yml).
That workflow also checks and builds the `home` and `maphost` applications.

## Build a standalone car-test APK

Prepare an external directory with these runtime authentication files:

```text
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
```

Use only the intended runtime files in this directory.
Keep the directory and its private key outside Git.
The build rejects unexpected credential containers in APK assets.

1. Set `DIPLAY_AUTH_ASSETS_DIR` to the absolute directory path.
2. Run the standalone debug task.

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

The task stops if either required file is absent, oversized, malformed or does not match its counterpart.
Its APK output is `mobile/build/outputs/apk/debug/mobile-debug.apk`.
Check both files in the APK against your selected local inputs:

- `assets/offline-mfi/identity.pk8`.
- `assets/offline-mfi/certificate.p7b`.

Use this APK for a standalone connection test.
Update the existing test app with the same signing key to keep its settings.
Different signing keys cannot update the same installed application.

## Build a release APK

### Build update attachments in one command

Prepare JDK 25, the Android SDK, the existing signing configuration and runtime authentication assets described below. Increment both values in `gradle.properties` before each update:

```properties
lynkplayVersionCode=45
lynkplayVersionName=0.1.7
```

Run from the repository root on macOS or Windows:

```sh
uv run --locked scripts/build_lynkplay_release.py
```

The script uses `.private/auth-assets` by default. Select another authentication directory with `--authentication-assets-directory /absolute/path/to/runtime-assets`.

It runs the complete Android CI task list from `AGENTS.md`, then `:mobile:lintRelease :mobile:assembleRelease`. It verifies the final APK's Android 9 signature, pinned signing certificate, application ID, version name/code and packaged authentication assets before generating:

```text
mobile/build/outputs/publish/0.1.7/LynkPlay-0.1.7.apk
mobile/build/outputs/publish/0.1.7/SHA256SUMS.txt
```

The output directory and APK filename follow `lynkplayVersionName`. Keep the existing signing key for all updates; `carTest` and `release` share that key and application ID.

### Publish an update

Create a Release in `leth0x01/LynkPlay` with tag `v0.1.7` pointing to the source commit used for the build. Upload the versioned APK and `SHA256SUMS.txt` before publishing. Each Release must contain exactly one main-app APK. The checksum file must refer to the uploaded APK's exact filename.

The app reads this repository's GitHub Releases API, compares the tag's three-part version with the installed version, downloads both attachments and verifies SHA256 before opening an installer. The current updater also considers prereleases. Downloading and installation are initiated by the user; background checks are enabled by default and limited to once per 24 hours.

Verify from an older installed version using **Settings → About → Check for updates**, download the package, confirm installation and check the new version and retained settings. The head unit must be able to access GitHub's API and release downloads.

### Manual release build

Prepare the runtime assets as described in the car-test procedure.
Set these environment variables locally:

| Variable | Value |
| --- | --- |
| `DIPLAY_AUTH_ASSETS_DIR` | Absolute path to the runtime assets directory |
| `ANDROID_KEYSTORE_PATH` | Path to your Android signing keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |
| `LYNKPLAY_SIGNER_SHA256` | Expected signing certificate SHA256 (64 hex digits) |

Alternatively, initialize the ignored `release-signing.properties` with `uv run scripts/initialize_lynkplay_signing.py`. The same command works on macOS and Windows. Environment variables take precedence over local properties. `carTest` and `release` use the same signing configuration and enforce its pinned certificate fingerprint.

Keep the keystore and passwords outside Git.
Run the release checks and build:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

The output is `mobile/build/outputs/apk/release/mobile-release.apk`.
The APK contains the runtime identity you supplied.
Recipients can extract that identity from the APK.
The APK excludes the Android signing key.
See [the project notices](THIRD_PARTY_NOTICES.md) for the experimental identity used in public releases.

The public source archive matches the release tag.
It excludes runtime identities, signing keys, local configuration, and build output.
The retired `build-beta.py` helper is no longer part of the build procedure.

## Correct build problems

| Symptom | Action |
| --- | --- |
| Installed app is DiPlay map host | Select `mobile` in Android Studio. Build and install the APK from the `mobile` output directory. |
| Gradle cannot find the Android SDK | Set `ANDROID_HOME` or the `sdk.dir` value in `local.properties`. |
| A required SDK or NDK package is absent | Install the listed package with SDK Manager. |
| Java or Gradle JDK version is incorrect | Set the terminal JDK and Android Studio Gradle JDK to Java 25. |
| Source APK cannot start standalone CarPlay | Supply runtime authentication assets. Use `:mobile:assembleStandaloneDebug`. |
| Standalone task reports missing authentication files | Check the external directory path and both required files. |
| Android rejects an app update | Use the same application ID and signing key as the installed app. |
