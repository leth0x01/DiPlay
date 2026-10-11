# LynkPlay Car Test Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task in this session. 用户已要求开始操作，不再次请求批准；不委派子代理。

**Goal:** 产出与正式版可覆盖升级的独立 LynkPlay 车测 APK。

**Architecture:** `mobile` 增加 release 派生的 `carTest`；本地签名配置与显式认证输入在构建前核验。PowerShell 脚本负责密钥初始化、已校验官方 APK 的限定导入以及产物核验。

**Tech Stack:** Gradle Kotlin DSL、JDK 25、Android SDK 37、PowerShell、keytool、apksigner、aapt2。

## Global Constraints

- 包名 `com.leth0x01.lynkplay`；Android 9 目标车辆尚未测试。
- 私密文件只写 `.private/` 和被忽略的 `release-signing.properties`。
- 源码 CI 不含认证输入；保持原上游构建路径可用。
- 每次发布递增 versionCode 与三段版本号；签名不得更换。

## Task 1：构建配置

Files: `mobile/build.gradle.kts`、`gradle.properties`。

- [x] 加入可配置版本、环境变量优先的本地签名配置、`carTest` 派生类型。
- [x] `preCarTestBuild` 与 `preReleaseBuild` 验证认证 P-256 密钥/证书配对以及签名证书指纹。
- [x] 无认证目录运行 `:mobile:verifyStandaloneAuthentication`，应拒绝；错误签名指纹运行 `:mobile:verifyLynkPlaySigning`，应拒绝。

## Task 2：本地构建脚本

Files: `scripts/Initialize-LynkPlaySigning.ps1`、`scripts/Import-DiPlayAuthentication.ps1`、`scripts/Build-LynkPlayCarTest.ps1`。

- [x] 初始化 RSA 3072 / PKCS12 签名，随机密码通过环境变量传给 keytool，生成指纹固定配置，拒绝覆盖旧 key。
- [x] 导入脚本验证完整 APK SHA256，只导入两个非空且不超过 16KiB 的固定条目；重复执行不得换身份。
- [x] 构建脚本设置显式输入、运行 `:mobile:assembleCarTest`、验证 APK 签名指纹、包名和两个认证资产，生成 SHA256SUMS。
- [x] 错误源 APK 哈希应拒绝，重复初始化应保持相同指纹；运行脚本生成车测 APK。

## Task 3：文档与验证

Files: `docs/LYNKPLAY-CAR-TEST.md`、`docs/BUILD.md`。

- [x] 说明认证来源、签名备份、升级规则及 Android 9 安装与回报流程。
- [x] 清除认证环境变量后运行 AGENTS.md 完整 CI，记录既有 Windows 失败与新增结果。
- [x] 执行 `:mobile:lintCarTest`，核验最终产物包含身份且签名固定；不推送密钥和 APK。
