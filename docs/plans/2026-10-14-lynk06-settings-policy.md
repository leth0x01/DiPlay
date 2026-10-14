# 领克 06 高级设置车型策略 Implementation Plan

**Goal:** 根据已采集的 IHU514G / BX11 / xecarx / API 28 信息精简高级设置，并使菜单与运行时使用同一策略。

**Architecture:** 在 common 中集中定义车型策略，完整匹配实车才应用固件专属规则。隐藏的固定关闭项在读取设置时屏蔽历史值；可调项保留持久化和重连流程。未知车型沿用通用行为。

**Tech Stack:** Kotlin、SharedPreferences、Robolectric、Android Gradle Plugin。

### 1. 集中车型规则

- 新建 `common/src/main/java/com/shilapi/xcertplay/VehicleSettingsPolicy.kt`。
- 修改 `AirPlayPersistence.kt`，将哈曼全屏及高通低延迟的领克有效值固定为关闭，保留原始保存值。
- 已验证的 MTK 低延迟和直接视频输出默认开启，保留用户回退选择；若用户选择隐藏，则同步固定有效值。

### 2. 菜单筛选

- 修改 `DiPlayActivity.kt` 的高级显示、媒体卡片，使用集中策略筛选控件。
- 搜索复用菜单生成，隐藏项随菜单退出搜索结果。
- 保留 HEVC、音频、分屏、旋转、侧栏及仪表地图现有操作。

### 3. 验证

- 新建车型策略及 UI 测试，覆盖完整/部分车型匹配、历史设置、默认值、用户回退、菜单与搜索。
- 使用 Native graphics 生成大字体紧凑与完整高级菜单截图并检查。
- 运行 AGENTS.md 的完整 CI 命令，并记录实际结果。
