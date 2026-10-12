# LynkPlay 领克 06 车测包

对象：2023 款领克 06 燃油版，Android 9。已收到实车反馈：无线能够连接，但画面和操作卡顿；USB 连接提示无法打开 VPN 授权界面。

用户提供的车机信息：12.3 英寸、8:3 横屏、1920 × 720 分辨率、亿咖通 E02、6 GB 内存、64 GB 存储。

## 三种构建

| 类型 | 包名 | 签名 | 用途 |
| --- | --- | --- | --- |
| debug | `com.leth0x01.lynkplay.hudtest` | Android debug 签名 | 源码 CI、开发测试，默认没有认证素材 |
| carTest | `com.leth0x01.lynkplay` | LynkPlay 长期签名 | 独立车测，必须提供有效配对的认证输入 |
| release | `com.leth0x01.lynkplay` | 同一 LynkPlay 长期签名 | 后续正式版，同样要求认证输入 |

当前 LynkPlay 版本为 `0.1.4-lynk06-test`，versionCode 42；DiPlay 基线为 0.2.18。与原版 DiPlay 共存。debug 与 carTest 是不同应用，设置不会自动迁移。

## 应用弹窗样式

热点配置、文字和数值输入、选择列表、确认提示及时间选择弹窗统一使用软件的深浅色配色、圆角边框和操作按钮。输入框随主题显示，操作按钮根据窗口宽度和文字长度切换横排或纵排，长内容保留底部操作区。热点名称及密码校验、密码显隐、保存和取消行为保持原有逻辑。

三指下滑菜单主体同样使用主设置页配色，包含固定标题栏、可滚动分组卡片和固定操作栏。输入行与底部操作按钮随可用宽度调整排列，菜单保留系统栏和屏幕缺口的安全边距。取消仍丢弃菜单草稿，“保存并重新连接”仍保存设置并重新连接。

## 实车宽屏尺寸

以实车 160 dpi 为依据：可用窗口宽度至少 1400 dp、高度至少 600 dp 时，三指菜单最大宽度为 1120 dp、应用弹窗最大宽度为 960 dp，按钮与输入框最低高度为 72 dp。菜单正文最低字号为 20 sp；弹窗正文为 20 sp、标题为 26 sp。窄窗和低高度窗口使用紧凑尺寸，所有宽度均限制在可用窗口内。

应用自动匹配已采集的 `IHU514G` product / device / model、`xecarx` hardware 和 Android 9 标识组合，统一使用领克 06 燃油版配置；诊断报告记录匹配结果。

## USB VPN 授权修复

当车机无法打开系统 VPN 授权界面时，App 在后台先通过 `su -c id -u` 确认自身取得 root，再执行针对当前包名的 `appops set … ACTIVATE_VPN allow`。root 命令等待上限为每次 15 秒。首次使用时，root 管理器可能要求允许 LynkPlay。

未取得 root 时才尝试车主已经批准的本机 ADB；取得 root 但授权命令失败时记录失败。完成后重新调用 `VpnService.prepare()`，仅在系统确认授权且仍为 USB 模式时继续连接。诊断日志记录 root 结果、ADB 状态及最终授权结果。

覆盖安装新版后，允许 LynkPlay 使用 root，再测试 USB 连接。此修复尚待目标车机验证。

## 认证素材从哪里来

[原版 README](https://github.com/shihabal3amri/DiPlay/blob/main/README.md) 说明：源码不包含认证素材，官方发布 APK 内置从公开 Carlinkit 固件恢复的实验性配件身份。它不是为 LynkPlay 新申请的 Apple MFi 认证；构建检查只能证明密钥与证书格式有效且配对，iPhone 是否接受仍需实测。

本地输入取自[官方 v0.2.18 发布页](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.18)，下载 APK 与 `SHA256SUMS.txt` 后，核对官方文件中的哈希：

```text
e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e  DiPlay-0.2.18.apk
```

安装 [uv](https://docs.astral.sh/uv/) 后，在仓库根目录执行。该命令可直接用于 macOS、Windows PowerShell 和 Windows 命令提示符：

```sh
uv run scripts/import_diplay_authentication.py --apk-path .private/upstream-release/DiPlay-0.2.18.apk --expected-sha256 e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e --source-url https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.18/DiPlay-0.2.18.apk
```

脚本只导入 `assets/offline-mfi/identity.pk8` 和 `certificate.p7b`，保存到 `.private/auth-assets/offline-mfi/`。来源记录为 `.private/auth-source.json`，不会打包进 APK。导入不会安装或执行原版 APK，不会覆盖已有不同身份。

认证私钥将随车测 APK 打包，接收 APK 的人可以提取它；APK 签名私钥不打包。素材与安装包均不提交到 Git。

## 初始化与备份签名

先配置 `JAVA_HOME` 为 JDK 25，`ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）为 Android SDK，然后执行：

```sh
uv run scripts/initialize_lynkplay_signing.py
```

生成 `.private/signing/lynkplay.p12` 和 `release-signing.properties`。**这两个文件必须一起安全备份**。此操作只生成本地文件，不替用户建立异地备份。重复运行保留现有签名；密钥丢失时应恢复备份，不能重新生成密钥覆盖旧安装。

本地配置保存证书 SHA256。构建和最终 APK 都会核对该指纹。原环境变量 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 优先于本地配置；使用外部签名时同时设置 `LYNKPLAY_SIGNER_SHA256`。

## 构建与检查

```sh
uv run scripts/build_lynkplay_car_test.py
```

也可通过 `--authentication-assets-directory` 指定另一目录。脚本运行 `:mobile:assembleCarTest`，核验 Android 9 APK 签名、长期证书指纹、正式包名及打包资产与选定输入完全一致。输出：

```text
mobile/build/outputs/apk/carTest/mobile-carTest.apk
mobile/build/outputs/apk/carTest/SHA256SUMS.txt
```

手动构建：设置 `DIPLAY_AUTH_ASSETS_DIR` 为认证根目录的绝对路径，执行 `:mobile:assembleCarTest`。缺失认证或配对不正确会失败。普通源码 CI 请清除该环境变量，避免把身份打包进 debug APK。

## 后续覆盖升级

在 `gradle.properties` 同时递增 `lynkplayVersionCode` 和 `lynkplayVersionName`，例如 42 和 `0.1.4`。更新检查按三段版本号比较，因此只改变 `-lynk06-test` 后缀不足以触发更新。

carTest 到 release 保持包名和签名不变，新版 versionCode 更高即可覆盖升级并保留数据。不要卸载旧版来绕过签名错误。正式发布前应再确认认证身份的分发适用性及上游声明；本次仅准备本地车测包。

## 上车测试

停车后安装车测 APK，记录车机完整固件版本、iPhone 型号与 iOS 版本。依次检查连接、画面、音乐/导航、Siri 麦克风、电话及方向盘按键，出现异常时从诊断页导出报告。此包不能代表这些功能已经适配成功。
