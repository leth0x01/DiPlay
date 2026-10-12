# 领克 06 仪表联动修改计划

**Goal:** 根据专项采集证据接入可核实的仪表模式，并改进断线后的采集流程。

**Architecture:** 复用 CarPlay 真实导航状态，领克仪表输出单独管理导航状态与显示模式。厂商接口通过反射调用并限定实车配置，调用失败记录诊断；不将显示器存在当作仪表地图模式。

**Tech Stack:** Kotlin、Android Display / Presentation、亿咖通 AdaptAPI、PowerShell。

## 任务 1：固件契约核对

- 从采集到的 AdapterAPI 和原车导航 APK 核对创建方式、模式回调和投屏参数。
- 实现范围由实际方法与原车调用确定，保留无法确定的投屏服务权限问题。

## 任务 2：仪表联动

- shared：提供 CarPlay 导航进行状态，复用现有 iAP2 导航状态解析。
- common：新增领克固件适配器；CarPlayHostActivity 管理导航、模式和 Presentation 生命周期。
- 测试导航结束、模式变化、调用失败和清理时的行为。

## 任务 3：采集补充

- scripts/collect_lynk06_cluster.ps1：采集原车投屏相关组件，断线时停止推进阶段，支持按阶段补采。
- 用 PowerShell 解析器检查及离线替身验证断线处理。

## 任务 4：验证与记录

- 运行 AGENTS.md 的完整 CI 命令。
- 更新仪表研究与实车测试说明，记录代码验证结果及待实车核对项。
- 原始采集数据与测试截图保留本地。
