# LynkPlay 稳定车测包

用户已授权开始建立独立车测包：2023 款领克 06 燃油版、Android 9，尚未实车测试。

采用 `carTest` 构建类型，包名 `com.leth0x01.lynkplay`，与未来 release 使用同一本地签名。保留上游 debug 构建供源码 CI 使用。相比沿用 debug 签名，此方案能稳定覆盖升级；相比提前发布正式版，此方案保留明确车测标识。

APK 签名由本地生成的长期 keystore 提供。密码与密钥仅放在 Git 忽略目录及 `release-signing.properties`；构建验证证书 SHA256，防止意外换签名。版本使用 LynkPlay 自身的 `0.1.0`、versionCode 38，后续每次发包递增版本号和 versionCode。

认证输入与 APK 签名分开。原版官方 v0.2.18 APK 明确内置实验性配件身份，源码不包含。仅从通过官方 SHA256 校验的本地 APK 导入指定两项 assets，放入 `.private/auth-assets/offline-mfi/`。不提交、不自动下载、不改写运行时认证逻辑。配对验证不代表 Apple 认证，也不能保证 iPhone 接受。

交付包含初始化签名、导入认证、构建与核验脚本及中文说明。验证缺失输入、错误来源哈希、证书配对、签名固定、包名和安装包资产，以及 AGENTS.md 全套 CI。安装和实车功能验证仍需用户在车上执行。
