import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("installation", Path(__file__).parents[1] / "verify-installation.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicationMetadataTest(unittest.TestCase):
    def setUp(self):
        self.package = "com.nameemrooz.journal"
        self.badging = "package: name='com.nameemrooz.journal' versionCode='39'\napplication-label:'امروز'\nlaunchable-activity: name='com.nameemrooz.journal.MainActivity'\n"
        self.manifest = "A: android:debuggable(0x0101000f)=(type 0x12)0x0"
        self.certificate = "Signer #1 certificate DN: CN=Emrooz\nSigner #1 certificate SHA-256 digest: " + "a" * 64

    def verify(self):
        module.validate_metadata(self.badging, self.manifest, self.certificate, self.package)

    def test_accepts_release_metadata(self):
        self.verify()

    def test_rejects_renamed_launcher(self):
        self.badging = self.badging.replace("امروز", "امروز اصلاح‌شده")
        with self.assertRaisesRegex(ValueError, "launcher label"):
            self.verify()

    def test_rejects_localized_launcher_suffix(self):
        self.badging += "application-label-en:'Emrooz Preview'\n"
        with self.assertRaisesRegex(ValueError, "launcher label"):
            self.verify()

    def test_rejects_preview_identity(self):
        self.badging = self.badging.replace(self.package, self.package + ".preview")
        with self.assertRaisesRegex(ValueError, "application ID"):
            self.verify()

    def test_rejects_debug_and_test_only_apks(self):
        for attribute in ("debuggable", "testOnly"):
            with self.subTest(attribute=attribute):
                self.manifest = f"A: android:{attribute}(0x0101000f)=(type 0x12)0xffffffff"
                with self.assertRaisesRegex(ValueError, attribute):
                    self.verify()

    def test_rejects_debug_certificate(self):
        self.certificate = self.certificate.replace("CN=Emrooz", "CN=Android Debug")
        with self.assertRaisesRegex(ValueError, "debug certificate"):
            self.verify()

    def test_requires_certificate(self):
        self.certificate = ""
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.verify()

    def test_requires_launcher(self):
        self.badging = self.badging.split("launchable-activity")[0]
        with self.assertRaisesRegex(ValueError, "launcher activity"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
