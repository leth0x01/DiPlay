#!/usr/bin/env python3
"""LynkPlay 跨平台脚本共用工具。"""

from __future__ import annotations

import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Mapping, Sequence

ROOT = Path(__file__).resolve().parents[1]
_VERSION_PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


class ScriptError(RuntimeError):
    """可直接展示给用户的脚本错误。"""


def platform_executable(name: str, *, windows_suffix: str = ".exe") -> str:
    """返回当前平台上的工具文件名。"""
    return name + windows_suffix if os.name == "nt" else name


def gradle_wrapper() -> Path:
    """返回当前平台对应的 Gradle Wrapper。"""
    return ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")


def find_command(name: str, *, java_home: str | None = None) -> str:
    """从 JAVA_HOME 或 PATH 中查找命令。"""
    executable = platform_executable(name)
    if java_home:
        candidate = Path(java_home).expanduser() / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(executable)
    if found:
        return found
    raise ScriptError(f"找不到 {name}，请检查 JAVA_HOME 或 PATH。")


def find_android_build_tools() -> Path:
    """查找 Android SDK 中版本最高的稳定 build-tools。"""
    sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_value:
        raise ScriptError("请设置 ANDROID_HOME（或 ANDROID_SDK_ROOT）。")

    build_tools = Path(sdk_value).expanduser() / "build-tools"
    if not build_tools.is_dir():
        raise ScriptError(f"找不到 Android SDK build-tools：{build_tools}")

    versions: list[tuple[tuple[int, int, int], Path]] = []
    for child in build_tools.iterdir():
        match = _VERSION_PATTERN.fullmatch(child.name)
        if child.is_dir() and match:
            versions.append((tuple(map(int, match.groups())), child))
    if not versions:
        raise ScriptError("找不到稳定版本的 Android SDK build-tools。")
    return max(versions, key=lambda item: item[0])[1]


def require_file(path: Path, description: str) -> Path:
    """确认文件存在，并返回其绝对路径。"""
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise ScriptError(f"{description}不存在：{resolved}")
    return resolved


def run_command(
    command: Sequence[str | os.PathLike[str]],
    *,
    error_message: str,
    cwd: Path = ROOT,
    env: Mapping[str, str] | None = None,
    capture_output: bool = False,
) -> str:
    """运行外部命令，并将失败转换为统一错误。"""
    normalized = [os.fspath(part) for part in command]
    try:
        result = subprocess.run(
            normalized,
            cwd=cwd,
            env=env,
            check=False,
            stdout=subprocess.PIPE if capture_output else None,
            stderr=subprocess.STDOUT if capture_output else None,
            text=capture_output,
            encoding="utf-8" if capture_output else None,
            errors="replace" if capture_output else None,
        )
    except OSError as exc:
        raise ScriptError(f"无法启动命令 {normalized[0]}：{exc}") from exc
    if result.returncode != 0:
        details = (result.stdout or "").strip()
        suffix = f"\n{details}" if details else ""
        raise ScriptError(error_message + suffix)
    return result.stdout or ""


def sha256_file(path: Path) -> str:
    """计算文件 SHA256。"""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_property(path: Path, name: str) -> str | None:
    """读取本项目生成的简单 Java properties 配置项。"""
    if not path.is_file():
        return None
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == name:
            return value.strip()
    return None


def write_utf8(path: Path, content: str) -> None:
    """以无 BOM、LF 换行写入 UTF-8 文本。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def report_error(exc: Exception) -> int:
    """将预期错误输出到 stderr，供 CLI main 使用。"""
    print(f"错误：{exc}", file=sys.stderr)
    return 1
