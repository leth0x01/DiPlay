# 领克 06 高级设置策略

策略适用于完整匹配 `product=IHU514G`、`device=IHU514G`、`model=IHU514G/BX11`、`hardware=xecarx`、Android API 28 的车机。规则集中在 `VehicleSettingsPolicy.kt`，菜单、搜索和设置读取使用同一规则。

| 设置 | 领克 06 行为 | 依据 |
| --- | --- | --- |
| MTK 解码器低延迟 | 默认开启，保留用户回退开关，下次连接生效 | 实车使用 `OMX.MTK.VIDEO.DECODER.AVC`，已记录低延迟/无重排配置与成功输出；解码器原有失败降级继续生效 |
| 直接视频输出 | 默认开启，保留用户回退开关，修改时按现有说明重连 | 实车记录 SurfaceView 直接输出与成功显示 |
| 哈曼车机全屏 | 隐藏，运行时固定关闭 | 已采集车机为亿咖通 MTK 固件；该开关用于哈曼原厂侧栏 |
| 高通低延迟解码 | 隐藏，运行时固定关闭 | 已采集解码器为 MTK，使用独立 MTK 调优路径；此通用开关还联动专用输出线程，本轮不自动启用该组合 |

MTK 低延迟和直接视频输出的默认值只用于尚未保存该项的用户，已保存的开启/关闭选择继续生效。固定关闭项保留原始偏好数据，但领克 06 的运行时读取返回关闭，避免历史值或导入值激活隐藏设置。

HEVC、平滑视频、通话回声消除、语音滤波、音频焦点、缓冲音频、蓝牙音频、分屏、旋转、侧栏和仪表地图保留现有菜单及持久化方式。

## 验证（2026-10-14）

- 新增 6 项测试通过，覆盖已验证默认值、用户关闭回退、历史隐藏值的运行时屏蔽、部分设备匹配、菜单与搜索筛选、通用设备菜单。
- 大字体 1.5 倍的紧凑、完整、阿拉伯语高密度截图已生成并检查，位于 `common/build/reports/lynk06-advanced-settings-ui/`。
- 完整 CI 命令成功结束：shared 1433 项（跳过 1 项）、common 1343 项、home 6 项，0 失败/错误；mobile、home、maphost 的 Lint 与 Debug 构建通过。
- 编译使用 JDK 25，测试 JVM 使用本机 JDK 26。JDK 25 两次全量执行均卡在已有 `SettingsBackupServerTest.stopDoesNotFailWhileConnectionsAreEnding` 的 `UnixDispatcher.preClose0`；保存线程栈后终止对应 worker。该测试在 JDK 26 单独通过，随后全量通过。临时测试配置及日志位于 `.private/lynk06-settings-test-runtime.gradle`、`.private/lynk06-settings-ci-jdk26.log`。
