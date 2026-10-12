# 领克 06 宽屏适配 Implementation Plan

> 按用户授权在当前工作区直接执行。

**Goal:** 按 1920 × 720、160 dpi 调整菜单和弹窗，并自动匹配已采集车机标识。

**Architecture:** 在 common 中集中定义宽屏尺寸策略，根据可用窗口宽高选择尺寸，不修改系统密度或视频画布。shared 中使用完整设备标识匹配领克 06 燃油版配置；保留现有构建目标作为未匹配时的默认值。USB 沿用 root 优先流程，验证授权结果后启动。

**Tech Stack:** Kotlin、Android View、Robolectric、Gradle。

## 执行步骤

1. 添加宽屏尺寸策略及边界测试；宽屏菜单 1120 dp、弹窗 960 dp、控件最低 72 dp，窄窗和低高度窗口使用现有尺寸。
2. 修改 `CarPlayMenuLayout.kt`、`AppDialogStyle.kt` 使用统一策略；宽屏文字同步放大，保留取消草稿、保存和输入校验。
3. 修改 `VehicleIntegration.kt`，匹配 IHU514G / xecarx / Android 9 标识；补充识别与误匹配测试。
4. 扩展 `CarPlayMenuUiTest.kt` 和 `AppDialogUiTest.kt`，生成实车密度、窄窗、高密度和 RTL 大字体截图；核对操作区可见。
5. 核对 root 优先授权现有实现与测试；运行 AGENTS.md 完整 CI 命令，构建并校验 0.1.4 车测包，更新实际验证记录。
