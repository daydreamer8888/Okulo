"""Model staging preserves verified inputs and removes superseded assets."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from package import stage


class ModelPackageTest(unittest.TestCase):
    def test_yolo_staging_replaces_legacy_detectors_and_preserves_other_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sources = self.sources(root)
            destination = root / "models"
            destination.mkdir()
            for name in ("detector-320.onnx", "detector-800.onnx", "detector.weights"):
                (destination / name).write_bytes(b"legacy")
            (destination / "keep.txt").write_bytes(b"keep")
            with patch.dict("package.ARTIFACTS", self.hashes(), clear=True):
                stage(sources, destination)
            self.assertEqual(b"scorer", (destination / "s2c.onnx").read_bytes())
            self.assertEqual(b"detector", (destination / "yolo26n.onnx").read_bytes())
            self.assertEqual(b"keep", (destination / "keep.txt").read_bytes())
            self.assertEqual({"s2c.onnx", "yolo26n.onnx", "keep.txt"}, {p.name for p in destination.iterdir()})
            self.assertEqual(b"detector", sources["yolo26n.onnx"].read_bytes())

    def test_corrupt_detector_leaves_existing_assets_intact(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sources = self.sources(root)
            sources["yolo26n.onnx"].write_bytes(b"corrupt")
            destination = root / "models"
            destination.mkdir()
            (destination / "s2c.onnx").write_bytes(b"existing")
            with patch.dict("package.ARTIFACTS", self.hashes(), clear=True):
                with self.assertRaisesRegex(ValueError, "yolo26n.onnx"):
                    stage(sources, destination)
            self.assertEqual({"s2c.onnx"}, {p.name for p in destination.iterdir()})
            self.assertEqual(b"existing", (destination / "s2c.onnx").read_bytes())

    @staticmethod
    def sources(root):
        sources = {name: root / name for name in ("s2c.onnx", "yolo26n.onnx")}
        sources["s2c.onnx"].write_bytes(b"scorer")
        sources["yolo26n.onnx"].write_bytes(b"detector")
        return sources

    @staticmethod
    def hashes():
        return {
            "s2c.onnx": "c528cab03ad47ed5fceb01fd4f4557f12d8f2bcb2aa9f6e78087380aab8fde5f",
            "yolo26n.onnx": "f2b3cbe41413047352141e5b863d87e696ec4f52b503040dba3a5700acd529a0",
        }


if __name__ == "__main__":
    unittest.main()
