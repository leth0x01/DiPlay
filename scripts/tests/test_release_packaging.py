"""验证发布附件及 APK 校验能拦截不可覆盖升级的错误产物。"""

import contextlib
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_lynkplay_car_test import (
    CAR_TEST_PACKAGE,
    PLATFORM_SIGNER_SHA256,
    SYSTEM_SHARED_UID,
    verify_apk,
)
from build_lynkplay_release import package_release, release_version
from script_utils import ScriptError


class ReleasePackagingTest(unittest.TestCase):
    def test_checksums_reference_final_attachment_and_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "mobile-release.apk"
            content = b"release APK contents"
            source.write_bytes(content)
            apk, checksums = package_release(source, "0.1.7", root / "publish")
            self.assertEqual(content, apk.read_bytes())
            self.assertEqual(
                f"{hashlib.sha256(content).hexdigest()}  LynkPlay-0.1.7.apk\n",
                checksums.read_text(encoding="utf-8"),
            )

    def test_rejects_version_names_the_updater_cannot_compare(self):
        for name, code in (("latest", "45"), ("0.1", "45"), ("0.1.7", "0")):
            with self.subTest(name=name, code=code):
                with patch("build_lynkplay_release.read_property", side_effect=[name, code]):
                    with self.assertRaises(ScriptError):
                        release_version()

    def verify_fixture(self, *, signer="a" * 64, package="com.leth0x01.lynkplay",
                       name="0.1.7", code="45", mismatch_asset=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / "offline-mfi"
            assets.mkdir()
            apk = root / "release.apk"
            with ZipFile(apk, "w") as archive:
                for filename in ("identity.pk8", "certificate.p7b"):
                    content = filename.encode()
                    (assets / filename).write_bytes(content)
                    archive.writestr(
                        f"assets/offline-mfi/{filename}",
                        b"wrong input" if mismatch_asset else content,
                    )
            outputs = [
                f"Number of signers: 1\nSigner #1 certificate SHA-256 digest: {signer}\n",
                f"package: name='{package}' versionCode='{code}' versionName='{name}'\n",
            ]
            with patch.dict("os.environ", {"LYNKPLAY_SIGNER_SHA256": "a" * 64}):
                with patch("build_lynkplay_car_test.run_command", side_effect=outputs):
                    with contextlib.redirect_stdout(io.StringIO()):
                        verify_apk(apk, root, Path("apksigner"), Path("aapt2"),
                                   version_name="0.1.7", version_code=45)

    def test_accepts_matching_signed_release(self):
        self.verify_fixture()

    def test_rejects_different_signer(self):
        with self.assertRaisesRegex(ScriptError, "签名"):
            self.verify_fixture(signer="b" * 64)

    def test_rejects_debug_package(self):
        with self.assertRaisesRegex(ScriptError, "包名"):
            self.verify_fixture(package="com.leth0x01.lynkplay.hudtest")

    def test_rejects_wrong_version(self):
        for values in ({"name": "0.1.6"}, {"code": "44"}):
            with self.subTest(values=values):
                with self.assertRaisesRegex(ScriptError, "版本"):
                    self.verify_fixture(**values)

    def test_rejects_changed_authentication_assets(self):
        with self.assertRaisesRegex(ScriptError, "认证资产"):
            self.verify_fixture(mismatch_asset=True)

    def verify_system_fixture(self, *, uid=SYSTEM_SHARED_UID, signer=PLATFORM_SIGNER_SHA256):
        """构造平台签名系统包的校验环境。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / "offline-mfi"
            assets.mkdir()
            apk = root / "cluster.apk"
            with ZipFile(apk, "w") as archive:
                for filename in ("identity.pk8", "certificate.p7b"):
                    content = filename.encode()
                    (assets / filename).write_bytes(content)
                    archive.writestr(f"assets/offline-mfi/{filename}", content)
            outputs = [
                f"Number of signers: 1\nSigner #1 certificate SHA-256 digest: {signer}\n",
                f"package: name='{CAR_TEST_PACKAGE}' versionCode='47'"
                " versionName='0.1.9-bx11-system-test'\n",
                f'    A: android:sharedUserId(0x0101000b)="{uid}" (Raw: "{uid}")\n',
            ]
            with patch("build_lynkplay_car_test.run_command", side_effect=outputs):
                with contextlib.redirect_stdout(io.StringIO()):
                    verify_apk(
                        apk,
                        root,
                        Path("apksigner"),
                        Path("aapt2"),
                        signer_sha256=PLATFORM_SIGNER_SHA256,
                        package_name=CAR_TEST_PACKAGE,
                        system_uid=SYSTEM_SHARED_UID,
                    )

    def test_accepts_platform_signed_system_package(self):
        self.verify_system_fixture()

    def test_rejects_long_term_signature_in_car_test_package(self):
        with self.assertRaisesRegex(ScriptError, "签名"):
            self.verify_system_fixture(signer="e" * 64)

    def test_rejects_car_test_package_without_system_uid(self):
        with self.assertRaisesRegex(ScriptError, "系统共享 UID"):
            self.verify_system_fixture(uid="com.leth0x01.lynkplay")


if __name__ == "__main__":
    unittest.main()
