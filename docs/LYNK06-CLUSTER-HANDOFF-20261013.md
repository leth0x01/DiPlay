# 领克 06 仪表地图排查交接（2026-10-13）

## 2026-10-14：新系统测试版独立显示物理仪表地图已验证

用户明确确认三个条件同时满足：**新 LynkPlay 系统测试版、原车高德未开启导航、iPhone 正在真实导航**，物理仪表已经显示 CarPlay 地图。成功现场保存于 `.private/lynkplay-system-live-20261014-002908/map-success-012231/`，后续结束导航快照另存于同目录下 `iphone-stop-*`。这是用户物理反馈与 ADB 业务调用共同确认的成功，不只是副屏窗口存在。

必须区分包版本：从成功现场重新拉取的已安装 APK SHA-256 是 `446951431db6a37c3f00a567a5569e13916a2148fd34dc563d674579c3f01ad7`，不同于前面最初构建的 `a5c592...`。两者版本显示均为 47 / `0.1.9-bx11-system-test`，所以只看版本号不能区分。实际成功包、签名和哈希已保存，源码工作区另有后续 `SystemUidVpnPreparation` 修复；其背景见 [系统 UID VPN 修复](LYNK06-SYSTEM-VPN-FIX-20261014.md)。不能将成功连接归因于最初 APK 或仅归因于删除另一 App。

成功时实际进程 PID19212 为系统 UID，VPN/tun1 已连接。NaviInteractionImpl 在该进程内记录真实完整导航卡片 `navStatus=1`、转向103、下一路口距离、目的地距离、剩余秒及道路名称。OEM 导航包为 `enabled=2`，无运行进程。SDK 的 AdaptAPI 业务路径实际执行并与物理地图成功对应；EAS navi 模块许可仍未独立证明。

用户随后仅结束 iPhone 导航，反馈“地图仍显示”。只读快照里 OEM 包仍为 `enabled=2`，因此不能宣称退出恢复已验证成功，也不能只凭地图保留判定恢复逻辑失败。另一 App 先前曾禁用 OEM，之后被用户删除；当前暂停所有权还不能从 shell 确认。读取本包私有恢复记录和诊断日志被 UID2000 shell 拒绝，具体 Permission denied 已保存，没有绕过权限。

已请求用户正常断开 CarPlay、保留安装并先不启动高德，以继续验证退出清理与 OEM 原状态恢复。本段采集没有通过 ADB 申请焦点、写导航状态、切换模式、发原始 DIM 或手动改变 OEM enabled setting。

## 2026-10-14：系统测试包已安装，等待真实导航实验

用户恢复 ADB 后，设备确认为 `172.20.10.6:5555` / BX11 / IHU514G / SDK28，固件 fingerprint 与平台签名核验目标一致。新证据目录：`.private/lynkplay-system-live-20261014-002908/`。

独立包名测试 APK 安装成功。`dumpsys package` 显示 `userId=1000`、`android.uid.system`，实际启动进程的 `/proc/<pid>/status` 四个 UID 字段均为 1000。`CHANGE_COMPONENT_ENABLED_STATE` 和 `ecarx.oem.permission.OPENAPI_NAVI_PERMISSION` 均 granted=true；这只是安装权限结果，不证明 EAS navi 模块许可。

原车导航包在安装前和打开测试版设置页面后均为 `enabled=0`。初始检查另一 CarPlay 无运行进程。未通过 ADB 发送导航状态、申请焦点、切换模式或原始 DIM 消息。日志已连续采集，未清空缓冲；采集进程 ID 已保存。

已请求用户连接 iPhone 到测试版并开启既有仪表地图选项，先保持双方未导航。SDK 仪表连接、真实导航后的物理显示和结束后的 OEM 恢复实验尚待用户准备确认，不应将“安装成功”写为“地图成功”。

## 23:03 起：按用户“都做”授权接入组合实现，并解决系统签名材料

本轮用户明确授权接入完整导航信息、原车导航暂停/恢复和系统 UID 构建，范围已从前面的只读排查扩展为产品代码实现。没有执行车机安装、应用启动或仪表写入。新证据目录为 `.private/lynkplay-system-implementation-20261013-230349/`；构建与恢复约束详见 [系统测试版本说明](LYNK06-CLUSTER-SYSTEM-BUILD-20261013.md)。

用户提供的 `.private/CarPlay-20261004-23.apk` 与实车拉取的成功 APK 字节哈希完全一致。其证书、只读拉取的本车 `framework-res.apk` 证书、官方 AOSP `platform.x509.pem` 证书 SHA-256 均为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

APK 不能还原签名私钥；本车使用的是公开 AOSP 开发测试平台证书，所以另从官方公开测试材料取得对应密钥，验证私钥/证书配对后创建被忽略的本地平台签名配置。没有复制成功 App 的包名、AppId/AppKey 或导航许可。系统 UID 签名材料问题已解决，但新包是否获授 EAS navi 仍未知。

已加入独立的真实 iAP2 导航信息通路，不复用断线保留的 UI 转向卡片；缺失真实完整数据时不编造路线。通过 `updateNavigationInfo(INavigationInfo)` 上报导航卡片，使用成功 APK 的 BX11 turn ID 映射，距离以米、ETA 以剩余秒计，驾驶方向按成功版本传 0。变化卡片最短 500 ms；未变化每 10 秒重发。完整卡片发送后尝试已确认的投屏门控解除，不新增焦点申请或原始 DIM 调用。

原车服务管理仅在车型匹配、实际 UID 1000、系统构建元数据开启及真实导航数据已到达的条件下运行。暂停前同步保存原 enabled setting，结束/关闭/连接故障后按原值恢复；恢复异常保留记录，下次打开 App 重试。对其他 App 已禁用的原车包不取得所有权、不擅自启用。Android 强杀时不能保证立即恢复；本车恢复唤醒广播的实际效果待实车检查。

`clusterSystem` 构建使用独立包名 `com.leth0x01.lynkplay.clustersystem`，强制核对本车平台证书；普通版本不改共享 UID 或签名。测试包默认仍须打开既有仪表地图选项。

首次完整 CI 执行了 1318 个 common 测试，新增导航卡片/恢复记录及仪表链路测试通过，4 个现有 shell 测试因本机未找到 `sh` 失败；已找到 `D:/Git/bin/sh.exe` 并设置 `DIPLAY_TEST_SHELL` 重新运行全部检查。最终结果以新证据目录里的后续构建日志为准。

2026-10-14 最终收尾：完整 CI 已通过（`ci-final-attempt2.txt`，复查 `ci-final-attempt3.txt` 亦通过），系统 APK 构建及专用 lint 已通过（`system-build-final2.txt`）。专用平台签名 Manifest 对 `CHANGE_COMPONENT_ENABLED_STATE` 的普通应用 lint 判断作单条明确说明，未关闭其他 lint 检查；此标记不授予运行时权限。公开源码凭据检查及 8 项 Python 脚本测试亦通过。

最终 APK：`.private/lynkplay-system-implementation-20261013-230349/LynkPlay-0.1.9-bx11-system-test.apk`，包名 `com.leth0x01.lynkplay.clustersystem`，versionCode 47 / versionName `0.1.9-bx11-system-test`，minSdk 25 / targetSdk 37。`apksigner verify` 通过，签名与本车平台一致；APK SHA-256 为 `a5c59288ab3b87f9c8f7ade5b81d9910f493d8a4f52c85cc5c02edcc8e3c5940`。源码、最终 Manifest 修订、关键测试报告、证书核验、原始日志及二进制哈希均已归档。

用户提供的外部 APK 分析与副屏、DimInteraction、完整卡片和投屏门控的已有证据相符，但须区分作者诊断日志与实车充分条件。外部描述的 `pm disable-user` 应更正为实际 `pm disable`（enabled=2）；完整导航卡片主要来自真实 CarPlay 数据，不能全部称为伪造。其“等待导航”占位卡属于无真实导航时仍保留投屏的另一行为，本实现遵循用户原约束，不发送虚构路线或等待占位导航卡。`getExtensionInfo` 也不在本车已核对的 INavigationInfo 14 个方法中，不能只凭字符串推定 ABI。

ADB 起初在线，证书拉取成功；后续变为 `offline`，尝试 `adb connect 172.20.10.6:5555` 仍 offline。因此尚未安装本包，也未验证本包 UID、SDK 实际连接、物理仪表地图及原车导航恢复。下一步最小实验：另一成功 App 正常释放仪表，确认 OEM 包未被其遗留禁用，然后用独立包进行“高德不导航 + iPhone 真导航 → iPhone 结束导航”的显示及恢复检查。

## 22:50 对照已能独立显示仪表地图的 CarPlay 版本

用户安装并确认 `com.ganfandw.carplay` 在原车高德不导航时已能独立显示物理仪表地图。只读采集目录：`.private/lynkplay-working-carplay-20261013-225015/`。本轮没有启动/停止该应用、切换仪表开关、恢复/禁用任何包、安装探针或修改正式产品代码。

### 安装及运行身份

现场版本 1.3.2 / versionCode 1302，minSdk=28、targetSdk=37，首次安装和更新均为 22:46:13。base.apk 为 33,931,146 字节，所有三个 classes*.dex 已提取并保存反汇编及警告，APK SHA256 和文件清单保存于证据。

`dumpsys package` 确认共享用户 `android.uid.system/1000`，进程 PID=25531 实际以 UID1000 运行；OPENAPI_NAVI_PERMISSION 与 OPENAPI_MEDIACENTER_PERMISSION 已 granted=true。它的 flags 没有 SYSTEM，不能说该 data/app 安装位置自动带来了系统身份。当前 LynkPlay 的普通 UID10088 与此安装/签名/共享用户条件不同。仅在 Manifest 写 sharedUserId 不能证明其他 APK 能取得相同身份。

### 已找到的接管实现

classes2.dex 中有未混淆的应用类：`ClusterDimController`、`ClusterNavInfo`、`ClusterGuidanceStabilizer`、`ClusterOemNavigationGuard`、`ClusterProjection`，以及 CarPlayHostActivity 的仪表开关路径。

1. **暂停原车导航服务避免竞争。** CarPlayHostActivity 有 `ClusterOemNavigationGuard.pause(Context, logger)` 调用。Guard 通过 `/system/bin/pm ... --user 0 ecarx.naviservice` 设置包状态，保存原 enabled state 和 paused 标记，并有 restoreNow / restore / recoverUntrackedDisabled / wakeAfterRestore 路径。恢复后定向 BOOT_COMPLETED 用于唤起导航服务。这里只分析源码，不执行这些命令。
2. **持续发送完整导航卡片。** ClusterDimController.pushTbt 通过真实 `INaviInteraction$INavigationInfo` 的动态代理调用 `updateNavigationInfo`，覆盖导航状态、转向 ID/SVG、下一路名、下一路口距离、目的地距离、ETA、方向、车道等；updateChangingValues 另对变化字段更新。字段来自 ClusterNavInfo，应用日志另有 CarPlay TBT 原始字段记录。
3. **显示接管与数据维护分开。** open 连接 DimInteraction、等待车服务/Power 就绪、注册导航回调、建立投影声明并推送初始卡片。reassertDisplaySource 能调用 Power 的 disableScreenProjection；关闭路径有 TurnByTurn stopped、撤销投影声明及注销回调。不存在“只发一个状态值就完成全部流程”的证据。

推送节流常量 PUSH_INTERVAL_MS=500、PUSH_LOG_INTERVAL_MS=10000；代码还对未变化的完整卡片按 10000ms 判断是否重新发送。不能由常量直接断言每500ms都写同样数据。实际采集到两个完整更新相隔约10秒，变化字段路径另有逻辑。

### 当前运行态交叉验证

- 22:48:48.446，ActivityManager 记录 `Killing 1629:ecarx.naviservice ... change ecarx.naviservice`，随后 EAS 记录导航绑定死亡；原车导航进程被停止的事件真实存在。
- 现场原车导航包 User0 的 enabled=2（DISABLED），导航服务 dumpsys 没有活动服务记录，只剩历史/失效连接记录。APK 的 Guard 静态实现与这一运行态一致。没有把 Guard 字符串当作唯一证据，也没有由本轮反向改变该状态。
- 应用绑定 EAS 的 ServicePoolService；当前导航输出主要可直接观察为 AdaptAPI 的 NaviInteractionImpl / ECarXDimServiceImpl。绑定框架或获授权限不能替代具体业务路径证明。
- 22:50:00.080 及 22:50:10.183，**PID25531 自身**记录 `navStatus new:[1]`，随后 `updateNavigationInfo updateNAVInfo` 和 `ECarXDimServiceImpl.updateNAVInfo` 的实际发送记录，包含转向、路名、距离和ETA，确认不是只有接口声明。
- 用户确认高德不导航时物理仪表已独立显示；不拿副屏截图代替该用户观察。

### 对现有排查的含义

有了可工作的本车对照，不需要继续猜其他车型 session/token。已确认的重要差异是**真实系统 UID/权限、主动暂停原车导航服务、完整导航数据维护、投影接管流程**。这几项同时存在，当前不能认定其中任一项独立就是充分条件。

当前 LynkPlay 及一次状态1探针没有采用这一组合；单次补发1失败不能否定该版本的完整接管路径。下一步可先离线逐方法对照 ClusterDimController / Guard 与 Lynk06ClusterLink，区分普通应用可用的完整数据接口和依赖系统身份的包状态操作。不要直接复制禁用原车服务到正式产品；需要先确定部署身份、恢复边界及必要性。

Guard 有恢复代码，但本轮没有让用户退出仪表地图来验证恢复，因此**不能声称退出后原车导航已正确恢复**。当前 DISABLED 是实际状态，交接不能忽略。

私有目录保存现场 APK、原始日志、包/服务状态、三个 DEX、业务类片段、关键调用检索、用户确认及 SHA256。按 AGENTS.md 尝试全量 CI，wrapper 仍受沙箱外缓存 lock 写权限阻止，任务未通过。

## 22:40 单变量实车实验：仅重新发布一次导航状态 1，未恢复

证据：`.private/lynkplay-nav-one-status-20261013-223820/`。用户明确授权上一节提出的单次状态 1 实验，并在执行前确认：iPhone 仍在真实导航、原车高德已结束导航、物理仪表 CarPlay 地图已消失。

临时探针 `local.lynk.navstatusonce`（targetSdk=25、UID=10093、PID=24159）通过已验证的 AdaptAPI DimInteraction.create / IConnectable 连接。22:40:20.843 收到 onConnected；22:40:20.973 **仅调用一次** `INaviInteraction.notifyNavigationStatus(1)`；同一 PID 的 ECarXDimServiceImpl 记录 notifyNavigationStatus:1，22:40:20.974 void 返回，无调用异常。源码在调用前持久化 attempted 标志，无重试、无周期重发。

探针在用户反馈前一直保持连接，没有调用 TurnByTurn、updateNavigationInfo、模式、焦点、飞屏、投影声明、MCU 或原始 DIM 消息，也没有伪造路线。getNaviMode 在调用前、立即返回后及 +1/+3/+6 秒均为 1，CarPlay stream111 继续解码输出。

用户物理仪表反馈原文：**“未恢复，仍显示导航未规划路线”**。这次实车结果反证：当前独立探针条件下，原车导航结束后单独重新发布状态 1 **不是恢复地图的充分条件**。不能据此将正式实现改为无限重发 1；也不能把接口无异常返回说成物理仪表已接受该状态。

上一轮抓到的原车结束链 `status=17 → updateNaviStatusInner:0 → notifyNavigationStatus(0)` 仍是事实，但它不能独立解释全部显示条件。本轮没有证明独立 session/token，亦未确定状态 1 是否在接收端成为有效状态。调用者是独立临时包，不能外推为正式 LynkPlay 进程内重发已经验证。

22:42:25.926 起，用户失败反馈已保存后才停止探针：unregisterConnectWatcher、disconnect 均返回，随后卸载，包及服务复查无残留。探针没有另发 notifyNavigationStatus(0)，未操作正式包，也不能声称此次写入后的仪表状态已恢复为实验前状态。

后续优先离线对比 `notifyNavigationStatus` 与原厂持续 `updateNavigationInfo(NavigationInfoToDim)` 的底层发送、导航有效标志及导航结束清理路径；再决定下一项单变量实验。不要同时加入完整数据、飞屏、模式与焦点，避免失去因果区分。

原始日志、一次调用摘录、用户反馈、前后系统快照、源码/Manifest/APK/构建脚本和 SHA256 均保存于新目录。按 AGENTS.md 尝试 CI，wrapper 仍无法创建沙箱外缓存 lock，任务未通过。正式产品代码未修改。

## 22:30 只读短实验：结束高德导航时仪表地图消失

新证据目录：`.private/lynkplay-nav-stop-20261013-223010/`。本次仅被动 ADB 采集；用户正常操作高德，没有由探针申请焦点、切换模式、写导航状态、飞屏门控、MCU 或原始 DIM 消息，也没有改产品代码或安装探针。

设备仍为 `172.20.10.6:5555` / BX11 / IHU514G，transport_id=16，LynkPlay 为 0.1.9 / versionCode 47。用户两次明确确认：

- T0：高德正在导航，物理仪表显示 CarPlay 地图；先不结束导航。
- T2：只结束高德导航，保持 iPhone 导航，物理仪表的 CarPlay 地图消失。

完整 `logcat -b all -v epoch` 在操作前启动，不清空历史日志。T0 系统快照为 22:31:14.878–16.986，T2 为 22:31:56.244–57.525。日志中首个导航结束状态事件为 22:31:35.772（epoch 1791901895.772）；这用于定位 T1 附近，不等同于精确触摸时间。原始窗口取 epoch 1791901890–1791901901，约结束事件前后五秒，另保留完整连续日志及操作后回收日志。

### 已抓到的具体接口链

| 车机时间 | 日志及含义 |
| --- | --- |
| 22:31:35.047 | 原车 NaviDashboardMgr 更新 NavigationInfoToDim，mNaviStatus=1；NaviInteractionImpl 记录 navStatus new=[1]，仍在输出导航数据 |
| 22:31:35.772 | MapManager 接收 MapStatusInfo.status=17（结束导航；与此前离线分支一致） |
| 22:31:35.892 | IHUMapGuidingState 处理 status=17 |
| 22:31:36.218 | NaviDashboardManager.updateNaviStatus 收到 status=17 |
| 22:31:36.219 | NaviDashboardManager.updateNaviStatusInner:0 |
| 22:31:36.219 | **原车 PID 1629 的 ECarXDimServiceImpl 调用 notifyNavigationStatus:0** |
| 22:31:36.239–36.241 | 紧接着记录 V2DIM State:1、notifyV2DIMState isReady:true、setPresentationToDim on:true；不能把本次失败简单归因为这一投影声明被关闭 |

这次已经定位到具体的“结束导航 → DIM 导航状态清零”链路，优先研究该全局状态如何与真实 CarPlay 导航状态共存。它与物理显示消失相关性很强，但没有进行单变量写入验证，不能宣布 notifyNavigationStatus(0) 是唯一或充分原因，也未发现可确证的独立 NAV session/token 销毁。

### 同时成立及未确认的条件

- stream=111 在结束前 22:31:33.836 为 rx/shown=16.6fps，结束后 22:31:38.840 为 17.2fps，recoveries=0；后续仍持续约 16fps。**物理仪表消失时，CarPlay 独立视频流没有随之停止。**
- T0/T2 的同一个 displayId=1 窗口 `Sys2037:...CarPlayHostActivity`、Session、client Binder 均相同；均 mViewVisibility=0、mObscured=false、mHasSurface=true、isReadyForDisplay=true，1920×720。Android 窗口存在与物理显示仍须分开。
- 原车导航、Power、Interactive、EAS 和高德服务的结构字段（ServiceRecord、进程、Binder、客户端绑定）在两次快照之间无差异；没有从这些快照看到服务死亡或绑定注销。采样不能排除中间瞬态或服务内部未 dump 的 token。
- LynkPlay PID 2230 的 getNaviMode 在物理地图可见的 T0 附近已经为 1，T1/T2 后仍为 1。因此当前这个 getter 不能单独判定物理仪表是否显示地图，也没有证据表明本次消失由它从 FULL 变 OFF 触发。
- 当前连续日志只找到一次 notifyNavigationStatus 调用，即原车的 0；未看到 LynkPlay 在此后重新发布 1。源码 refresh 条件是 `navigation && (!sentNavigation || republishNavigation)`，已成功发送后不会每秒重新发布；该源码行为与“外部清零后未重申”假设相容，仍需独立实验验证。
- 本次窗口没有 NAVSYNCDISPLAY open/close、isNaviNotStarted 或焦点变化的明确记录，相关服务 dump 也没有给出可据以断言的实时字段。**不能把之前其他轮次的 MCU open、门控解除、焦点到达，拼成本次 T2 仍成立的事实。**

主要原始证据：T1-minus5-plus5-raw.txt、T1-minimal-evidence.txt、notify-zero-context.txt、live-logcat.txt。状态对比：snapshots/T0、snapshots/T2、service-structural-differences.txt、cluster-window-comparison.txt。用户确认原文及时间：user-observations.txt。命令和哈希：COMMANDS.md、snapshot.ps1、SHA256SUMS.csv。

### 下一步最小实验

候选实验应只改一个变量：在真实 CarPlay 仍导航、原车高德已结束且地图消失时，通过已确认的 AdaptAPI 重新发布一次真实的导航进行状态 1，核对物理显示是否恢复，并同时观察其他状态；不伪造路线、不混入飞屏、焦点或模式操作。这是后续写入实验，**本轮未执行，也未授权自动循环/持续争抢全局状态**。即使恢复，也还需设计导航结束时的正确清理和多导航源共存行为，不能立即改正式实现为无限重发。

CI 按 AGENTS.md 尝试，仍失败于 Gradle wrapper 无法创建沙箱外缓存 lock；不声称测试通过。本轮仅文档及私有采集材料变更。

## 22:00–22:20 ADB：LynkPlay EAS 授权与连接核对

证据目录：`.private/lynkplay-eas-auth-20261013-220044/`。本轮没有修改正式产品代码、权限或许可，没有调用导航焦点、模式、导航状态、路线或 DIM 原始消息。没有重复 DIMProtocolManager / DimNaviService 检查。

### 当前包与应用自带许可

- 重新 adb connect 历史地址后确认 `172.20.10.6:5555` 为 BX11 / IHU514G，transport_id=16；正式包 `com.leth0x01.lynkplay` 为 versionCode=47、versionName=0.1.9、targetSdk=37、UID=10088，进程 PID=2230。
- 已拉取实际安装的 base.apk。Manifest 中 `eCarX_OpenAPI_AppId`、`eCarX_OpenAPI_AppKey` 均无声明；`assets/license.txt` 不存在。报告只保存存在性，不导出或展示凭据值。安装包、现场 EAS Core、boot SDK 和导航 APK 的 SHA256 见证据 input-binary-hashes.csv。
- 独立探针读取目标包资源，并调用现场 Core 的应用自带许可读取器，返回操作列表为空。与 Manifest/asset 检查相符：**本次 APK 的自带许可路径没有 navi 授权材料**。不据此推断应用商店许可不存在。
- EAS Core 与 boot SDK、导航 APK 的哈希均与上一轮契约输入一致，离线分析可用于本轮入口核对。

### 应用商店许可及实际授权查询：未确认

首两版探针在同一个 `com.ecarx.sdk.openapi.c.e` 读取器上先查自带许可，再查商店许可。现场日志确认后一次为 `Get cached operations for com.leth0x01.lynkplay`；该读取器按包名共用缓存。因此首两版 STORE_LICENSE 空列表不能作为商店无授权的证据。

已构建第三版，仅查询许可、使用独立的新读取器避免缓存污染。商店路径返回 null，原始异常为：

```text
UncheckedExecutionException: NullPointerException:
Attempt to invoke virtual method
'com.qihoo360.replugin.component.ComponentList com.qihoo360.loader2.p.e(java.lang.String)'
on a null object reference
```

调用栈经过 `Factory.queryPluginComponentList → PluginProviderClient.getProcessByAuthority → toCalledUri → c.e$b`。这说明在临时应用进程中显式加载 Core APK，并不会取得原厂已初始化的 RePlugin 环境；不能把该 null 当作“商店无许可”。没有启动整个 Core Application 或改插件配置。

另通过本车导航 APK 内实际 `IEASFrameworkService$Stub.asInterface` 代理，执行已核对的只读 `permission / master / authentication` 契约；AuthParam 的查询目标是 LynkPlay、操作是 navi，真实 Binder 调用者仍为独立探针。未猜事务号，未伪造目标包的 PID/UID。

查询返回外层 401：

```text
No Authorization to operate [permission] for pid [19123] uid [10090]
```

请求被拒绝于 permission 模块访问校验，尚未产生针对 LynkPlay 的 AuthResponse。因此**LynkPlay 是否通过商店实际获授 navi 仍未确认**。不是“LynkPlay 的 navi 授权结果为 false”，也不能用探针的 navi 拒绝结果替代目标包结论。详见 probe-report-second.txt、second-lifecycle-excerpt.txt、probe-report-license-only.txt 和 license-only-process-excerpt.txt。

### SDK 工厂、连接入口和 Binder

现有正式包的 DEX 未检出 NaviAPI / ECarXApiClient / EASFramework 的名称或直接调用引用；当前 EAS 服务绑定列表也没有 LynkPlay 客户端。源码的仪表连接入口为 `DimInteraction.create(Context)` / AdaptAPI IConnectable，与 EAS NaviAPI 不是同一链路。`run-as com.leth0x01.lynkplay id` 被拒绝：package not debuggable。没有向正式进程注入代码，也没有重启正式包。

所以本轮可以确认：**当前采集未见正式 LynkPlay 建立 EAS 导航连接**；不能把以下独立探针的初始化结果说成正式进程内结果。

独立 targetSdk=37 探针使用自己的真实 Context、UID/PID，现场实测：

- 工厂 `NaviAPI.get(Context)` 返回 `com.ecarx.sdk.navi.NaviAPIImpl`，加载器是 BootClassLoader；不是 NaviAPIImpl2。
- 无参 `connect()` 返回 null。本车方法反汇编也显示其具体实现直接返回 null；首版探针对返回值调用 getClass 造成 NPE，第二版已正确记录 null。
- 实际可执行初始化入口为 `NaviAPIImpl.init(Context, ECarXApiClient.Callback)`。第二版调用后得到 `onAPIReady(false)`，+1 至 +10 秒 `isConnected=false`，`mNaviService=null`。
- V3 连接组件 `com.ecarx.sdk.openapi/.ServicePoolService`，action=`ecarx.intent.action.OpenAPIService`，实际 descriptor=`com.ecarx.sdk.openapi.IServicePool`，alive=true。getAvailableServices 列出 navi 只表示模块存在；以探针真实身份 getService(navi) 返回 null，服务端同时保留 `No Authorization to operate [navi] for [local.lynk.easreadonly] ... verification [true]`。
- V4 连接组件 `com.ecarx.sdk.openapi/.support.v4.EASFrameworkService`，实际 descriptor=`com.ecarx.eas.framework.sdk.IEASFrameworkService`，alive=true。用真实身份取得 navi Binder 仍返回 null。`init([permission,navi])` 仅初始化调用者授权缓存/请求模块，不写许可。permission 查询返回上述 401。
- 第一版试图从 Core 的仅服务端 Stub 取得代理，遇到 NoSuchMethodException；第二版改用现场导航 APK 已存在的客户端 Stub，成功取得代理及真实返回。保留首版源码/APK/错误，避免混用结果。

### 初始化监听及清理

22:15:39.694，服务端 `OpenAPI_RemoteCallback_V3` 记录 `Client PID=19123 Register listener`。SDK 同时记录内部 remote callback 加入及 HandlerThread 初始化。22:15:39.697，`registerNaviObserver service is not connected,null`，没有成功建立导航观察者。

22:15:50 起探针依次执行 NaviAPIImpl.disconnect/release、解除自身 V3/V4 bindService、ECarXApiClientImpl.release，均返回。仅返回不能证明服务端所有监听已移除：22:17:09 强制停止**临时探针**后，服务端明确记录 `Client PID=19123 is Died`。随后卸载；服务、广播和进程复查没有临时包残留。第三版许可专用探针不执行 SDK 初始化、没有创建 SDK 单例，采集后亦停止并卸载。

正式 LynkPlay PID 前后均为 2230；没有强制停止正式包或修改其授权。清理回执见 probe-cleanup-transcript.txt、listener-cleanup-excerpt.txt、license-only-cleanup-transcript.txt。

### 结论与下一步最小实验

1. **是否获授 navi：未确认。** APK 自带许可材料缺失；商店查询受到插件进程环境限制；系统 permission 查询明确返回 401。
2. **是否成功连接：当前正式包未见 EAS 绑定；独立探针初始化失败。** 已确认实际工厂与 init 入口，但没有目标包内成功连接证据。
3. 下一步优先从已获 permission 模块查询权的原厂诊断入口，读取 LynkPlay 的 navi 授权结果并注明许可来源。不要换包名/借其他应用许可/填假 PID/UID，也不要据此继续试写焦点或 3902。若随后需要正式包身份的 SDK 初始化验证，应在独立诊断版本中安排单独实验，并说明可能产生的监听和连接变化；本轮没有部署此类版本。

按 AGENTS.md 尝试完整 CI 命令，wrapper 因无法写沙箱外 Gradle 缓存 lock 退出 1，任务未运行通过。本轮仅文档与私有证据变更，不能宣称 CI 通过。

## 后续更新入口

用户后续提交了 Windows 只读 ADB 核对记录，本工作区保存于 `.private/lynkplay-cluster-offline-20261013/USER-ADB-HANDOFF.md`。随后同步了 `.private/lynkplay-cluster-contract-20261013-190825/`，其清单 138 项大小与 SHA256 全部匹配。已复核第三轮摘录：requestNaviFocus 被原车焦点服务处理，但模式仍为 1、物理仪表仍未显示；本文件下方“第三轮日志未独立核对”描述的是此前阶段。

离线复核的新结果见 [协议与权限分析](LYNK06-CLUSTER-OFFLINE-20261013.md)。新旧包 NaviService DEX 一致；同步处理路径中 3902 原样返回请求，4100 与实际模式处理分支 4101/4102 必须区分。EAS Core 已核实会校验模块许可、调用者身份和许可中的应用绑定信息。后续以该分析及新采集原件为准，不直接照接口名称试写。

## 目标与当前结论

目标：CarPlay 导航时，在不启动原车高德导航的情况下，物理仪表显示 CarPlay 路线地图，并在导航结束或关闭功能时恢复。

当前尚未修复。用户在本次会话确认，第三轮导航焦点验证后仍不显示。第三轮的 APK、完整日志、Binder 返回、焦点归属和具体执行顺序未在本次会话独立核对，因此该反馈只能说明本轮操作未解决显示问题，不能据此判定 requestNaviFocus 本身已成功或完全无效。

下一轮先补齐接口与调用契约证据；本文件不授权直接发送其他车型的 DIM 原始消息或 Binder 事务。

## 先读的本地资料

- `.private/lynkplay-cluster-evidence-20261013/README.md`：原始证据交接、文件范围和解析局限。
- `.private/lynkplay-cluster-evidence-20261013/TIMELINE.md`：两轮实验及用户确认的物理仪表结果。
- `01-raw-logs/session.txt`：实车反射 ABI、连接与视频日志。
- `01-raw-logs/SECOND_PROBE_LOG_EXCERPT.txt`：第二轮日志转录。
- `05-probe/ClusterProjectionProbe.java`：第二轮探针。
- `04-derived-disassembly/interactive-dexdump.txt`、`navi-dexdump.txt`：导航焦点与导航 SDK。
- `docs/LYNK06-CLUSTER-INTEGRATION-RESEARCH.md`：此前 SDK 和原厂调用核对记录。

以上编号目录均位于该证据目录内。保持旧证据不变，新采集另建时间戳目录；旧证据包有 SHA256 清单。

## 设备与已验证的显示链路

- 车型识别：BX11 / IHU514G / `lynk_06_2023_petrol`；此前记录的平台为 ECARX E02。
- AdapterAPI：1.1.18.9。仪表模式 FULL=3、SIMPLIFY=2、OFF=1。
- 历史 ADB 地址：`172.20.10.6:5555`；下轮以 `adb devices -l` 和实际设备为准。
- 证据包记录安装包：`com.leth0x01.lynkplay`，versionCode 46，0.1.8-lynk06-test；下轮重新记录实际安装版本和固件。
- `DashboardMapDisplay`：displayId=1，1920×720，213dpi，30Hz；导航前后均存在，存在本身不能代表仪表处于地图模式。
- CarPlay type=111 独立流、解码 Surface 和 Presentation 已建立；约 21–23fps。
- `com.neusoft.extraservice/.ipk.ThreeAlbumService` 覆盖过副屏。发送 `com.neusoft.audio.SingleShowBroadcast`、`isShown=false` 后，Android 副屏截图显示 CarPlay 路线；物理仪表仍显示“导航未规划路线”。
- 同时启动原车高德导航、三指飞屏、回到 LynkPlay，用户确认物理仪表显示 CarPlay 路线。
- 第二轮结束高德导航后，调用 `disableScreenProjection(false,2)`、`startScreenProjection(true)`，隐藏覆盖层。日志确认 `isNaviNotStarted=false` 和 `INFO_ID_MMI_NAVSYNCDISPLAY open`；物理仪表仍显示“导航未规划路线”。解除飞屏门控和 MCU open 不是充分条件。

物理仪表结果必须由用户观察确认，Android 副屏截图不能替代。

## 当前产品实现与报告差异

`common/src/main/java/com/shilapi/xcertplay/Lynk06ClusterLink.kt` 当前导航开始流程包含投影声明、`notifyNavigationStatus(1)`、TurnByTurn started、一次 FULL 模式请求和显示模式扩展信息更新；结束包含状态 0、TurnByTurn stopped 和撤销投影声明。源码未调用 requestNaviFocus，也未持续上报完整路名、距离、转向和 ETA。

当前源码及证据包快照的 `showMap = available && navigating`，不由 naviMode 决定。其他 AI 引用 report 4 的 `navigationActive=true / naviMode=1 / showMap=false`，该报告原件未在本次材料中找到；须核对对应 APK、字段含义和发生时间，不能直接认定 naviMode 导致 showMap=false。

源码中“第三方无法驱动原车 naviMode”和“注册回调后 DIM 据此认定导航源”的注释均不能代替已验证的厂商契约。

## 接口存在性与证据等级

| 路径 | 当前证据 | 下轮工作 |
| --- | --- | --- |
| DimInteraction.create、IConnectable、getNaviInteraction、菜单模式及投影声明 | 实车 ABI 与调用日志已确认 | 使用已有签名，核对实际结果与参数 |
| notifyNavigationStatus、TurnByTurn started/stopped | 实车 ABI 已确认 | 可研究 START→SUCCESS 顺序，尚未在 BX11 验证该序列 |
| updateNavigationInfo、updateNaviInfo、updateNaviStatus、路名/距离/ETA/箭头更新 | session.txt 的实车 ABI 已列出 | 核对 INavigationInfo 字段、单位及原厂上报顺序 |
| notifyProjectScreenAction | 本车已采集的 INaviInteraction 声明方法列表中未出现 | 不把新版 SDK 方法当作本车已具备接口 |
| IProjectionScreenInfo | 现有采集未专门枚举此嵌套类 | 如需复核，仅检查类可见性 |
| ecarx_interactive / requestNaviFocus(String) | 系统服务历史清单及 interactive.vdex 支持该线索；实现继承 IInteractiveAPI$Stub | 获取第三轮日志，核对 Binder、类加载和焦点变化 |
| IMapInteraction.requestShowOnDimFocus(RspShowOnDimFocus) | 本车 navi.apk 的 navi-dexdump.txt 约 491723 行有接口声明 | 确认可取得的实现对象、类可见性、参数与服务权限 |
| IMapInteraction.switchDimNaviMode(DimDisplayMode) | 同文件约 491740 行有接口声明 | 同上；接口声明不等于 LynkPlay 可调用 |
| ecarx.dimprotocol.DIMProtocolManager / sendMessageToDIM | 当前证据包二进制和已读日志未找到对应名称；文件采集不覆盖全部系统依赖 | 优先补查实际系统共享库和应用类加载环境 |
| DimNaviService / ecarx.dimprotocol.custom.service.IDimNaviService | 当前材料未找到服务记录 | 优先补查实际服务注册与 descriptor |

session.txt 中 INaviInteraction 方法列表位于约 49–81 行。ABI Probe 使用 declaredMethods，只列出指定类的声明方法；未出现的方法结论限定于该接口快照。

## 导航焦点线索

interactive-dexdump.txt 中可确认：

- InteractiveServiceImpl.requestNaviFocus(String)：约 4609 行。
- NaviFocusManager.requestNaviFocus：约 6394 行；当前产品记录、监听器通知、NATIVE_NAVI_APP 与 cancelNavi 线索。
- NativeNaviManager 的 onNaviEvent：约 45745 行；协议 1001=NAVI_NTF_MAP_STATUS，状态 0 分支记录 local navi started，状态 3 分支记录 local navi exit。
- GUIDE_STATUS_GPS=0、SIMULATE=1、CRUISE=2、NOGUIDE=3；MAP_STATUS_STARTED=0、FINISHED=1、FOREGROUND=3、BACKGOURND=4；ROUTE_VIEW_IN=1、OUT=2。
- 服务检查 systemSupportedAdapterApiAndIaServiceListenIt；可读分支中返回 false 时调用 onRemoteNaviEnter。
- 焦点监听可见向其他产品发送 onNaviFocusLose；尚未证明该链直接更新仪表路线有效状态。

VDEX/CDex 有 quickened 指令、unused-e* 和 GLITCH，异常段控制流需要反 quickening 或运行时复核。不能仅靠方法名拼出已验证因果链。

## 已核对的公开项目

### MConfig：原始 DIM 消息与仪表启动应用

[KeysConfigReceiver.kt，固定提交 3fc1082](https://github.com/ellusive3/MConfig/blob/3fc1082834db0a1020158f6054604489a927fdf0/app/src/main/java/ru/monjaro/mconfig/KeysConfigReceiver.kt)

START_ON_DIM 分支获取 DIMProtocolManager 和 DimInteraction。符合前台条件时 switchNaviMode(3)，等待 100ms，发送 `sendMessageToDIM(2,8,8,byteArrayOf(1))`，等待 200ms，广播给 ru.monjaro.helper 启动目标应用到 displayId=2。切回模式 1 也发送同一条消息。

该消息的开、关两种模式都相同，不能据此定名为 projection enable；协议参数和 BX11 固件映射尚未确认。分支也受当前前台应用条件影响，不能简化成所有情况都发送该消息。

### ANHUD：导航状态顺序与真实数据上报

[NativeNavigationController.kt，固定提交 0684883](https://github.com/g992/ANHUD/blob/0684883f1aaa9de2e99af6cd8e08932b55e603f3/app/src/main/java/com/g992/anhud/NativeNavigationController.kt)

依次尝试 GlyDimInteraction、ECARX DimInteraction、N155 DimNaviService。ECARX 路径发送 TurnByTurn started、状态 2，100ms 后状态 1；持续通过 updateNavigationInfo 上报转向、路名、距离和 ETA，另外尝试距离更新。ECARX stopNavigation 发送状态 0；该函数未发送 TurnByTurn stopped。不要把其他 AI 建议的结束序列误记为该项目实际代码。

公开接口常量为 UNNAVI=0、SUCCEED=1、START=2、END=3、REROUTING=4。BX11 原厂此前核对的进行/结束为 1/0，是否还存在短暂 START=2 尚未确认。ANHUD 的单位与返回默认值也不能直接视为本车厂商契约。

[DesaySvDimNavi.kt，同一提交](https://github.com/g992/ANHUD/blob/0684883f1aaa9de2e99af6cd8e08932b55e603f3/app/src/main/java/com/g992/anhud/DesaySvDimNavi.kt)

N155 后端硬编码服务名 DimNaviService、descriptor `ecarx.dimprotocol.custom.service.IDimNaviService`，事务 9 为投影开关、10 为读取投影状态；start 发送开关 true、状态 2/1，stop 发送状态 3、开关 false。这是 N155 路径，不能把事务号套用到 BX11。

该实现的 getter 捕获异常并返回 false；tryCreate 因而可能将失败误记为 acquired。复用诊断时须独立保存异常、transact 返回值及实际 descriptor，不能只相信 acquired 日志。

### GInputBridge：接口声明和仪表地图焦点模型

[DIMInteractionImpl.java，固定提交 1d7d1d8](https://github.com/Salat39/GInputBridge/blob/1d7d1d8bcf6e7dd9f4ac12857f388c1176bae4fa/adaptapi/src/main/java/com/ecarx/xui/adaptapi/diminteraction/DIMInteractionImpl.java) 的相关方法为空或返回 null，是接口桩。

[DimInteraction.java](https://github.com/Salat39/GInputBridge/blob/1d7d1d8bcf6e7dd9f4ac12857f388c1176bae4fa/adaptapi/src/main/java/com/ecarx/xui/adaptapi/diminteraction/DimInteraction.java) 定义 APP_TYPE_DEFAULT=0、APP_TYPE_AMAP=1，投影选项 NAVI_ROUTE=1、ALWAYS=2、NEVER=3。这些是公开版本常量，需与本车常量核对；setPresentationToDimSwitch 两个 String 的语义仍待本车实现确认。

[RspShowOnDimFocus.java](https://github.com/Salat39/GInputBridge/blob/1d7d1d8bcf6e7dd9f4ac12857f388c1176bae4fa/com_geely/src/main/java/com/geely/lib/oneosapi/navi/model/service/RspShowOnDimFocus.java) 保存 focusMapVendor、focusMapPackage。本车 navi.apk 中存在 ECARX 包名版本和 requestShowOnDimFocus 接口，但公开项目包名为 com.geely.lib.oneosapi，不能直接照搬。

### Monjaro-NaviMode3：模式切换工具

[KeyEnhancerService.java，固定提交 80716c6](https://github.com/iwandered/Monjaro-NaviMode3/blob/80716c61e4edbd219ae88eb6958c108079bf2421/app/src/main/java/com/navimode/KeyEnhancerService.java) 创建 DimMenuInteraction，切模式 3/1，没有回读确认结果。[ShortcutActivity.java](https://github.com/iwandered/Monjaro-NaviMode3/blob/80716c61e4edbd219ae88eb6958c108079bf2421/app/src/main/java/com/navimode/ShortcutActivity.java) 将 ru.monjaro.mconfig 启动到 displayId=2；不是独立地图实现。

其他 AI 提及新版 Zeekr 的 IProjectionScreenInfo / notifyProjectScreenAction，但未提供可核对的源码地址，本轮不把其枚举或架构推断作为 BX11 事实。

## 下轮 ADB 排查步骤与输出

1. 识别实际连接设备，记录设备型号、固件、SDK、安装包版本；使用明确 serial，多设备时先确认目标。
2. 读取 service list、dumpsys -l、pm list libraries，针对 DimNaviService 查注册情况，针对 DIMProtocolManager 定位实际共享库。ADB shell 可见性与普通应用可见性分别记录。
3. 按实际包/库路径获取相关 JAR/APK 及同目录 ODEX/VDEX。预优化 JAR/APK 没有 classes.dex 时继续检查伴随文件；解析失败不等于接口不存在。
4. 若静态材料仍不足，使用临时只读应用探针验证 Class.forName(..., false, classLoader)、完整类名、方法签名与 classLoader，以及 ServiceManager.getService 的 Binder、descriptor、isBinderAlive。优先复用已有普通应用探针环境；独立记录 targetSdk 和共享库声明。
5. 只列出 DIMProtocolManager 的 getInstance/getNaviMode/sendMessageToDIM 签名，不调用 sendMessageToDIM。DimNaviService 先取得 Binder 与 descriptor，不猜测或写入事务；读取状态也必须先核对本车 Stub 的事务号及 Parcel 契约。
6. 顺带定位 IMapInteraction 的取得入口、实现类、requestShowOnDimFocus/switchDimNaviMode 参数、调用包名和权限；只分析和读取，不申请焦点或切换模式。
7. 若可找到第三轮探针和日志，核对焦点申请是否真正执行以及具体结果。
8. 新采集目录至少保存 commands.txt、设备信息、原始日志、相关二进制及哈希、临时探针源码/Manifest/构建结果和结论。每项结论标明：静态存在、应用运行时可见、Binder 可取得、调用成功或物理显示成功。

这一轮完成标准：明确 DIMProtocolManager 与 DimNaviService 的证据和可见性，列出已找到接口的准确签名；受权限或解析阻碍的项记录具体错误与缺失文件。暂不以诊断结果宣布修复。

导航状态 2→1、完整 CarPlay 数据上报、DIM 原始消息及仪表地图焦点申请属于后续分项实验，待本轮核对完成后安排，避免同时改动多个变量。
