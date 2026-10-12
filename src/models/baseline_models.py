"""Single-modality baseline models.

These exist to PROVE the fusion model adds value. The report compares:
baseline (voice-only / motion-only / etc.) vs adaptive fusion.
"""
from __future__ import annotations

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

from config import VOICE_FEATURES, MOTION_FEATURES, TAP_FEATURES, SPIRAL_FEATURES


def modality_model(
    input_dim: int, name: str, num_classes: int = 2, include_severity: bool = True,
) -> keras.Model:
    """Single-expert model for one modality."""
    inp = layers.Input(shape=(input_dim,), name=name)
    x = layers.Dense(64, activation="relu")(inp)
    x = layers.Dropout(0.3)(x)
    x = layers.Dense(32, activation="relu")(x)
    on_off = layers.Dense(num_classes, activation="softmax", name="on_off")(x)
    outputs = [on_off]
    if include_severity:
        outputs.append(layers.Dense(1, activation="sigmoid", name="severity")(x))
    model = keras.Model(inp, outputs if len(outputs) > 1 else outputs[0], name=name)
    model.compile(
        optimizer=keras.optimizers.Adam(1e-3),
        loss={
            "on_off": keras.losses.CategoricalCrossentropy(from_logits=False),
            "severity": keras.losses.MeanSquaredError(),
        },
        metrics={"on_off": "accuracy", "severity": "mse"},
    )
    return model


def build_all_baselines():
    return {
        "voice_baseline": modality_model(VOICE_FEATURES, "voice_baseline"),
        "motion_baseline": modality_model(MOTION_FEATURES, "motion_baseline"),
        "tap_baseline": modality_model(TAP_FEATURES, "tap_baseline"),
        "spiral_baseline": modality_model(SPIRAL_FEATURES, "spiral_baseline"),
    }