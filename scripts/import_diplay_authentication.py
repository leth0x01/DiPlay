#!/usr/bin/env python3
"""从经过哈希固定的上游 APK 导入本地实验性认证素材。"""

from __future__ import annotations

import argparse
import hmac
import json
from datetime import datetime, timezone
from pathlib import Path
from zipfile import BadZipFile, ZipFile

from script_utils import ROOT, ScriptError, report_error, require_file, sha256_file, write_utf8

ASSET_NAMES = ("identity.pk8", "certificate.p7b")
MAX_ASSET_SIZE = 16_384


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-path", required=True, type=Path, help="上游 APK 路径")
    parser.add_argument(
        "--expected-sha256",
        required=True,
        help="官方公布的 APK SHA256（64 位十六进制）",
    )
    parser.add_argument("--source-url", required=True, help="上游 APK 来源 URL")
    return parser.parse_args()


def read_asset(archive: ZipFile, name: str) -> bytearray:
    """读取唯一且大小受限的 APK 认证条目。"""
    archive_name = f"assets/offline-mfi/{name}"
    entries = [entry for entry in archive.infolist() if entry.filename == archive_name]
    if len(entries) != 1 or not 1 <= entries[0].file_size <= MAX_ASSET_SIZE:
        raise ScriptError("APK 缺少唯一、有效大小的认证条目，拒绝导入。")
    with archive.open(entries[0]) as stream:
        content = bytearray(stream.read(MAX_ASSET_SIZE + 1))
    if not 1 <= len(content) <= MAX_ASSET_SIZE:
        content.clear()
        raise ScriptError("APK 认证条目的实际大小无效，拒绝导入。")
    return content


def import_authentication(
    apk_path: Path,
    expected_sha256: str,
    source_url: str,
) -> None:
    """校验 APK 后导入两个允许的认证文件。"""
    if len(expected_sha256) != 64 or any(
        char not in "0123456789abcdefABCDEF" for char in expected_sha256
    ):
        raise ScriptError("预期 SHA256 必须是 64 位十六进制值。")

    apk = require_file(apk_path, "APK 文件")
    actual_hash = sha256_file(apk)
    if not hmac.compare_digest(actual_hash, expected_sha256.lower()):
        raise ScriptError("APK SHA256 与指定的官方校验值不一致，拒绝导入。")

    target = ROOT / ".private" / "auth-assets" / "offline-mfi"
    inputs: dict[str, bytearray] = {}
    try:
        try:
            with ZipFile(apk) as archive:
                for name in ASSET_NAMES:
                    inputs[name] = read_asset(archive, name)
        except BadZipFile as exc:
            raise ScriptError("APK 不是有效的 ZIP 文件，拒绝导入。") from exc

        for name, content in inputs.items():
            existing_path = target / name
            if existing_path.exists():
                same_content = existing_path.is_file() and hmac.compare_digest(
                    existing_path.read_bytes(), content
                )
                if not same_content:
                    raise ScriptError("已有不同认证身份；请先检查原有输入，禁止自动覆盖。")

        target.mkdir(parents=True, exist_ok=True)
        for name, content in inputs.items():
            (target / name).write_bytes(content)

        record = {
            "sourceUrl": source_url,
            "apkSha256": actual_hash,
            "importedAtUtc": datetime.now(timezone.utc).isoformat(),
            "experimental": True,
        }
        write_utf8(
            ROOT / ".private" / "auth-source.json",
            json.dumps(record, ensure_ascii=False, indent=2) + "\n",
        )
        print("已导入本地实验性认证输入；未输出密钥内容。格式与配对会在 Gradle 构建时检查。")
    finally:
        for content in inputs.values():
            content.clear()


def main() -> int:
    args = parse_args()
    try:
        import_authentication(args.apk_path, args.expected_sha256, args.source_url)
    except (OSError, ScriptError) as exc:
        return report_error(exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
