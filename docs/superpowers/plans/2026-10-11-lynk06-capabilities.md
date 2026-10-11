# 领克 06 能力隔离实施计划

**Goal:** 实现已确认的第一优先级，并保持上游差异集中。

**Architecture:** shared 定义 VehicleIntegration 和车型资源；mobile 覆盖目标与 BYD 开关。Geely 的输入与麦克风能力分别判断，common 显示引导和诊断。

**Tech Stack:** Kotlin、Android resources、Robolectric、Gradle。

## 全局约束

minSdk 25，目标车机 Android 9，未实车验证；中文注释与全部语言文案；独立 applicationId 不变；不导入其他车机 SDK；执行根目录 AGENTS.md 检查。

## 执行任务

- [x] shared 新增 `vehicle/VehicleIntegration.kt` 与 profile 资源，mobile 覆盖领克目标及关闭 BYD。测试禁用开关优先于旧偏好和包检测。
- [x] `GeelyHeadUnit.kt` 拆分已验证输入与 Siri 能力，`MicrophoneUplink.kt` 独立使用麦克风能力。`GeelyWheelKeys.kt` 检查服务、权限、Binder 描述符，记录失败并释放绑定。测试品牌泛匹配不启用，精确 Android 11 组合仍支持。
- [x] `BydOutputSettings.kt`、`BydNavigationOutputs.kt`、胎压和氛围灯入口加入运行门禁；`AirPlayPersistence.kt` 禁用 BYD ADB 仪表旧值。通用恢复与标准第二屏保持可用。
- [x] `DiPlayActivity.kt` 提供领克目标引导、隐藏 BYD 专属卡片与引导，加入能力诊断；新增所有语言资源和引导隔离测试。
- [x] 获取紧凑/全宽大字体截图，执行完整 CI 命令，记录结果与限制。

各任务在本会话顺序执行；完成后保留在当前 lynkplay 分支供审阅。

验证结果：新增 8 项测试通过；全部构建和 Lint 通过。完整测试仍有先前已复现的 10 项 Windows 基线失败。高密度/紧凑/阿拉伯语大字体截图已审阅并保存到 docs/screenshots/lynk06-capabilities。详见 docs/LYNKPLAY-VALIDATION.md。
