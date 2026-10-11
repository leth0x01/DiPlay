# LynkPlay 第一阶段设计

## 已确认目标

- 从 DiPlay 提交 `24ae1fa5` 开始，在 `lynkplay` 分支维护。
- 目标车辆：2023 款领克 06 燃油版，车机 Android 9。
- LynkPlay 与原版 DiPlay 可以同时安装。
- `main` 保留上游代码，通过 merge 将后续上游更新合入 `lynkplay`。

## 方案选择

推荐直接在 mobile 应用上使用独立安装标识和 LynkPlay 名称，保留内部 namespace、类名和现有资源键。改动集中，方便合并上游。

备选方案是增加 LynkPlay product flavor，同时从同一分支构建两种品牌。该方案需要调整构建任务、CI 和变体配置；目前已有 main 分支保留原版，第一阶段不采用。

## 第一阶段实现范围

1. mobile 的 release applicationId 使用 `com.leth0x01.lynkplay`，debug 保留上游 `.hudtest` 后缀，最终为 `com.leth0x01.lynkplay.hudtest`。保留 namespace `com.shilapi.xcertplay`，防止无关文件迁移。
2. 应用名称和用户可见品牌文字改为 LynkPlay，包括多语言资源、主页、通知、小组件和 iPhone 可见的接收器名称。检查硬编码文本，保留解析协议、持久化键、内部日志标识和兼容接口所需的原值。
3. 保留现有图标。本阶段不新增视觉设计或调整设置布局。
4. 更新检查改为当前 fork `leth0x01/DiPlay` 的 Releases，不向用户提供上游 DiPlay APK 作为 LynkPlay 更新。保留无发布版本时的正常空结果处理；若未来仓库改名，再调整地址。
5. README 中说明 LynkPlay 的目标车辆、基线、安装标识、验证状态和上游同步方式，保留上游许可证与来源说明。历史发布记录不全局替换。
6. Android CI 增加 `lynkplay` push 触发，保留原有 main、pull_request 和 workflow_dispatch 触发。
7. 检查 providers、权限、服务、显式组件和示例地图客户端的包名依赖。已有 FileProvider authorities 使用 `${applicationId}`，无需改成硬编码值。保留现有地图 action 协议，客户端须按实际目标包区分两个应用。
8. 第一阶段只改主应用品牌；home、maphost、automotive 不作为领克 06 发布应用改名，但仍执行项目要求的相关构建检查。

## 车辆适配边界

现有 `GeelyHeadUnit` 根据 PRODUCT、DEVICE、MODEL、MANUFACTURER、BRAND 检测吉利／领克设备，`GeelyWheelKeys` 绑定 `com.geely.inputservice.InputService`，`MicrophoneUplink` 对识别到的车机选择语音通信音源。

上游 `docs/COMPATIBILITY.md` 只记录 `geely_fx11` / `CS11_A4` / DesaySV / Android 11 的验证。不能由此断定 Android 9 领克 06 使用相同接口。

本阶段保留已有车辆检测和连接逻辑。下一阶段根据车机诊断报告或 ADB 的系统属性、服务列表和实际故障，决定方向盘、Siri、音频、全屏及启动方面的具体修改。不伪造车型 SDK、不强制开启 BYD 专属功能、不自动禁用车机系统应用。

Android 9 已存在旧版 Wi-Fi Direct 路径，优先验证 USB 和车机热点，再验证 Wi-Fi Direct。默认分辨率、音频流和麦克风策略在取得故障证据前沿用基线。

## 数据与安装

独立 applicationId 会隔离私有数据和权限，可以与原版共存。原版设置不会自动迁移；若需复用，使用现有设置备份／恢复并验证结果。

独立安装身份不代表两款应用可以同时占用 USB、热点或 CarPlay 会话。实车测试时只运行当前待测应用。

## 验证与交付

- 检查所有实际应用名称、合并后的 manifest、provider authorities 和更新目标。
- 对涉及更新和硬编码包名的行为，复用现有测试，必要时补充有实际行为意义的回归测试。
- 按 AGENTS.md 执行：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

- 项目要求 JDK 25、Android Platform 37、Build Tools 36.0.0、NDK 28.2.13676358；当前 JAVA_HOME 指向 JDK 17，构建前检查已有工具并配置合适环境。
- 普通源码 APK 默认不包含运行时配件身份；构建成功不等于已能在车上连接 iPhone。仅在用户已提供本地认证资源时使用现有 standalone 构建流程，不将资源或签名密钥提交到仓库。
- 在没有实车证据前，README 标注“面向 2023 款领克 06 Android 9 的适配分支，待实车验证”，不宣称适配完成。

## 当前状态

用户已于 2026-10-11 确认此方案。仓库名称和 GitHub 默认分支不在本次修改范围内。
