#!/usr/bin/env python3
"""检查并构建 LynkPlay 正式更新包及 GitHub Release 附件。"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil

from build_lynkplay_car_test import PLATFORM_SIGNER_SHA256, SYSTEM_SHARED_UID, verify_apk
from script_utils import (
    ROOT, ScriptError, configure_console, find_android_build_tools,
    gradle_wrapper, platform_executable, read_property, report_error,
    require_file, run_command, sha256_file, write_utf8,
)

CI_TASKS = (
    ":shared:testDebugUnitTest", ":common:testDebugUnitTest", ":home:testDebugUnitTest",
    ":mobile:lintDebug", ":home:lintDebug", ":maphost:lintDebug",
    ":mobile:assembleDebug", ":home:assembleDebug", ":maphost:assembleDebug",
)


def release_version() -> tuple[str, int]:
    """只接受更新器能够比较的三段版本号。"""
    properties = ROOT / "gradle.properties"
    name = read_property(properties, "lynkplayVersionName") or ""
    code = read_property(properties, "lynkplayVersionCode") or ""
    if not re.fullmatch(r"\d+\.\d+\.\d+", name):
        raise ScriptError("lynkplayVersionName 必须为三段版本号，例如 0.1.7。")
    if not code.isdecimal() or int(code) < 1:
        raise ScriptError("lynkplayVersionCode 必须为正整数。")
    return name, int(code)


def package_release(apk: Path, version: str, output: Path) -> tuple[Path, Path]:
    """将已验证的 APK 复制为发布附件，并对最终文件生成校验和。"""
    output.mkdir(parents=True, exist_ok=True)
    published = output / f"LynkPlay-{version}.apk"
    shutil.copyfile(apk, published)
    checksums = output / "SHA256SUMS.txt"
    write_utf8(checksums, f"{sha256_file(published)}  {published.name}\n")
    return published, checksums


def build_release(authentication_assets_directory: Path) -> None:
    version, code = release_version()
    input_directory = authentication_assets_directory.expanduser().resolve()
    if not input_directory.is_dir():
        raise ScriptError(f"认证素材目录不存在：{input_directory}")
    build_tools = find_android_build_tools()
    apksigner = require_file(
        build_tools / platform_executable("apksigner", windows_suffix=".bat"), "apksigner",
    )
    aapt2 = require_file(build_tools / platform_executable("aapt2"), "aapt2")
    wrapper = require_file(gradle_wrapper(), "Gradle Wrapper")

    # 源码检查不打包认证素材；正式包使用明确选定的本地素材。
    check_env = os.environ.copy()
    check_env.pop("DIPLAY_AUTH_ASSETS_DIR", None)
    run_command(
        [wrapper, *CI_TASKS, "--console=plain"],
        env=check_env, error_message="Android CI 检查失败，停止正式出包。",
    )
    release_env = {**check_env, "DIPLAY_AUTH_ASSETS_DIR": str(input_directory)}
    run_command(
        [wrapper, ":mobile:lintRelease", ":mobile:assembleRelease", "--console=plain"],
        env=release_env, error_message="正式构建失败。",
    )
    apk = require_file(
        ROOT / "mobile/build/outputs/apk/release/mobile-release.apk", "正式 APK",
    )
    verify_apk(
        apk,
        input_directory,
        apksigner,
        aapt2,
        version_name=version,
        version_code=code,
        signer_sha256=PLATFORM_SIGNER_SHA256,
        system_uid=SYSTEM_SHARED_UID,
    )
    published, checksums = package_release(
        apk, version, ROOT / "mobile/build/outputs/publish" / version,
    )
    print(f"正式更新包：{published}")
    print(f"校验文件：{checksums}")
    print(f"发布到 leth0x01/LynkPlay 的 v{version} Release，并同时上传以上两个附件。")


def main() -> int:
    configure_console()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--authentication-assets-directory", type=Path,
        default=ROOT / ".private/auth-assets",
        help="认证素材根目录（默认：.private/auth-assets）",
    )
    args = parser.parse_args()
    try:
        build_release(args.authentication_assets_directory)
    except (OSError, ScriptError) as exc:
        return report_error(exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
