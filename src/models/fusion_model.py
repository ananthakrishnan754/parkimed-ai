"""Adaptive attention fusion model.

Architecture:
  voice (39)      -> dense expert
  motion (36)     -> dense expert
  tap (20)        -> dense expert
  spiral (15)     -> dense expert
  quality gates   -> per-sample trust signals
  attention layer -> softmax weights over modalities (adaptive)
  weighted sum    -> fused embedding
  classifier      -> [P(ON), P(OFF)] + severity

The "adaptive" property: each modality receives a weight that depends on its
per-sample quality gate, so noisy or absent modalities are down-weighted.

The graph is intentionally FLAT (no nested Functional models, only one
registered serializable function) so Keras can save/load and convert to
TensorFlow Lite without deserialization issues.
"""
from __future__ import annotations

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

from config import VOICE_FEATURES, MOTION_FEATURES, TAP_FEATURES, SPIRAL_FEATURES

EXPERT_HIDDEN = 32
N_MODALITIES = 4


def _expert_block(inp, name: str):
    x = layers.Dense(64, activation="relu", name=f"{name}_d1")(inp)
    x = layers.Dropout(0.3, name=f"{name}_drop")(x)
    x = layers.Dense(EXPERT_HIDDEN, activation="relu", name=f"{name}_d2")(x)
    return layers.Dense(EXPERT_HIDDEN, name=f"{name}_embedding")(x)


def _quality_gate(inp, name: str):
    x = layers.Dense(16, activation="relu", name=f"{name}_q_d1")(inp)
    return layers.Dense(1, activation="sigmoid", name=f"{name}_quality")(x)


def build_fusion_model(
    voice_dim: int = VOICE_FEATURES,
    motion_dim: int = MOTION_FEATURES,
    tap_dim: int = TAP_FEATURES,
    spiral_dim: int = SPIRAL_FEATURES,
    num_classes: int = 2,
    include_severity: bool = True,
) -> keras.Model:
    """Build the full adaptive multimodal fusion model (flat graph)."""
    voice_in = layers.Input(shape=(voice_dim,), name="voice")
    motion_in = layers.Input(shape=(motion_dim,), name="motion")
    tap_in = layers.Input(shape=(tap_dim,), name="tap")
    spiral_in = layers.Input(shape=(spiral_dim,), name="spiral")

    # Expert embeddings
    voice_emb = _expert_block(voice_in, "voice_expert")
    motion_emb = _expert_block(motion_in, "motion_expert")
    tap_emb = _expert_block(tap_in, "tap_expert")
    spiral_emb = _expert_block(spiral_in, "spiral_expert")

    # Per-sample quality gates -> (batch,4)
    q_v = _quality_gate(voice_in, "voice")
    q_m = _quality_gate(motion_in, "motion")
    q_t = _quality_gate(tap_in, "tap")
    q_s = _quality_gate(spiral_in, "spiral")
    quality_stack = layers.Concatenate(name="quality_stack")([q_v, q_m, q_t, q_s])

    # Adaptive attention weights derived from quality signals.
    raw_w = layers.Dense(N_MODALITIES, use_bias=False, name="attention_dense")(quality_stack)
    w = layers.Softmax(axis=1, name="attention_softmax")(raw_w)  # (batch,4)

    # Stack expert embeddings -> (batch,4,H), weights -> (batch,4,1),
    # elementwise scale, then reduce with a standard layer (avg == weighted
    # average here because softmax weights already sum to 1).
    emb_stack = layers.Concatenate(name="emb_stack")(
        [voice_emb, motion_emb, tap_emb, spiral_emb]
    )
    emb_stack4 = layers.Reshape((N_MODALITIES, EXPERT_HIDDEN), name="emb_stack_4d")(emb_stack)
    w4 = layers.Reshape((N_MODALITIES, 1), name="weights_4d")(w)
    scaled = layers.Multiply(name="scale_experts")([emb_stack4, w4])
    fused = layers.GlobalAveragePooling1D(name="fused")(scaled)

    x = layers.Concatenate(name="fused_with_quality")([fused, quality_stack])

    x = layers.Dense(64, activation="relu")(x)
    x = layers.Dropout(0.4)(x)
    x = layers.Dense(32, activation="relu")(x)

    on_off = layers.Dense(num_classes, activation="softmax", name="on_off")(x)

    outputs = [on_off]
    if include_severity:
        outputs.append(layers.Dense(1, activation="sigmoid", name="severity")(x))

    model = keras.Model(
        inputs={
            "voice": voice_in, "motion": motion_in,
            "tap": tap_in, "spiral": spiral_in,
        },
        outputs=outputs,
        name="parkimed_fusion",
    )
    return model


def compile_model(model: keras.Model, lr: float = 1e-3) -> keras.Model:
    model.compile(
        optimizer=keras.optimizers.Adam(lr),
        loss={
            "on_off": keras.losses.CategoricalCrossentropy(from_logits=False),
            "severity": keras.losses.MeanSquaredError(),
        },
        metrics={"on_off": "accuracy", "severity": "mse"},
    )
    return model


if __name__ == "__main__":
    import os
    os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
    m = build_fusion_model()
    m.summary()
    print("Total params:", m.count_params())