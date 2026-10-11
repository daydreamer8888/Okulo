"""Shared detector packaging preserves graph-specific inference."""
import tempfile
import unittest
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper

from shared_weights import share_detector_weights


def detector(path, negate=False, weight=2.0):
    nodes = [helper.make_node("MatMul", ["image", "weight"], ["product"])]
    nodes.append(helper.make_node("Neg" if negate else "Identity", ["product"], ["result"]))
    graph = helper.make_graph(
        nodes, "detector", [helper.make_tensor_value_info("image", TensorProto.FLOAT, [1, 1])],
        [helper.make_tensor_value_info("result", TensorProto.FLOAT, [1, 1])],
        [numpy_helper.from_array(np.array([[weight]], dtype=np.float32), "weight")],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)], ir_version=10)
    onnx.save_model(model, path)


class SharedWeightsTest(unittest.TestCase):
    def test_shared_weights_preserve_both_graphs_and_source_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fast, standard = root / "fast.onnx", root / "standard.onnx"
            detector(fast)
            detector(standard, negate=True)
            originals = [p.read_bytes() for p in (fast, standard)]
            destination = root / "packed"
            share_detector_weights({"detector-320.onnx": fast, "detector-800.onnx": standard}, destination)
            self.assertEqual(originals, [p.read_bytes() for p in (fast, standard)])
            self.assertEqual(4, (destination / "detector.weights").stat().st_size)
            for name, expected in [("detector-320.onnx", 6.0), ("detector-800.onnx", -6.0)]:
                model = onnx.load(str(destination / name), load_external_data=False)
                self.assertFalse(model.graph.initializer[0].HasField("raw_data"))
                self.assertEqual(TensorProto.EXTERNAL, model.graph.initializer[0].data_location)
                actual = ort.InferenceSession(str(destination / name)).run(
                    None, {"image": np.array([[3]], dtype=np.float32)}
                )[0]
                np.testing.assert_array_equal(actual, [[expected]])

    def test_different_weights_are_rejected_before_outputs_are_written(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fast, standard = root / "fast.onnx", root / "standard.onnx"
            detector(fast)
            detector(standard, weight=3.0)
            destination = root / "packed"
            with self.assertRaisesRegex(ValueError, "identical"):
                share_detector_weights({"detector-320.onnx": fast, "detector-800.onnx": standard}, destination)
            self.assertFalse(destination.exists())
