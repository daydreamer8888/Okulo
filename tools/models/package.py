"""Validate and stage the ONNX artifacts required by the photo validation app."""
import argparse
import hashlib
import shutil
from pathlib import Path

ARTIFACTS = {
    "yolo26n.onnx": "b6e38fcd4bd123458e5f2d2e611107547b7587634f6ef46e328b9213ddad8e7d",
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
    scorer = sources["s2c.onnx"]
    target = destination / "s2c.onnx"
    if scorer.resolve() != target.resolve():
        shutil.copyfile(scorer, target)
    if "yolo26n.onnx" in sources:
        detector = sources["yolo26n.onnx"]
        target = destination / "yolo26n.onnx"
        if detector.resolve() != target.resolve():
            shutil.copyfile(detector, target)
        for name in ("detector-320.onnx", "detector-800.onnx", "detector.weights"):
            (destination / name).unlink(missing_ok=True)
        print(f"Ready: {destination}")
        return
    from shared_weights import share_detector_weights

    share_detector_weights(
        {name: sources[name] for name in ("detector-320.onnx", "detector-800.onnx")}, destination
    )
    print(f"Ready: {destination}")



if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scorer", type=Path, required=True)
    parser.add_argument("--detector", type=Path)
    parser.add_argument("--standard-detector", type=Path)
    parser.add_argument("--fast-detector", type=Path)
    parser.add_argument("--destination", type=Path, default=Path("app/model-assets/models"))
    args = parser.parse_args()
    if args.detector:
        sources = {"s2c.onnx": args.scorer, "yolo26n.onnx": args.detector}
    elif args.standard_detector and args.fast_detector:
        sources = {"s2c.onnx": args.scorer, "detector-800.onnx": args.standard_detector,
                   "detector-320.onnx": args.fast_detector}
    else:
        parser.error("provide --detector or both legacy detector paths")
    stage(sources, args.destination)
