"""Train the fusion (or baseline) model on the processed feature dataset.

Assumes `data/features/` contains a features.csv where each row is one
sample with columns:
    subject_id, split, label (ON/OFF), severity,
    voice_0..voice_38, motion_0..motion_35, tap_0..tap_19, spiral_0..spiral_14

Splitting is performed by subject_id (no leakage).
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path

# Force CPU unless the user explicitly wants GPU (avoids XLA/ptxas aborts
# on machines with incomplete CUDA toolchains).
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit
import tensorflow as tf

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # src on path (avoid models/ artifact dir clash)
sys.path.append(str(Path(__file__).resolve().parents[2]))     # project root (for config.py)
from config import *
from models.fusion_model import build_fusion_model, compile_model
from models.baseline_models import build_all_baselines

FEATURES_CSV = FEATURES_DIR / "features.csv"
ON_OFF_DIM = 2


def load_dataset() -> tuple[pd.DataFrame, dict, dict]:
    if not FEATURES_CSV.exists():
        raise FileNotFoundError(
            f"{FEATURES_CSV} not found. Run feature extraction first."
        )
    df = pd.read_csv(FEATURES_CSV)
    df["label_oh"] = df["label"].map({"ON": 1.0, "OFF": 0.0})
    df = df.dropna(subset=["label_oh"])

    def block(name):
        cols = [c for c in df.columns if c.startswith(name)]
        return df[cols].values.astype(np.float32)

    X = {
        "voice": block("voice_"),
        "motion": block("motion_"),
        "tap": block("tap_"),
        "spiral": block("spiral_"),
    }
    y_on_off = tf.keras.utils.to_categorical(df["label_oh"].values, num_classes=2)
    sev = df["severity"].fillna(SEVERITY_RANGE[1] / 2).values.astype(np.float32)
    y_sev = (sev / SEVERITY_RANGE[1]).reshape(-1, 1)  # scale 0..5 -> 0..1 (sigmoid output)
    return df, X, {"on_off": y_on_off, "severity": y_sev}


def split_by_subject(df, X, y):
    gss = GroupShuffleSplit(n_splits=1, test_size=TEST_SPLIT + VAL_SPLIT, random_state=SEED)
    train_idx, rest_idx = next(gss.split(df.index, groups=df["subject_id"]))

    rest_df = df.iloc[rest_idx]
    gss2 = GroupShuffleSplit(n_splits=1, test_size=VAL_SPLIT / (TEST_SPLIT + VAL_SPLIT), random_state=SEED)
    val_idx, test_idx = next(gss2.split(rest_df.index, groups=rest_df["subject_id"]))
    val_idx = rest_df.index[val_idx]
    test_idx = rest_df.index[test_idx]

    out = {}
    for name, arr in X.items():
        out[name] = {
            "train": arr[train_idx], "val": arr[list(val_idx)], "test": arr[list(test_idx)],
        }
    y_out = {
        k: {
            "train": v[train_idx], "val": v[list(val_idx)], "test": v[list(test_idx)],
        }
        for k, v in y.items()
    }
    return out, y_out, (train_idx, list(val_idx), list(test_idx))


def build_input_dict(bucket: dict):
    # bucket['voice']['train'] etc. — repack into keras input dict
    return {name: bucket[name]["train"] for name in bucket}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", choices=["fusion", "voice", "motion", "tap", "spiral"], default="fusion")
    args = ap.parse_args()

    tf.random.set_seed(SEED)
    np.random.seed(SEED)

    df, X, y = load_dataset()
    X_split, y_split, _ = split_by_subject(df, X, y)

    if args.model == "fusion":
        model = build_fusion_model()
        model = compile_model(model)
        train_inputs = build_input_dict(X_split)
        train_targets = {k: y_split[k]["train"] for k in y_split}
        val_inputs = {name: X_split[name]["val"] for name in X_split}
        val_targets = {k: y_split[k]["val"] for k in y_split}
    else:
        models = build_all_baselines()
        model = models[f"{args.model}_baseline"]
        train_inputs = X_split[args.model]["train"]
        train_targets = {k: y_split[k]["train"] for k in y_split}
        val_inputs = X_split[args.model]["val"]
        val_targets = {k: y_split[k]["val"] for k in y_split}

    class_weights = None
    if CLASS_WEIGHTING:
        on = (y_split["on_off"]["train"][:, 1]).sum()
        off = (y_split["on_off"]["train"][:, 0]).sum()
        class_weights = {0: off.detach() if hasattr(off, "detach") else float(max(off, 1)),
                         1: float(max(on, 1))}

    print(f"\n[training] model={args.model} | train={len(X_split[list(X_split)[0]]['train'])} "
          f"val={len(X_split[list(X_split)[0]]['val'])} test={len(X_split[list(X_split)[0]]['test'])}")

    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(MODELS_DIR / f"{args.model}_best.keras",
                                           save_best_only=True, monitor="val_loss"),
        tf.keras.callbacks.EarlyStopping(patience=10, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=5),
    ]

    history = model.fit(
        train_inputs, train_targets,
        validation_data=(val_inputs, val_targets),
        epochs=EPOCHS, batch_size=BATCH_SIZE,
        callbacks=callbacks, verbose=1,
    )

    export_preprocessing_config()
    print(f"\n[saved] best checkpoint -> {MODELS_DIR / f'{args.model}_best.keras'}")


if __name__ == "__main__":
    main()