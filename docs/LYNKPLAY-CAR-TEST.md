# LynkPlay 领克 06 车测包

对象：2023 款领克 06 燃油版，Android 9。当前未实车验证。

## 三种构建

| 类型 | 包名 | 签名 | 用途 |
| --- | --- | --- | --- |
| debug | `com.leth0x01.lynkplay.hudtest` | Android debug 签名 | 源码 CI、开发测试，默认没有认证素材 |
| carTest | `com.leth0x01.lynkplay` | LynkPlay 长期签名 | 独立车测，必须提供有效配对的认证输入 |
| release | `com.leth0x01.lynkplay` | 同一 LynkPlay 长期签名 | 后续正式版，同样要求认证输入 |

当前 LynkPlay 版本为 `0.1.0-lynk06-test`，versionCode 38；DiPlay 基线为 0.2.18。与原版 DiPlay 共存。debug 与 carTest 是不同应用，设置不会自动迁移。

## 认证素材从哪里来

[原版 README](https://github.com/shihabal3amri/DiPlay/blob/main/README.md) 说明：源码不包含认证素材，官方发布 APK 内置从公开 Carlinkit 固件恢复的实验性配件身份。它不是为 LynkPlay 新申请的 Apple MFi 认证；构建检查只能证明密钥与证书格式有效且配对，iPhone 是否接受仍需实测。

本地输入取自[官方 v0.2.18 发布页](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.18)，下载 APK 与 `SHA256SUMS.txt` 后，核对官方文件中的哈希：

```text
e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e  DiPlay-0.2.18.apk
```

在仓库根目录执行：

```powershell
.\scripts\Import-DiPlayAuthentication.ps1 `
  -ApkPath .private\upstream-release\DiPlay-0.2.18.apk `
  -ExpectedSha256 e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e `
  -SourceUrl 'https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.18/DiPlay-0.2.18.apk'
```

脚本只导入 `assets/offline-mfi/identity.pk8` 和 `certificate.p7b`，保存到 `.private/auth-assets/offline-mfi/`。来源记录为 `.private/auth-source.json`，不会打包进 APK。导入不会安装或执行原版 APK，不会覆盖已有不同身份。

认证私钥将随车测 APK 打包，接收 APK 的人可以提取它；APK 签名私钥不打包。素材与安装包均不提交到 Git。

## 初始化与备份签名

先配置 `JAVA_HOME` 为 JDK 25，`ANDROID_HOME` 为 Android SDK，然后执行：

```powershell
.\scripts\Initialize-LynkPlaySigning.ps1
```

生成 `.private/signing/lynkplay.p12` 和 `release-signing.properties`。**这两个文件必须一起安全备份**。此操作只生成本地文件，不替用户建立异地备份。重复运行保留现有签名；密钥丢失时应恢复备份，不能重新生成密钥覆盖旧安装。

本地配置保存证书 SHA256。构建和最终 APK 都会核对该指纹。原环境变量 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 优先于本地配置；使用外部签名时同时设置 `LYNKPLAY_SIGNER_SHA256`。

## 构建与检查

```powershell
.\scripts\Build-LynkPlayCarTest.ps1
```

也可通过 `-AuthenticationAssetsDirectory` 指定另一目录。脚本运行 `:mobile:assembleCarTest`，核验 Android 9 APK 签名、长期证书指纹、正式包名及打包资产与选定输入完全一致。输出：

```text
mobile/build/outputs/apk/carTest/mobile-carTest.apk
mobile/build/outputs/apk/carTest/SHA256SUMS.txt
```

手动构建：设置 `DIPLAY_AUTH_ASSETS_DIR` 为认证根目录的绝对路径，执行 `:mobile:assembleCarTest`。缺失认证或配对不正确会失败。普通源码 CI 请清除该环境变量，避免把身份打包进 debug APK。

## 后续覆盖升级

在 `gradle.properties` 同时递增 `lynkplayVersionCode` 和 `lynkplayVersionName`，例如 39 和 `0.1.1`。更新检查按三段版本号比较，因此只改变 `-lynk06-test` 后缀不足以触发更新。

carTest 到 release 保持包名和签名不变，新版 versionCode 更高即可覆盖升级并保留数据。不要卸载旧版来绕过签名错误。正式发布前应再确认认证身份的分发适用性及上游声明；本次仅准备本地车测包。

## 上车测试

停车后安装车测 APK，记录车机完整固件版本、iPhone 型号与 iOS 版本。依次检查连接、画面、音乐/导航、Siri 麦克风、电话及方向盘按键，出现异常时从诊断页导出报告。此包不能代表这些功能已经适配成功。
