# BX11 系统测试包有线 CarPlay VPN 修复

实车 `com.leth0x01.lynkplay.clustersystem` 使用 UID 1000，USB 权限和 iAP2 通道正常，但 `VpnService.establish()` 返回 null。系统日志报 `Bad call: specified package [Legacy VPN] under uid 1000 but it is really -1`。

[Android 9 Vpn.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/connectivity/Vpn.java) 的 `isCurrentPreparedPackage()` 只比较 UID，初始 Legacy VPN 的 owner UID 也是 1000。准备应用 VPN 因而被误判为已完成，实际归属包仍是 Legacy VPN；establish 的 AppOps 校验使用这个无效包名而失败。

`SystemUidVpnPreparation` 仅在 Android 9、实际 UID 1000、具备 CONTROL_VPN、首次 establish 返回 null、没有现有 VPN 配置或 always-on VPN 时恢复归属。通过系统接口暂时准备 UID 2000 的 `com.android.shell`，随后在 finally 中恢复本应用包名，绕过 UID 相同的短路。中间包不授予 VPN 权限，也不建立隧道。本应用的正常 VPN 授权和 Builder 应用范围限制仍由第二次 establish 校验；恢复失败则走原有清理流程。

2026-10-14 实车已安装修复后的系统签名 APK。系统日志确认 `[Legacy VPN] → com.android.shell → com.leth0x01.lynkplay.clustersystem`，随后 `Established ... on tun0`。截图确认 CarPlay 高德地图显示，视频日志持续约 30 fps，recoveries=0、late=0。一次 USB 读取失败后重新连接成功，未为此修改 USB 传输逻辑。

新增三项回归测试覆盖 UID 短路、切换异常时恢复本包、切换或恢复拒绝不得报告成功。全量检查同时发现已有仪表测试过早断言结束通知后的异步清理，已改为等待 TurnByTurn 和投影声明清理完成。Windows USB 权限测试使用本地 Git shell，通过 DIPLAY_TEST_SHELL 指定。

AGENTS.md 要求的完整 CI 命令通过，shared、common、home 共 2756 项单元测试通过，mobile/home/maphost debug lint 和构建均通过。系统包 `:mobile:assembleClusterSystem :mobile:lintClusterSystem` 也通过，lint 为 0 errors。Windows Robolectric 在退出清理临时目录时打印了 `.obsolete` 文件不存在的后台异常，但测试报告和 Gradle 最终状态均成功。安装并验证的 APK 已保存为 `.private/LynkPlay-0.1.9-bx11-system-vpn-fix.apk`，SHA-256 为 `446951431db6a37c3f00a567a5569e13916a2148fd34dc563d674579c3f01ad7`。
