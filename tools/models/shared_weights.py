"""Keep detector graphs separate while storing their identical weights once."""
from pathlib import Path

import onnx
from onnx.external_data_helper import set_external_data


def share_detector_weights(sources, destination):
    models = {name: onnx.load(str(path)) for name, path in sources.items()}
    first = next(iter(models.values()))
    reference = {tensor.name: tensor.SerializeToString() for tensor in first.graph.initializer}
    if not reference or any(
        {tensor.name: tensor.SerializeToString() for tensor in model.graph.initializer} != reference
        for model in models.values()
    ):
        raise ValueError("Detector weights must be identical")
    if any(not tensor.HasField("raw_data") for tensor in first.graph.initializer):
        raise ValueError("Expected embedded raw detector weights")
    offsets = {}
    data = bytearray()
    for tensor in first.graph.initializer:
        offsets[tensor.name] = (len(data), len(tensor.raw_data))
        data.extend(tensor.raw_data)
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "detector.weights").write_bytes(data)
    for name, model in models.items():
        for tensor in model.graph.initializer:
            offset, length = offsets[tensor.name]
            set_external_data(tensor, "detector.weights", offset, length)
            tensor.ClearField("raw_data")
        onnx.save_model(model, str(destination / name))
