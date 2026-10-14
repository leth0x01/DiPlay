# LynkPlay 0.2.1

版本配置：`0.2.1` / versionCode 48。上游基线：DiPlay 0.2.19。

## 本次更新

- 车测包与正式包合并为同一个 `release` 构建，包名统一为 `com.leth0x01.lynkplay`。
- 该包使用本车 BX11 / IHU514G 平台证书签名，并在 Manifest 声明 `android.uid.system`，取得 UID 1000 后仪表盘 CarPlay 地图可以显示。
- 移除 `carTest` 与 `clusterSystem` 两个构建；车测、正式发布共用一份安装包与一份签名配置。
- 打包脚本改为构建 `:mobile:assembleRelease`，并核验平台证书指纹、包名、`android.uid.system` 声明及打包认证素材。

## 验证与实车复核

2026-10-14 实车反馈：平台签名的包可以显示仪表盘 CarPlay 地图；此前不带系统 UID 的包不能显示。

`scripts/tests` 11 项通过；`:mobile:assembleDebug`、`:mobile:assembleRelease` 与车测打包脚本均通过。

## 升级说明

已安装的长期签名版本（`0.1.9` 及更早的正式版）与本包签名不同，无法覆盖升级，需要卸载后重装。本包之后的版本沿用同一平台签名，递增 versionCode 即可覆盖升级并保留数据。

安装与诊断步骤见[车测说明](LYNKPLAY-CAR-TEST.md)，平台签名准备与构建方式同见该文档。
