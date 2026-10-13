# LynkPlay

基於 [DiPlay](https://github.com/shihabal3amri/DiPlay) 的 CarPlay 接收器，針對 **2023 款領克 06 燃油版、Android 9 車機**維護。[简体中文](README.zh-CN.md) · [English](README.md)。

目前 LynkPlay 版本為 **0.1.9**（versionCode 47），已同步 DiPlay 0.2.19。

已有實車回饋確認無線、有線 CarPlay 均能在目標車機上連線。舊版斷開車機藍牙後，方向盤按鍵無法控制音樂。本版已接入原車媒體中心，新的媒體按鍵、音訊表現及儀表顯示調整仍需實車複核。

## 安裝與建置

- 主應用程式模組：`mobile`。
- Release 與 carTest 套件名稱：`com.leth0x01.lynkplay`。
- Debug 套件名稱：`com.leth0x01.lynkplay.hudtest`。
- 可與 DiPlay 共存，各自儲存資料與權限；設定可透過備份／還原移轉。
- 診斷報告儲存至 `Downloads/LynkPlay`，車機限制可能使其改存應用程式專用目錄。

依[建置指南](docs/BUILD.md)準備 JDK 25 與 Android SDK，再執行：

```powershell
.\gradlew.bat :mobile:assembleDebug
```

APK 位於 `mobile/build/outputs/apk/debug/mobile-debug.apk`。一般原始碼建置預設不包含執行時認證素材；獨立車測包的認證輸入、長期簽章及建置流程請見[車測說明](docs/LYNKPLAY-CAR-TEST.md)。

後續實車驗證重點為聲音異常、儀表地圖、連線穩定性，以及 Siri、方向盤按鍵、全螢幕和開機啟動。測試時只執行一款接收器應用程式。

## 更新與上游同步

LynkPlay 檢查[本 fork 的 Releases](https://github.com/leth0x01/LynkPlay/releases)，也可進入 **設定 → 關於 → 檢查更新**，下載並驗證 APK 後安裝。正式更新包沿用長期簽章與認證素材：

```sh
uv run --locked scripts/build_lynkplay_release.py
```

完整流程請見[正式出包與更新發布](docs/BUILD.md#build-a-release-apk)。`main` 保留 DiPlay 上游，`lynkplay` 維護品牌與領克 06 調整；目前已合併 DiPlay 0.2.19（`9aa9c549`）。上游同步指令請見[簡體中文首頁](README.zh-CN.md#更新与上游同步)。

本次更新接入原車媒體中心的播放/暫停、上一首、下一首回呼，補充媒體按鍵診斷，並包含領克 06 語音鍵、麥克風及儀表適配。詳見 [LynkPlay 0.1.9 版本說明](docs/LYNKPLAY-RELEASE-NOTES-0.1.9.md)與[驗證紀錄](docs/LYNKPLAY-VALIDATION.md)。

## 來源與授權

LynkPlay 衍生自 DiPlay，接收器源自 xcertplay，介面採用 DiAuto 設計。保留原專案版權、授權與貢獻聲明，詳見 [LICENSE](LICENSE)、[第三方聲明](docs/THIRD_PARTY_NOTICES.md)及[上游繁體中文 README](docs/DIPLAY-README.zh-TW.md)。上游文件中的車型支援與發布連結屬於 DiPlay。

CarPlay 與其圖示屬於 Apple Inc.；此衍生專案不表示獲得 Apple 或領克官方認證。
