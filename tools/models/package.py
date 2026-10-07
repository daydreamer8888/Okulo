"""Validate and stage the ONNX artifacts required by the photo validation app."""
import argparse
import hashlib
import shutil
from pathlib import Path

ARTIFACTS = {
    "s2c.onnx": "e9d5b6518a878f6873f00482bd48c96c82980ef22e7715fb0cb73a066156348a",
    "detector-800.onnx": "c71ccb64fd0221b9308d10f45e5b66182de09e96f214ffcfacf4e2a42578bdef",
    "detector-320.onnx": "d5c15586b8c10bfd2b1f729766af1a754eb6b3fe1475d12ed5efd8b87439f339",
}


def stage(sources, destination):
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
        print(f"Ready: {target}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scorer", type=Path, required=True)
    parser.add_argument("--standard-detector", type=Path, required=True)
    parser.add_argument("--fast-detector", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=Path("app/model-assets/models"))
    args = parser.parse_args()
    stage(
        {"s2c.onnx": args.scorer, "detector-800.onnx": args.standard_detector,
         "detector-320.onnx": args.fast_detector}, args.destination
    )
