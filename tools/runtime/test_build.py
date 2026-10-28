"""Runtime packaging replaces the native pair and preserves the official Java API."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from build import package


class RuntimeBuildTest(unittest.TestCase):
    def test_replaces_arm64_pair_and_preserves_classes_and_other_abis(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = root / "official.aar"
            with zipfile.ZipFile(base, "w") as archive:
                archive.writestr("classes.jar", b"java-api")
                archive.writestr("jni/arm64-v8a/libonnxruntime.so", b"original-core")
                archive.writestr("jni/arm64-v8a/libonnxruntime4j_jni.so", b"original-jni")
                archive.writestr("jni/x86_64/libonnxruntime.so", b"simulator-core")
            build = self.native_pair(root)
            output = root / "reduced.aar"
            package(base, build, output)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(b"java-api", archive.read("classes.jar"))
                self.assertEqual(b"reduced-core", archive.read("jni/arm64-v8a/libonnxruntime.so"))
                self.assertEqual(b"paired-jni", archive.read("jni/arm64-v8a/libonnxruntime4j_jni.so"))
                self.assertEqual(b"simulator-core", archive.read("jni/x86_64/libonnxruntime.so"))

    def test_incomplete_base_aar_keeps_previous_dependency(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = root / "incomplete.aar"
            with zipfile.ZipFile(base, "w") as archive:
                archive.writestr("classes.jar", b"java-api")
            output = root / "runtime.aar"
            output.write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "JNI"):
                package(base, self.native_pair(root), output)
            self.assertEqual(b"previous", output.read_bytes())
            self.assertFalse(output.with_suffix(".partial").exists())

    @staticmethod
    def native_pair(root):
        build = root / "build"
        build.mkdir()
        (build / "libonnxruntime.so").write_bytes(b"reduced-core")
        (build / "libonnxruntime4j_jni.so").write_bytes(b"paired-jni")
        return build


if __name__ == "__main__":
    unittest.main()
