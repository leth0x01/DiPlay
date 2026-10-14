# 领克 06 媒体方控开发交接（2026-10-14）

后续源码适配见[CarPlay 仪表音乐适配记录](LYNK06-CARPLAY-MUSIC-ADAPTATION-20261014.md)。本文中的安装包、源码快照和现场日志仍描述原交接基线；后续源码改动的实车效果需通过新构建确认。

## 当前目标与最新实车反馈

目标车：2023 款领克 06 燃油版，BX11 / IHU514G / Android 9。

用户要求收集方控信息，供其他电脑继续开发。优先让原车媒体的“音乐”项接到 CarPlay，完成播放、暂停、上一首、下一首。

**以最后一次反馈为准：上一首、下一首已可用；播放键会触发 CarPlay 音乐播放，但无法暂停。** 起初用户描述播放和暂停都不生效，随后明确修正为“会触发carplay音乐播放，但是无法暂停”。不能把旧描述继续当成当前结论。

用户于 2026-10-14 补充澄清：**仪表已显示的封面、歌词和歌手来自原车音乐，而非 CarPlay。** 此反馈不能作为 CarPlay 媒体信息已显示在仪表上的证据；CarPlay 仪表媒体信息显示情况仍待实车确认。修复暂停后应回归检查原车音乐的信息显示。本轮未通过截图独立验证封面，也未定位歌词究竟通过哪个字段传输；日志中歌曲信息随歌词变化，其来源需进一步核对，不应直接归为 CarPlay 信息、当成错误歌名或另加一套歌词实现。

原车媒体上下切换的项目为：蓝牙、音乐、USB、新闻、听书。用户观察新闻、听书、音乐点击播放会打开 App；蓝牙、USB 点击播放则播放音乐。用户最低要求是将“音乐”入口接到 CarPlay；本轮未确认点击后具体打开哪个 App，也未证明播放成功是经过“音乐”入口、Android MediaSession 或蓝牙 AVRCP 中的哪条路径。

本轮收集资料，不修改播放控制实现，不安装新包。暂停问题仍未修复。

## 源码基线与运行身份

- 分支：`lynkplay`。
- 本轮起点提交：`3f3116f945a8c83df7f259dd52dbb40a2a551ed3`。
- 媒体中心实现随 `086164a5` 的 0.1.9 发布进入仓库；当前提交另含系统 UID VPN / 仪表修复。
- 实车 ADB：`172.20.10.6:5555`，本轮重新连接成功并执行只读采集。
- 当前前台及 MediaSession 所属包：`com.leth0x01.lynkplay.clustersystem`，PID 6704，UID 1000。
- 实车包显示 `versionName=0.1.9-bx11-system-test`，最后更新时间为车机时间 `2026-10-14 01:12:00`。同版本名存在多个构建，必须以 APK 哈希核对，不能只看版本号。
- 本轮直接在设备上核对已安装 base.apk 的 SHA-256：`446951431db6a37c3f00a567a5569e13916a2148fd34dc563d674579c3f01ad7`，与早先仪表成功现场的实际安装包一致。源码快照是当前 HEAD，不能将尚未安装的最新源码当成实车正在运行的构建。

## 本轮已保存的实车证据

本地目录：`.private/lynk06-media-handoff-20261014/`。此目录被 Git 忽略，换电脑时必须单独携带。

采集包括设备属性、完整包列表、当前 Activity、音频状态、Android MediaSession、原车媒体中心服务与包信息、LynkPlay 包信息、原车媒体 App 服务与包信息，以及两份现存 logcat 快照。本轮没有清空 logcat，也没有模拟按键、申请媒体焦点或修改原车设置。

关键文件：

| 文件 | 用途 |
| --- | --- |
| `media-session.txt` | 初始 MediaSession 快照；CarPlay session active=true，state=2（暂停），actions=566；蓝牙 session 也 active=true |
| `media-session-after-play-pause.txt` | 用户修正为能播放但无法暂停后采集的状态 |
| `media-center-services.txt` | 当前 LynkPlay 系统测试进程有原车 MediaCenterService 绑定记录；绑定记录不等于取得媒体控制权 |
| `logcat-current.txt` | 本轮初始完整历史缓冲快照 |
| `logcat-after-play-pause.txt` | 用户最新反馈后的完整缓冲快照；未精确标记按键时间，不能把相邻日志直接认定为该次操作 |
| `media-events-after-play-pause.txt` | 媒体请求、来源类型、客户端日志与 Activity 启动的检索摘录，保留原日志行号 |
| `historical-token-error-excerpt.txt` | 早先仪表现场的 token 错误摘录，不能与当前按键现场混用 |
| `source-3f3116f9.zip` | Git 跟踪的源码基线快照，不包含 Git 历史、本机认证素材或本轮新增交接文档 |
| `multimedia-services.txt`、`multimedia-package.txt` | 原车 `com.ecarx.multimedia` 的服务、组件和权限 |
| `oem-file-paths.txt` | 实际 APK / VDEX 路径，避免误用其他架构目录 |
| `SHA256SUMS.txt` | 移动资料后核对文件内容 |

已核对原车程序位置：

- 媒体中心：`/system/app/XSFMediaCenter/XSFMediaCenter.apk`；协议代码另在 `oat/arm64/XSFMediaCenter.vdex`。
- 原车媒体 App：`/system/app/XCMedia2/XCMedia2.apk`，包名 `com.ecarx.multimedia`，其服务包含 `MediaCoreService` 和酷我组件。不能据此将“音乐”点击的具体启动目标认定为已证实。
- 按键服务：`/system/priv-app/XSFInputService/XSFInputService.apk`；代码另在 `oat/arm64/XSFInputService.vdex`。

最初按 `oat/arm` 和 `/system/app/XSFInputService` 拉取失败，随后以设备目录列表核对实际 `arm64` / `priv-app` 路径。XCMedia2 目录未列出 oat，分析其 APK 内的 DEX。

最新日志中，车机时间 `09:30:55.569` 和 `09:30:55.774` 两次 `onUpdateMediaStatusRequest:7`，均走 `MediaCtrlByDimCallback` 的 `playCtrlPlay=0`，随后 `playCtrlPlay execute true`。邻近播放状态上报为 `state:1`，并记录 `updateMusicPlaybackStateInner source type 6, music info source type 0`。采集后的 CarPlay MediaSession 为 `state=3`（播放中）。这证明该窗口里确实存在重复播放请求及来源类型差异；但按键精确时间未标记，`source type 6` 的含义与所有权还需反编译确认，不能直接宣称已经找到暂停失败的根因。

## 现有实现：下一位开发者先读这些文件

| 文件 | 关注点 |
| --- | --- |
| `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenter.kt` | 绑定、注册、媒体控制权、状态发布、重试；回调要求 `control.owned` |
| `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenterProtocol.kt` | 本车 Binder ABI、来源列表、播放状态和启动 PendingIntent |
| `common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt` | CarPlay 会话激活时创建媒体客户端；iPhone NowPlaying 更新；Android MediaSession 回调 |
| `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaKeyPolicy.kt` | 前台硬件播放、暂停、播放/暂停都映射到切换指令 |
| `common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt` | `dispatchKeyEvent` 中领克窗口媒体键处理 |
| `shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaButton.kt` | CarPlay PLAY=1、PAUSE=2、PLAY_PAUSE=3、NEXT=4、PREVIOUS=5 |
| `common/src/test/java/com/shilapi/xcertplay/Lynk06MediaCenterTest.kt` | 现有协议与控制权测试；模拟通过不能替代实车暂停验证 |

原车服务描述符为 `ecarx.xsf.mediacenter.IMediaCenterSvc`。当前使用 transaction：注册 1、注销 4、requestControl（原车 requestPlay）5、播放状态 6、来源列表 7、当前来源 8、进度 10、能力 30。

客户端 `IMusicClient` transaction：1 → PLAY、2 → PAUSE、3 → NEXT、4 → PREVIOUS；25 更新控制权所属包。**这些原车 transaction 编号和 CarPlay HID 索引不是同一套编号。** 明确播放/暂停回调保留原意；前台硬件键走 PLAY_PAUSE，两条入口不能混为一谈。

当前固定上报 `SOURCE_LOCAL=0`，能力 `[0,2]`（标准媒体键与 DIM）。播放 getter 的状态为暂停 0、播放 1，启动 PendingIntent 指向 `CarPlayHostActivity`。这只说明客户端提供了启动目标，尚未证明原车“音乐”入口选用该 PendingIntent。

启用条件包括车型匹配、活跃 CarPlay 控制器、非原车蓝牙音频模式；实际回调还要求会话活跃、非通话且持有原车媒体控制权。蓝牙音频模式走 AVRCP，验证时必须记录本次连接的实际模式。

## 排查顺序与待证实问题

1. **先定位暂停那一次的完整链路。** 同一首歌播放后按一次播放/暂停键，记录按键时间和物理结果；同时抓 DIM 状态、MediaCtrlByDimCallback、IMusicClient 回调、LynkPlay 转发结果与 iPhone NowPlaying 状态。
2. 核对原车是否根据过期播放状态始终调用 `onPlay`，导致“能开始播放、不能暂停”；也可能暂停调用根本没有到达客户端。当前尚无证据确定哪一种。
3. 核对控制权和状态发布是否成功：注册成功、Android MediaSession active、绑定存在都不能替代 `requestPlay` 成功及原车接受播放状态的证据。
4. 分析 XCMedia2 与 XSFMediaCenter 的来源选择、播放键以及启动目标分支，确定“音乐”项选择客户端的条件。`SOURCE_LOCAL=0` 是否对应用户看到的“音乐”，仍需核对，不直接改枚举数值。
5. 如果原车明确暂停已经转发，继续核对 CarPlay HID 发送及 iPhone 状态；如果只收到播放，则修正状态同步或上游路由后再判断是否需要切换指令。
6. 保持 CarPlay 上一首/下一首已可用，并回归检查原车音乐在仪表上的封面、歌词、歌手显示；单独确认 CarPlay 媒体信息能否显示在仪表上。补测前后台、暂停后再播放、切到其他媒体再回到音乐、通话与断开后的原车行为。

历史辅助证据：`.private/lynkplay-system-live-20261014-002908/map-success-012231/full-current-logcat.txt` 在 `01:23:27` 起出现 `checkMusicClientTokenValid: please requestPlay first`，涉及 `updateCurrentProgress` / 播放状态发布。它属于早先仪表实验的不同现场，不是这次暂停按键的直接根因证据。原始历史文件未随此次媒体目录整体复制，后续可另带，或使用归档的摘录。

更早的 DIM → 原车蓝牙 AVRCP 证据与接口研究见 [方控、麦克风与蓝牙适配](LYNK06-WHEEL-MICROPHONE-BLUETOOTH.md) 和 [媒体方控实现计划](plans/2026-10-13-lynk06-media-wheel-controls.md)。这些文档引用的 `lynk06_cluster_20261012_154433` 当前工作区未找到，不能假定克隆仓库就能获得原始采集。

## 换电脑继续开发

1. 携带本交接文档与 `.private/lynk06-media-handoff-20261014/` 的完整资料包，源码使用上述提交。原车 APK / 原始日志只放私有资料目录。
   本次便携包为 `.private/LYNK06-MEDIA-HANDOFF-20261014.zip`，内含交接文档、原车程序文件、诊断快照、摘录及源码 ZIP。已有 Git 仓库可切换到上述提交；离线电脑可解压源码 ZIP，再将交接文档复制到 `docs/`。源码 ZIP 不含 `.git`，需要版本历史时另克隆仓库。
2. 安装 JDK 25、Android SDK `platforms;android-37.0`、Build Tools 36.0.0、NDK 28.2.13676358、Platform Tools、uv；重建 `local.properties` 或设置 `ANDROID_HOME`。本机的 SDK 路径 `D:/software/Android/Sdk` 不保证适用于另一台电脑。
3. Windows 设置 `DIPLAY_TEST_SHELL` 指向可用 Git `sh.exe`，避免已有 shell 测试找不到 sh。
4. 普通 Debug 源码 APK 不含运行时配件认证，编译成功不等于能连接 iPhone。实际车测构建与独立系统包配置见 [构建说明](BUILD.md)、[车测说明](LYNKPLAY-CAR-TEST.md)、[系统测试版说明](LYNK06-CLUSTER-SYSTEM-BUILD-20261013.md)。认证素材和签名配置需要另行通过私有渠道迁移；本次媒体资料包不包含它们。
5. 遵守 `AGENTS.md`，实现修复后运行完整 CI：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

本轮完整 CI 命令已自行结束：`BUILD FAILED in 28m 10s`，退出码 1。shared 共 1431 项，2 项失败：`BufferedAudioStreamTest.tinyAuthenticatedFramesCannotGrowAnUnboundedObjectQueue`（condition not met in time）及 `AmbientWorkerOwnerLockTest.anotherProcessCannotEnterUntilOwnerReleases`（Child lock probe timed out）。本轮没有修改这些测试或产品代码，也没有为了此次文档交接重跑全量检查；不能将未获成功结果的其余任务标为通过。没有触发手动停止或 30 分钟超时。

`git diff --check` 通过。最终结果、失败 XML 和检查过程中采集的线程栈保存于资料目录，见 `validation.txt`、`ci-final-output.txt` 与 `TEST-*.xml`。源码快照中的实车暂停问题仍未修复。
