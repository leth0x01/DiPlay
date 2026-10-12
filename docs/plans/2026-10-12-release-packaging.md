# LynkPlay 更新发布 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建 0.1.7 正式 APK，生成应用更新器可读取的发布附件。

**Architecture:** 保留现有 GitHub Releases 更新协议，复用车测脚本的签名与认证素材校验。正式构建脚本读取项目版本，执行 CI 检查与 release 构建，核对最终 APK 的包名、版本及签名，再生成版本化 APK 和 SHA256SUMS.txt。

**Tech Stack:** Python 标准库、uv、Gradle、Android SDK apksigner / aapt2。

---

1. 在 `scripts/build_lynkplay_car_test.py` 提取可复用的 APK 校验函数；新增 `scripts/build_lynkplay_release.py`，输出 `mobile/build/outputs/publish/<version>/LynkPlay-<version>.apk` 与 `SHA256SUMS.txt`。
2. 在 `scripts/tests/test_release_packaging.py` 验证错误签名、错误包名、版本不匹配和附件校验文件；扩展 `scripts/tests/test_cli_encoding.py` 的中文 CLI 检查。
3. 将 `gradle.properties` 的版本递增至 0.1.7 / 45，在 `docs/BUILD.md` 与中文 README 记录实际命令和发布附件要求。
4. 执行 `uv run python -m unittest discover -s scripts/tests`，再运行正式构建脚本内的完整 Android CI 命令及 release lint / assemble。记录真实构建结果并交付附件，GitHub Release 指向与 APK 一致的源码提交。
