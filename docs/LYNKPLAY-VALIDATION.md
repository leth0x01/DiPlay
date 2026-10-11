# LynkPlay 品牌基线验证（2026-10-11）

## 修改范围

主应用品牌改为 LynkPlay，release 安装标识为 `com.leth0x01.lynkplay`，debug 为 `com.leth0x01.lynkplay.hudtest`。保留 namespace、存储键、兼容 action 和车型逻辑。更新来源为 `leth0x01/DiPlay` 的 Releases。

## 本地环境与检查

Windows、JDK 25.0.3、Android SDK Platform 37、Build Tools 36.0.0、NDK 28.2.13676358。测试 PATH 包含 Git Bash 的 sh。

执行 AGENTS.md 要求的全部任务，使用 `--continue` 让测试失败后其余任务继续完成：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug --continue --console=plain
```

| 检查 | 结果 |
| --- | --- |
| shared 单元测试 | 1348 项，3 项失败 |
| common 单元测试 | 1148 项，7 项失败 |
| home 单元测试 | 4 项，全部通过 |
| mobile / home / maphost Lint | 全部通过 |
| mobile / home / maphost Debug 构建 | 全部通过 |
| 新增 fork 更新来源与空 Releases 测试 | 通过 |
| VPN 授权错误提示测试（新品牌） | 16 项全部通过 |
| 诊断导出 MediaStore 测试（新目录） | 5 项全部通过 |
| 35 个修改资源文件内容核对 | 仅替换可见品牌文字，保留资源键、格式参数及许可归属文案 |
| git diff --check | 通过 |

完整检查命令返回失败，不能宣称全量测试通过。剩余 10 项失败均在独立工作区中的未修改 DiPlay 基线 `24ae1fa5` 用同一 JDK/SDK 单独复现：

- `HotspotJoinNormalizedTransactionTest`：5 项中 3 项失败，事务返回 `STORAGE_FAILED`。
- `UpdateInstallCompatibilityTest`：10 项中 4 项失败，FileProvider 无法匹配 Windows 缓存文件路径。
- `UsbPermissionSetupTest`：15 项中 3 项失败，Git Bash 接收的 shell 参数引号导致语法错误。

以上测试和对应实现未因品牌修改而改变，未为绕过本地失败而修改 Android 运行行为或屏蔽测试。Linux CI 的结果仍需单独核实。

## APK 验证

`aapt2 dump badging` 已确认 APK 的 application label 为 `LynkPlay`，包括简体中文、繁体中文和其他 locale；package 为 `com.leth0x01.lynkplay.hudtest`。

合并 manifest 中 diagnostic-reports、update-apks 的 provider authorities 均使用新 package，application label 指向 `app_name`。debug manifest 的原 `DiPlay HUD Test` 硬编码 label 已移除。

输出：`mobile/build/outputs/apk/debug/mobile-debug.apk`。该 APK 是普通源码构建，没有提供运行时配件身份。本次没有上传或发布 APK。

## 实车验证边界

目标为 2023 款领克 06 燃油版、Android 9。本次没有实车连接、音频、方向盘、Siri 或启动行为的验证证据，不宣称车型适配完成。下一阶段需获取车机诊断报告及实际故障，再核对车机系统属性、厂商服务和按键接口。

## 官方领克图标替换复检

同日将默认 BYD 车机按钮图标替换为领克官方 LYNK & CO 字标，默认按钮名称同步为 `LYNK & CO`。来源和处理方式见 [品牌素材说明](LYNKPLAY-BRANDING.md)。

再次执行上面的完整检查：三个 Debug 构建和三个 Lint 任务全部通过，测试仍为 shared 1348 项中 3 项失败、common 1148 项中 7 项失败、home 4 项通过。失败测试与前述基线问题一致，没有新增失败。

已检查生成的 192 × 192 图标，并从最终 APK 提取 `res/raw/ic_car_home.png`：SHA-256 为 `0ddacb2f9e9b0f0bda8382053521c6e44867400265205a74a54c43e90c5914f3`，与源资源一致。既有 AirPlayIconSizing 测试全部通过，图标仍符合方形 PNG 和最大 192 px 的传输要求。未改变设置布局或行对齐。

## 领克 06 第一优先级：车型与厂商能力隔离

目标配置通过 `mobile/src/main/res/values/vehicle_config.xml` 固定为 `lynk_06_2023_petrol`，关闭 BYD 集成。shared 的通用资源保留上游能力，避免大规模删除 BYD 代码。目标标识是构建配置，不是实车检测结果。

Geely 输入契约和 Siri 录音源分别判断，仅接受已有验证记录的 `geely_fx11` / `CS11_A4` / API 30 组合；Android 9、品牌字段或单个型号字段不启用特殊策略。输入服务还检查可见性、启用状态、导出状态、权限及 Binder 描述符，失败释放绑定并记录标准媒体键回退。标准 MediaSession 与原有录音源后备继续使用。

BYD HUD、车辆数据、专属 ADB 仪表、胎压和氛围灯的入口受到运行门禁保护，已保存的偏好和残留 BYD 包无法开启它们。通用 GPS、电话音频路由、公共第二屏和 Wi-Fi 扫描恢复保留。直接调用 BYD 车辆检查或探测接口会在建立 ADB 连接前拒绝请求。

引导不再要求选择 DiLink 代际，显示领克目标、实际 Android 版本与待验证状态。新增四个字符串覆盖全部七套语言资源。诊断记录构建目标、设备标识、固件、ABI、各项策略及输入服务状态。

完整 CI 命令执行结果：shared 1350 项中 3 项失败，common 1154 项中 7 项失败，home 4 项通过；三个 Lint 和三个 Debug 构建通过。共 2508 项，新增加的 8 项能力隔离、服务回退和 UI 测试通过，剩余 10 项失败与前述已复现的 Windows 基线问题一致，不能宣称全量 CI 通过。

最终补充直接探测门禁后再次执行完整 CI，仍为同一组 10 项基线失败。随后定向复检新 UI、车辆门禁与输入绑定测试全部通过。

大字体截图由 `LynkSetupUiTest` 调用真实引导组件并以 Robolectric native graphics 绘制，覆盖紧凑 360 dp / mdpi、宽屏 1280 dp / xhdpi 和阿拉伯语 600 dp / mdpi，字体缩放 1.5。生成截图在 `common/build/reports/lynk-setup-ui/`，审阅副本见 [紧凑布局](screenshots/lynk06-capabilities/compact.png)、[高密度宽屏布局](screenshots/lynk06-capabilities/full-xhdpi.png) 和 [阿拉伯语布局](screenshots/lynk06-capabilities/rtl.png)。UI 渲染环境使用 API 29，车型运行门禁另在 API 28 测试。这些都是模拟验证，不是实车照片。

最终 APK 资源检查确认 `config_vehicle_profile=lynk_06_2023_petrol`、`config_byd_integration=false`。普通 Debug APK 仍是未提供配件认证输入的源码构建；未制作独立车测包，未进行实车连接、声音、Siri 或方向盘验收。
