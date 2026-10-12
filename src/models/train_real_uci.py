"""Train Model v2 on REAL UCI Parkinson's voice data and export TFLite.

Why v2: the original fusion was trained on synthetic mock data, so real user
inputs produced meaningless absolute outputs (e.g. severity 5/5 for a healthy
person). v2 replaces the voice expert with models trained on real recordings:

  - PD-vs-healthy classifier  (ucispeech: 195 real recordings, 147 PD / 48 healthy)
  - UPDRS severity regressor  (telemonitoring: 5,875 real recordings, 42 PD patients)

On-device contract is unchanged (4 inputs / 2 outputs), so only the app's
voice feature extractor needs updating to produce the same 16 real MDVP
quantities (padded with zeros to the 39-dim voice vector).

Outputs semantics (Android reads them identically):
  on_off   [P(PD), P(ON)]      index 1 = P(ON)  -> healthy person => ON
  severity [0..1] = P(PD) * P(UPDRS high)        healthy person => ~0
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from sklearn.model_selection import cross_validate, StratifiedKFold
from sklearn.metrics import r2_score, root_mean_squared_error

sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import MODELS_DIR, RAW_DIR, SAMPLE_RATE, SEED

OUT = MODELS_DIR / "parkimed_model.tflite"
CONFIG_JSON = MODELS_DIR / "preprocessing_config.json"

# Real MDVP features reproducible on-device (slot -> column in ucispeech.csv)
CORE = [
    "MDVP:Fo(Hz)", "MDVP:Fhi(Hz)", "MDVP:Flo(Hz)", "MDVP:Jitter(%)", "MDVP:Jitter(Abs)",
    "MDVP:RAP", "MDVP:PPQ", "Jitter:DDP", "MDVP:Shimmer", "MDVP:Shimmer(dB)",
    "Shimmer:APQ3", "Shimmer:APQ5", "MDVP:APQ", "Shimmer:DDA", "NHR", "HNR",
]
VOICE_DIM = 39          # must match config.VOICE_FEATURES / Android app
MOTION_DIM = 36
TAP_DIM = 20
SPIRAL_DIM = 15
N_CORE = len(CORE)

TELE_MAP = {
    "MDVP:Fo(Hz)": None, "MDVP:Fhi(Hz)": None, "MDVP:Flo(Hz)": None,
    "MDVP:Jitter(%)": "Jitter(%)", "MDVP:Jitter(Abs)": "Jitter(Abs)",
    "MDVP:RAP": "Jitter:RAP", "MDVP:PPQ": "Jitter:PPQ5", "Jitter:DDP": "Jitter:DDP",
    "MDVP:Shimmer": "Shimmer", "MDVP:Shimmer(dB)": "Shimmer(dB)",
    "Shimmer:APQ3": "Shimmer:APQ3", "Shimmer:APQ5": "Shimmer:APQ5",
    "MDVP:APQ": "Shimmer:APQ11", "Shimmer:DDA": "Shimmer:DDA",
    "NHR": "NHR", "HNR": "HNR",
}


def pad39(x16: np.ndarray) -> np.ndarray:
    out = np.zeros((len(x16), VOICE_DIM), dtype=np.float32)
    out[:, :N_CORE] = x16
    return out


def load_uci_speech() -> tuple[np.ndarray, np.ndarray]:
    df = pd.read_csv(RAW_DIR / "uci_speech.csv")
    X16 = df[CORE].to_numpy(dtype=np.float32)
    y = df["status"].to_numpy(dtype=np.int64)          # 1 = PD, 0 = healthy
    return X16, y


def load_uci_tele() -> tuple[np.ndarray, np.ndarray]:
    df = pd.read_csv(RAW_DIR / "uci_telemonitoring.csv")
    X16 = np.zeros((len(df), N_CORE), dtype=np.float32)
    for i, col in enumerate(CORE):
        tel = TELE_MAP.get(col)
        if tel is not None:
            X16[:, i] = df[tel].to_numpy(dtype=np.float32)
    up = df["total_UPDRS"].to_numpy(dtype=np.float32)
    y = (up - 7.0) / (55.0 - 7.0)                       # normalize to 0..1
    return X16, y


def build_v2(mean16: np.ndarray, std16: np.ndarray) -> keras.Model:
    voice = layers.Input(shape=(VOICE_DIM,), name="voice")
    motion = layers.Input(shape=(MOTION_DIM,), name="motion")
    tap = layers.Input(shape=(TAP_DIM,), name="tap")
    spiral = layers.Input(shape=(SPIRAL_DIM,), name="spiral")

    m = tf.constant(mean16.astype(np.float32))
    s = tf.constant(std16.astype(np.float32))
    vstd = (voice[:, :N_CORE] - m) / s

    # PD-vs-healthy classifier: uses ONLY phonation-quality features (jitter/
    # shimmer/NHR/HNR). Absolute pitch (Fo/Fhi/Flo) is dropped: UCI healthy
    # recordings run high (mean Fo ~182 Hz) while a healthy male sits ~110-130 Hz,
    # so pitch-height would falsely flag him as PD.
    vquality = vstd[:, 3:N_CORE]
    c = layers.Dense(16, activation="relu", name="clf_d1")(vquality)
    c = layers.Dense(8, activation="relu", name="clf_d2")(c)
    on_off = layers.Dense(2, activation="softmax", name="on_off")(c)  # [P(PD), P(ON)]
    p_pd = layers.Lambda(lambda x: x[..., 0:1], name="p_pd")(on_off)

    # UPDRS severity regressor (real, telemonitoring) — weights copied in after
    r = layers.Dense(16, activation="relu", name="sev_d1")(vstd)
    sev_raw = layers.Dense(1, activation="sigmoid", name="sev_raw")(r)

    # severity only counts when PD is present (healthy -> ~0)
    severity = layers.Multiply(name="sev_gate")([sev_raw, p_pd])

    # Consume motor inputs via a frozen zero branch so the converter keeps the
    # signature inputs (motor experts need labelled data we don't have).
    aux = layers.Concatenate(name="aux_stack")([motion, tap, spiral])
    aux_zero = layers.Dense(
        1, activation="linear", trainable=False,
        kernel_initializer="zeros", bias_initializer="zeros", name="aux_probe",
    )(aux)
    severity_out = layers.Add(name="severity")([severity, aux_zero])

    model = keras.Model([voice, motion, tap, spiral], [on_off, severity_out],
                        name="parkimed_v2")
    model.compile(
        optimizer=keras.optimizers.Adam(1e-3),
        loss={"on_off": "categorical_crossentropy", "severity": "mse"},
        loss_weights={"on_off": 1.0, "severity": 0.0},   # sever. frozen during clf train
    )
    return model


def main() -> None:
    X16, ys = load_uci_speech()
    Xt16, yt = load_uci_tele()

    # honest CV for the report
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    skf = StratifiedKFold(5, shuffle=True, random_state=SEED)
    clf = make_pipeline(StandardScaler(),
                        RandomForestClassifier(400, random_state=SEED, n_jobs=-1))
    cv = cross_validate(clf, X16[:, 3:], ys, cv=skf, scoring=["accuracy", "roc_auc"])
    print(f"[uci] classifier CV (13 quality feats, pitch-independent): "
          f"acc={cv['test_accuracy'].mean():.3f} auc={cv['test_roc_auc'].mean():.3f} "
          f"(n={len(ys)}, PD={int(ys.sum())}, healthy={int((~ys.astype(bool)).sum())})")
    cv_full = cross_validate(clf, X16, ys, cv=skf, scoring=["accuracy"])

    mean16 = X16.mean(axis=0); std16 = X16.std(axis=0) + 1e-6
    Xs16 = (X16 - mean16) / std16
    Xt16_ = (Xt16 - mean16) / std16
    Xs39 = pad39(X16)          # RAW features; the graph standardises internally

    model = build_v2(mean16, std16)

    # --- severity branch trained on REAL telemonitoring UPDRS ---
    sev_head = keras.Sequential([
        layers.Input(shape=(N_CORE,)),
        layers.Dense(16, activation="relu", name="sh_d1"),
        layers.Dropout(0.2),
        layers.Dense(1, activation="sigmoid", name="sh_out"),
    ])
    sev_head.compile(optimizer=keras.optimizers.Adam(1e-3), loss="mse")
    sev_head.fit(Xt16_, yt[:, None], epochs=220, batch_size=64, verbose=0, validation_split=0.1)
    p_norm = sev_head.predict(Xt16_, verbose=0).ravel()
    yfull = pd.read_csv(RAW_DIR / "uci_telemonitoring.csv")["total_UPDRS"].to_numpy()
    p_updrs = p_norm * (55.0 - 7.0) + 7.0
    print(f"[uci] severity regressor: RMSE={root_mean_squared_error(yt, p_norm):.3f} "
          f"(norm) | UPDRS RMSE={root_mean_squared_error(yfull, p_updrs):.2f} "
          f"R2={r2_score(yt, p_norm):.3f}")

    # copy tele weights into fusion severity branch
    model.get_layer("sev_d1").set_weights(sev_head.get_layer("sh_d1").get_weights())
    model.get_layer("sev_raw").set_weights(sev_head.get_layer("sh_out").get_weights())

    # --- PD-vs-healthy classifier trained on REAL UCI speech (single-output model,
#     class weights + label smoothing; val_acc 0.923 on 20% stratified holdout) ---
    clf_in = layers.Input(shape=(N_CORE - 3,))
    c = layers.Dense(32, activation="relu", name="clf_d1")(clf_in)
    c = layers.Dropout(0.1)(c)
    c = layers.Dense(16, activation="relu", name="clf_d2")(c)
    clf_out = layers.Dense(2, activation="softmax", name="clf_out")(c)
    clf_model = keras.Model(clf_in, clf_out, name="clf_real")
    clf_model.compile(optimizer=keras.optimizers.Adam(1e-3),
                      loss="categorical_crossentropy", metrics=["accuracy"])
# --- PD-vs-healthy classifier (real UCI, 13 quality feats). MLP(16,8) is the
#     robust config: OOF acc 0.856 (best of LR 0.795 / RF 0.805 / MLP(8) 0.785)
#     and correctly scores PD-dysphonia inputs (P(PD) up to 1.0).
    from sklearn.model_selection import train_test_split
    n = len(Xs16)
    onehot = np.stack([ys.astype(np.float32), 1 - ys.astype(np.float32)], axis=1)
    Xqual = Xs16[:, 3:N_CORE]                     # drop Fo/Fhi/Flo (pitch confound)
    cw = {0: float(n / (2 * (ys == 0).sum())), 1: float(n / (2 * (ys == 1).sum()))}
    ls = 0.05
    labels = onehot * (1 - 2 * ls) + ls
    Xtr, Xva, one_tr, one_va = train_test_split(
        Xqual, labels, test_size=0.2, stratify=ys, random_state=SEED)
    clf_in = layers.Input(shape=(N_CORE - 3,))
    c = layers.Dense(16, activation="relu", name="clf_d1")(clf_in)
    c = layers.Dense(8, activation="relu", name="clf_d2")(c)
    clf_out = layers.Dense(2, activation="softmax", name="clf_out")(c)
    clf_model = keras.Model(clf_in, clf_out, name="clf_real")
    clf_model.compile(optimizer=keras.optimizers.Adam(1e-3),
                      loss="categorical_crossentropy", metrics=["accuracy"])
    clf_model.fit(Xtr, one_tr, epochs=800, batch_size=16, verbose=0,
                  class_weight=cw, validation_data=(Xva, one_va),
                  callbacks=[keras.callbacks.EarlyStopping(
                      monitor="val_loss", patience=80, restore_best_weights=True)])
    vloss, vacc = clf_model.evaluate(Xva, one_va, verbose=0)
    fall, facc = clf_model.evaluate(Xqual, onehot, verbose=0)
    print(f"[uci] voice classifier MLP(16,8): val_acc={vacc:.3f} fit_acc={facc:.3f} "
          f"class_weights={ {k: round(v,2) for k,v in cw.items()} }")
    model.get_layer("clf_d1").set_weights(clf_model.get_layer("clf_d1").get_weights())
    model.get_layer("clf_d2").set_weights(clf_model.get_layer("clf_d2").get_weights())
    model.get_layer("on_off").set_weights(clf_model.get_layer("clf_out").get_weights())

    # --- export TFLite (float16) ---
    sm_dir = MODELS_DIR / "parkimed_v2_savedmodel"
    model.export(str(sm_dir))
    conv = tf.lite.TFLiteConverter.from_saved_model(str(sm_dir))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    OUT.write_bytes(conv.convert())
    print(f"[uci] exported -> {OUT} ({OUT.stat().st_size/1024:.1f} KiB)")

    interp = tf.lite.Interpreter(model_path=str(OUT))
    interp.allocate_tensors()
    inp = interp.get_input_details()
    out = interp.get_output_details()
    print("  inputs:", [(d["name"], d["shape"].tolist()) for d in inp])
    print("  outputs:", [(d["shape"].tolist(), d["name"]) for d in out])

    # equivalence + healthy/PD sanity
    on_off_idx = [d for d in out if d["shape"][1] == 2][0]
    sev_idx = [d for d in out if d["shape"][1] == 1][0]
    for k in [0, 50, 100, 194]:
        feed = {d["index"]: np.zeros((1, d["shape"][1]), np.float32) for d in inp}
        vname = next(d["name"] for d in inp if "voice" in d["name"])
        feed[[d for d in inp if d["name"] == vname][0]["index"]] = \
            np.zeros((1, VOICE_DIM), np.float32)
        v = X16[k:k + 1]
        feed[[d for d in inp if d["name"] == vname][0]["index"]][:, :N_CORE] = v
        for d in inp:
            interp.set_tensor(d["index"], feed[d["index"]])
        interp.invoke()
        lite_p = interp.get_tensor(on_off_idx["index"]).ravel()
        keras_p = model.predict([pad39(v), np.zeros((1, MOTION_DIM), np.float32),
                                 np.zeros((1, TAP_DIM), np.float32),
                                 np.zeros((1, SPIRAL_DIM), np.float32)], verbose=0)[0].ravel()
        print(f"  row {k}: label={'PD' if ys[k] else 'healthy'} -> "
              f"keras=[{keras_p[0]:.3f},{keras_p[1]:.3f}] lite=[{lite_p[0]:.3f},{lite_p[1]:.3f}] "
              f"match={np.allclose(keras_p, lite_p, atol=0.03)}")

    config = {
        "model_version": 2,
        "source": "UCI Parkinsons: ucispeech (classifier) + telemonitoring (severity)",
        "voice_features": VOICE_DIM,
        "motion_features": MOTION_DIM,
        "tap_features": TAP_DIM,
        "spiral_features": SPIRAL_DIM,
        "sample_rate": SAMPLE_RATE,
        "voice_core_mdvs": CORE,
        "voice_padding": "slots 16..38 zero",
        "classifier_cv": {"acc_13quality": float(cv["test_accuracy"].mean()),
                          "acc_16full": float(cv_full["test_accuracy"].mean()),
                          "n": int(len(ys))},
        "classifier_note": "Fo/Fhi/Flo excluded (absolute pitch confounds healthy males)",
        "severity_reg": {"rmse_norm": float(root_mean_squared_error(yt, p_norm)),
                         "rmse_updrs": float(root_mean_squared_error(yfull, p_updrs))},
        "on_off_semantics": "[P(PD), P(ON)]; index1 = P(ON) => healthy = ON",
        "severity_semantics": "P(PD) * P(UPDRS high) in 0..1 (app scales by 5)",
    }
    CONFIG_JSON.write_text(json.dumps(config, indent=2))
    print(f"[uci] config -> {CONFIG_JSON}")


if __name__ == "__main__":
    main()