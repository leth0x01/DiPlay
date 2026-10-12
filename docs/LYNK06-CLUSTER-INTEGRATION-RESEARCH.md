# 领克 06 原车仪表联动检索（2026-10-12）

## 已核实的接口线索

[百度 MapAuto 官方输出文档](https://vodp.baidu.com/docs/map/screen-output)明确要求仪表显示以 DashboardMapDisplay 名称暴露，并通过 DisplayManager 的 Presentation 分类查询；应用监听显示变化并建立 Presentation。与当前 0.1.5 的画面输出路径一致。此文档未给出本车原厂导航状态和手势契约。

[GInputBridge](https://github.com/Salat39/GInputBridge)公开了亿咖通 AdaptAPI 接口桩。README 说明厂商模块用于编译链接，运行实现来自车机系统；不能将空实现直接当作可运行仪表联动代码。

| 目标 | 源码中存在的方法 / 回调 | 来源 |
| --- | --- | --- |
| 导航状态 | notifyNavigationStatus、notifyTurnByTurnStarted、notifyTurnByTurnStopped、updateNaviStatus | [INaviInteraction.java](https://github.com/Salat39/GInputBridge/blob/main/adaptapi/src/main/java/com/ecarx/xui/adaptapi/diminteraction/INaviInteraction.java) |
| 仪表导航模式 | getNaviMode、switchNaviMode、onChangeNaviMode | [IDimMenuInteraction.java](https://github.com/Salat39/GInputBridge/blob/main/adaptapi/src/main/java/com/ecarx/xui/adaptapi/diminteraction/IDimMenuInteraction.java) |
| 投屏选项 | getShowPresentationOption、onShowPresentationOptionChanged、setPresentationToDimSwitch | [DimInteraction.java](https://github.com/Salat39/GInputBridge/blob/main/adaptapi/src/main/java/com/ecarx/xui/adaptapi/diminteraction/DimInteraction.java) |

这些签名为后续核对提供依据。setPresentationToDimSwitch 的四个参数语义、实车库版本及调用权限，尚未从本车固件确认。

## 论坛与同平台项目

[HYWebview E01 作者帖子](https://lynkco.hyphp.cn/t/1892/2.html)介绍第三方地图仪表支持，并说明部分方式使用另外生成的导航路线。页面标注 E01，评论也有等待 E02 适配的反馈；该帖不能证明 2023 款领克 06 E02 的调用契约。

[ecarx-e02-sideload README 的搜索缓存](https://github.com/ardentlab/ecarx-e02-sideload/blob/main/README.md)曾列出宝腾 S70 V333 的 Cast to Meter Panel。复核时 GitHub 仓库与 API 返回 404，可读取的 raw README 也未包含该指南，本轮未取得其飞屏源码。

## 与已有实车采集交叉核对

系统包清单存在 ecarx.xsf.gestureservice、ecarx.naviservice、com.autonavi.amapauto、com.ecarx.naviwidget 和 com.ecarx.eas.carservice。日志记录 AdaptBinderProvider 与 AdaptApplication 启动，同时有 EasPluginProvider 查询失败。系统服务清单存在 ecarx_interactive。这些是本车平台服务的定位线索，尚未证明上述接口可由 LynkPlay 调用。

## 后续核对

1. 从本车系统 SDK / JAR 和上述服务核对 DimInteraction、INaviInteraction、IDimMenuInteraction 的类、方法、参数和权限。
2. 对比原车导航开始、结束、三指飞屏及仪表菜单切换时的日志，确定事件方向和显示模式。
3. 确认后将 CarPlay 的真实导航状态通知仪表，并让应用的地图输出跟随原车显示模式；与本 App 的三指设置手势协调。

检索阶段仅记录接口线索；后续修改依据下述实车采集与原车 APK 核对结果。


## 专项采集入口

脚本：`scripts/collect_lynk06_cluster.ps1`，Windows PowerShell 5.1 语法，UTF-8 BOM。放在 adb.exe 同目录，或用 `-AdbPath` 指定 adb 路径；多设备时用 `-Serial` 指定车机。

```powershell
powershell -ExecutionPolicy Bypass -File .\collect_lynk06_cluster.ps1
```

执行前关闭 LynkPlay 的仪表地图开关。脚本先按提示记录原车未导航、开始导航后的简洁箭头提示、三指左划显示地图、仪表菜单切回简洁模式、仪表菜单切到地图模式、退出导航 6 个阶段，随后采集方向盘与媒体语音对照。每个阶段操作完成后按回车，可在同一次输入中补充现象备注。

采集包括车机标识、系统服务与共享库、LynkPlay 权限信息、导航/手势/仪表组件 APK、厂商 JAR 与预编译代码文件、各阶段日志标记、全缓冲区日志、显示和窗口状态、中控截图。仪表画面需另用手机拍摄并标注对应阶段。

输出为脚本目录下的 `lynk06_cluster_时间戳` 和同名 ZIP，已加入 Git 忽略规则。文件用于核对 SDK 定义、参数含义、权限要求及原车事件顺序；运行时接口可用性还需后续应用探测。脚本已通过本机临时安装的 PowerShell 7.6.6 语法解析检查，Windows/车机执行结果待采集。


## 用户确认的原车操作流程

开始导航后，仪表呈现简洁箭头提示。中控原车地图中的三指向左划将仪表切换到地图；仪表菜单切回简洁模式后，导航继续。退出导航则结束导航显示。

后续联动需要分别管理导航是否进行，以及仪表采用简洁或地图模式。三指左划与本 App 三指下滑设置菜单的方向分工需结合系统手势处理核对。


## 同一轮补充方向盘、媒体与语音证据

已采集的系统包中存在 android.car.input.service、ecarx.xsf.inputservice、com.ecarx.multimedia、ecarx.xsf.mediacenter、com.tencent.wecarspeech、com.ecarx.btphone、com.ecarx.hmi 和 com.ecarx.systemui.plugin。专项脚本增加这些组件的 APK / 预编译文件和包信息。

共 22 个阶段：6 个原车仪表阶段，9 个原车音乐及方向盘阶段，7 个 CarPlay 对照阶段。按键包括上一曲、下一曲、暂停、播放、音量加减和语音键短按/约 2 秒长按。每个阶段记录输入设备事件、厂商服务日志、媒体会话、音频状态和语音服务；CarPlay 阶段在 shell 已取得 root 时额外读取应用会话日志。CarPlay 连接失败可在准备阶段输入“无法连接”，脚本跳过后续 CarPlay 按键阶段。

输入事件采集使用 getevent，按已有 root 探测结果选择 root 或普通 shell，每个阶段远端最多运行 120 秒，完成后停止该阶段的本机采集进程。CAN 按键可能只出现在厂商回调中，核对时结合完整日志判断。

用户另提供方向盘按键布局照片、电话/自定义键原有功能说明、仪表简洁/全景模式及菜单名称照片。语音键测试备注应区分唤醒 Siri 或原车语音，并记录音乐暂停、压低或继续播放的现象。

用户在 Windows PowerShell 执行时遇到第 189 行解析错误。两处提示中的中文弯引号已改为「」；PowerShell 7.6.6 解析器复现修复前的 5 个错误，修复后整份脚本为 0 个解析错误。Windows 与车机采集运行待复核。

## 2026-10-12 15:44 专项采集复核与代码接入

采集第 1–14 阶段的完整日志均包含本阶段 BEFORE / AFTER 标记；第 15 阶段起日志为 ADB offline，后续部分出现指定设备找不到。72 个拉取文件的大小与 SHA256 全部一致。

- 六个原车仪表阶段中，DashboardMapDisplay 均持续存在且为 1920×720 / 30 Hz，显示器存在不是仪表地图模式信号。
- 三指左划阶段 15:53:50.005 记录 ECarXScreenProjectionService.startScreenProjection start=true；15:53:50.145 记录 INFO_ID_MMI_NAVSYNCDISPLAY open。菜单简洁／地图阶段存在原车 ThreeAlbumService Presentation 的 show／hide 变化，不能将这些日志直接视作 IDimMenuInteraction 的完整模式回调。
- 本车 AdapterAPI.jar 中存在 DimInteraction.create(Context)、IConnectable 的连接与断开回调、getDimMenuInteraction、getNaviMode、onChangeNaviMode，以及 notifyNavigationStatus。SDK 版本为 1.1.18.9，FULL=3、SIMPLIFY=2、OFF=1。
- 从本车 XSFNaviService APK 核对原车先连接 IConnectable，再取得导航接口；导航进行调用 notifyNavigationStatus(1)，结束调用 0。原车 setPresentationToDimSwitch 的调用为 (1, com.autonavi.amapauto, com.autonavi.amapauto, on)，其第三方接入权限与参数语义仍需实现代码复核。
- 切歌／暂停／播放通过原车 MediaCenterClient 进入蓝牙 AVRCP；音量加减厂商键码为 301 / 302，语音短按为 298。第 14 阶段触发原车语音并发送蓝牙音乐暂停命令。

0.1.6 新增 CarPlayNavigationStatus 与 Lynk06ClusterLink。领克链接仅在已确认的实车配置且仪表地图开关开启时初始化，厂商调用使用独立线程；读取／监听仪表模式，跟随真实导航状态显示或隐藏窗口。接口失败隐藏输出，关闭时撤销本次导航通知并释放菜单和连接回调。模式切换保留视频 Surface，不因此重连 CarPlay。

原车投屏服务与 MCU 开关调用、权限和实车模式回调待核对。采集脚本增加 ecarx.power.service、com.neusoft.extraservice、com.ecarx.ecarxinteractiveservice，支持 -StartPhase 与 -SkipComponents；断线后停止推进、保留目录与 ZIP。

## 运行时 ABI Probe 与文本 snapshot

`Lynk06ClusterAbiProbe` 在仪表链接首次初始化时使用 `Class.forName(..., false, classLoader)` 读取六个仪表相关类的声明方法，排序后以 `领克仪表 ABI：` 前缀写入会话日志。Probe 不调用厂商方法。仪表链接仍由已验证车型与仪表地图开关控制。

在本车开启仪表地图并建立会话后，导出诊断报告，提取 `领克仪表 ABI：开始` 至 `领克仪表 ABI：结束` 的全部记录，去掉每行时间戳后保存为 UTF-8 文本。snapshot 应附实际固件号与 AdapterAPI 版本，重点核对 `create(Context)`、连接 watcher 注册／解除、连接／断开、菜单与导航接口获取、模式读取／回调注册／解除，以及 `notifyNavigationStatus(int)`。记录中的读取失败属于 ABI 验证异常，需先核对再判断兼容。

测试桩验证反射采集流程、恢复状态机和生命周期；本次新增 Probe 的实车输出尚待采集。固件 ABI snapshot 以实车运行结果为准，厂商 JAR 保留在本地采集资料中。
