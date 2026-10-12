#!/usr/bin/env python3
"""初始化 LynkPlay 本地长期 APK 签名。"""

from __future__ import annotations

import argparse
import base64
import os
import secrets

from script_utils import (
    configure_console,
    ROOT,
    ScriptError,
    find_command,
    report_error,
    run_command,
    sha256_file,
    write_utf8,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    return parser.parse_args()


def initialize_signing() -> None:
    """生成本地 PKCS12 密钥库和对应的忽略配置。"""
    config_path = ROOT / "release-signing.properties"
    signing_directory = ROOT / ".private" / "signing"
    key_path = signing_directory / "lynkplay.p12"

    if config_path.exists():
        if not key_path.is_file():
            raise ScriptError("签名配置存在但密钥缺失，请恢复备份，不要重新生成。")
        print("已有本地签名；保留原密钥和配置。构建时会核验证书指纹。")
        return
    if key_path.exists():
        raise ScriptError("密钥已存在，请恢复签名配置；禁止覆盖已有密钥。")

    keytool = find_command("keytool", java_home=os.environ.get("JAVA_HOME"))
    signing_directory.mkdir(parents=True, exist_ok=True)
    password = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
    command_env = os.environ.copy()
    command_env["LYNKPLAY_STORE_PASSWORD"] = password

    run_command(
        [
            keytool,
            "-genkeypair",
            "-keystore",
            key_path,
            "-storetype",
            "PKCS12",
            "-alias",
            "lynkplay",
            "-keyalg",
            "RSA",
            "-keysize",
            "3072",
            "-validity",
            "10000",
            "-dname",
            "CN=LynkPlay,OU=Local Build,O=LynkPlay,C=CN",
            "-storepass:env",
            "LYNKPLAY_STORE_PASSWORD",
            "-keypass:env",
            "LYNKPLAY_STORE_PASSWORD",
        ],
        error_message="生成 APK 签名失败。",
        env=command_env,
    )

    certificate_path = signing_directory / "lynkplay-signing.cer"
    run_command(
        [
            keytool,
            "-exportcert",
            "-keystore",
            key_path,
            "-alias",
            "lynkplay",
            "-storepass:env",
            "LYNKPLAY_STORE_PASSWORD",
            "-file",
            certificate_path,
        ],
        error_message="导出签名证书失败。",
        env=command_env,
    )

    fingerprint = sha256_file(certificate_path)
    properties = (
        "storeFile=.private/signing/lynkplay.p12\n"
        f"storePassword={password}\n"
        "keyAlias=lynkplay\n"
        f"keyPassword={password}\n"
        f"signerSha256={fingerprint}\n"
    )
    write_utf8(config_path, properties)
    print(f"已生成 LynkPlay 长期签名，证书 SHA256：{fingerprint}")
    print("请一起备份 .private/signing/lynkplay.p12 和 release-signing.properties；后续版本必须使用同一密钥。")


def main() -> int:
    configure_console()
    parse_args()
    try:
        initialize_signing()
    except (OSError, ScriptError) as exc:
        return report_error(exc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
