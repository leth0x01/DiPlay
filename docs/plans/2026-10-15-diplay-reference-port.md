# DiPlay 参考项目核对与定位编码移植计划

**目标：** 核对 749001858/DiPlay 的公开分支和发布资料，移植适用于 LynkPlay 的定位数据修复。

**实现方式：** 在现有 NMEA 编码器中直接处理缺失字段，复用坐标、时间和校验和编码。Android 定位只把明确提供的平均海平面高度用于 GGA；保留现有定位来源选择和 iAP2 订阅流程。

**技术栈：** Kotlin、Android Location、iAP2 LocationInformation、JUnit、Robolectric。

## 范围核对

1. 固定参考提交：main `4ed5514daf5c205175621fd03884f0de68375b13`、android-4.3 `4bd0732773655d9b2a70b3e4f9f483b80d7c099c`。
2. 用提交差异区分已包含的上游实现与独有改动，核对旧平台接收器中的连接、音视频、GPS、按键和资源释放逻辑。
3. 静态检查 v0.23.10 发布包，核对发布说明的功能线索与公开源码的对应范围。

## 定位编码修复

文件：

- `shared/src/main/java/com/shilapi/xcertplay/transport/Iap2LocationClient.kt`
- `shared/src/main/java/com/shilapi/xcertplay/location/AndroidCarPlayLocationProvider.kt`
- `shared/src/test/java/com/shilapi/xcertplay/transport/Iap2LocationClientTest.kt`
- `shared/src/test/java/com/shilapi/xcertplay/location/AndroidCarPlayLocationFixTest.kt`

步骤：

1. 为未知 GGA 字段、未知/无效速度、航向边界和 Android 高度基准添加回归测试，先确认旧实现失败。
2. GGA 卫星数与 HDOP 留空；未知高度及其单位留空；未知大地水准面差及单位留空。RMC 缺失速度留空，保留真实零速和正北航向。
3. Android API 34 及以上只读取明确存在的 MSL 高度；旧系统不把 WGS84 椭球高度直接作为海拔发送。
4. 运行专项回归，再运行 AGENTS.md 要求的完整 Android CI 和工作流中的 Python 检查。
5. 记录移植来源、实际验证结果和车测待验证项，复核最终差异。

## 显示权诊断

文件：`AirPlayModeDiagnostics.kt`、`AirPlaySession.kt`、`AirPlayModeDiagnosticsTest.kt`（均在 shared 模块对应源码/测试目录）。

移植旧接收器的 modesChanged 状态摘要：仅接受固定字段的布尔值和有限整数，每个列表最多读取 8 项，摘要最多 600 字符。收到模式变更时记录屏幕/音频资源状态，保留现有命令处理。验证嵌套状态、非法值过滤和输出上限。
