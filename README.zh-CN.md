# LynkPlay

基于 [DiPlay](https://github.com/shihabal3amri/DiPlay) 的 CarPlay 接收器，面向 **2023 款领克 06 燃油版、Android 9 车机**维护。

当前为品牌与独立安装基线，**待实车验证**。现有吉利／领克方向盘及麦克风适配来自上游已记录的 Android 11 车机结果，不代表已验证本车型的 Android 9 车机。

## 安装与构建

- 主应用模块：`mobile`。
- Release 安装标识：`com.leth0x01.lynkplay`。
- Debug 安装标识：`com.leth0x01.lynkplay.hudtest`。
- 可与 DiPlay 共存，私有数据与权限独立；原版设置不会自动迁移，可使用现有备份／恢复功能。
- 保留上游内部 namespace、类型及资源键，方便后续合并更新。
- 诊断报告保存到 `Downloads/LynkPlay`；车机限制可能触发应用专用目录回退。

按 [构建说明](docs/BUILD.md) 准备 JDK 25 和 Android SDK，再执行：

```powershell
.\gradlew.bat :mobile:assembleDebug
```

APK 位于 `mobile/build/outputs/apk/debug/mobile-debug.apk`。普通源码构建默认没有运行时配件身份；仅编译成功不足以连接 iPhone。本地 standalone 构建流程见构建说明，不应提交认证资源或签名密钥。

初次实车验证建议依次测试 USB、车机热点、Wi-Fi Direct，以及音频、Siri、方向盘按键、全屏和开机启动。Android 9 的 Wi-Fi Direct 限制见 [说明](docs/ANDROID9_WIFI_DIRECT.md)。测试时只运行一款接收器应用，避免同时占用连接和音频。

## 更新与上游同步

LynkPlay 检查 [本 fork 的 Releases](https://github.com/leth0x01/DiPlay/releases)，不提供原版 DiPlay APK 作为本应用更新。尚无发布版本时不会推荐更新；本阶段没有发布 APK。以后仓库改名时须同步修改更新地址。

- `main`：保留 DiPlay 上游。
- `lynkplay`：品牌修改和领克 06 适配。
- 初始基线：`24ae1fa5`（DiPlay 0.2.18）。

提交或暂存当前修改后，同步上游：

```powershell
git fetch upstream
git switch main
git merge --ff-only upstream/main
git push origin main
git switch lynkplay
git merge main
```

解决冲突并执行完整检查后，再推送 `lynkplay`：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
git push origin lynkplay
```

CI 同时检查 `main` 和 `lynkplay` 的 push，以及 pull request。

## 来源与许可证

LynkPlay 是 DiPlay 的派生项目，接收器源自 xcertplay，界面采用 DiAuto 设计。保留原项目版权、许可与贡献说明，详见 [LICENSE](LICENSE)、[第三方许可声明](docs/THIRD_PARTY_NOTICES.md)、[上游中文 README](docs/DIPLAY-README.zh-CN.md) 和 [上游英文 README](docs/DIPLAY-README.md)。上游 README 中的车型支持、名称及发布链接属于原项目。

CarPlay 名称和图标归 Apple Inc. 所有。此派生项目不表示获得 Apple 或领克官方认证。
