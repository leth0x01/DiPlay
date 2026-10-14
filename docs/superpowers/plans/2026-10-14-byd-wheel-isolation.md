# BYD Wheel Isolation Implementation Plan

> **For agentic workers:** 用户已确认隔离两项控件，按当前会话执行。

**Goal:** 让领克车辆页不显示或执行 BYD 摇杆与地图缩放功能。

**Architecture:** 使用既有车型能力门面统一限制界面和设置读取，保留历史原始偏好与 BYD 兼容路径。

**Tech Stack:** Kotlin、Android Views、JUnit、Robolectric。

## Global Constraints

- 不删除历史偏好，不影响 Siri、外接控制器或地图显示本身。
- 界面与运行入口使用同一车型能力；不增加字符串、尺寸或布局常量。

### Task 1: 车型门控及回归测试

**Files:** `common/src/main/java/com/shilapi/xcertplay/{DiPlayActivity,WheelKeyService}.kt`；`common/src/test/java/com/shilapi/xcertplay/LynkSiriSettingsTest.kt`。

- [x] 添加历史开启摇杆、地图缩放并连接副屏时的领克隔离测试；更新历史 Siri 测试对摇杆服务需求的断言。
- [x] `enabled(context)` 和 `joystick(context)` 在读取布尔偏好前判断 `VehicleIntegration.bydEnabled(context)`。
- [x] `wheelMapZoomAvailable()` 和 `wheelKeyControls(card)` 在非 BYD 车型直接返回不可用；菜单与搜索不显示相关控件。
- [x] 大字体截图测试加入历史开启值和仪表副屏，验证这些条件不能让 BYD 控件重新出现。
- [x] 运行针对性测试：`./gradlew.bat :common:testDebugUnitTest --tests '*LynkSiriSettingsTest' --tests '*WheelSiriWindowTest' --tests '*WheelKeyServiceTest'`。
- [x] 运行 `.github/workflows/android.yml` 中全部单元测试、lint 和 debug 构建任务，查看截图并记录结果。

### 验证期间发现的测试时序问题

完整 CI 与独立复跑均发现 `Lynk06ClusterLinkTest` 的关闭重连用例提前读取诊断快照。原车调用记录先更新，状态列表仍可能包含旧链路的可用状态，等待条件不足以保证新链路发布了清理完成快照。

- [x] 在既有等待条件中加入 `navigationInfoCleanupPending=false`，保留清理调用次数和最终状态断言，不改动仪表链路产品代码。
- [x] 复跑仪表链路用例，再重跑完整 CI。

## 验证结果

- 49 项车型隔离与原有方控针对性测试通过。
- 仪表链路等待条件修正后，35 项链路测试通过；最终完整 CI 成功。
- 最终单元测试：shared 1486 项、common 1465 项、home 6 项，均无失败或错误；mobile、home、maphost 的 lint 与 debug 构建通过。
- 大字体截图已生成并检查：360dp 中文、1280dp 高密度中文、600dp 阿拉伯语，字体比例 1.5；开启历史 BYD 偏好并提供领克仪表副屏仍不会显示 BYD 控件。
