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
