"""Model v3 — multimodal fusion with REAL heads on real datasets.

Heads (each trained on real data; scalers embedded in the graph):
  voice  [39]  MLP(16,8) on 13 pitch-free quality slots (UCI speech) + UPDRS sev
  spiral [15]  MLP(16,8) on normalized tablet spirals    (UCI spiral, id 395)
  visual [20]  MLP(16,8) on webcam finger-tap features   (ROC-HCI, real PD)
Reserved (no trained head yet -> gate forced to 0): motion[36], tap[20], gait[36].

Fusion (pure arithmetic => TFLite-safe, no external scaler at runtime):
  presence gate   g_m = 1 if raw L2 norm > EPS else 0   (absent task => ignored)
  fused_logit    = (sum g_m L_m) / (sum g_m)
  P_off          = sigmoid(fused_logit);  on_off = [P_off, P_ON]
  severity       = P_off * (sum g_m sev_m)/(sum g_m)    (sev: voice=UPDRS,
                                                         visual=Rating/4, spiral=1)
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
from sklearn.model_selection import train_test_split

sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import MODELS_DIR, RAW_DIR, SEED

OUT = MODELS_DIR / "parkimed_model.tflite"
CONFIG_JSON = MODELS_DIR / "preprocessing_config.json"

VOICE_DIM, MOTION_DIM, TAP_DIM, SPIRAL_DIM, VISUAL_DIM, GAIT_DIM = 39, 36, 20, 15, 20, 24
N_CORE = 16
EPS = 1e-4


def mlp_head(X, y, name, seed=SEED):
    """MLP(16,8)->softmax(2) on standardized features (mirror of the proven v2
    voice config: categorical CE + class weights + early stop). Returns
    (model, Xs, (m,s), holdout_acc)."""
    m, s = X.mean(0), X.std(0) + 1e-6
    Xs = (X - m) / s
    oneh = np.stack([1 - y, y], 1).astype(np.float32)          # [P(PD), P(ON)]
    cw = {0: float(len(y) / (2 * (y == 0).sum())), 1: float(len(y) / (2 * (y == 1).sum()))}
    tr, va = train_test_split(np.arange(len(y)), test_size=0.2, stratify=y, random_state=seed)
    inp = layers.Input((Xs.shape[1],))
    x = layers.Dense(16, activation="relu")(inp)
    x = layers.Dense(8, activation="relu")(x)
    out = layers.Dense(2, activation="softmax")(x)
    mm = keras.Model(inp, out, name=name)
    mm.compile(keras.optimizers.Adam(1e-3), loss="categorical_crossentropy",
               metrics=["accuracy"])
    mm.fit(Xs[tr], oneh[tr], epochs=800, batch_size=16, verbose=0,
           class_weight=cw, validation_data=(Xs[va], oneh[va]),
           callbacks=[keras.callbacks.EarlyStopping("val_loss", patience=80,
                                                    restore_best_weights=True)])
    _, acc = mm.evaluate(Xs[va], oneh[va], verbose=0)
    return mm, Xs, (m, s), float(acc)


def sev_head(X, t, seed=SEED):
    """16-hidden -> sigmoid(1) severity regressor on normalized targets."""
    m, s = X.mean(0), X.std(0) + 1e-6
    Xs = (X - m) / s
    tn = (t - t.min()) / (t.max() - t.min() + 1e-6)
    inp = layers.Input((Xs.shape[1],))
    x = layers.Dense(16, activation="relu")(inp)
    out = layers.Dense(1, activation="sigmoid")(x)
    mm = keras.Model(inp, out, name="sev")
    mm.compile(keras.optimizers.Adam(1e-3), "mse")
    mm.fit(Xs, tn, epochs=350, batch_size=64, verbose=0, validation_split=0.1,
           callbacks=[keras.callbacks.EarlyStopping("val_loss", patience=60,
                                                    restore_best_weights=True)])
    return mm, (m, s)


def logit_layer(inp, ws):
    """Dense(16,8)->Dense(1); ws = [(W1,b1),(W2,b2),(W3,b3)] from a trained head."""
    a = layers.Dense(16, activation="relu")
    x = a(inp); a.set_weights(ws[0])
    b = layers.Dense(8, activation="relu")
    y = b(x); b.set_weights(ws[1])
    c = layers.Dense(1)
    z = c(y); c.set_weights(ws[2])
    return z


def sev_weights(model):
    return [l.get_weights() for l in model.layers if l.get_weights()]


def sev_layer(inp, ws):
    a = layers.Dense(16, activation="relu")
    x = a(inp); a.set_weights(ws[0])
    b = layers.Dense(1, activation="sigmoid")
    z = b(x); b.set_weights(ws[1])
    return z


def flatten_weights(model):
    return [l.get_weights() for l in model.layers if l.get_weights()]


def logit_weights(ws):
    """Transform softmax(2) head weights [d1, d2, softmax] into [d1, d2, Dense(1)]
    taking the PD logit (softmax class 1 = PD)."""
    w, b = ws[2][0].astype(np.float32), np.asarray(ws[2][1], np.float32)
    return [ws[0], ws[1], [w[:, 1:2], np.asarray([b[1]], np.float32)]]


def presence_gate(x, eps=EPS):
    return layers.Lambda(lambda t: tf.minimum(
        1.0, tf.sqrt(tf.reduce_sum(t * t, axis=1, keepdims=True)) / eps))(x)


def main() -> None:
    from models.train_real_uci import load_uci_speech, load_uci_tele
    Xv16, yv = load_uci_speech()
    Xt16, yt = load_uci_tele()

    spiral = pd.read_csv(RAW_DIR / "spiral_features.csv")
    Xsp = spiral[[f"s{i}" for i in range(15)]].to_numpy(np.float32)
    ysp = spiral["label"].to_numpy(np.int64)

    tap = pd.read_csv(RAW_DIR / "roc_hci_tap/fingertapping_features_severity_diagnosis_June13_2023.csv")
    leak = ("Rating", "Comment", "Check", "diagnosed", "gender", "age", "Unnamed")
    num = tap.select_dtypes(include=[np.number]).drop(
        columns=[c for c in tap.select_dtypes(include=[np.number]) if any(k in c for k in leak)],
        errors="ignore")
    diag = (tap["diagnosed"] == "yes").to_numpy(np.int64)
    rating = tap["Rating"].to_numpy(np.float32)
    corr = np.abs(np.nan_to_num([np.corrcoef(num[c], diag)[0, 1] for c in num.columns]))
    top = num.columns[np.argsort(corr)[-VISUAL_DIM:]].tolist()
    Xvt = num[top].to_numpy(np.float32)

    # gait ON/OFF (real REMAP Kinect turns, medication-labeled)
    gait = pd.read_csv(RAW_DIR / "gait_onoff_features.csv")
    Xga = gait[[f"g{i}" for i in range(24)]].to_numpy(np.float32)
    yga_off = (1 - gait["state"].to_numpy(np.int64))          # 1 = OFF medication

    voice_h, Xv_s, (mv, sv), v_acc = mlp_head(Xv16[:, 3:N_CORE], yv, "clf_voice")
    Msp, Ssp = Xv16.mean(0), Xv16.std(0) + 1e-6            # speech scaler (16 slots)
    Xt_scaled = (Xt16 - Msp) / Ssp                          # tele rows, speech scale
    sev_vh, _ = sev_head(Xt_scaled, yt)                     # UPDRS severity (0..1)
    spiral_h, Xsp_s, (msp, ssp), sp_acc = mlp_head(Xsp, ysp, "clf_spiral")
    vis_h, Xvt_s, (mvt, svt), vt_acc = mlp_head(Xvt, diag, "clf_visual")
    gait_h, Xga_s, (mga, sga), ga_acc = mlp_head(Xga, yga_off, "clf_gait")
    sev_wh, _ = sev_head(Xvt, rating / 4.0)
    print(f"[v3] gait ON/OFF head acc={ga_acc:.3f} (real REMAP, off=1)")

    w_voice = logit_weights(flatten_weights(voice_h))
    w_spiral = logit_weights(flatten_weights(spiral_h))
    w_visual = logit_weights(flatten_weights(vis_h))
    w_gait = logit_weights(flatten_weights(gait_h))
    w_sevv = flatten_weights(sev_vh)
    w_sevw = flatten_weights(sev_wh)

    print(f"[v3] heads: voice_acc={v_acc:.3f} spiral_acc={sp_acc:.3f} visual_acc={vt_acc:.3f}")
    print(f"[v3] visual columns: {top}")

    voice = layers.Input((VOICE_DIM,), name="voice")
    motion = layers.Input((MOTION_DIM,), name="motion")
    tap_in = layers.Input((TAP_DIM,), name="tap")
    spiral_i = layers.Input((SPIRAL_DIM,), name="spiral")
    visual = layers.Input((VISUAL_DIM,), name="visual")
    gai = layers.Input((GAIT_DIM,), name="gait")

    def stdz(x, m, s):
        return (x - tf.constant(m.astype(np.float32))) / tf.constant(s.astype(np.float32))

    Lv = logit_layer(stdz(voice[:, 3:N_CORE], mv, sv), w_voice)   # 13 quality cols
    Ls = logit_layer(stdz(spiral_i, msp, ssp), w_spiral)
    Lw = logit_layer(stdz(visual, mvt, svt), w_visual)
    Lg = logit_layer(stdz(gai, mga, sga), w_gait)                  # off-med logit
    sev_v = sev_layer(stdz(voice[:, 0:N_CORE], Msp, Ssp), w_sevv) # 16-slot speech scale
    sev_w = sev_layer(stdz(visual, mvt, svt), w_sevw)

    gv = presence_gate(voice[:, 3:N_CORE])
    gs = presence_gate(spiral_i)
    gw = presence_gate(visual)
    gg = presence_gate(gai)
    g_m = presence_gate(motion) * 0.0
    g_t = presence_gate(tap_in) * 0.0

    wsum = gv + gs + gw + gg + g_m + g_t + 1e-6
    fused_logit = (gv * Lv + gs * Ls + gw * Lw + gg * Lg) / wsum
    p_off = layers.Activation("sigmoid", name="p_off")(fused_logit)
    sev_scale = (gv * sev_v + gs * 1.0 + gw * sev_w + gg * 1.0) / (gv + gs + gw + gg + 1e-6)
    severity = layers.Multiply(name="severity")([p_off, sev_scale])
    on_off = layers.Concatenate(name="on_off")([p_off, 1.0 - p_off])

    model = keras.Model([voice, motion, tap_in, spiral_i, visual, gai],
                        [on_off, severity], name="parkimed_v3")

    sm = MODELS_DIR / "parkimed_v3_savedmodel"
    model.export(str(sm))
    conv = tf.lite.TFLiteConverter.from_saved_model(str(sm))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    OUT.write_bytes(conv.convert())
    print(f"[v3] exported -> {OUT} ({OUT.stat().st_size / 1024:.1f} KiB)")

    interp = tf.lite.Interpreter(model_path=str(OUT))
    interp.allocate_tensors()
    ins = interp.get_input_details(); outs = interp.get_output_details()
    print("  inputs:", [(d["name"], d["shape"].tolist()) for d in ins])
    print("  outputs:", [(d["shape"].tolist(), d["name"]) for d in outs])

    def run(v, sp_, vi_, ga=None):
        feed = {d["index"]: np.zeros((1, d["shape"][1]), np.float32) for d in ins}
        for d in ins:
            n = d["name"]
            if "voice" in n and v is not None: feed[d["index"]][0, :len(v)] = v
            if "spiral" in n and sp_ is not None: feed[d["index"]][0, :len(sp_)] = sp_
            if "visual" in n and vi_ is not None: feed[d["index"]][0, :len(vi_)] = vi_
            if "gait" in n and ga is not None: feed[d["index"]][0, :len(ga)] = ga
        for d in ins: interp.set_tensor(d["index"], feed[d["index"]])
        interp.invoke()
        on = interp.get_tensor([o for o in outs if o["shape"][1] == 2][0]["index"]).ravel()
        sev = interp.get_tensor([o for o in outs if o["shape"][1] == 1][0]["index"]).ravel()[0]
        return on, sev

    rng = np.random.RandomState(0)
    hi_v = Xv16[yv == 0]; hi_s = Xsp[ysp == 0]; hi_t = Xvt[diag == 0]
    pd_v = Xv16[yv == 1]; pd_s = Xsp[ysp == 1]; pd_t = Xvt[diag == 1]
    on_g = Xga[yga_off == 0]; off_g = Xga[yga_off == 1]   # on/off medication gait
    for name, v, s, vi, ga in [
        ("healthy  voice+spiral+visual",  hi_v[0], hi_s[0], hi_t[0], None),
        ("PD       voice+spiral+visual",  pd_v[0], pd_s[0], pd_t[0], None),
        ("PD       voice only",           pd_v[0], None, None, None),
        ("PD       spiral only",          None, pd_s[0], None, None),
        ("PD       visual-tap only",      None, None, pd_t[0], None),
        ("PD       gait OFF-med only",    None, None, None, off_g[0]),
        ("healthy  gait ON-med only",     None, None, None, on_g[0]),
    ]:
        on, sev = run(v, s, vi, ga)
        print(f"[v3] {name:28s} P(PD)={on[0]:.2f} P(ON)={on[1]:.2f} sev={sev*5:.1f}/5")

    cfg = {
        "model_version": 3,
        "inputs": {"voice": VOICE_DIM, "motion": MOTION_DIM, "tap": TAP_DIM,
                   "spiral": SPIRAL_DIM, "visual": VISUAL_DIM, "gait": GAIT_DIM},
        "visual_feature_columns": top, "visual_feature_source": "ROC-HCI MediaPipe (repo feature_extraction.py)",
        "head_accs": {"voice": float(v_acc), "spiral": float(sp_acc),
                      "visual": float(vt_acc), "gait_on_off": float(ga_acc)},
        "on_off_semantics": "[P(PD/OFF-med), P(ON)]; index1 = P(ON)",
        "severity_semantics": "P(PD) * avg(modality sev scale) in 0..1 (app x5)",
        "sources": "real: UCI speech + UCI telemonitoring + UCI spiral(id395) + ROC-HCI tapping + REMAP gait(ON/OFF med)",
        "notes": "motion/tap reserved (touch-screen heads pending); gait = camera Kinect turns (ON/OFF med)",
    }
    CONFIG_JSON.write_text(json.dumps(cfg, indent=2))
    print(f"[v3] config -> {CONFIG_JSON}")


if __name__ == "__main__":
    main()