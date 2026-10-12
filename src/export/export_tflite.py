"""Export trained keras model -> quantized TFLite for offline Android use.

Usage:
    python -m export.export_tflite --model models/fusion_best.keras

Writes:
  - models/parkimed_model.tflite   (quantized, ~5-10 MB)
  - models/preprocessing_config.json (already produced by train.py)
"""
from __future__ import annotations

import argparse
from pathlib import Path

import tensorflow as tf

import sys
sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import MODELS_DIR


def convert(model_path: Path, representative_loader=None, float16: bool = True) -> Path:
    model = tf.keras.models.load_model(model_path)
    opts = [tf.lite.Optimize.DEFAULT]
    target_types = [tf.float16] if float16 else None

    def _convert_with(c):
        c.optimizations = opts
        if target_types:
            c.target_spec.supported_types = target_types
        return c.convert()

    try:
        tflite_model = _convert_with(tf.lite.TFLiteConverter.from_keras_model(model))
    except AttributeError:
        # Keras 3 regression ('Functional' has no '_get_save_spec') -> SavedModel route.
        sm = MODELS_DIR / "parkimed_savedmodel"
        model.export(str(sm))
        tflite_model = _convert_with(tf.lite.TFLiteConverter.from_saved_model(str(sm)))

    out = MODELS_DIR / "parkimed_model.tflite"
    out.write_bytes(tflite_model)
    return out


def size_verbose(path: Path) -> None:
    kib = path.stat().st_size / 1024
    print(f"[ok] exported -> {path} ({kib:.1f} KiB)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=str(MODELS_DIR / "fusion_best.keras"))
    ap.add_argument("--float16", action="store_true")
    args = ap.parse_args()

    p = Path(args.model)
    if not p.exists():
        raise FileNotFoundError(f"model not found: {p}")
    out = convert(p, float16=args.float16)
    size_verbose(out)