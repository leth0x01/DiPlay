#!/usr/bin/env python3
"""构建并校验 LynkPlay 车测 APK（BX11 平台签名系统包）。

车测包与正式包是同一个 `release` 构建，包名 `com.leth0x01.lynkplay`，使用本车平台签名并
声明 android.uid.system；仪表 CarPlay 地图依赖系统 UID，因此只有该包的仪表结果有效。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
from pathlib import Path
from zipfile import BadZipFile, ZipFile

from script_utils import (
    configure_console,
    ROOT,
    ScriptError,
    find_android_build_tools,
    gradle_wrapper,
    platform_executable,
    report_error,
    require_file,
    run_command,
    sha256_file,
    write_utf8,
)

ASSET_NAMES = ("identity.pk8", "certificate.p7b")
FINGERPRINT_PATTERN = re.compile(
    r"^(?:Signer #1 |V\d+(?:\.\d+)? Signer: )certificate SHA-256 digest: ([0-9a-fA-F]{64})$"
)
# 本车 BX11 / IHU514G 固件使用的 AOSP 平台测试证书，与 mobile/build.gradle.kts 的构建门禁一致。
PLATFORM_SIGNER_SHA256 = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"
CAR_TEST_PACKAGE = "com.leth0x01.lynkplay"
SYSTEM_SHARED_UID = "android.uid.system"
KEYSTORE_PROPERTIES = "keystore.properties"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--authentication-assets-directory",
        type=Path,
        default=ROOT / ".private" / "auth-assets",
        help="认证素材根目录（默认：.private/auth-assets）",
    )
    return parser.parse_args()


def verify_packaged_assets(apk: Path, input_directory: Path) -> None:
    """确认最终 APK 中的认证素材与本地输入完全一致。"""
    try:
        with ZipFile(apk) as archive:
            for name in ASSET_NAMES:
                archive_name = f"assets/offline-mfi/{name}"
                entries = [entry for entry in archive.infolist() if entry.filename == archive_name]
                if len(entries) != 1:
                    raise ScriptError("最终 APK 缺少唯一的认证资产。")
                with archive.open(entries[0]) as stream:
                    packaged_hash = hashlib.sha256()
                    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                        packaged_hash.update(chunk)
                source = require_file(input_directory / "offline-mfi" / name, "认证资产")
                if packaged_hash.hexdigest() != sha256_file(source):
                    raise ScriptError("最终 APK 认证资产与选定输入不同。")
    except BadZipFile as exc:
        raise ScriptError("最终 APK 不是有效的 ZIP 文件。") from exc


def signing_properties_path() -> Path:
    """官方写法的签名配置：项目根目录的 keystore.properties。"""
    return ROOT / KEYSTORE_PROPERTIES


def resolve_signer_pin(signer_sha256: str | None) -> str:
    """解析并校验签名证书指纹；未显式给出时读取环境变量。"""
    pin = signer_sha256 or os.environ.get("LYNKPLAY_SIGNER_SHA256")
    if not pin or not re.fullmatch(r"[0-9a-fA-F]{64}", pin):
        raise ScriptError("缺少有效的签名证书 SHA256；请检查签名环境变量。")
    return pin.lower()


def build_car_test(authentication_assets_directory: Path) -> None:
    """执行车测包（平台签名系统 APK）构建及产物校验。"""
    input_directory = authentication_assets_directory.expanduser().resolve()
    if not input_directory.is_dir():
        raise ScriptError(f"认证素材目录不存在：{input_directory}")
    require_file(
        signing_properties_path(),
        "签名配置（运行 java scripts/PrepareBx11PlatformSigning.java 生成）",
    )

    build_tools = find_android_build_tools()
    apksigner = require_file(
        build_tools / platform_executable("apksigner", windows_suffix=".bat"),
        "apksigner",
    )
    aapt2 = require_file(build_tools / platform_executable("aapt2"), "aapt2")
    wrapper = require_file(gradle_wrapper(), "Gradle Wrapper")

    command_env = os.environ.copy()
    command_env["DIPLAY_AUTH_ASSETS_DIR"] = str(input_directory)
    run_command(
        [wrapper, ":mobile:assembleRelease", "--console=plain"],
        error_message="车测构建失败，未通过校验的安装包不可用于车测。",
        env=command_env,
    )

    apk = require_file(
        ROOT / "mobile" / "build" / "outputs" / "apk" / "release" / "mobile-release.apk",
        "车测 APK",
    )
    verify_apk(
        apk,
        input_directory,
        apksigner,
        aapt2,
        signer_sha256=PLATFORM_SIGNER_SHA256,
        package_name=CAR_TEST_PACKAGE,
        system_uid=SYSTEM_SHARED_UID,
    )
    apk_hash = sha256_file(apk)
    write_utf8(apk.parent / "SHA256SUMS.txt", f"{apk_hash}  mobile-release.apk\n")
    print(f"APK SHA256：{apk_hash}")
    print(f"车测包：{apk}")


def verify_apk(
    apk: Path,
    input_directory: Path,
    apksigner: Path,
    aapt2: Path,
    *,
    version_name: str | None = None,
    version_code: int | None = None,
    signer_sha256: str | None = None,
    package_name: str = "com.leth0x01.lynkplay",
    system_uid: str | None = None,
) -> None:
    """核验最终 APK 的签名、包名、版本、系统 UID 声明和认证素材。"""
    verification = run_command(
        [apksigner, "verify", "--min-sdk-version", "28", "--verbose", "--print-certs", apk],
        error_message="APK 签名无法通过 Android 9 校验。",
        capture_output=True,
    )

    pin = resolve_signer_pin(signer_sha256)

    verification_lines = [line.strip() for line in verification.splitlines()]
    fingerprints = [
        match.group(1).lower()
        for line in verification_lines
        if (match := FINGERPRINT_PATTERN.fullmatch(line.strip()))
    ]
    if "Number of signers: 1" not in verification_lines or not fingerprints or any(
        fingerprint != pin for fingerprint in fingerprints
    ):
        raise ScriptError("最终 APK 签名与选定签名证书不一致。")

    badging = run_command(
        [aapt2, "dump", "badging", apk],
        error_message="最终 APK 包名校验失败。",
        capture_output=True,
    )
    badging_lines = badging.splitlines()
    if not badging_lines or not re.match(
        rf"^package: name='{re.escape(package_name)}'", badging_lines[0]
    ):
        raise ScriptError("最终 APK 包名校验失败。")

    metadata = dict(re.findall(r"(\w+)='([^']*)'", badging_lines[0]))
    if version_name is not None and metadata.get("versionName") != version_name:
        raise ScriptError("最终 APK 版本名称与发布版本不一致。")
    if version_code is not None and metadata.get("versionCode") != str(version_code):
        raise ScriptError("最终 APK 版本代码与发布版本不一致。")
    if system_uid is not None:
        manifest = run_command(
            [aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
            error_message="最终 APK 系统 UID 校验失败。",
            capture_output=True,
        )
        if "sharedUserId" not in manifest or f'"{system_uid}"' not in manifest:
            raise ScriptError("最终 APK 未声明系统共享 UID。")
    verify_packaged_assets(apk, input_directory)
    print(badging_lines[0])
    print(f"签名证书 SHA256：{pin}")


def main() -> int:
    configure_console()
    args = parse_args()
    try:
        build_car_test(args.authentication_assets_directory)
    except (OSError, ScriptError) as exc:
        return report_error(exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
