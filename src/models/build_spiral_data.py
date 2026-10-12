"""Build a real spiral-drawing dataset from UCI 'Parkinson Disease Spiral
Drawings Using Digitized Graphics Tablet' (id 395).

Rows: X;Y;Z;Pressure;GripAngle;Timestamp(ms);TestID
  TestID 0 = Static Spiral Test, 1 = Dynamic Spiral Test, 2 = Circular/Stability
We split each subject file into per-test segments, normalize (x,y) to a unit
box, and compute the same 15-dim spiral features FeatureExtractor.spiral uses
(so the on-device extractor stays in the training distribution).
Labels: parkinson=1 (hw_dataset/parkinson + new_dataset/parkinson), control=0.
"""
from __future__ import annotations

import glob
from pathlib import Path

import numpy as np
import pandas as pd
import sys

sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import RAW_DIR

OUT = RAW_DIR / "spiral_features.csv"
DIRS = [("hw_dataset/parkinson", 1), ("new_dataset/parkinson", 1), ("hw_dataset/control", 0)]


def spiral_feats(x: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Mirror of FeatureExtractor.spiral on a unit-normalized path."""
    if len(x) < 10:
        return np.zeros(15, np.float32)
    cx = x.mean(); cy = y.mean()
    seg = np.hypot(np.diff(x), np.diff(y))
    total = seg.sum()
    out = np.zeros(15, np.float32)
    out[0] = total / len(x)                 # smoothness
    out[1] = total
    out[2] = seg.mean(); out[3] = seg.std()  # tremor central+spread
    out[4] = seg.max()
    out[5] = seg.mean() / (1e-6 + (np.max(x) - np.min(x)))   # speed-like (unitless)
    out[6] = seg.std()
    out[7] = np.max(x) - np.min(x); out[8] = np.max(y) - np.min(y)
    radii = np.hypot(x - cx, y - cy)
    out[9] = radii.std() / (radii.mean() + 1e-6)
    out[10] = len(x)
    out[11] = np.arccos(np.clip((np.max(x) - cx) / (radii.max() + 1e-6), -1, 1))
    out[12] = np.arctan2(y[-1] - cy, x[-1] - cx)
    out[13] = total
    out[14] = y.mean()
    # tremor band via FFT of the radius residual (normalized amplitude at 4-6 Hz
    # window equivalent; sampling unknown so use spectral peak of radial dev)
    if len(radii) > 16:
        r = radii - radii.mean()
        fft = np.abs(np.fft.rfft(r))
        out = np.concatenate([out, [fft[3:7].mean() / (fft[1:].mean() + 1e-6)]])
        out = out[:15]
    return out


def main() -> None:
    rows = []
    for folder, label in DIRS:
        files = sorted(glob.glob(str(RAW_DIR / "uci_spiral" / folder / "*.txt")))
        for f in files:
            subj = Path(f).stem
            data = []
            for ln in open(f):
                p = ln.strip().split(";")
                if len(p) < 7:
                    continue
                try:
                    x, y, t, tid = float(p[0]), float(p[1]), float(p[5]), int(p[6])
                except ValueError:
                    continue
                data.append((x, y, t, tid))
            # split into per-test segments (TestID changes => new gesture)
            segs = []
            cur_tid, cur = None, []
            for x, y, t, tid in data:
                if tid != cur_tid:
                    if cur:
                        segs.append((cur_tid, cur))
                    cur_tid, cur = tid, []
                cur.append((x, y, t))
            if cur:
                segs.append((cur_tid, cur))
            for tid, pts in segs:
                if len(pts) < 10:
                    continue
                x = np.array([p[0] for p in pts], np.float64)
                y = np.array([p[1] for p in pts], np.float64)
                span = max(x.max() - x.min(), y.max() - y.min()) + 1e-6
                xn = (x - x.min()) / span
                yn = (y - y.min()) / span
                feats = spiral_feats(xn, yn)
                rows.append([subj, tid, label] + list(feats))
    df = pd.DataFrame(rows, columns=["subject", "test_id", "label"] +
                      [f"s{i}" for i in range(15)])
    df.to_csv(OUT, index=False)
    print(f"[spiral] wrote {OUT} rows={len(df)} "
          f"PD={int(df.label.sum())} ctrl={int((df.label==0).sum())} "
          f"subjects={df.subject.nunique()} "
          f"tests={sorted(df.test_id.unique().tolist())}")


if __name__ == "__main__":
    main()