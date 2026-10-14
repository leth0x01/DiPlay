# BX11 / IHU514G 系统仪表测试版本

此实现根据 2026-10-13 实车独立显示成功的 `com.ganfandw.carplay` 对照结果接入。代码完成不等于 LynkPlay 已经在物理仪表验证成功；需要独立安装和真实导航实验。

2026-10-14 已完成完整 CI、系统版本构建和专用 lint，最终 APK 位于 `.private/lynkplay-system-implementation-20261013-230349/LynkPlay-0.1.9-bx11-system-test.apk`。已核验签名和 Manifest 的系统共享 UID 声明；尚未安装，因此实际 UID 和实车效果未确认。

## 签名核验

用户提供的 `.private/CarPlay-20261004-23.apk` 与从已安装成功版本拉取的 APK，SHA-256 完全一致：`1bddc6efb8608e6eb11683c098c380a0531522f1655503609b3e7f05f2d98f15`。

参考 APK、车机 `/system/framework/framework-res.apk`、AOSP 公开 `platform.x509.pem` 的证书 SHA-256 均为：

```
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

APK 的签名证书和公钥不能还原私钥。本车恰好使用 AOSP 的公开开发测试平台证书，因此可以从 [AOSP 官方镜像](https://github.com/aosp-mirror/platform_build/tree/master/target/product/security)取得对应测试密钥；这和“从 APK 提取私钥”是两回事。已验证私钥和证书配对。本构建仅用于已核验固件的本地测试，不作为通用发布签名。

## 构建

使用 JDK 25。显式准备本地官方测试密钥文件后，运行一次：

```powershell
java scripts/PrepareBx11PlatformSigning.java <platform.pk8路径> <platform.x509.pem路径>
```

脚本检查证书指纹及密钥配对，只在 `.private/signing/bx11-platform.p12` 和被忽略的 `platform-signing.properties` 创建本地配置，不覆盖已有文件、不打印密码、不安装应用。密钥文件和本地配置不得提交。

使用现有本地 CarPlay 认证资产：

```powershell
$env:DIPLAY_AUTH_ASSETS_DIR = (Resolve-Path .private/auth-assets).Path
./gradlew.bat :mobile:assembleClusterSystem :mobile:lintClusterSystem
```

也可通过 `LYNKPLAY_PLATFORM_KEYSTORE`、`LYNKPLAY_PLATFORM_STORE_PASSWORD`、`LYNKPLAY_PLATFORM_ALIAS`、`LYNKPLAY_PLATFORM_KEY_PASSWORD` 提供平台签名配置。构建强制检查本车已核验平台指纹；普通 debug/release 不改签名、不加入系统共享 UID。

独立配置文件并非 Android 的技术要求：普通版本继续使用 `release-signing.properties` 的长期签名，系统测试版本使用本车平台签名。覆盖原 release 配置会改变普通版签名，破坏已安装版本的升级匹配，因此保留两套签名配置。

测试 APK 包名为 `com.leth0x01.lynkplay.clustersystem`，Manifest 声明 `android.uid.system`。普通 UID 包不能直接原位升级为共享系统 UID；独立包名用于并存及回退。是否获授 UID 1000 仍须安装后以 `dumpsys package` 和实际进程 UID 确认。

## 已接入行为

- 使用既有 iAP2 解码的真实导航进行状态和路线卡片；保留原始 Apple maneuver type、driving side，并按实车成功版本的映射转换 BX11 turn ID。
- `updateNavigationInfo(INavigationInfo)` 发布米、剩余秒、道路名称、转向。ETA 使用剩余时长，不用到达时间戳。`getDrivingDirection` 按成功版本为 0，不把 Apple 的驾驶侧冒充为车辆航向。未解码的车道/高速出口/摄像头/服务区信息不编造。
- 缺失真实 maneuver、驾驶侧、剩余时间或距离时不创建完整卡片。改变的卡片最短间隔 500 ms，未变化卡片每 10 秒重发；结束真实导航后停止发送。
- 完整卡片发送后尝试已核对的 `mPowerOperation.disableScreenProjection(false, 2)`；没有新增原始 DIM 消息或焦点调用。
- 仅当车型匹配、实际 UID 为 1000、系统构建元数据开启，且真实导航和完整卡片已到达、AdaptAPI 已连接时，暂停 `ecarx.naviservice`。
- 暂停前同步记录原 enabled setting，再设为 disabled=2。结束导航、关闭链路或断开后恢复原值，并向原包发送其恢复唤醒广播；异常保留记录，下次打开 App 重试。恢复唤醒是否在本车足以重启 OEM 服务尚需实车验证。
- 已经处于 disabled=2/3/4 的原包视为用户或其他 App 所有，不建立暂停记录，也不会擅自启用。恢复中发现外部状态变化时保留其状态。

Android 不保证强杀后运行退出回调；持久记录用于下次启动恢复，不能保证强杀当场恢复。在测试包持有暂停记录时，应先正常结束导航并确认恢复，再卸载。

## 最小实车实验

先让已验证的另一个 CarPlay 正常释放仪表，确认原导航服务恢复可用；不能让两个 App 同时控制仪表，也不能把此前别人禁用的 OEM 状态当成本包已经暂停成功。

然后使用独立包名测试版本，仅开启既有仪表地图选项，保持原车高德不导航：

1. 记录测试包 UID、OEM enabled setting 和 AdaptAPI 连接日志。
2. iPhone 开始真实导航，核对完整卡片、OEM 暂停、投影声明及物理仪表地图。
3. iPhone 结束导航，确认 OEM enabled setting 回到步骤 1 的原值，原车高德可以再次正常导航。

这是对成功版本组合实现的复现验证，不再声称单独状态 1 足够；若失败，再依据日志拆分变量。当前并未证明本包 EAS navi 模块获授许可，平台签名也不能替代许可查询结果。
