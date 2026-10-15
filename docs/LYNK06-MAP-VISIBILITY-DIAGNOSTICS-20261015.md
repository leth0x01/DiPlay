# 领克仪表地图显示依据与诊断

## 已确认的判断链路

### iPhone 导航请求

`FceNavigationState` 从 iAP2 RouteGuidanceUpdate 中读取状态字段 `0x01` 和来源字段 `0x13`。状态为 1、3、4、5、6 时判为导航进行中；明确为空的来源会结束导航；尚未收到状态字段时，非空来源作为导航依据。`CarPlayNavigationStatus` 将变化传给 `Lynk06ClusterLink.setNavigation()`。

### 应用地图窗口

`Lynk06ClusterState.showMap` 的计算为 `available && navigating`，没有使用 `getNaviMode()`。导航结束会先在主线程隐藏窗口，再由厂商接口 worker 清理导航声明。原车显示器常驻，不能用显示器是否存在判断地图。

`ClusterMapPresentation.setMapVisible()` 在 BX11 上同时设置 decorView 的 VISIBLE/INVISIBLE 与窗口 alpha，避免 Android 9 的 SurfaceView 独立图层继续覆盖原车界面。窗口生命周期、Surface、视频流分别记录；`appMapReady` 仅表示应用窗口与输出条件齐备。

`getNaviMode()` 在历史实车地图显示和消失时均出现过 1。已保存的本车 `DimInteractionImpl.getShowPresentationOption()` 方法直接返回 0。因此两个 getter 都不能单独作为物理仪表地图可见性的判据。

### 原车投影门控

本车 Power 服务 `ECarXScreenProjectionService.bM()` 的已核对分支：

- `isFactoryReset=true` 或 `isNaviNotStarted=true` 时拒绝投影。
- 取得主机状态对象后，`ihuState=25` 或 `lcdState` 为 2、3、4、5 时拒绝；状态对象为空的分支返回允许。
- `startScreenProjection(start)` 先检查上述门控；通过后，start=true 的分支向属性 `0x2870006c` 写入字节 1，并记录 `INFO_ID_MMI_NAVSYNCDISPLAY open`；start=false 分支写入字节 2，并记录 `close`。
- 车辆信号连接情况 `mIsECarXSingalConnect` 在这条路径中有独立日志，诊断保留原值。

原车门控拒绝日志、启动/停止请求和 MCU 写入记录分别保存。收到 start=true 不能直接当作 open；门控拒绝时也不能自行把最后的 MCU 开关记录改成 false。MCU 写入日志用于判断原车发出了什么指令，不等同于物理屏幕像素回执。

## 自动诊断与媒体关联

每次独立状态变化生成 `mapRevision`。封面登记/读取、原车歌曲与播放信息发布，以及 CarPlay 媒体请求/发送/手机状态记录，均附带显示状态版本和应用输出、最后 MCU 开关指令摘要。

显示诊断记录保留导航请求、接口可用性、应用窗口显示策略、菜单 getter 与回调原值、投屏选项 getter 与回调原值、窗口生命周期、可见性、alpha、显示器编号、Surface 有效性和流状态。回调原值在 getter 不变时也会记录。

原车日志订阅加入 `ECarXPowerManagerService` 和 `ECarXScreenProjectionService`，从订阅开始时间读取，不把上一会话遗留的最后一行当成本次反馈。已核对的 Power 投影事件在原始日志限流前解析。重复的同状态事件更新时间，不重复刷状态变化日志。

报告保留原车事件时间、接收后的证据年龄、采集状态和日志缺口。新一轮采集清空旧原车投影结论；旧窗口或旧链路的迟到关闭记录不能覆盖新实例。尚未观测到的值保留为 null，物理屏幕可见性保留为 unknown。

## 证据来源

- 项目：`shared/.../hud/FceClusterOutput.kt`、`CarPlayNavigationStatus.kt`；`common/.../Lynk06ClusterLink.kt`、`ClusterMapPresentation.kt`、`CarPlayHostActivity.kt`。
- 本车离线 Power 反汇编：`.private/lynkplay-cluster-evidence-20261013/04-derived-disassembly/power-dexdump.txt`，bM 方法约第 16628 行、startScreenProjection 约第 16920 行。
- 本轮仅恢复 quickened 指令宽度的派生文件：`.private/report8-analysis/power/power-widths-dexdump.txt`，bM 约第 15023 行、startScreenProjection 约第 15295 行。quickened 方法、实例字段的符号索引未恢复，不引用其中的占位符作为真实调用名。
- 原车投屏选项 getter：`.private/lynkplay-cluster-contract-20261013-190825/boot-AdapterAPIImpl-remainder-dexdump.txt` 约第 26780 行，常量 0 后直接返回。
- 历史原车日志：`.private/lynkplay-cluster-evidence-20261013/01-raw-logs/power-logcat.txt` 包含 naviNotStarted=true 后拒绝；`power-logcat-working.txt` 包含 naviNotStarted=false、ihuState=17、lcdState=0，以及后续 MCU open。属于 10 月 13 日的历史证据，不作为报告 8 当前会话的原车状态。
