"""Verify and stage the S2C scorer and YOLO26n detector."""
import argparse
import hashlib
import shutil
from pathlib import Path

ARTIFACTS = {
    "s2c.onnx": "e9d5b6518a878f6873f00482bd48c96c82980ef22e7715fb0cb73a066156348a",
    "yolo26n.onnx": "b6e38fcd4bd123458e5f2d2e611107547b7587634f6ef46e328b9213ddad8e7d",
}


def stage(sources, destination):
    if set(sources) != set(ARTIFACTS):
        raise ValueError("Provide the scorer and detector together")
    for name, source in sources.items():
        with source.open("rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        if checksum != ARTIFACTS[name]:
            raise ValueError(f"Unexpected artifact: {name}")
    destination.mkdir(parents=True, exist_ok=True)
    for name, source in sources.items():
        target = destination / name
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
    for name in ("detector-320.onnx", "detector-800.onnx", "detector.weights"):
        (destination / name).unlink(missing_ok=True)
    print(f"Ready: {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scorer", type=Path, required=True)
    parser.add_argument("--detector", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=Path("app/model-assets/models"))
    args = parser.parse_args()
    stage({"s2c.onnx": args.scorer, "yolo26n.onnx": args.detector}, args.destination)
