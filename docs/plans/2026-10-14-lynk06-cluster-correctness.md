# 领克仪表生命周期正确性修改计划

**目标：** iPhone 导航结束后立即隐藏仪表地图，并释放导航与投影接管、恢复原车导航；修复导航卡片字段限制和回调生命周期问题，调整系统测试构建。

**实现：** 保留已有导航显示和地图模式请求时序；导航通知、TBT 和投影声明分别追踪清理责任。地图隐藏通过主线程通知完成，不等待厂商 Binder 返回。运行期间使用现有轮询与重连重试，关闭期间最多尝试三次，持久记录与诊断保留未完成项；新链路等待旧链路关闭后，再恢复记录并接管。OEM 恢复 journal 沿用现有实现。

**技术：** Kotlin、Android Handler、厂商反射接口、Robolectric、Gradle。

1. 在 `Lynk06NavigationInfoTest.kt` 验证缺失驾驶侧字段的真实卡片仍可转换；在 `Lynk06ClusterLinkTest.kt` 注入停止通知和投影释放失败，验证逐项重试、重连补偿及关闭重试；在厂商测试替身中统计存活 callback。
2. 在 `Lynk06NavigationInfo.kt` 移除未参与厂商输出的驾驶侧字段校验。
3. 在 `Lynk06ClusterLink.kt` 保存逐项清理责任，失败后保留，补齐重连和关闭时 callback 注销，并增加清理诊断。
4. 在 `mobile/build.gradle.kts` 将 `clusterSystem` 改为继承 release，显式关闭调试，依赖回退使用 release。
5. 运行定向回归测试，再执行 AGENTS.md 要求的完整 Android CI；检查系统变体的合并 Manifest。

完整 CI 命令：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug \
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

## 实际验证结果

- Java 25 定向回归通过：shared 6 项、common 58 项，0 失败、0 错误。覆盖结束状态、地图隐藏、逐项清理失败、重连补偿、持久恢复、旧链路关闭交接和 callback 释放。日志：`/private/tmp/lynkplay-cluster-targeted-verified.log`。
- mobile、home、maphost 的 Lint 和 Debug 构建均通过。
- 系统变体源码编译与专用 Lint 通过；合并 Manifest 的 debuggable 为默认 false。日志：`/private/tmp/lynkplay-cluster-system-check.log`。此检查只编译和检查源码，没有验证认证与签名材料。
- 完整 CI 已执行，最终未通过：现有 `SettingsBackupServerTest.stopDoesNotFailWhileConnectionsAreEnding` 在 macOS Java 25.0.3 的 `ServerSocket.close()` 原生调用中阻塞，停止该测试子进程后，其余检查完成。日志：`/private/tmp/lynkplay-cluster-ci-verified.log`；线程栈：`/private/tmp/lynkplay-ci-final-worker.txt`。
- 本轮未完成新代码的实车验收。验收要求：iPhone 正常结束导航后仪表地图关闭，导航与投影接管释放，原车导航恢复其原状态。
