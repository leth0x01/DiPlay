# Vehicle Siri Switch Implementation Plan

> **For agentic workers:** 使用当前会话按任务执行；用户已确认行为方案。

**Goal:** 按车型将现有 Siri 开关接到领克 06 原车语音键。

**Architecture:** UI 与运行入口读取同一车型能力，沿用已有持久化。独立判断自定义 Siri 按键是否需要服务，避免领克历史按键产生额外触发。

**Tech Stack:** Kotlin、Android Views、JUnit、Robolectric。

## Global Constraints

- 完整设备匹配；未知固件沿用通用行为。
- 开关立即生效，已验证领克 06 默认开启、其他车型默认关闭；已保存的选择优先，长按打开 LynkPlay 不受影响。
- 中文注释；所有新增设置文案覆盖现有语言。

### Task 1: 联动运行时与车型菜单

**Files:** `common/src/main/java/com/shilapi/xcertplay/{Lynk06VoiceKeyReceiver,WheelKeyService,DiPlayActivity}.kt`；`common/src/main/res/values*/strings.xml`；`common/src/test/java/com/shilapi/xcertplay/Lynk06VoiceKeyReceiverTest.kt`。

- [x] 增加关闭放行与连接期间切换的广播回归测试，开启现有正向用例的 Siri 开关。
- [x] 短按条件增加 `WheelZoomSettings.siriKey(context)`；长按分支保持现状。
- [x] 增加 `siriKeyNeedsService(context: Context): Boolean = siriKey(context) && !VehicleIntegration.detectedCapabilities.voiceKeyReceiver`；服务需求、自定义 Siri 匹配及冲突检查使用此判断。
- [x] 菜单在原车语音能力开启时选择专用说明，跳过 Siri 学习按钮；服务入口使用 `siriKeyNeedsService(this)`。
- [x] `siriKey` 的缺省值使用 `VehicleIntegration.detectedCapabilities.voiceKeyReceiver`，验证默认开启只适用于完整实车匹配，并保留已保存选择。
- [x] 新增 `settings_oem_siri_key_description` 的全部翻译，明确立即生效及原车放行条件。

### Task 2: 验证车型隔离与布局

**Files:** `common/src/test/java/com/shilapi/xcertplay/LynkSiriSettingsTest.kt`。

- [x] 检查已验证车型无需 Siri 学习或服务、历史按键不执行、开关保存；部分匹配恢复通用控件。
- [x] 生成紧凑与完整大字体中文截图及阿拉伯语截图，检查控件位于窗口内。
- [x] 运行 `./gradlew.bat :common:testDebugUnitTest --tests '*Lynk06VoiceKeyReceiverTest' --tests '*LynkSiriSettingsTest' --tests '*WheelSiriWindowTest' --tests '*WheelKeyServiceTest'`。
- [x] 运行 CI：`./gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug --stacktrace`。

## 验证结果

- 新增关闭放行与立即生效测试在修复前失败，修复后通过。
- 最终完整 CI 成功：shared 1486 项、common 1464 项、home 6 项测试均无失败或错误；三个应用 lint 与 debug 构建通过。
- 领克默认开启、已保存的关闭选择、其他车型默认关闭已验证。
- 已生成并查看 360dp 中文、1280dp 高密度中文和 600dp 阿拉伯语截图，字体比例均为 1.5。
- 本机验证指定完整 JDK 25 并禁用自动 JDK 探测、守护进程复用及配置缓存，避免误选缺少 jlink 的 VS Code JRE；未修改项目构建配置。
