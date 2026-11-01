#!/usr/bin/env python3
"""Quantize the pinned Apache-2.0 47-language punctuation model on CPU.

Source: https://huggingface.co/1-800-BAD-CODE/punct_cap_seg_47_language/tree/1b9d51f
Install tools/model-requirements.txt, then pass the original ONNX and an output path.
The Gather embeddings account for 131MB of the original; quantizing MatMul alone
leaves those float weights untouched. No graph outputs or token labels are removed.
"""
import argparse
import hashlib
import json
from pathlib import Path

import onnx
import onnxruntime
from onnxruntime.quantization import QuantType, quantize_dynamic

SOURCE_SHA256 = "640d91c06b7cc5b3e065c12a7097188378aad3bc11568ff1d72c4c0a2acb0df4"
OUTPUT_SHA256 = "fa630cad87cdf398422aee51c66fda532b82dc8164d9653a588277a2ea98adcb"
OUTPUT_BYTES = 58_702_685

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def interface(path):
    model = onnx.load(path)
    return ([x.SerializeToString() for x in model.graph.input],
            [x.SerializeToString() for x in model.graph.output])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error("input and output must differ")
    if sha256(args.input) != SOURCE_SHA256:
        parser.error("original model SHA-256 does not match pinned upstream revision 1b9d51f")
    if onnxruntime.__version__ != "1.20.1" or onnx.__version__ != "1.17.0":
        parser.error("install the pinned tools/model-requirements.txt for reproducible output")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    quantize_dynamic(str(args.input), str(args.output), weight_type=QuantType.QInt8,
                     op_types_to_quantize=["MatMul", "Gather"],
                     extra_options={"MatMulConstBOnly": True})
    onnx.checker.check_model(str(args.output))
    if interface(args.input) != interface(args.output):
        raise RuntimeError("quantization changed the model I/O contract")
    onnxruntime.InferenceSession(str(args.output), providers=["CPUExecutionProvider"])
    digest = sha256(args.output)
    if args.output.stat().st_size != OUTPUT_BYTES or digest != OUTPUT_SHA256:
        raise RuntimeError("quantized bytes differ from the pinned reproducible artifact")
    print(json.dumps({"path": str(args.output), "bytes": args.output.stat().st_size,
                      "sha256": digest, "onnx": onnx.__version__,
                      "onnxruntime": onnxruntime.__version__}, indent=2))

if __name__ == "__main__":
    main()
