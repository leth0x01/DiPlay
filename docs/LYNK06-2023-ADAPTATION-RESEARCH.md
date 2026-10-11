# 2023 款领克 06 燃油版适配调研

调研日期：2026-10-11。源码基线：DiPlay 0.2.18，LynkPlay 分支 `0f9f455936e55ce7c3a68ce2c267ebc911921948`。

## 结论与证据范围

实施进展：已按用户要求实现下文第 1、2 项第一优先级，详见 [车型能力设计](superpowers/specs/2026-10-11-lynk06-capabilities-design.md)。下文源码调查描述的是实施前的 0f9f4559 基线；当前车型仍未实车验证。

目标车辆是用户确认的 **2023 款领克 06 燃油版、Android 9**。按照用户最后的修正，当前状态为**尚未实车测试**；此前“能连接”不作为验证证据。

Android 9 本身不是需要重写 CarPlay 的理由。当前代码已有 API 25 起的兼容基础、Android 9 Wi-Fi Direct 旧版路径和软件 Opus 编码。真正的适配工作集中在车型能力隔离、厂商音频/按键路由、连接链验证和车机窗口行为。

本轮没有取得这台 06 的完整固件号、设备标识、CPU ABI、屏幕信息或诊断报告，也没有找到能证明**这个型号与固件组合**全部兼容的一手资料。因此不确定它是否使用 E01/E02、CS11/8155 或 OS N；不把其他领克车型的参数写死为 06 默认值。

品牌名称、独立 applicationId、更新源和 CarPlay 返回车机标志已经完成；这些不是实车兼容性验证。本轮只增加调研文档，没有修改运行代码。

## 现有能力：应复用，不必重新实现

| 能力 | 当前证据 | 适配含义 |
| --- | --- | --- |
| Android 9 安装基础 | `mobile/build.gradle.kts`：minSdk 25、targetSdk 37 | API 28 满足最低版本；仍需核实实际 ABI、安装策略和运行表现，不应仅为 Android 9 降低 targetSdk |
| Android 9 Wi-Fi Direct | `docs/ANDROID9_WIFI_DIRECT.md`，已有旧版建组、生成凭据、清理和取消逻辑 | 不沿用旧资料中“无线必须 Android 10”的结论；领克固件能否建组、选信道和稳定传输仍待测试 |
| Siri/通话 Opus 编码 | `shared/.../media/OpusEncoderSupport.kt`、`SoftwareOpusEncoder.kt` | 没有系统 Opus 编码器时已有软件后备，不必新增一套编码器 |
| USB 配置切换 | `shared/.../transport/UsbConfigurationSelection.kt`、`IphoneUsbHost.kt` | 0.2.18 已处理部分内核驱动占用；不能直接套用基于 0.2.12 的旧修复 |
| 标准媒体控制与厂商按键入口 | `common/.../CarPlayMediaKeys.kt`、`GeelyWheelKeys.kt` | 先查标准媒体键，再验证目标车机是否具有兼容的吉利输入服务 |
| 显示、音频、连接设置 | `common/.../AirPlayPersistence.kt`、`CarPlayHostActivity.kt` | 先用已有设置测出合理参数，再决定是否增加 06 专属默认值 |
| 本地诊断、启动记录 | `DiPlayActivity.kt` 的诊断导出、`DisplayDiagnosticSnapshot.kt`、`StartupDiagnosticSnapshot.kt` | 优先扩充现有报告；无需新建上传服务 |

## 源码已经能确认的修改点

### 1. 增加领克车型及独立能力判断：第一优先级

现有 `GeelyHeadUnit.detected` 同时控制方向盘输入服务订阅和 Siri 录音源。它接受 `geely_fx11`、`cs11_a4`，也接受品牌/厂商等字段中出现 `geely` 或 `lynk`。

这存在两种适配风险：宽泛品牌匹配可能让 Android 9 误用另一套固件的策略；设备若只报告 Qualcomm 等名称，也可能无法识别。应把“目标车机身份”与“某项接口可用”分开：

- 车辆配置记录 Lynk 06 / 2023 / 燃油版；未知固件保留通用行为。
- 方向盘服务、麦克风录音源、音频焦点策略和显示区域分别判断。
- 厂商服务存在不等于 AIDL 版本、权限和按键码兼容，绑定与注册失败需要可诊断的回退。
- 只有证据明确的设备/固件才自动启用特殊策略；Android 9 不能单独成为某个厂商策略的识别依据。

主要文件：

- `shared/src/main/java/com/shilapi/xcertplay/network/GeelyHeadUnit.kt`
- `common/src/main/java/com/shilapi/xcertplay/GeelyWheelKeys.kt`
- `shared/src/main/java/com/shilapi/xcertplay/media/MicrophoneUplink.kt`

### 2. 隔离 BYD/DiLink 专属引导和接口：第一优先级

已确认：首次车型步骤直接枚举 DiLink 3/4/5/不确定；`SetupGuide.features(UNKNOWN)` 仍提供实验性的 BYD 导航和仪表地图；高级车辆数据入口也仍存在。部分导航设置已有 BYD 硬件检查，但尚无统一的 LynkPlay 功能隔离。

建议为 LynkPlay 增加明确的车型/能力开关，在界面和运行调用两层隔离 BYD HUD、仪表、车辆数据和 DiLink ADB 控制。不要仅把 BYD 文案改成领克，因为背后的协议没有变。

保留通用位置上报、屏幕显示、CarPlay 音乐/导航/电话、通用 USB 权限和媒体控制。燃油车不开放没有数据来源的动力电池/充电上报；方向盘音量键继续由系统处理。

主要文件：

- `common/.../setup/DiLinkGeneration.kt`、`SetupGuide.kt`
- `common/.../DiPlayActivity.kt`：`setupCarStep`、`advancedVehicleDataSettings` 等
- `shared/.../hud/BydOutputSettings.kt`、`BydNavigationOutputs.kt`
- `common/.../CarPlayHostActivity.kt`：车辆状态、仪表和侧栏调用

### 3. 建立可供实车验收的独立包：测试前置条件

普通 `:mobile:assembleDebug` 是源码构建，当前项目要求通过 `DIPLAY_AUTH_ASSETS_DIR` 显式提供本地认证输入，再执行 `:mobile:assembleStandaloneDebug`，才能生成独立车测包。详情见 [构建说明](BUILD.md)。缺少认证输入造成的启动失败，不应误判为领克硬件问题。

还需确定长期包名、签名与版本序列。当前 debug 包名是 `com.leth0x01.lynkplay.hudtest`，正式 applicationId 是 `com.leth0x01.lynkplay`；两者是不同安装，不自动继承设置。持续车测应使用稳定的包名和签名，发布前检查内置更新能否安装到同一包，避免把 `.hudtest` 包当作正式版升级路径。

认证素材与签名留在本地构建输入，不进入 Git。保留现有 GPL 许可和上游署名。

## 实车验证后才决定的修改

| 顺序 | 项目 | 需要验证什么 | 可能需要改什么 |
| --- | --- | --- | --- |
| 1 | 有线 USB | 数据口能否发现 iPhone；活动配置、USBMUX/NCM 接口；授权/信任；退出后原生 USB 功能是否恢复 | 必要时补配置读回、错误码、活动配置共享和受限驱动恢复；先比较现有 0.2.18，避免重复补丁 |
| 2 | 无线车机热点 | Android 蓝牙 RFCOMM 是否可用；热点接口归属、真实频段、IP、Bonjour/TCP 到达 | 固件专属热点接口观察方式、权限门禁或发现地址选择；不根据 `wlan0` 名称就认定它是热点 |
| 3 | Wi-Fi Direct | Android 9 建组、凭据、信道操作/清理、传输是否连续 | 复用已有旧版路径；固件不支持时明确引导回系统热点，不全局禁止 Android 9 |
| 4 | 音乐与导航播报 | 焦点请求返回值、实际输出设备/音源、导航压低音乐、电话结束恢复 | 基于测量选择音频 attributes/stream、延迟焦点和恢复策略；先用已有音频设置对照 |
| 5 | Siri/电话麦克风 | 权限、录音源、是否有有效采样、是否被原生助手占用；电话回声与恢复 | 独立的录音源选择或已证实的固件策略；系统无 Opus 时复用软件后备 |
| 6 | 方向盘及原生音乐卡片 | Activity/MediaSession 是否收到键；输入服务是否存在、兼容、允许订阅；前后台与蓝牙音频模式 | 标准媒体接入或厂商服务适配；必要时媒体通知与会话关联；避免标准事件、厂商事件和 AVRCP 重复切歌 |
| 7 | 显示与触摸 | 实际窗口、分辨率、density、系统栏/Dock、触摸映射、倒车切换恢复 | 基于实际 insets 和窗口的显示配置；有证据后才增加 Dock 空间选项，不套用另一辆车的 1920×920 |
| 8 | 开机/唤醒/后台 | 冷启动与休眠唤醒分别测试；BOOT_COMPLETED、手机蓝牙广播；切桌面/倒车与清最近任务的区别 | 厂商启动入口或受控重连，仅在实际生命周期证据充分时添加 |

### 网络上的一个明确边界

当前 `AirPlayPersistence.loadWirelessHotspotMode` 与保存逻辑会把 `LOCAL_ONLY_HOTSPOT` 转成 `MANUAL`。因此底层存在 `LocalOnlyHotspotManager`，不等于当前 UI 已支持免配置的自动临时热点；该 Manager 还校验 5 GHz。

若需要“打开即自动创建热点”，这是额外开发项：需要确定 Android 9 实际热点频段/信道、蓝牙共存和会话结束恢复策略。首轮优先验证已有系统车机热点和 Wi-Fi Direct，不为了免填密码提前修改无线协议或自动切换蓝牙。

### 音频上的一个明确边界

当前 API 26+ 的焦点请求封装没有启用 delayed focus；播放器与媒体键代码均有焦点相关处理。是否需要改变默认策略，应由目标车机的请求/回调和音频输出证据决定。Android 官方要求按焦点获取/丢失调整播放，并支持延迟焦点，不能把持续抢焦点当成正常恢复策略。[Android 音频焦点文档](https://developer.android.com/media/optimize/audio-focus)

### 仪表、歌词、车辆数据单列为后续项目

本轮没有取得这台 06 对应的厂商 SDK、服务协议或有效授权资料。BYD 的导航广播、车速/电池字段和仪表投屏服务不能通过替换包名变成领克接口。

中控 CarPlay 应先完成验收。仪表导航、原生歌词卡片、胎压/车辆数据需要另行确认目标固件的接口与访问限制，不能承诺普通 APK 一定能实现。

## 外部参考及使用限制

1. [DiPlay PR #626](https://github.com/shihabal3amri/DiPlay/pull/626)：贡献者报告在 `geely_fx11` / `CS11_A4`、德赛西威 Android 11 上验证方向盘和 Siri；该实现已在当前源码。它不是本车 Android 9 的证明。
2. [LynkCo-DiPlay README](https://github.com/soBigRice/LynkCo-DiPlay)：社区项目针对另一款领克的 Android 9 车机，有有线成功反馈，无线仍存在问题，可作为对照候选。
3. [该项目领克 profile 配置](https://github.com/soBigRice/LynkCo-DiPlay/blob/07075fdd9f52b2f9526df1ceac42747d44b2abb4/mobile/src/lynkDebug/res/values/config.xml)：将 BYD 集成、USB 核验、驱动恢复和热点能力独立控制。
4. [该项目 USB 配置实现](https://github.com/soBigRice/LynkCo-DiPlay/blob/07075fdd9f52b2f9526df1ceac42747d44b2abb4/shared/src/main/java/com/shilapi/xcertplay/transport/IphoneUsbConfiguration.kt)：可对照活动配置核验及错误诊断，不能直接覆盖当前更新的上游代码。
5. [Android USB 接口文档](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#claimInterface(android.hardware.usb.UsbInterface,%20boolean))：force claim 可断开内核驱动，不应把它看作只读探测；目标车测试要包含退出后的原生功能恢复。

外部分支核查提交：`07075fdd9f52b2f9526df1ceac42747d44b2abb4`。如后续复用，逐项核对授权、归属与测试，再移植小范围改动；本轮没有导入该分支代码或素材。

## 最小实车采集与验收

### 补充：GitHub 接口实现调查（2026-10-11）

除了 Android 9 连接适配，已经找到社区媒体接口实现。以下是源码或作者文档证据，不是本车验收结果：

| 项目 | 查到的实现 | 与本车的关系 |
| --- | --- | --- |
| [soBigRice/LynkCo-DiPlay](https://github.com/soBigRice/LynkCo-DiPlay) | 领克 03 OS N / Android 9 的 DiPlay 适配；作者记录有线成功，无线仍待解决 | 最接近当前 Android 版本的连接与生命周期参考，不是 06 验证 |
| [Tamshen/GalaxyPlay](https://github.com/Tamshen/GalaxyPlay/blob/main/galaxy/common/src/main/java/com/shilapi/xcertplay/L7ReflectiveMediaCenter.kt) | 加载设备 SDK 的 `MediaCenterAPI`、`MusicClient`、`MusicPlaybackInfo`，注册本应用，处理播放/暂停/切歌回调、歌曲状态和进度上报 | 银河 L7/L6、Android 11，最低 Android 10；接口思路值得核对，应用不能直接用作本车 Android 9 方案 |
| [Salat39/GMediaProxy](https://github.com/Salat39/GMediaProxy) | 作者文档说明用 OneOS `IMusicManager` AIDL，把第三方 MediaSession 元数据和控制接入原生媒体中心 | 依赖 OneOS 契约，使用 OEM 在线音乐包名；不能直接移植到要求独立安装标识的 LynkPlay |
| [mmwtl/AtlasMediaWidget](https://github.com/mmwtl/AtlasMediaWidget) | 当前仓库含 OneOS/ECarX 适配、媒体 runtime、仪表媒体桥接及自建桌面卡片；原 AtlasMediaApi 已迁入 | 面向 Android 11 OneOS，自建卡片不等同于领克原生卡片，需分别核实契约与许可 |
| [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay/blob/main/docs/GEELY_PROJECTION.md) | 方控映射、独立车辆桥接、Android 第二屏导航提示/布局代码 | 文档明确第二屏功能为源码阶段，系统公开显示屏不等于厂商私有仪表通道；不能据此承诺 06 仪表导航 |
| [huangwits/DiPlay-Preface](https://github.com/huangwits/DiPlay-Preface) | 星瑞 E01 / MT6735 / Android 5.1 适配 | 搜索曾因描述包含领克 06 命中，当前 README 主目标为星瑞；不视为 2023 06 / Android 9 验证 |

上述发现补充了媒体/方控的可参考实现，但尚未找到明确验证 **2023 领克 06 燃油版 / Android 9** 的完整开源厂商接口接入方案。优先对照目标设备是否具有 EAS MediaCenterAPI 或 OneOS 媒体/输入服务，再选择一个适配后端；接口名字相似不证明版本、权限或行为兼容。此调查未下载 APK、导入 SDK、绕过授权或修改车机。

没有 ADB 也可以先测试与导出应用诊断；如车机已有可用 ADB，以下命令只读取目标标识和显示信息，无需先改系统参数：

```powershell
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.brand
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.product.name
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.build.display.id
adb shell wm size
adb shell wm density
adb shell pm path com.geely.inputservice
```

`pm path` 没有结果只说明该包未被此查询发现，不能证明方向盘不支持；没有服务也不意味着必须增加无障碍权限。无需为调研禁用原生助手、HiCar 或其他系统应用。

首次测试建议顺序：

1. 核实包内认证输入、实际包名/签名与车机安装结果，先保证测试包可用。
2. 停车状态先试有线：手机解锁、确认系统授权/信任，记录首帧或失败阶段；不要同时运行两款投屏应用。
3. 测音乐、导航、Siri、电话、方控，再测试切桌面/倒车后恢复。一次只改变一个参数。
4. 用系统车机热点尝试无线，另行测试 Wi-Fi Direct；记录各自成功与失败，不能以建热点成功代表 CarPlay 成功。
5. 保存现有诊断报告，补充完整固件号、iPhone/iOS、连接方式和失败时间。按需分享前检查其中内容。
6. 基础功能通过后再测连续使用、断线重连、冷启动与休眠唤醒；以至少一次 30 分钟使用作为初步持续性观察，不视为全部稳定性验收。

## 建议实施顺序与上游维护

第一批修改范围建议为：**车型/能力入口、BYD 功能隔离、领克引导与诊断补充、稳定车测包配置**。随后按实车结果分别提交音频、按键、USB/无线或显示补丁。

继续保留 `main` 为上游基线，`lynkplay` 承载适配。领克差异尽量放在小范围资源/profile 和能力接口内；通用协议修复独立提交，便于后续合并 upstream、确认是否已被上游修复。当前方案不需要重命名所有源代码包或大规模删除 BYD 源文件。

实施时遵守根目录 AGENTS.md：设置归类、多语言、重连提示、布局截图及完整 CI 检查。既有的 Windows 基线测试失败见 [验证记录](LYNKPLAY-VALIDATION.md)，新增适配仍需做同基线对照；本次文档调研未重新运行构建，也没有扩大此前验证范围。
