"""Evaluation: metrics + baseline-vs-fusion comparison table.

Run after training with:  python -m models.evaluate
Produces outputs/report.json, outputs/confusion.png, outputs/comparison.md
"""
from __future__ import annotations

import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import (accuracy_score, f1_score, auc, roc_auc_score,
                             confusion_matrix, roc_curve, classification_report)

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import MODELS_DIR, OUTPUTS_DIR, CONFIG_JSON, ON_OFF_THRESHOLD
from models.train import load_dataset, split_by_subject, build_input_dict


def to_vectors(pred_probs: np.ndarray, thresh: float = ON_OFF_THRESHOLD) -> np.ndarray:
    return (pred_probs[:, 1] >= thresh).astype(int)


def eval_one(model, X_test, y_test) -> dict:
    y_true = np.argmax(y_test["on_off"], axis=1)
    pred_probs = model.predict(X_test) if not isinstance(model.predict(X_test), list) else model.predict(X_test)[0]
    if isinstance(pred_probs, list):
        pred_probs = pred_probs[0]
    pred = to_vectors(pred_probs)
    acc = accuracy_score(y_true, pred)
    f1 = f1_score(y_true, pred, zero_division=0)
    try:
        auc_ = roc_auc_score(y_true, pred_probs[:, 1])
    except Exception:
        auc_ = float("nan")
    cm = confusion_matrix(y_true, pred, labels=[0, 1])
    tn, fp, fn, tp = cm.ravel() if cm.size == 4 else (0, 0, 0, 0)
    sensitivity = tp / (tp + fn) if (tp + fn) else 0.0
    specificity = tn / (tn + fp) if (tn + fp) else 0.0
    return {"accuracy": acc, "f1": f1, "auc": auc_, "sensitivity": sensitivity,
            "specificity": specificity, "confusion": cm.tolist()}


def main() -> None:
    df, X, y = load_dataset()
    X_split, y_split, _ = split_by_subject(df, X, y)
    X_test = {n: X_split[n]["test"] for n in X_split}
    y_test = {k: y_split[k]["test"] for k in y_split}

    results = {}
    for name in ["voice", "motion", "tap", "spiral"]:
        path = MODELS_DIR / f"{name}_best.keras"
        if not path.exists():
            print(f"[skip] baseline {name} not trained")
            continue
        m = tf.keras.models.load_model(path)
        results[f"{name}_baseline"] = eval_one(m, X_test[name], y_test)

    fusion_path = MODELS_DIR / "fusion_best.keras"
    if fusion_path.exists():
        m = tf.keras.models.load_model(fusion_path)
        test_inputs = {n: X_split[n]["test"] for n in X_split}
        results["fusion"] = eval_one(m, test_inputs, y_test)

    # --- comparison report ------------------------------------------------
    lines = ["# Evaluation Comparison\n", "| Model | Acc | F1 | AUC | Sens | Spec |"]
    lines.append("|---|---|---|---|---|---|")
    best = None
    for k, v in results.items():
        lines.append(f"| {k} | {v['accuracy']:.3f} | {v['f1']:.3f} | "
                     f"{v['auc']:.3f} | {v['sensitivity']:.3f} | {v['specificity']:.3f} |")
        if best is None or v["accuracy"] > best["accuracy"]:
            best = {"model": k, **v}
    lines.append("")
    lines.append(f"**Best:** `{best['model']}` at {best['accuracy']:.3f} accuracy.")
    (OUTPUTS_DIR / "comparison.md").write_text("\n".join(lines))
    print("\n".join(lines))

    results["_best"] = best
    (OUTPUTS_DIR / "report.json").write_text(json.dumps(results, indent=2))
    print(f"\n[saved] outputs/report.json and outputs/comparison.md")


if __name__ == "__main__":
    main()