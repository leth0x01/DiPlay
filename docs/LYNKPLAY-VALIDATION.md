## 2026-10-12：领克 06 实车密度宽屏适配（0.1.4）

按采集的 1920 × 720、160 dpi 主屏参数新增宽屏尺寸策略。可用窗口达到 1400 × 600 dp 时，菜单最大宽度 1120 dp、弹窗最大宽度 960 dp、按钮和输入框最低高度 72 dp；菜单正文最低 20 sp，弹窗正文 20 sp、标题 26 sp。窄窗和低高度窗口使用紧凑布局。

车型自动匹配使用 IHU514G product / device / model、xecarx hardware 和 API 28 的完整组合，统一使用领克 06 燃油版配置。新增测试覆盖完整匹配、部分标识误匹配、其他平台以及 Android 运行时匹配和诊断输出。

界面测试覆盖实车主屏尺寸、普通应用安全窗口 1800 × 670、深浅主题、窄窗、高密度窗口、RTL 和 1.5 字体缩放。热点弹窗另以 400 px 可用高度模拟软键盘占用，验证底部操作按钮可见；这是窗口高度模拟，实际车机键盘行为仍需实测。

大字体截图由 API 29 Robolectric native graphics 生成，存放在本地 `common/build/reports/carplay-menu-ui/` 和 `common/build/reports/app-dialog-ui/`。

USB 继续使用已实现的 root 优先流程，5 项 VpnPreConsent 回归测试通过。实车目前已有 ACTIVATE_VPN allow 和 USB 视频阶段证据；本轮未取得新版本完整前台 USB 会话。

最终完整 CI 命令通过：shared 1353 项（1 项跳过）、common 1166 项、home 4 项，0 失败；mobile、home、maphost 的 Lint 和 Debug 构建均通过。

已生成 `0.1.4-lynk06-test` / versionCode 42；正式包名、长期签名、Android 9 签名兼容性和认证素材检查通过。APK SHA256：`2dad1aad8018cc0e9fec65363b258b2418e00155fdeae9dd023048ebf2e23be2`。

# LynkPlay 品牌基线验证（2026-10-11）

## 2026-10-12：三指下滑菜单主体适配

菜单主体改为固定标题栏、可滚动分组卡片和固定操作栏，配色与主设置页一致。输入框使用主题圆角背景，窄窗口中标签与输入框纵向排列；操作按钮按可用宽度调整排列。横排选项空间不足时转为纵排，窗口变宽后可恢复横排。系统栏和屏幕缺口通过窗口 insets 保留安全边距。

已有 34 项菜单功能测试通过，覆盖手势、草稿、取消、保存重连及主题切换。新增 UI 测试在深浅色、360 dp 紧凑窗口、1920 × 720 横屏、宽屏和 RTL 场景中检查分组卡片、固定区域可见性、输入框样式以及取消时的草稿回退。

完整 CI 命令通过：shared 1350 项（1 项跳过）、common 1164 项、home 4 项，0 失败；三个 Lint 和三个 Debug 构建通过。

截图由 API 29 Robolectric native graphics 绘制，字体缩放为 1.5，存放在本地 `common/build/reports/carplay-menu-ui/`。1920 × 720 场景使用模拟密度 320 dpi，实车密度仍以诊断报告为准。

已生成 `0.1.3-lynk06-test` / versionCode 41 车测包，原长期签名、Android 9 签名、正式包名和认证素材检查通过。APK SHA256：`c6d6366575a71f16e58def1c65bf9a596c4ce1f792e1d1f37d761d02734739c1`。菜单的实车显示、触摸和键盘行为待覆盖安装后确认。

## 2026-10-12：应用弹窗样式统一

热点配置、文字及数值输入、选择列表、确认提示和时间选择弹窗使用统一的应用样式入口。外框、输入框和按钮沿用软件的深浅色配色与圆角规则。按钮按可用宽度及文字长度横排或纵排，长内容弹窗保留操作区的最低高度。

用户提供的车机信息为 12.3 英寸、8:3、1920 × 720 横屏、亿咖通 E02、6 GB 内存、64 GB 存储，Android 9。

完整 CI 命令通过：shared 1350 项（1 项跳过）、common 1163 项、home 4 项，0 失败；三个 Lint 和三个 Debug 构建通过。新增 4 项 UI 测试验证热点校验、密码显隐、保存与取消、列表选中及回调、时间选择和低高度横屏操作按钮可见性。分辨率、夜间模式及氛围灯长内容弹窗的既有测试通过。补充 1920 × 720 横屏场景后，定向 UI 测试再次通过。

大字体截图由 API 29 Robolectric native graphics 绘制，字体缩放为 1.5，存放在本地 `common/build/reports/app-dialog-ui/`。1920 × 720 场景按模拟密度 320 dpi 配置；截图为弹窗本身，不能代表实车实际密度或软键盘行为。

已生成 `0.1.2-lynk06-test` / versionCode 40 车测 APK，包含上一版的 root VPN 授权修复。Android 9 签名、正式包名、认证素材及原长期签名检查通过。APK SHA256：`8504d564f9297b81a84bceb3b54619bbb6c572ac874a538e230748c159555178`。本版实车显示和输入行为待覆盖安装后确认。

## 2026-10-12：USB VPN root 授权修复

用户实车反馈：2023 款领克 06 燃油版能够无线连接，但 CarPlay 画面和操作卡顿；USB 模式提示无法打开 VPN 授权界面。车机已有 root，电脑 ADB 已连接过。

VPN 授权界面无法使用时，新增 App 内 root 优先的授权路径。通过 `su -c id -u` 确认 root 后，仅为当前应用包名设置 `ACTIVATE_VPN`；未取得 root 才使用原有 ADB 后备。root 命令失败不会触发 ADB，最终通过 `VpnService.prepare()` 验证系统授权。后台完成时确认仍为 USB 模式，避免无线切换期间触发 USB 启动。

本地完整 CI 命令通过：shared 1350 项（1 项跳过）、common 1159 项、home 4 项，0 失败；mobile、home、maphost 的 Lint 和 Debug 构建通过。新增 5 项测试覆盖当前包名授权、root 不可用、root 命令失败、ADB 后备顺序及包名校验。

已生成 `0.1.1-lynk06-test` / versionCode 39 的车测 APK。构建脚本核验 Android 9 签名、正式包名、认证素材配对及原长期签名通过。APK SHA256：`64b7b28db8d9f3729d5192842a75a0763e7fa5834fe298f9d1537b2d84fe31ba`。root 授权修复的实车结果待新版验证。

## 修改范围

主应用品牌改为 LynkPlay，release 安装标识为 `com.leth0x01.lynkplay`，debug 为 `com.leth0x01.lynkplay.hudtest`。保留 namespace、存储键、兼容 action 和车型逻辑。更新来源为 `leth0x01/LynkPlay` 的 Releases。

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

大字体截图由 `LynkSetupUiTest` 调用真实引导组件并以 Robolectric native graphics 绘制，覆盖紧凑 360 dp / mdpi、宽屏 1280 dp / xhdpi 和阿拉伯语 600 dp / mdpi，字体缩放 1.5。生成截图保存在本地 `common/build/reports/lynk-setup-ui/`。UI 渲染环境使用 API 29，车型运行门禁另在 API 28 测试。这些都是模拟验证，不是实车照片。

最终 APK 资源检查确认 `config_vehicle_profile=lynk_06_2023_petrol`、`config_byd_integration=false`。普通 Debug APK 仍是未提供配件认证输入的源码构建；未制作独立车测包，未进行实车连接、声音、Siri 或方向盘验收。

## 2026-10-12：稳定独立车测包

新增 `carTest` 构建，包名为 `com.leth0x01.lynkplay`，版本 `0.1.0-lynk06-test` / versionCode 38，非 debug。未来 release 使用相同的本地长期 APK 签名，保证在递增版本后能够覆盖升级。

签名证书 SHA256：`ea6a1ad297d40af0ab441543b08c3d5a26f237a0ba1096448d07ab5a0227fc74`。密钥和密码只保存在忽略文件中，尚未建立异地备份。初始化脚本重复运行保留原签名。

认证输入来自[原版官方 v0.2.18 APK](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.18)，APK SHA256 与官方 `SHA256SUMS.txt` 一致：`e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e`。仅导入固定两项认证资产到 `.private/`，来源记录不会打包。本地验证证书为 P-256 且与私钥配对，不代表 Apple 认证。

已执行负向检查：错误源 APK SHA256、缺失认证、不配对的密钥和证书、错误签名证书指纹均被拒绝。PowerShell 三个脚本语法检查通过，公开源码及新增文件按凭据扫描规则检查通过。源码 debug APK 未包含认证资产。

车测 APK 通过 `apksigner verify --min-sdk-version 28`（v2 签名），证书与固定指纹相同；`aapt2` 确认包名、版本和应用名称 LynkPlay。打包的两个认证文件与选定输入 SHA256 完全一致。重新构建仍使用同一签名。构建流程见 [车测说明](LYNKPLAY-CAR-TEST.md)。

没有进行安装、发布、上传或实车验收。

本次完整执行 AGENTS.md 命令：shared 1350 项中 3 项失败、common 1154 项中 7 项失败、home 4 项通过，共 2508 项。失败名称与上述已确认的 Windows 基线 10 项完全一致。三个 Debug 构建与三个 Debug Lint 通过；全量 CI 仍返回失败，不宣称全量通过。

`:mobile:lintCarTest` 通过；随后重新构建并通过最终包名、签名和认证资产核验。产物 `mobile/build/outputs/apk/carTest/mobile-carTest.apk` 的 SHA256 为 `74ec294ed38bf188f22a9fad7db1b4de17d718271095c9a2a81d655ed1d41a4e`，旁边生成 `SHA256SUMS.txt`。认证输入和签名私钥未进入公开源码。
