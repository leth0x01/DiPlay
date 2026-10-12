# 领克 06 仪表地图接入 Implementation Plan

**Goal:** 接入采集到的 DashboardMapDisplay，使用现有 CarPlay 独立地图输出和设置开关。

**Architecture:** 仅在完整 IHU514G / xecarx / API 28 标识匹配时选择名为 DashboardMapDisplay 的有效 Presentation 显示，尺寸必须为 1920 × 720。沿用独立仪表视频 Surface 和关闭释放流程，默认原生 100% 地图流、30 fps，使用通用地图边距，避免沿用比亚迪仪表校准。

**Tech Stack:** Kotlin、Android Presentation / DisplayManager、Robolectric、Gradle。

## 执行步骤

1. 添加匹配与地图参数测试，覆盖错误设备、错误名称、尺寸、Presentation 标记和主屏排除。
2. 新增 Lynk06ClusterDisplay 策略，接入 ClusterMapPresentation；保持已有比亚迪选择逻辑。
3. 配置原生默认尺寸与居中车标，保留用户已保存的比例和位置；使用领克设置说明并补齐各语言。
4. 验证独立仪表 Surface 路由、开关关闭释放和默认关闭；复检现有显示与设置同意流程测试。
5. 执行完整 CI，生成 0.1.5 车测 APK，记录模拟验证与实车待验项目。
