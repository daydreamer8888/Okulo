"""Verify and stage the versioned Android inference runtime."""
import argparse
import hashlib
import tempfile
from pathlib import Path

EXPECTED_SHA256 = "6a80cbf455b885a516aa0c2e650ee0972e7197f977b7fcc43ed2951c5508963e"
DESTINATION = Path(".gradle/okulo-runtime/onnxruntime-android.aar")


def stage(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = None
    try:
        digest = hashlib.sha256()
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as output:
            partial = Path(output.name)
            with source.open("rb") as stream:
                while chunk := stream.read(1024 * 1024):
                    digest.update(chunk)
                    output.write(chunk)
        if digest.hexdigest() != EXPECTED_SHA256:
            raise ValueError("Unexpected runtime artifact")
        partial.replace(destination)
    finally:
        if partial is not None:
            partial.unlink(missing_ok=True)
    print(f"Ready: {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=DESTINATION)
    args = parser.parse_args()
    stage(args.source, args.destination)
