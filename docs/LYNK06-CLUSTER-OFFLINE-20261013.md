# BX11 导航协议与权限离线复核（2026-10-13）

## 新采集包同步后的补充结果

已取得 `.private/lynkplay-cluster-contract-20261013-190825/`，138 个清单条目的文件大小与 SHA256 全部一致。新 XSFNaviService.apk/classes.dex 与此前输入完全一致，因此下方 3902/4100/4101/4102 的代码结论适用于本次采集，不再只是旧包线索。

新输入 XSFEASCoreService.apk/classes.dex SHA256 为 `300349f277eb5fa669733d7c623af1545056d3628ee04a99a9dd2a0420493934`。本轮使用 JADX 1.5.3 输出到 `.private/lynkplay-cluster-offline-20261013/eas-core/sources/`；日志为 eas-core-jadx.log，返回码 1、报告 2 个反编译错误，异常方法保留指令形式并限定结论范围。

### EAS 认证与导航模块访问

已核实的服务端代码链：

- `com.ecarx.sdk.openapi.a.f657a=true`，`e.b.a()` 返回该值，本版本权限验证开关启用。
- `support/ConfigService.java` 将 V3_NAVI 和 V4_NAVI 都映射为服务名 `navi`，分别连接 OpenNaviAdapt 和 EASNaviAdapt 对应 action。
- `support/v4/a.java` 在实际调用前检查消息服务名和 Binder 调用 PID 对应的授权缓存。模块权限列表必须包含目标服务；不满足时返回 401 / No Authorization。服务未注册或未准备好另返回 404，不能将两类故障混为一谈。
- 同类的直接获取模块 Binder 入口会校验传入 PID/UID/包名及目标模块权限，不是任意普通包都能领取导航 Binder。
- 该类初始化权限时从实际运行进程 PID/UID 和 pkgList 定位包名，再读取许可中的操作列表；请求中填写其他包名不能作为已获授权的依据。
- `c/f.java` 的模块操作校验读取应用商店许可提供者 `content://com.ecarx.appstore.licenseProvider`（通过 eas-installer-verifier 插件），并尝试应用自身许可路径。
- `c/e.java` 的应用资源路径读取 Manifest 的 `eCarX_OpenAPI_AppId`、`eCarX_OpenAPI_AppKey`，非空后尝试 assets/license.txt。AppKey 在此可读路径中用作输入/非空条件，不能说只填 AppKey 就取得权限。
- `c/d.java` 解析许可，核对 AppId，再核对许可中的应用绑定信息。普通应用绑定包含包名和签名摘要；`c/c.java` 判定系统例外同时检查系统/更新系统标志及与 android 的签名匹配，不是仅安装为 system app 就成立。模块操作必须实际出现在许可列表中。

这是本次固件的公开可读授权路径，不能用其他项目的 AppId/AppKey、包名或临时探针调用返回替代 LynkPlay 的实际授权结果。本轮未尝试修改许可、包名、系统身份或认证行为。

本工作区 common/src/main/AndroidManifest.xml 未声明这两个元数据，common/mobile 的 main/assets 中未找到 license.txt。该事实只覆盖当前源码路径；应用商店提供者是否给当前安装包授予 navi、其他源集/最终 APK 是否包含许可，在本轮运行时检查里未能确认。2026-10-16 的实车采集显示，平台签名包 `com.leth0x01.lynkplay` 已在物理仪表显示 CarPlay 地图，即该路径在当前安装包上可用（见[验证记录](LYNKPLAY-VALIDATION.md)）；navi 授权本身仍未经独立查询证实。因此不能直接宣称 LynkPlay 一定无授权，也不能宣称能够经 EAS 访问导航模块。

### boot SDK 与第三轮原始摘录

新包 `runtime-focus-bridge.txt` 确认 boot SDK 的 NaviAPI.requestShowOnDimFocus 直接调用 invokeAPISync，没有额外协议转换，因而可与同一 NaviService DEX 的 3902 原样返回分支串联。

`runtime-impl-binding.txt` 的 NaviAPIImpl 路径能读到未连接时返回空以及向 INaviServer 转发的调用；quickened 段仍不能用当前输出还原全部参数。不要把 NaviAPIImpl2 的 EAS 路径和工厂实际选择的 NaviAPIImpl 混为同一对象。

已核对 `round3-verified/focus-excerpt.txt` 和 RESULT.md：16:19:29 的临时探针调用实际到达服务，onNaviFocusChanged 的产品为 LynkPlay，随后有 cancelNavi / HiCar 焦点丢失；+1/+3/+6 秒 getNaviMode 都为 1。物理仪表失败反馈保存在 RESULT.md。这一记录确认“焦点请求到达仍未修复”，不能继续把重复申请焦点当作优先实验。

### 当前下一步

不需要重复采集 DIMProtocolManager 或 DimNaviService，也不需要试写 3902。剩余有价值的运行态工作是：

1. 对当前安装的 LynkPlay 只读确认 EAS 的 navi 授权结果、实际工厂选择/连接情况；SDK 初始化可能注册监听，应单独记录，并与业务写入区分。若直接权限查询本身不可达，记录入口和错误，不能反复猜事务号。
2. 若继续追仪表路线门控，被动记录用户正常原车导航→飞屏→结束的 NaviDashboardManager、DimPresentationMgr_SwitchNaviMode、EAS 和 MCU 日志，重点对照完整导航数据、地图归属和模式事件，不由探针写状态。
3. 当前正式实现未持续发送完整 CarPlay 导航信息，可先离线核对其字段与原厂 NavigationInfoToDim 的单位和默认值；是否影响物理仪表需后续分项实车验证。

## 本轮结果

已从现有原始 DEX 复核 OpenNaviAdapt、EASNaviAdapt 的同步请求链路及 DIM 模式管理器。没有操作 ADB，没有修改正式产品代码，也没有执行 SDK 工厂、连接或业务调用。

重要修正：接口名 requestShowOnDimFocus 不能证明固件会申请仪表焦点。旧证据包中的同步协议 3902 分支直接返回输入请求。真正的 DIM 模式管理分支是 4101 与 4102；4100 是另外的模式模型协议，不能机械替代。

## 材料来源与范围

- 本轮原始输入：`.private/lynkplay-cluster-evidence-20261013/03-device-binaries/navi.apk` 的 classes.dex。
- classes.dex SHA256：`730120b92a66f3f7cde5f8ae3252b7487f404725897375d9aa72b993ac8467c0`。
- 与证据包 `04-derived-disassembly/navi.dex`、本机缓存 `/private/tmp/xsf/classes.dex` 的 SHA256 完全一致。
- 使用本机 JADX 1.5.3，对证据包 navi.dex 重新输出选定类，保存在 `.private/lynkplay-cluster-offline-20261013/derived/`。OpenAPIManager 同时保留 fallback 指令形式便于核对跳转。
- 部分 JADX 任务返回 1，记录 decompilation error，但已输出类。日志逐类保存；未成功反编译的段落不作为完整控制流结论。
- 用户提交的 Windows ADB 记录原文保存于 `.private/lynkplay-cluster-offline-20261013/USER-ADB-HANDOFF.md`，来源是本次聊天附件。
- 新采集包及其中 round3-verified 已同步并核对，输入一致性与 EAS Core 来源见本文件开头。

## 3902：同步路径返回输入，没有执行仪表焦点接管

调用链：

```text
OpenNaviAdapt.invokeNaviAPI(TransferModel)
  → mAPIManager.b(NaviBaseModel)
  → ecarx.naviservice.g.d.b → super.b
  → ecarx.naviservice.g.l.b
  → protocolID == 3902 → return reqModel
```

EAS 的 `callMaster("invokeNaviAPI", ...) → getSyncResult()` 同样调用 mAPIManager.b。EAS 的成功返回码 200 表示这层调用完成，不能证明仪表焦点发生变化。

复核证据：

- `derived/OpenAPIManager-fallback.java`：约 685–771 行；3902 跳到 L9e，仅写覆盖率标记并 return r6。
- `derived/ecarx.naviservice.g.d.java`：b(NaviBaseModel) 只转调 super.b。
- `derived/ecarx.naviservice.service.easadapt.OpenNaviAdapt.java`：invokeNaviAPI。
- `derived/ecarx.naviservice.service.easadapt.EASNaviAdapt.java`：callMaster、getSyncResult。

在这条同步分支没有读取 RspShowOnDimFocus 的 focusMapVendor / focusMapPackage，也没有校验其合法性或更新仪表焦点。不能声称“换 vendor 或包名就会成功”。该结论不外推到所有 SDK 版本、其他服务或未核对的异步路径。

## 4100、4101、4102：不同协议与角色

本车旧包 NaviProtocolID 定义：

| 值 | 常量 | 同步管理器处理 |
| --- | --- | --- |
| 4100 | NAVI_DIM_DISPLAY_MODE | 未有专门分支，进入地图代理 C(reqModel) |
| 4101 | RSP_DIM_DISPLAY_MODE | NaviDimPresentationManager.c(reqModel)：接收地图侧显示模式并更新记录/扩展信息 |
| 4102 | NAVI_DIM_SWITCH_MODE | NaviDimPresentationManager.a(reqModel)：处理模式切换请求 |

4100 的默认分支进入 Qa.C；该基类 C 只专门处理 3407、3411、3420、5011，其他走错误模型。检索到的 NoMapProxy.C 返回错误模型。因此不能把构造 DimDisplayMode（默认 4100）加上 invokeAPISync 当作已存在的仪表模式切换入口。

以上只说明本包可见的同步分发；不能排除其他框架/地图客户端处理模式事件。未发送任何协议，也不建议在未核对具体 SDK 方法封装前自行改 protocolID 试写。

## 真正模式处理有地图归属条件

类 `ecarx.naviservice.c.k` 的覆盖率元数据保留原名 `NaviDimPresentationManager`。

### 4102：切换请求

- 从 reqModel 读取 mapVendor。
- `n.c(mapVendor)` 将 10、-2、-1 视为特殊/非具体 vendor。
- 非具体 vendor：替换为当前地图记录的 vendor，再处理切换。
- 具体 vendor：必须等于 `mapManager.e().b().m()`；否则返回 10054。
- 10054 在 NaviResultCode 定义为 `RESULT_DIM_NOT_PRESENTATION`，消息“不能进行仪表投屏”。
- 通过后把 SDK 模式转换为 AdaptAPI 模式，最终调用原车 NaviDashboardManager.c → DimMenuInteraction.switchNaviMode。

这与 LynkPlay 已调用的 AdaptAPI 模式切换存在汇合点；获得这个 SDK 入口本身未必能绕过原车条件。

### 4101：地图侧模式更新

- 具体 vendor 校验失败返回 10001（非法参数）。
- FULL / AR 更新仅在 mapVendor 等于 widgetMapVendor 时应用；不一致时可原样返回而不更新。
- OFF / SIMPLIFY 也检查当前 DIM 地图归属，避免其他地图改当前投影。
- 应用时记录当前 DIM vendor、SDK 模式，并通过 EXT_KEY_DIM_DISPLAY_MODE 发给 AdaptAPI。

### 模式映射

| SDK DimDisplayMode | AdaptAPI 菜单模式 | 扩展动作 |
| --- | --- | --- |
| OFF=0 | OFF=1 | 33 |
| SIMPLIFY=1 | SIMPLIFY=2 | 34 |
| FULL=2 | FULL=3 | 35 |
| AR=3 | 4 | 36 |

源码证据：`derived/ecarx.naviservice.c.k.java`、`derived/ecarx.naviservice.c.i.java`、`derived/ecarx.naviservice.h.n.java`、`derived/com.ecarx.sdk.navi.model.base.NaviResultCode.java`。

## EAS 接入：导航适配端与客户端路径

NaviService.onBind 按 action/category 返回不同 Binder：

- `ecarx.intent.action.NAVI_SERVICE_STARTED` + `ecarx.intent.category.NAVI` → OpenNaviAdapt。
- 同 action + `ecarx.intent.category.NAVI_INNER` → InnerSDKAdapt。
- `ecarx.naviservice.intent.action.EAS_SUPPORT_SERVICE` 或 `com.ecarx.eas.core.intent.action.SUPPORT_SERVICE` → EASNaviAdapt。

新采集 navi-manifest.txt、naviservice-running.txt 和普通探针报告确认：直接 NaviService 受 signature|privileged 权限 OPENAPI_NAVI_PERMISSION 保护，普通探针没有该权限。

旧包内 EAS 客户端代码显示：

1. framework 客户端使用 action `com.ecarx.easframework.intent.action.EASFRAMEWORK` 定位统一框架服务，再 bindService。
2. 服务连接后，框架客户端申请模块、读取 getAvailableEASServices / getAvailableServices，检查目标模块是否可用。
3. 可进一步读取 getEasServiceInfo 并调用 onAPIReady 回调。
4. 代理消息的外层返回码不是 200 时抛出错误，包含 403 分支；它不是“接口在类列表中即有使用权”。

EAS Core 的许可及身份检查已补齐，见本文件开头；当前安装的 LynkPlay 实际授权结果仍须运行态确认。

## 原厂导航数据输出的可见差异

NaviDashboardManager.a(NavigationInfoToDim) 调用 updateNavigationInfo 上报完整对象。其 mapStatus 更新分支将状态 16/33 映射为 notifyNavigationStatus(1)，17/35 映射为 0，9001/9002/9003 映射为 4/5/6。此前已知的进行/结束 1/0 因此有代码依据。

该函数未显示 ANHUD 的 START=2 → SUCCESS=1 序列；不能把其他平台的顺序宣布为 BX11 必需契约。继续对照真实 CarPlay 路名、转向、距离和 ETA 上报属于独立方向，尚未执行实车验证。
