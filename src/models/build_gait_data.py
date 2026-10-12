"""Build a real gait dataset from REMAP (Bristol) turning-of-gait 2D skeletons.

REMAP open dataset: Kinect RGB -> HRNet 2D skeletons (17 joints, 34 cols
x0,y0,...,x16,y16 per frame) of turning episodes; filenames encode
PD (Pt<id>_PD_n_<ep>) vs Control (Pt<id>_C_n_<ep>). PD participants were
recorded with medication labels (On/Off) in the human-rated .xls; here we use
PD-vs-Control as the diagnostic target (real recordings, home-like setting).

Per-episode gait features (24 dims) computed on the raw 2D joint trajectories:
  - overall & per-joint movement speed stats
  - body sway (spine base/shoulder lateral std, range)
  - left/right arm-swing asymmetry (wrist amplitude)
  - step cadence proxy (ankle zero-crossing of vertical motion) + asymmetry
  - turn duration & size-normalised path
"""
from __future__ import annotations

import glob
from pathlib import Path

import numpy as np
import pandas as pd

import sys; sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import RAW_DIR

OUT = RAW_DIR / "gait_features.csv"
ROOT = (RAW_DIR /
        "remap/extracted/21h9f9e30v9cl2fapjggz4q1x7/Turning/Data/"
        "turning_2D3D_skeletons_coarsened/Turning_coarsen_CSV")


def feats_kp(kp: np.ndarray) -> np.ndarray:
    """kp: (T,17,2). Returns 24-dim feature vector (unit-normalized)."""
    T = kp.shape[0]
    if T < 12:
        return np.zeros(24, np.float32)
    # size normalisation (subject distance/scale)
    span = np.percentile(kp[:, :, 0], 98) - np.percentile(kp[:, :, 0], 2)
    span = max(span, 1e-6)
    k = kp / span
    d = np.linalg.norm(np.diff(k, axis=0), axis=2)           # (T-1, 17)
    spd = d.mean(axis=0)                                      # per-joint speed
    out = np.zeros(24, np.float32)
    out[0] = d.mean(); out[1] = d.std()
    out[2:11] = spd[:9]                                       # head/neck/shoulders/elbows/wrists
    out[11] = spd[[4, 7]].mean()                              # wrists mean
    # body sway: lateral (x) of spine/shoulder (joints 1 neck, 8 mid-hip)
    sway_x = k[:, 1, 0] * 0.5 + k[:, 8, 0] * 0.5
    out[12] = sway_x.std(); out[13] = sway_x.max() - sway_x.min()
    # arm-swing asymmetry: L/R wrist lateral amplitude
    lw, rw = k[:, 7, 0], k[:, 4, 0]
    out[14] = abs(np.ptp(lw) - np.ptp(rw))
    out[15] = abs(lw.std() - rw.std())
    # step cadence proxy: ankle vertical oscillation (joints 11 L, 14 R)
    for i, a in ((16, 11), (17, 14)):
        v = k[1:, a, 1] - k[:-1, a, 1]
        zc = ((v[:-1] * v[1:]) < 0).sum()
        out[i] = zc / (T - 1)
    out[18] = abs(out[16] - out[17])                          # step asymmetry
    out[19] = np.linalg.norm(k[-1] - k[0])                    # net displacement (norm)
    out[20] = (np.abs(np.diff(k[:, :, 0], axis=0)).mean())    # mean lateral motion
    out[21] = out[20] / (out[0] + 1e-6)                       # lateral fraction
    out[22] = T
    out[23] = d[:, [4, 7]].max()                              # peak wrist swing
    return out.astype(np.float32)


def main() -> None:
    rows = []
    for path in sorted(glob.glob(str(ROOT) + "/*")):
        if not Path(path).is_dir():
            continue
        stem = Path(path).stem          # Pt{id}_{PD|C}_n_{ep}
        label = 1 if stem.split("_")[1] == "PD" else 0
        kp_file = Path(path) / "input_2D" / "keypoints.csv"
        if not kp_file.exists():
            continue
        try:
            a = np.loadtxt(kp_file, delimiter=",", ndmin=2)
        except Exception:
            continue
        if a.ndim != 2 or a.shape[1] < 34:
            continue
        T = a.shape[0]
        kp = a[:, :34].reshape(T, 17, 2)
        feats = feats_kp(kp)
        rows.append([stem, label] + list(feats))
    df = pd.DataFrame(rows, columns=["clip", "label"] + [f"g{i}" for i in range(24)])
    df.to_csv(OUT, index=False)
    print(f"[gait] wrote {OUT} clips={len(df)} PD={int((df.label==1).sum())} "
          f"C={int((df.label==0).sum())} subjects={df.clip.str.split('_n_').str[0].nunique()}")


if __name__ == "__main__":
    main()