# 领克 06 方控、麦克风与蓝牙适配 Implementation Plan

**Goal:** 为已采集的 IHU514G / xecarx / Android 9 车机接入短按 Siri / 长按打开 LynkPlay，补齐前台媒体键，并完善录音回退和蓝牙状态诊断。

**Architecture:** 用完整实车标识启用领克路径；语音键使用 XSFInputService APK 已核实的有序广播。媒体键沿用系统 KeyEvent 与 MediaSession。蓝牙音频模式由原车处理媒体操作；麦克风按真实录音阶段选择回退源并记录实际来源。

**Tech Stack:** Kotlin、Android AudioRecord / MediaSession / BroadcastReceiver、Robolectric。

1. 在 `VehicleIntegration.kt` 暴露完整标识匹配结果；新增 `Lynk06VoiceKeyReceiver.kt` 并注册清单有序广播接收者。短按只在 CarPlay 活跃且未通话时请求 Siri，长按启动 LynkPlay，成功处理才终止原车广播。用 `Lynk06VoiceKeyReceiverTest.kt` 验证参数、原车放行与启动行为。
2. 更新 `CarPlayMediaKeys.kt`、`CarPlayHostActivity.kt`，目标车前台处理播放、暂停和切歌；蓝牙音频模式释放媒体键焦点与控制权。补充前台按键测试。
3. 更新 `MicrophoneUplink.kt`、`MicrophoneCaptureStats.kt`，启动失败释放录音器后回退，记录实际来源；领克在两个语音源均拒绝时尝试 MIC。扩展 `MicrophoneSourceFallbackTest.kt`。
4. 更新 `DiPlayBluetooth.kt`，目标车使用 A2DP_SINK=11 / HEADSET_CLIENT=16；补充 Profile 选择测试，并由会话诊断调用。
5. 执行专项测试与 AGENTS.md 完整 CI 命令。记录实际结果及实车待验证项至适配文档。
