"""Runtime staging preserves the previous dependency when a download is invalid."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from prepare import stage


class RuntimePreparationTest(unittest.TestCase):
    def test_verified_runtime_replaces_previous_dependency(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "download.aar"
            source.write_bytes(b"runtime")
            destination = root / "cache/runtime.aar"
            destination.parent.mkdir()
            destination.write_bytes(b"previous")
            with patch(
                "prepare.EXPECTED_SHA256",
                "d92c6a81b2ff50096bcda80885427d1f59a25b5f483f7055523504925d16ab23",
            ):
                stage(source, destination)
            self.assertEqual(b"runtime", destination.read_bytes())
            self.assertEqual(b"runtime", source.read_bytes())
            self.assertEqual({"runtime.aar"}, {p.name for p in destination.parent.iterdir()})

    def test_invalid_runtime_keeps_previous_dependency(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "download.aar"
            source.write_bytes(b"invalid")
            destination = root / "runtime.aar"
            destination.write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "runtime"):
                stage(source, destination)
            self.assertEqual(b"previous", destination.read_bytes())


if __name__ == "__main__":
    unittest.main()
