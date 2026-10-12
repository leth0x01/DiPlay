"""复现 Windows CP1252 管道输出，验证中文 CLI 不会编码失败。"""

import os
from pathlib import Path
import subprocess
import sys
import unittest


class CliEncodingTest(unittest.TestCase):
    def test_chinese_help_uses_utf8_with_legacy_console_encoding(self):
        root = Path(__file__).resolve().parents[2]
        for name in (
            "import_diplay_authentication.py",
            "initialize_lynkplay_signing.py",
            "build_lynkplay_car_test.py",
        ):
            with self.subTest(script=name):
                result = subprocess.run(
                    [sys.executable, str(root / "scripts" / name), "--help"],
                    env={**os.environ, "PYTHONIOENCODING": "cp1252", "PYTHONUTF8": "0"},
                    capture_output=True,
                    cwd=root,
                )
                self.assertEqual(0, result.returncode, result.stderr.decode("utf-8"))
                output = result.stdout.decode("utf-8")
                self.assertIn("usage:", output)
                self.assertTrue(any("\u4e00" <= char <= "\u9fff" for char in output))


if __name__ == "__main__":
    unittest.main()
