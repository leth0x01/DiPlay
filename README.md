# LynkPlay

A [DiPlay](https://github.com/shihabal3amri/DiPlay) fork targeting the **2023 Lynk & Co 06 petrol model with an Android 9 head unit**. [中文说明](README.zh-CN.md).

This stage establishes the application branding and independent install identity. **Vehicle validation is pending.** Upstream Geely/Lynk steering-wheel and microphone support was documented on an Android 11 head unit; it does not establish compatibility with this Android 9 vehicle.

## Build and install

Build the main `mobile` module using JDK 25 and the SDK specified in [BUILD.md](docs/BUILD.md):

```powershell
.\gradlew.bat :mobile:assembleDebug
```

- Release application ID: `com.leth0x01.lynkplay`.
- Debug application ID: `com.leth0x01.lynkplay.hudtest`.
- APK: `mobile/build/outputs/apk/debug/mobile-debug.apk`.
- DiPlay and LynkPlay can coexist; app data and permissions are separate. Settings do not migrate automatically.
- Public diagnostic directory: `Downloads/LynkPlay`, with app-specific fallbacks when needed.
- Internal namespaces and compatibility actions remain unchanged to simplify upstream merges.

Ordinary source builds contain no runtime accessory identity. A successful build alone does not establish an iPhone connection. Follow the existing local standalone build instructions if you have provisioned runtime assets; never commit those assets or signing keys.

Validate USB, the car hotspot, Wi-Fi Direct, audio, Siri, steering-wheel keys, display and startup on the vehicle. See [Android 9 Wi-Fi Direct limits](docs/ANDROID9_WIFI_DIRECT.md). Run one receiver app at a time during testing.

## Maintenance

`main` follows upstream DiPlay; `lynkplay` carries this fork's changes. Initial baseline: `24ae1fa5` (DiPlay 0.2.18). With a clean working tree:

```powershell
git fetch upstream
git switch main
git merge --ff-only upstream/main
git push origin main
git switch lynkplay
git merge main
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
git push origin lynkplay
```

Resolve conflicts before running checks. CI runs on pushes to both long-lived branches and on pull requests.

The app checks [this fork's Releases](https://github.com/leth0x01/LynkPlay/releases). It does not offer upstream DiPlay APKs as LynkPlay updates. No APK is published in this stage.

## Attribution

LynkPlay derives from DiPlay, whose receiver is based on xcertplay and whose interface follows DiAuto. Existing copyright and licensing notices remain in place. See [LICENSE](LICENSE), [third-party notices](docs/THIRD_PARTY_NOTICES.md), the [upstream README](docs/DIPLAY-README.md), and its [Chinese edition](docs/DIPLAY-README.zh-CN.md). Their support scope and release links describe the upstream project.

CarPlay and its icon belong to Apple Inc. This fork does not imply Apple or Lynk & Co certification.
