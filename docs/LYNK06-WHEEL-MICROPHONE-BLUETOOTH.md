# 领克 06 方控、麦克风与蓝牙适配

目标：2023 款领克 06 燃油版，IHU514G / xecarx / Android 9。厂商语音键与 MIC 末级回退仅在 product、device、model、hardware、API 28 完整匹配时启用。

## 实车依据

核对了 `lynk06_20261012_121852`、`lynk06_20261012_131000_wireless`、`lynk06_20261012_131105_menu` 和 `lynk06_cluster_20261012_154433`。

- XSFInputService APK 的 `ECarXRVoiceAssistAction` 使用有序广播 `ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT`。键码 extra 为 `ecarx.extra.ECARX_KEY_EVENT_TYPE=200231`；动作 extra 为 `ecarx.extra.ECARX_KEY_ACTION_TYPE`，短按 0、长按 1。广播携带 DEFAULT 类别及 INCLUDE_BACKGROUND。
- 同 APK 的媒体键实现将方控转换为 Android 标准媒体 KeyEvent。现有实车原车音乐采集显示媒体中心将操作送往蓝牙 AVRCP。
- 蓝牙状态采集显示 A2dpSinkService、HeadsetClientService 和 AvrcpControllerService 已连接。车机音频接收端 Profile ID 为 A2DP Sink 11、HFP Client 16。
- 专项采集的 audio-state 记录 LynkPlay 曾启动 VOICE_COMMUNICATION 录音。该记录只证明录音启动，音量、语音质量仍需实际采样确认。
- 用户确认原车短按打开领克智能语音，长按打开 HiCar；本次要求为 CarPlay 连接时短按 Siri，长按尝试打开 LynkPlay。

## 实现行为

语音键通过清单接收有序广播，优先级 999。CarPlay 会话活跃且未通话时，短按请求 Siri；成功后终止后续广播。未连接、连接建立中、通话中或 Siri 请求失败时继续原车处理。长按请求打开 LynkPlay 设置主界面，启动调用成功后终止后续广播。诊断报告包含最近一次事件及处理结果。

本车媒体中心 `ecarx.xsf.mediacenter.MediaCenterService` 通过音乐客户端回调控制 CarPlay。会话建立时注册客户端、声明标准媒体键和 DIM 能力、请求原车媒体控制权；明确播放、暂停、下一首、上一首分别发送 CarPlay PLAY、PAUSE、NEXT、PREVIOUS。iPhone 歌曲、播放状态和进度同步到媒体中心；退出会话时关闭回调并注销。失去原车媒体控制权后停止转发，iPhone 再次开始播放时尝试重新取得控制权。Binder 描述符、transaction 编号和参数类型来自本车 XSFMediaCenter VDEX。

CarPlay 窗口处理本车标准上一曲、下一曲、播放、暂停和播放/暂停事件：按下发送一次，重复与释放被消费。通话期间交给原车处理。原车蓝牙音频模式下，媒体操作交给 AVRCP，辅助 MediaSession 不申请媒体键焦点。音频模式随本次连接配置固定，修改设置后到重新连接才改变媒体控制路径。

Siri 的 VOICE_RECOGNITION 在创建、初始化或启动失败时尝试 VOICE_COMMUNICATION。本车在两个源均失败时再尝试标准 MIC。切换前释放失败录音器；不修改全车 AudioManager 模式。诊断记录每次失败阶段和实际录音源，包含扬声器通话使用 MIC 的情况。

本车在连接配置生成时及 30 秒后检查 A2DP Sink / HFP Client。记录当前音频模式、连接设备数量、Profile 请求拒绝或异常，并释放 Profile 代理。

## 参考源码

以下为本轮读取的版本；本车广播参数依据实车 APK 独立核对。

| 仓库 | Commit | 核对内容 |
| --- | --- | --- |
| [LynkCo-DiPlay](https://github.com/soBigRice/LynkCo-DiPlay/tree/07075fdd9f52) | 07075fdd9f52 | Android 9 前台媒体键、蓝牙生命周期 |
| [OsnPlay](https://github.com/xxxcrel/OsnPlay/tree/c55c361411e1) | c55c361411e1 | 独立录音源选择、Siri 录音源回退、吉利媒体键与 AVRCP 分工 |
| [749001858/DiPlay](https://github.com/749001858/DiPlay/tree/4ed5514daf5c) | 4ed5514daf5c | MediaSession 和麦克风源实现 |
| [Lynk-820A-Play](https://github.com/MrZhangWeiCe/Lynk-820A-Play/tree/b430fb6ef35e) | b430fb6ef35e | Android 9 前台媒体键、接收端蓝牙 Profile |
| [DiPlay-Legacy-Android](https://github.com/trngish/DiPlay-Legacy-Android/tree/1c3bee25992b) | 1c3bee25992b | 旧 Android 媒体键及 AudioRecord 实现 |
| [DiPlay-ECARX](https://github.com/jiopia/DiPlay-ECARX/tree/7f78343642fe) | 7f78343642fe | 亿咖通集成边界、标准麦克风回退 |

## 自动化验证（2026-10-13）

执行 AGENTS.md 要求的完整 CI 命令，最终 `BUILD SUCCESSFUL`：shared 1420 项（跳过 1 项）、common 1294 项、home 6 项，0 失败／错误。mobile、home、maphost 的 Lint 与 Debug 构建通过；`git diff --check` 通过。

新增覆盖语音键短按 Siri 成功／失败、未连接与连接建立中放行、通话放行、长按打开 LynkPlay、错误车型／键码／动作放行；领克前台播放/暂停只发送一次；蓝牙音频不申请媒体键焦点、不重复控制、设置待重连时保持原会话路径；Siri 录音启动失败释放与回退、领克 MIC 末级回退，以及蓝牙 Profile 选择。

最终复核曾在已有 `SettingsBackupServerTest.stopDoesNotFailWhileConnectionsAreEnding` 卡住，线程栈停在 JDK 25.0.3 的 `ServerSocket.close()` / `UnixDispatcher.preClose0`。保留线程栈并终止该测试 worker，完整命令重跑后通过。

## 媒体方控实车证据（2026-10-13）

用户在诊断版测试中断开 iPhone 与车机蓝牙后，方控无法控制音乐。`diplay-report 5.txt` 记录 MediaSession 已启用、音频焦点申请成功，但没有窗口或 MediaSession 媒体按键记录。

专项采集记录了 DIM → MediaCtrlByDimCallback → 蓝牙播放器 → AVRCP 的完整链路：`10-wheel-pause/logcat.txt` 的 15:59:38 为暂停，`11-wheel-play/logcat.txt` 的 16:01:59 为播放，`08-wheel-next/logcat.txt` 的 16:00:00 为下一首，`09-wheel-previous/logcat.txt` 的 16:00:29 为上一首。

## 媒体中心客户端自动化验证（2026-10-13）

完整 CI 命令通过：shared 1427 项（跳过 1 项）、common 1309 项、home 6 项，0 失败／错误；mobile、home、maphost 的 Lint 与 Debug 构建通过。媒体中心专项 10 项覆盖四种操作的转发、Binder 参数与返回值、iPhone 播放状态、能力声明、即时取得控制权、失去控制权、迟到请求、同步状态查询和退出后的回调门控。

## 实车复核

停车后验证 CarPlay 连接时短按只打开 Siri，长按打开 LynkPlay；未连接时短按仍打开领克语音。长按能否抢在原车 HiCar 接收者之前，以及 App 未运行时的启动，需要实车确认广播顺序和车机启动限制。

分别在 CarPlay 音频、原车蓝牙音频模式下检查切歌、暂停/播放、前台和后台控制；测试 Siri 语音输入与电话后音乐恢复。导出诊断时关注“领克语音键”、`Car Bluetooth audio`、`Microphone: start`、`Microphone: failure` 和麦克风的 `peakDbfs` / `captureBytes` / `udpSent`。
