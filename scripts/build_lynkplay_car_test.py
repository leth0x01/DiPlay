#!/usr/bin/env python3
"""构建并校验 LynkPlay 车测 APK。"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
from pathlib import Path
from zipfile import BadZipFile, ZipFile

from script_utils import (
    ROOT,
    ScriptError,
    find_android_build_tools,
    gradle_wrapper,
    platform_executable,
    read_property,
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


def build_car_test(authentication_assets_directory: Path) -> None:
    """执行 Gradle 构建及产物校验。"""
    input_directory = authentication_assets_directory.expanduser().resolve()
    if not input_directory.is_dir():
        raise ScriptError(f"认证素材目录不存在：{input_directory}")

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
        [wrapper, ":mobile:assembleCarTest", "--console=plain"],
        error_message="车测构建失败，未通过校验的安装包不可用于车测。",
        env=command_env,
    )

    apk = require_file(
        ROOT / "mobile" / "build" / "outputs" / "apk" / "carTest" / "mobile-carTest.apk",
        "车测 APK",
    )
    verification = run_command(
        [apksigner, "verify", "--min-sdk-version", "28", "--verbose", "--print-certs", apk],
        error_message="APK 签名无法通过 Android 9 校验。",
        capture_output=True,
    )

    pin = os.environ.get("LYNKPLAY_SIGNER_SHA256") or read_property(
        ROOT / "release-signing.properties", "signerSha256"
    )
    if not pin or not re.fullmatch(r"[0-9a-fA-F]{64}", pin):
        raise ScriptError("缺少有效的长期签名 SHA256；请检查签名环境变量或本地配置。")
    pin = pin.lower()

    verification_lines = [line.strip() for line in verification.splitlines()]
    fingerprints = [
        match.group(1).lower()
        for line in verification_lines
        if (match := FINGERPRINT_PATTERN.fullmatch(line.strip()))
    ]
    if "Number of signers: 1" not in verification_lines or not fingerprints or any(
        fingerprint != pin for fingerprint in fingerprints
    ):
        raise ScriptError("最终 APK 签名与长期签名不一致。")

    badging = run_command(
        [aapt2, "dump", "badging", apk],
        error_message="最终 APK 包名校验失败。",
        capture_output=True,
    )
    badging_lines = badging.splitlines()
    if not badging_lines or not re.match(
        r"^package: name='com\.leth0x01\.lynkplay'", badging_lines[0]
    ):
        raise ScriptError("最终 APK 包名校验失败。")

    verify_packaged_assets(apk, input_directory)
    apk_hash = sha256_file(apk)
    write_utf8(apk.parent / "SHA256SUMS.txt", f"{apk_hash}  mobile-carTest.apk\n")
    print(badging_lines[0])
    print(f"签名证书 SHA256：{pin}")
    print(f"APK SHA256：{apk_hash}")
    print(f"车测包：{apk}")


def main() -> int:
    args = parse_args()
    try:
        build_car_test(args.authentication_assets_directory)
    except (OSError, ScriptError) as exc:
        return report_error(exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
